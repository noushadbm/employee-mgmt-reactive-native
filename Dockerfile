# ---- build stage: GraalVM native image (linux/amd64 for Cloud Run) ----
FROM --platform=linux/amd64 ghcr.io/graalvm/native-image-community:21 AS build
# findutils + maven for the wrapper build
RUN microdnf install -y findutils maven 2>/dev/null && microdnf clean all || (dnf install -y findutils maven && dnf clean all) || true
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -B -Pnative native:compile -DskipTests

# ---- runtime stage (Cloud Run) ----
FROM --platform=linux/amd64 debian:bookworm-slim
RUN apt-get update \
  && apt-get install -y --no-install-recommends unzip ca-certificates \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/employee-service /app/service
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh \
  && mkdir -p /app/wallet /mnt/wallet
# Wallet zip is mounted at runtime (e.g. GCS FUSE volume), NOT baked in.
# At startup the entrypoint unzips $WALLET_ZIP into $TNS_ADMIN.
ENV TNS_ADMIN=/app/wallet
ENV WALLET_ZIP=/mnt/wallet/Wallet_mytestdb.zip
ENV ORACLE_TNS_ALIAS=mytestdb_low
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["/app/service"]
