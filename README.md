# Patient Management Application

**Task #2 (Design and coding test)** of the Xtramile Solutions Java Engineer Test: a patient CRUD
application with a grid, search, server side pagination and an Australian address model.

---

## 1. Requirements from the test document

| Requirement | Where it is implemented |
| --- | --- |
| Spring Boot, Spring JPA, Spring REST API, in Java | `backend/src/main/java/com/xtramile/patient` |
| Angular front end | `frontend/src/app` |
| Build with Gradle or Maven | Maven, wrapper committed (`backend/mvnw`) |
| Unit tests for the REST API and Service layers | `PatientControllerTest`, `PatientServiceTest`, plus repository and full stack suites |
| PID, first and last name, date of birth, gender, phone | `domain/Patient` - PID is server allocated, immutable and unique; phone normalised to E.164; gender aligned to HL7 FHIR |
| Australian address: address, suburb, state, postcode | `domain/AustralianAddress` + `AustralianState`, with the postcode validated against its state's ranges |
| Grid displaying the patient list | `PatientList`, an Angular Material table |
| Create, update, delete | `PatientForm` and a confirm dialog, over `POST` / `PUT` / `DELETE /api/v1/patients` |
| Search by PID or patient name | `?search=` across PID, first name, last name and "first last" |
| Server side pagination | `?page=&size=&sort=`, paged in the database, sort restricted to an allow list |
| Other frameworks or technologies welcome | Flyway, springdoc OpenAPI, Docker Compose, Makefile, Vitest |

---

## 2. Tech stack

| | |
| --- | --- |
| **Back end** | Java 17, Spring Boot 4.1, Spring Data JPA, Spring Web MVC, Bean Validation |
| **Database** | H2 in memory, schema owned by Flyway (`ddl-auto: validate`) |
| **Front end** | Angular 21 (standalone components, signals, zoneless) with Angular Material |
| **Build** | Maven (wrapper committed) and npm |
| **Tests** | JUnit 5, Mockito, AssertJ, MockMvc; Vitest on the front end. 180 in total, 134 back end and 46 front end |
| **API docs** | springdoc OpenAPI 3.1, Swagger UI |
| **Tooling** | Makefile, Docker Compose |

---

## 3. Quick start

Needs **JDK 17+** and **Node 20.19+**. No database to install: H2 runs in memory and is seeded
with 42 demo patients on startup. Maven comes from the committed wrapper.

```bash
make dev        # API + web app together, Ctrl+C stops both
```

Or without `make`, in two terminals:

```bash
cd backend  && ./mvnw spring-boot:run
cd frontend && npm install && npm start
```

### Links

| URL | What it is |
| --- | --- |
| http://localhost:4200 | **The application.** Angular dev server, proxying `/api` to port 8080 |
| http://localhost:8080/api/v1/patients | The REST API |
| http://localhost:8080/swagger-ui.html | Swagger UI, interactive API reference |
| http://localhost:8080/v3/api-docs | Raw OpenAPI 3.1 document |
| http://localhost:8080/h2-console | Database console. JDBC URL `jdbc:h2:mem:patientdb`, user `sa`, no password |
| http://localhost:8080/actuator/health | Health check |

---

## 4. Common commands

`make` on its own prints every target. Each one is a thin wrapper around the underlying command.

| Command | What it does |
| --- | --- |
| `make dev` | Run the API and the web app together in one terminal |
| `make api` / `make web` | Run one side only |
| `make test` | Both test suites |
| `make test-api` / `make test-web` | One suite (`./mvnw test` / `npm run test:ci`) |
| `make test-one T=PatientServiceTest` | A single back end test class |
| `make build` | Package the jar and build the production front end bundle |
| `make verify` | Clean build plus both suites, as a CI pipeline would run it |
| `make status` | Probe the URLs above and report what is currently up |
| `make stop` | Stop every process and container this project starts |
| `make format` / `make format-check` | Prettier on the front end sources |
| `make clean` | Remove build output |
| `make stack-up` / `make stack-down` | Build and run the whole app in Docker, for a sanity check |

---

## 5. Architecture

```
Angular      PatientList / PatientForm -> PatientApi -> errorInterceptor (-> ApiError)
   |         /api on the same origin: dev-server proxy, or nginx in Docker
Spring Boot  PatientController       thin: bind, validate, choose a status
   |         GlobalExceptionHandler  every error -> RFC 9457 problem document
   |         PatientService          use cases, transactions, business rules
   |         PatientRepository       Spring Data JPA
Hibernate -> H2, schema owned by Flyway (ddl-auto: validate)
```

Dependencies point strictly downwards, which is what lets the service be unit tested without a
servlet container and the controller without a database.

---

## 6. API

Base path `/api/v1`. Full reference at `/swagger-ui.html`, raw document at `/v3/api-docs`.

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/patients` | `page` (0), `size` (10, capped at 100), `sort` (`lastName,asc`), `search` |
| `GET` | `/patients/{id}`, `/patients/by-pid/{pid}` | |
| `POST` | `/patients` | 201 + `Location`. No `pid` in the body; the server allocates it |
| `PUT` | `/patients/{id}` | Requires the `version` last read; a concurrent edit gets 409, not a lost update |
| `DELETE` | `/patients/{id}` | 204 |
| `GET` | `/reference-data/genders`, `/reference-data/states` | Dropdown values, so the UI hard codes nothing |

Errors are RFC 9457 problem documents; validation failures carry a per field `errors` array, which
is what lets the Angular form put each message under the input that caused it.

```bash
curl -X POST http://localhost:8080/api/v1/patients -H 'Content-Type: application/json' -d '{
  "firstName":"Jane","lastName":"Citizen","dateOfBirth":"1985-04-12",
  "gender":"FEMALE","phoneNo":"(02) 9876 5432",
  "address":{"street":"12 Wallaby Way","suburb":"Sydney","state":"NSW","postcode":"2000"}}'
```

---

## 7. Notes on the approach

**Two ways to run it.** Locally (`make dev`) for day to day work, where breakpoints and
restart-on-change work. In Docker (`make stack-up`) for a sanity check: a smoke test, an end to end
pass, or a last look before opening a pull request.

**Swagger UI is for development, not production.** The service serves it itself at
`/swagger-ui.html`, generated by springdoc from the controllers, so there is nothing separate to
keep in step. The `prod` profile switches it off along with the raw OpenAPI document, so neither
can reach production by accident.

**No authentication or authorisation.** Out of scope for the test, and leaving it out keeps the
project runnable with no setup. For real use it would be the first thing added, and it is additive:
the API is already versioned and stateless.

**Production preparation is application level only, and minimal.** `application-prod.yml` covers
what the application itself controls: data source from the environment, no seed data, no database
console, no interactive docs. Nothing is assumed about the infrastructure, since its shape is not
known.
