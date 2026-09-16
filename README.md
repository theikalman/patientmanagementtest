# Patient Management Application

**Task #2 (Design and coding test)** of the Xtramile Solutions Java Engineer Test.

A full stack patient CRUD application with a grid, search, server side pagination and an
Australian address model.

| | |
| --- | --- |
| **Back end** | Java 17, Spring Boot 4.1, Spring Data JPA, Spring REST, Flyway, H2 |
| **Front end** | Angular 21 (standalone, signals, zoneless) with Angular Material |
| **Build** | Maven (wrapper committed), npm |
| **Tests** | 181 automated tests: 135 back end, 46 front end |
| **Extras** | springdoc OpenAPI, Docker Compose, Makefile |

---

## 1. Run it

Needs **JDK 17+** and **Node 20.19+** (24 was used here). No database to install: H2 runs in
memory, seeded with 42 demo patients, and Maven comes from the committed wrapper.

```bash
make dev
```

That runs the API and the web app together (Ctrl+C stops both) and starts Swagger UI if Docker is
available. Then open **http://localhost:4200**.

Without `make`, in two terminals:

```bash
cd backend  && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm start   # http://localhost:4200
```

| URL | What |
| --- | --- |
| http://localhost:4200 | The application |
| http://localhost:8080/api/v1/patients | The API |
| http://localhost:8080/swagger-ui.html | Interactive API reference |
| http://localhost:8080/h2-console | Database console (`jdbc:h2:mem:patientdb`, user `sa`, no password) |

### Tests

```bash
make test                         # both suites

# or individually
cd backend  && ./mvnw test        # 135 tests
cd frontend && npm run test:ci    #  46 tests
```

### Everything in Docker

For a sanity check that it builds and runs outside a developer machine:

```bash
make stack-up      # or: docker compose --profile app up -d --build
```

Back end as a jar on a JRE, front end as a static bundle on nginx which proxies `/api` to the API
container, so the browser sees one origin. `make stack-down` removes it. `docker-compose.yml` and
the two Dockerfiles carry the reasoning in comments.

---

## 2. Requirements checklist

| Requirement from the brief | Where |
| --- | --- |
| Spring Boot, Spring JPA, Spring REST API | `backend/src/main/java/com/xtramile/patient` |
| Back end in Java | Java 17 |
| Front end in Angular | `frontend/src/app`, Angular 21 |
| Build with Gradle or Maven | Maven, wrapper committed |
| Unit tests for REST API and Service layers | `PatientControllerTest`, `PatientServiceTest`, plus repository and full stack suites |
| Other technologies welcome | Flyway, Angular Material, springdoc OpenAPI, Docker Compose, H2, Vitest |
| Field: PID | `Patient.pid`, server allocated, unique, immutable |
| Fields: first name, last name | `Patient.firstName` / `lastName` |
| Field: date of birth | `LocalDate`, must be in the past |
| Field: gender | `Gender` enum, aligned to HL7 FHIR |
| Field: phone no | `Patient.phoneNo`, normalised to E.164 |
| Australian address | `AustralianAddress` embeddable + `AustralianState` enum |
| Grid of patient data | `PatientList`, Material table |
| Create / update / delete | `POST` / `PUT` / `DELETE /api/v1/patients`, with `PatientForm` and a confirm dialog |
| Search by PID or name | `?search=` across PID, first name, last name and "first last" |
| Server side pagination | `?page=&size=&sort=`, paged in the database |

---

## 3. Architecture

```
Browser  ->  Angular 21          PatientList / PatientForm
                |                PatientApi -> HttpClient -> errorInterceptor (-> ApiError)
                |  /api (same origin: dev-server proxy, or nginx in Docker)
                v
             Spring Boot 4       PatientController      thin: bind, validate, status
                |                GlobalExceptionHandler every error -> RFC 9457
                |                PatientService         use cases, transactions, rules
                |                PatientRepository      Spring Data JPA
                v
             Hibernate 7 -> H2   schema owned by Flyway
```

Dependencies point strictly downwards, which is what lets the service be unit tested without a
servlet container and the controller without a database.

---

## 4. API

Base path `/api/v1`. Full reference at `/swagger-ui.html`; raw document at `/v3/api-docs`.

| Method | Path | Purpose | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET` | `/patients` | List, search, page, sort | 200 | 400 |
| `GET` | `/patients/{id}` | One patient by id | 200 | 400, 404 |
| `GET` | `/patients/by-pid/{pid}` | One patient by business PID | 200 | 404 |
| `POST` | `/patients` | Create; server allocates the PID | 201 + `Location` | 400 |
| `PUT` | `/patients/{id}` | Replace; requires `version` | 200 | 400, 404, 409 |
| `DELETE` | `/patients/{id}` | Delete | 204 | 404 |
| `GET` | `/reference-data/genders`, `/reference-data/states` | Dropdown values | 200 | |

`GET /patients` parameters: `page` (default 0), `size` (default 10, capped at 100),
`sort` (default `lastName,asc`, allow list only), `search`.

```bash
curl 'http://localhost:8080/api/v1/patients?search=jane&page=0&size=5'

# Create. Note there is no "pid" field; the server allocates it.
curl -X POST http://localhost:8080/api/v1/patients -H 'Content-Type: application/json' -d '{
  "firstName":"Jane","lastName":"Citizen","dateOfBirth":"1985-04-12",
  "gender":"FEMALE","phoneNo":"(02) 9876 5432",
  "address":{"street":"12 Wallaby Way","suburb":"Sydney","state":"NSW","postcode":"2000"}}'
```

A validation failure returns a problem document naming each rejected field:

```json
{ "status": 400, "title": "Validation failed",
  "errors": [
    { "field": "address.postcode",
      "message": "postcode 2000 is not allocated to VIC (valid ranges: [3000-3999, 8000-8999])",
      "rejectedValue": "2000" }
  ] }
```

---

## 5. Commands

`make` with no arguments prints the full list. Every target is a thin wrapper around the real
command, so nothing is hidden.

| Target | What it does |
| --- | --- |
| `make dev` | API and web app together in one terminal, plus Swagger UI. Ctrl+C stops both servers. |
| `make api` / `make web` | Run one side only |
| `make test` / `test-api` / `test-web` | Test suites |
| `make test-one T=PatientServiceTest` | A single back end test class |
| `make build` / `make verify` | Build both; or the full clean build plus both suites, as CI would |
| `make stack-up` / `stack-down` | The containerised stack |
| `make swagger-up` / `swagger-down` | The Swagger UI container alone |
| `make status` | Probes all three URLs and reports what is up |
| `make stop` | Stops every process and container this project starts |
| `make format` / `format-check` | Prettier |
| `make clean` / `clean-all` | Build output, and build output plus `node_modules` |

Two behaviours worth knowing: `make dev` runs both servers under `trap 'kill 0'`, so one Ctrl+C
stops them together instead of orphaning one; and `make install` only reruns `npm ci` when the
lockfile actually changes. The file targets GNU Make 3.81, the version macOS ships.
