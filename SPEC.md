# Spring Boot Reactive Native Service — SPEC

## 1. Overview
A reactive HTTP service built with Spring Boot and Project Reactor (WebFlux), packaged as a GraalVM native image.

## 2. Requirements
| # | Requirement | Decision |
|---|-------------|----------|
| 1 | Java 21 | `java.version=21`; build and run on a GraalVM JDK 21 (LTS) |
| 2 | Maven project | `pom.xml`, packaging `jar`, parent `spring-boot-starter-parent` |
| 3 | Reactive library | Spring WebFlux + Project Reactor (`Mono`/`Flux`), Netty runtime |
| 4 | Maven 3.x.x | Maven 3.9.x. Enforced by `maven-enforcer-plugin` (`requireMavenVersion [3.9.0,4.0.0)`); ship Maven Wrapper (`mvnw`) pinned to 3.9.x |
| 5 | Native image | `native-maven-plugin` (GraalVM) via the Spring Boot `native` profile; `mvn -Pnative native:compile` |

## 3. Technology Stack
- Spring Boot 3.3.x or later 3.x (Java 21 baseline, native AOT support)
- `spring-boot-starter-webflux` (reactive web, Netty)
- `spring-boot-starter-actuator` (health and readiness probes)
- `spring-boot-starter-validation`
- Test: `spring-boot-starter-test`, `reactor-test` (`StepVerifier`), `WebTestClient`
- Persistence: Spring Data R2DBC (`spring-boot-starter-data-r2dbc`) with the in-memory H2 R2DBC driver (`io.r2dbc:r2dbc-h2`, runtime). Avoid JDBC/JPA, which is blocking.

## 4. Project Layout
```
.
├── pom.xml
├── README.md                              # setup, build, run, API usage (see section 11)
├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties   # Maven 3.9.x
├── src/main/java/com/example/app/
│   ├── Application.java
│   ├── web/        # EmployeeController (@RestController returning Mono/Flux)
│   ├── service/    # EmployeeService, reactive business logic
│   ├── repository/ # EmployeeRepository extends ReactiveCrudRepository<Employee, Long>
│   ├── model/      # Employee (record/entity), EmployeeRequest DTO
│   └── config/
├── src/main/resources/application.yml
├── src/main/resources/schema.sql          # employee table DDL
├── src/test/java/com/example/app/
└── Dockerfile
```

## 5. pom.xml Essentials
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.5</version>
</parent>

