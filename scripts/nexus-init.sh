#!/bin/sh
#
# Idempotently configures the local Nexus instance used for self-hosted builds:
#   - sets the admin password
#   - creates a proxy repository for the Shibboleth releases (OpenSAML 5.x, which
#     spring-security-saml2-service-provider depends on, is not published to Maven Central)
#   - adds that repository to the maven-public group
#
set -eu

NEXUS_URL="${NEXUS_URL:-http://nexus:8081}"
NEXUS_ADMIN_USER="${NEXUS_ADMIN_USER:-admin}"
NEXUS_ADMIN_PASSWORD="${NEXUS_ADMIN_PASSWORD:-admin123}"
SHIBBOLETH_REPOSITORY="${SHIBBOLETH_REPOSITORY:-shibboleth}"
SHIBBOLETH_REMOTE_URL="${SHIBBOLETH_REMOTE_URL:-https://build.shibboleth.net/maven/releases}"

log() {
  echo "[nexus-init] $*"
}

log "waiting for $NEXUS_URL"
until curl -fsS -o /dev/null "$NEXUS_URL/service/rest/v1/status"; do
  sleep 3
done

# Nexus generates the initial admin password on first start and removes the file once the
# password has been changed.
if [ -f /nexus-data/admin.password ]; then
  initial_password=$(cat /nexus-data/admin.password)
  log "setting admin password"
  curl -fsS -u "$NEXUS_ADMIN_USER:$initial_password" \
    -X PUT -H 'Content-Type: text/plain' \
    --data "$NEXUS_ADMIN_PASSWORD" \
    "$NEXUS_URL/service/rest/v1/security/users/admin/change-password"
else
  log "admin password file not found, assuming the password is already configured"
fi

auth="$NEXUS_ADMIN_USER:$NEXUS_ADMIN_PASSWORD"

# Nexus Community Edition refuses to serve repositories until its EULA is accepted.
eula=$(curl -fsS -u "$auth" "$NEXUS_URL/service/rest/v1/system/eula")
if echo "$eula" | jq -e '.accepted' >/dev/null; then
  log "EULA already accepted"
else
  log "accepting EULA"
  echo "$eula" | jq '.accepted = true' \
    | curl -fsS -u "$auth" -X POST -H 'Content-Type: application/json' -d @- \
      "$NEXUS_URL/service/rest/v1/system/eula"
fi

if curl -fsS -o /dev/null -u "$auth" "$NEXUS_URL/service/rest/v1/repositories/$SHIBBOLETH_REPOSITORY"; then
  log "repository '$SHIBBOLETH_REPOSITORY' already exists"
else
  log "creating proxy repository '$SHIBBOLETH_REPOSITORY'"
  curl -fsS -u "$auth" \
    -X POST -H 'Content-Type: application/json' \
    -d "{
      \"name\": \"$SHIBBOLETH_REPOSITORY\",
      \"online\": true,
      \"storage\": {\"blobStoreName\": \"default\", \"strictContentTypeValidation\": true},
      \"proxy\": {\"remoteUrl\": \"$SHIBBOLETH_REMOTE_URL\", \"contentMaxAge\": 1440, \"metadataMaxAge\": 1440},
      \"negativeCache\": {\"enabled\": true, \"timeToLive\": 1440},
      \"httpClient\": {\"blocked\": false, \"autoBlock\": true},
      \"maven\": {\"versionPolicy\": \"RELEASE\", \"layoutPolicy\": \"STRICT\"}
    }" \
    "$NEXUS_URL/service/rest/v1/repositories/maven/proxy"
fi

group_url="$NEXUS_URL/service/rest/v1/repositories/maven/group/maven-public"
group=$(curl -fsS -u "$auth" "$group_url")
if echo "$group" | jq -e --arg r "$SHIBBOLETH_REPOSITORY" '.group.memberNames | index($r)' >/dev/null; then
  log "maven-public already contains '$SHIBBOLETH_REPOSITORY'"
else
  log "adding '$SHIBBOLETH_REPOSITORY' to maven-public"
  echo "$group" | jq --arg r "$SHIBBOLETH_REPOSITORY" '.group.memberNames += [$r]' \
    | curl -fsS -u "$auth" -X PUT -H 'Content-Type: application/json' -d @- "$group_url"
fi

log "done"
