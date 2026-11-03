# Supply reviewed linux/arm64 image references with @sha256 digests at build time.
ARG JAVA_IMAGE=eclipse-temurin:21-jdk-jammy@sha256:3cecce1b41af0b39a980b7c8d955354fc9538435bd30127c3fe3ddc7f01029e3
ARG POSTGRES_IMAGE=postgres:17.11-bookworm@sha256:a85953d6f830fd55a12929df3b7a4fa94dc96d652de62d2d714e0867ccb3670d
FROM ${JAVA_IMAGE} AS java
FROM ${POSTGRES_IMAGE}
USER root
COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:$PATH
# Network is allowed only during the separately authorized image preparation.
RUN apt-get update && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*
COPY m2 /var/lib/postgresql/.m2/repository
COPY project /workspace
COPY entrypoint.sh /usr/local/bin/isolation-entrypoint
COPY network_guard.py /usr/local/lib/isolation/network_guard.py
COPY lifecycle.py /usr/local/lib/isolation/lifecycle.py
RUN chmod -R a+rX /workspace /var/lib/postgresql/.m2 \
    && chmod 755 /usr/local/bin/isolation-entrypoint \
    && chmod 644 /usr/local/lib/isolation/network_guard.py /usr/local/lib/isolation/lifecycle.py \
    && mkdir -p /var/lib/postgresql/isolation-data /evidence \
    && chown postgres:postgres /var/lib/postgresql/isolation-data /evidence
ENV HOME=/var/lib/postgresql
USER postgres
ENTRYPOINT ["/usr/local/bin/isolation-entrypoint"]
# Build-time checks only: never invoke the maintenance runner or initdb.
RUN postgres --version \
    && postgres --version | grep -E '^postgres \(PostgreSQL\) 17\.11( |$)' \
    && java -version \
    && python3 --version \
    && python3 -c "import importlib.util,pathlib,subprocess; p=pathlib.Path('/workspace/tools/flyway/run-public-v4.py'); s=importlib.util.spec_from_file_location('launcher',p); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); jars=m.dependency_paths(pathlib.Path.home()/'.m2/repository'); subprocess.run(['javac','--release','21','-proc:none','-d','/tmp/runner-compile','-cp',':'.join(map(str,jars)),'/workspace/tools/flyway/PublicV4Runner.java'],check=True); print('PASS: seven pinned JARs and Java compilation')"
