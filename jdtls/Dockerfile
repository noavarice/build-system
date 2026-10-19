# The latest jdtls snapshot requires Java 25.
FROM docker.io/library/maven:3.9.11-eclipse-temurin-25

# jdtls' launcher (bin/jdtls) is a Python 3 script.
RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 ca-certificates \
  && rm -rf /var/lib/apt/lists/*

ARG JDTLS_URL=https://www.eclipse.org/downloads/download.php?file=/jdtls/snapshots/jdt-language-server-latest.tar.gz
RUN curl -fsSL "$JDTLS_URL" -o /tmp/jdtls.tar.gz \
  && mkdir -p /opt/jdtls \
  && tar --no-same-owner -xzf /tmp/jdtls.tar.gz -C /opt/jdtls \
  && rm /tmp/jdtls.tar.gz \
  && chmod +x /opt/jdtls/bin/jdtls \
  && ln -s /opt/jdtls/bin/jdtls /usr/local/bin/jdtls

WORKDIR /workspace
