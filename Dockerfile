# ---- build stage: native image ----
FROM ghcr.io/graalvm/native-image-community:21 AS build
RUN microdnf install -y findutils maven && microdnf clean all || true
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -B -Pnative native:compile -DskipTests

# ---- runtime stage ----
FROM debian:bookworm-slim
WORKDIR /app
COPY --from=build /workspace/target/employee-service /app/service
# Oracle ADB wallet (unzip Wallet_*.zip to ./wallet before docker build)
COPY wallet /app/wallet
ENV TNS_ADMIN=/app/wallet
ENV ORACLE_TNS_ALIAS=mytestdb_low
EXPOSE 8080
ENTRYPOINT ["/app/service"]
