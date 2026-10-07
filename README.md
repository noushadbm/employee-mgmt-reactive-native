# employee-service

Reactive REST service to manage employees. Stack: **Java 21**, **Spring Boot 3.3**, **WebFlux / Project Reactor**,
**Spring Data R2DBC + in-memory H2**, **Maven 3.9.x**, buildable as a **GraalVM native image**.

## Prerequisites
| Tool | Version | Check |
|------|---------|-------|
| GraalVM JDK (needed for native; any JDK 21 works for JVM mode) | 21 (e.g. `sdk install java 21.0.2-graalce`) | `java -version` |
| Maven (or use the bundled `./mvnw`) | 3.9.x (4.x is rejected) | `./mvnw -v` |
| Native toolchain (native build only) | gcc + zlib headers (Linux), Xcode CLT (macOS), VS Build Tools (Windows) | `gcc --version` |
| RAM (native build only) | 8 GB or more | |
| Docker (optional) | any recent | `docker -v` |

The build fails with a clear message if Java is not 21 or Maven is not 3.9.x (maven-enforcer-plugin).

## Setup
```bash
git clone <repo-url> && cd employee-service
```
Layout:
```
src/main/java/com/example/app/
  Application.java
  web/         EmployeeController, GlobalExceptionHandler
  service/     EmployeeService + exceptions
  repository/  EmployeeRepository (ReactiveCrudRepository)
  model/       Employee, EmployeeRequest
  config/      NativeHints (GraalVM hints)
src/main/resources/ application.yml, schema.sql
```
Configuration:
| Setting | Default | Notes |
|---------|---------|-------|
| `PORT` env var | `8080` | HTTP port |
| `spring.r2dbc.url` | `r2dbc:h2:mem:///employeesdb;DB_CLOSE_DELAY=-1` | in-memory H2 |

## Build
```bash
./mvnw clean verify                          # JVM build + tests -> target/employee-service-0.0.1-SNAPSHOT.jar
./mvnw -Pnative native:compile               # native executable -> target/employee-service
./mvnw -Pnative spring-boot:build-image      # native container image (buildpacks, needs Docker)
docker build -t employee-service .           # native container image via Dockerfile
```

## Run
```bash
./mvnw spring-boot:run                                   # JVM, from sources
java -jar target/employee-service-0.0.1-SNAPSHOT.jar     # JVM, from jar
./target/employee-service                                # native executable
docker run -p 8080:8080 employee-service                 # container
curl localhost:8080/actuator/health                      # {"status":"UP",...}
```

## API (`/api/employees`, JSON)
| Operation | Request | Success | Errors |
|-----------|---------|---------|--------|
| ADD | `POST /api/employees` | `201` + `Location` + employee | `400`, `409` duplicate email |
| LIST | `GET /api/employees[?department=X]` | `200` array (`Accept: text/event-stream` streams) | |
| GET | `GET /api/employees/{id}` | `200` | `404` |
| UPDATE | `PUT /api/employees/{id}` (full body) | `200` | `400`, `404`, `409` |
| DELETE | `DELETE /api/employees/{id}` | `204` | `404` |

Fields: `firstName`\*, `lastName`\*, `email`\* (valid, unique), `department`, `jobTitle`, `salary` (>= 0), `hireDate` (`yyyy-MM-dd`). \* required.

```bash
# ADD
curl -i -X POST localhost:8080/api/employees -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","department":"Engineering"}'
# HTTP/1.1 201 Created   Location: /api/employees/1
# {"id":1,"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","department":"Engineering","jobTitle":null,"salary":null,"hireDate":null}

# LIST (optionally filtered)
curl localhost:8080/api/employees
curl "localhost:8080/api/employees?department=Engineering"

# GET by id
curl localhost:8080/api/employees/1

# UPDATE
curl -X PUT localhost:8080/api/employees/1 -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Byron","email":"ada@example.com"}'

# DELETE
curl -i -X DELETE localhost:8080/api/employees/1     # 204 No Content
```

### Errors
All errors share one format:
```json
// 400 (invalid input)
{"timestamp":"...","status":400,"error":"Bad Request","message":"Validation failed","path":"/api/employees",
 "errors":[{"field":"firstName","message":"must not be blank"},{"field":"email","message":"must be a well-formed email address"}]}
// 404
{"timestamp":"...","status":404,"error":"Not Found","message":"Employee not found with id 42","path":"/api/employees/42"}
// 409
{"timestamp":"...","status":409,"error":"Conflict","message":"Employee with email 'ada@example.com' already exists","path":"/api/employees"}
```

## Testing
```bash
./mvnw test                 # JVM tests (WebTestClient against in-memory H2)
./mvnw -PnativeTest test    # run tests as a native image (needs GraalVM, lots of RAM)
```

## Troubleshooting
- **Data disappears on restart**: H2 is in-memory by design.
- **Native build runs out of memory**: give it 8 GB or more, or build with Docker/buildpacks on a bigger machine.
- **Enforcer error "Java 21 is required" / "Maven 3.9.x is required"**: switch JDK/Maven, or use `./mvnw`.
- **Port already in use**: `PORT=9090 java -jar ...`.