<properties>
  <java.version>21</java.version>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webflux</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-r2dbc</artifactId></dependency>
  <dependency><groupId>io.r2dbc</groupId><artifactId>r2dbc-h2</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>io.projectreactor</groupId><artifactId>reactor-test</artifactId><scope>test</scope></dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-enforcer-plugin</artifactId>
      <executions>
        <execution>
          <id>enforce-versions</id>
          <goals><goal>enforce</goal></goals>
          <configuration><rules>
            <requireMavenVersion><version>[3.9.0,4.0.0)</version></requireMavenVersion>
            <requireJavaVersion><version>[21,22)</version></requireJavaVersion>
          </rules></configuration>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>org.graalvm.buildtools</groupId>
      <artifactId>native-maven-plugin</artifactId>
    </plugin>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```
The parent already defines the `native` profile (AOT processing plus `native-maven-plugin`).

## 6. Functional Scope

### 6.1 Employee model
| Field | Type | Rules |
|-------|------|-------|
| `id` | Long | Auto-generated primary key, read-only |
| `firstName` | String | Required, not blank, max 100 |
| `lastName` | String | Required, not blank, max 100 |
| `email` | String | Required, valid email, unique |
| `department` | String | Optional, max 100 |
| `jobTitle` | String | Optional, max 100 |
| `salary` | BigDecimal | Optional, `>= 0` |
| `hireDate` | LocalDate | Optional |

### 6.2 Employee API (base path `/api/employees`, JSON)
| Operation | Method and path | Request | Success | Errors |
|-----------|-----------------|---------|---------|--------|
| ADD | `POST /api/employees` | `Mono<EmployeeRequest>` body | `201 Created`, `Location` header, created employee | `400` validation failure, `409` duplicate email |
| LIST | `GET /api/employees` | Optional `?department=` filter | `200`, `Flux<Employee>` (JSON array). `Accept: text/event-stream` streams it | none |
| GET by id | `GET /api/employees/{id}` | none | `200`, employee | `404` not found |
| UPDATE | `PUT /api/employees/{id}` | Full `EmployeeRequest` body | `200`, updated employee | `400`, `404`, `409` |
| DELETE | `DELETE /api/employees/{id}` | none | `204 No Content` | `404` |

All handlers return `Mono`/`Flux` only. A global `@RestControllerAdvice` maps errors to a consistent JSON body (`timestamp`, `status`, `error`, `message`, `path`). Validation errors also list the offending fields.

Example:
```
POST /api/employees
{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","department":"Engineering","jobTitle":"Engineer","salary":120000,"hireDate":"2024-01-15"}
-> 201 {"id":1,"firstName":"Ada",...}
```

### 6.3 Storage
- In-memory H2 through R2DBC: `r2dbc:h2:mem:///employeesdb;DB_CLOSE_DELAY=-1`.
- Schema is created at startup from `schema.sql` (`spring.sql.init.mode=always`). `email` has a UNIQUE constraint.
- Data is lost on restart (by design). No seed data by default. Optional `data.sql` for demo rows.

### 6.4 Other endpoints
- `GET /actuator/health` is the health check, with liveness and readiness probes.

### 6.5 Rules
- No blocking calls on event-loop threads. Wrap unavoidable blocking work in `Schedulers.boundedElastic()`.
- Use constructor injection and Java 21 features (records for DTOs, pattern matching) where fitting.
- Business logic lives in `EmployeeService`. The controller only maps HTTP to service calls.

## 7. Native Image Constraints
- Avoid runtime reflection, dynamic proxies and classpath scanning that AOT cannot see. Where unavoidable, register hints via `RuntimeHintsRegistrar` or `@RegisterReflectionForBinding`.
- Prefer functional or annotated config that AOT can process. Avoid `@Profile` and `@ConditionalOnProperty` that change at runtime, because bean conditions are fixed at build time.
- H2 and R2DBC must work in native mode: keep the `schema.sql` resource included (`resource-config` hint if needed) and register reflection hints for the entity and DTO classes.
- Every dependency must be native-compatible (check the GraalVM reachability metadata repository).
- Tests run on the JVM (`mvn test`). Optionally run native tests with `mvn -PnativeTest test`.

## 8. Build and Run
| Task | Command |
|------|---------|
| Prerequisites | GraalVM JDK 21, Maven 3.9.x (or `./mvnw`) |
| Verify tools | `./mvnw -v` (Maven 3.9.x, Java 21) |
| JVM build and test | `./mvnw clean verify` |
| Run on JVM | `./mvnw spring-boot:run` |
| Native executable | `./mvnw -Pnative native:compile` → `target/<artifactId>` |
| Native container image (buildpacks) | `./mvnw -Pnative spring-boot:build-image` |
| Run native | `./target/<artifactId>` |

## 9. Configuration (`application.yml`)
```yaml
server:
  port: ${PORT:8080}
spring:
  application:
    name: reactive-native-service
  r2dbc:
    url: r2dbc:h2:mem:///employeesdb;DB_CLOSE_DELAY=-1
    username: sa
    password:
  sql:
    init:
      mode: always
  main:
    lazy-initialization: false
management:
  endpoints.web.exposure.include: health,info
  endpoint.health.probes.enabled: true
```

## 10. Containerization / Deployment
Multi-stage `Dockerfile`:
1. Build stage: `ghcr.io/graalvm/native-image-community:21` plus Maven 3.9.x, running `./mvnw -Pnative native:compile -DskipTests`.
2. Runtime stage: `debian:bookworm-slim` or distroless, copying only the native binary.
3. `EXPOSE 8080` and `ENTRYPOINT ["/app/service"]`.

The service must read `PORT` from the environment and bind to `0.0.0.0`.

## 11. README.md (deliverable)
The project must include a `README.md` at the repository root. A new developer must be able to follow it from a clean machine to a working service. It must contain these sections:

1. **Overview**: what the service does and the stack (Java 21, Spring Boot 3.x, WebFlux, R2DBC + H2, GraalVM native image, Maven 3.9.x).
2. **Prerequisites**: how to install and verify the tools.
   - GraalVM JDK 21 (for example via SDKMAN `sdk install java 21.0.x-graal`), checked with `java -version`.
   - Maven 3.9.x, or use `./mvnw`, checked with `./mvnw -v`.
   - A native build toolchain (`gcc`, `zlib-devel` on Linux, Xcode CLT on macOS, Visual Studio Build Tools on Windows).
   - At least 8 GB RAM for native compilation.
   - Docker (optional).
3. **Project setup**: clone, project layout summary, configuration table (`PORT`, R2DBC URL).
4. **Build**:
   - JVM: `./mvnw clean verify`
   - Native: `./mvnw -Pnative native:compile`
   - Container: `./mvnw -Pnative spring-boot:build-image` or `docker build`
5. **Run**:
   - JVM: `./mvnw spring-boot:run`
   - Native: `./target/<artifactId>`
   - Docker: `docker run -p 8080:8080 <image>`
   - Health check: `curl localhost:8080/actuator/health`
6. **API reference**: for each Employee endpoint, a table of the method, path, request body, responses and status codes, plus a working `curl` example with sample output:
   ```bash
   # ADD
   curl -i -X POST localhost:8080/api/employees -H 'Content-Type: application/json' \
     -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","department":"Engineering"}'
   # LIST (optionally filtered)
   curl localhost:8080/api/employees
   curl "localhost:8080/api/employees?department=Engineering"
   # GET by id
   curl localhost:8080/api/employees/1
   # UPDATE
   curl -X PUT localhost:8080/api/employees/1 -H 'Content-Type: application/json' \
     -d '{"firstName":"Ada","lastName":"Byron","email":"ada@example.com"}'
   # DELETE
   curl -i -X DELETE localhost:8080/api/employees/1
   ```
   It must also show the error response format with examples for `400`, `404` and `409`.
7. **Testing**: `./mvnw test`, and how to run native tests.
8. **Notes and troubleshooting**:
   - Data is in-memory and lost on restart.
   - Native build out-of-memory.
   - Wrong Java or Maven version (enforcer error).
   - Port already in use.

Every command in the README must have been run and verified, and the `curl` examples must match the real API behaviour.

## 12. Non-Functional Targets
- Native startup under 200 ms.
- Memory (RSS) under roughly 100 MB when idle.
- Native build needs at least 8 GB RAM. Allow several minutes for the build.
- Unit tests use `StepVerifier`. Web tests use `WebTestClient` against the real H2 in-memory DB. Target at least 80% line coverage on service code.

## 13. Acceptance Criteria
1. `./mvnw -v` reports Maven 3.9.x and Java 21.
2. The build fails with a clear message under Maven 4.x or a JDK other than 21 (enforcer).
3. `./mvnw clean verify` passes.
4. `./mvnw -Pnative native:compile` produces a runnable executable.
5. The native binary serves `/actuator/health` (`UP`) and all Employee endpoints.
   - POST returns `201`, then GET by id returns that employee.
   - LIST includes the created employee.
   - PUT changes the stored values.
   - DELETE returns `204`, and a following GET returns `404`.
   - A duplicate email returns `409`, and invalid input returns `400`.
6. No blocking-call violations. Optionally add BlockHound in tests.
7. `README.md` exists, contains all sections from section 11, and its commands and `curl` examples work as written.
