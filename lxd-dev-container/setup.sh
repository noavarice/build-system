#!/usr/bin/env bash
# Create (or re-improvision) the LXD dev container "build-system-dev" for this repo.
#
# Requires: a working LXD 5+/6.x server with the 'ubuntu' or 'images' remote.
# Creates the container from Ubuntu 26.04 with the cloud-init in
# cloud-init-build-system-dev.yaml, bind-mounts this repo into /workspace/build-system,
# starts it, and waits for cloud-init to finish.
#
# Re-runnable: existing named container is stopped+deleted before re-creating.
# User state that must survive re-provisioning (~/.m2, ~/.config/opencode) lives
# in LXD custom storage volumes on the 'default' pool; those volumes are
# independent of the container, so deleting/recreating the container preserves them.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"
CLOUD_INIT="$HERE/cloud-init-build-system-dev.yaml"

CONTAINER="build-system-dev"
IMAGE="ubuntu:26.04"
MOUNT_SOURCE="$REPO_ROOT"
MOUNT_TARGET="/workspace/build-system"

POOL="default"
M2_VOLUME="build-system-m2"
OPENCODE_CONFIG_VOLUME="build-system-opencode-config"

# Persistent custom storage volumes. Created once here and reused on later runs;
# they outlive container (re)creations, which is the point (see README).
for vol in "$M2_VOLUME" "$OPENCODE_CONFIG_VOLUME"; do
  if lxc storage volume show "$POOL" "$vol" >/dev/null 2>&1; then
    echo "Storage volume '$vol' already exists; reusing (data preserved)"
  else
    echo "Creating storage volume '$vol'..."
    lxc storage volume create "$POOL" "$vol" --type filesystem
  fi
done

if ! lxc info "$CONTAINER" >/dev/null 2>&1; then
  : # not present - good
else
  echo "Removing existing container '$CONTAINER'..."
  lxc delete --force "$CONTAINER"
fi

echo "Initializing '$CONTAINER' from $IMAGE with cloud-init..."
lxc init "$IMAGE" "$CONTAINER" -c user.user-data="$(cat "$CLOUD_INIT")"

echo "Mounting $MOUNT_SOURCE -> $MOUNT_TARGET ..."
lxc config device add "$CONTAINER" project disk \
  source="$MOUNT_SOURCE" path="$MOUNT_TARGET"

# Mount the persistent state volumes. These are custom storage volumes (not host
# bind mounts), so their contents survive any container deletion/recreation.
echo "Mounting persistent volume $M2_VOLUME -> /home/noavarice/.m2 ..."
lxc config device add "$CONTAINER" m2 disk \
  pool="$POOL" source="$M2_VOLUME" path="/home/noavarice/.m2"
echo "Mounting persistent volume $OPENCODE_CONFIG_VOLUME -> /home/noavarice/.config/opencode ..."
lxc config device add "$CONTAINER" opencode-config disk \
  pool="$POOL" source="$OPENCODE_CONFIG_VOLUME" path="/home/noavarice/.config/opencode"

echo "Mapping the container 'noavarice' user (uid/gid 1000) to the host uid/gid 1000..."
lxc config set "$CONTAINER" raw.idmap 'both 1000 1000'

echo "Starting '$CONTAINER'..."
lxc start "$CONTAINER"

echo "Waiting for cloud-init to complete..."
# cloud-init status --wait exits non-zero (2) when the status is 'degraded done'
# (here caused by a schema lint), which would trip set -e and skip the trailing
# home reclaim. Tolerate it; ownership is now handled by runcmd itself.
lxc exec "$CONTAINER" -- cloud-init status --wait || true

echo "Fixing home ownership (cloud-image quirk; also done by cloud-init)."
lxc exec "$CONTAINER" -- chown -R noavarice:noavarice /home/noavarice

echo
echo "=== Done. Verify the toolchain ==="
lxc exec "$CONTAINER" -- bash -lc 'java -version; echo ---; javac -version; echo ---; mvn -version | head -3; echo ---; node --version; npm --version; echo ---; git --version'
echo "--- project mount ---"
lxc exec "$CONTAINER" -- ls /workspace/build-system/pom.xml
echo
echo "=== Drop into a shell ==="
echo "  lxc exec $CONTAINER -- su - noavarice"
echo "  # then: cd /workspace/build-system && mvn clean install"