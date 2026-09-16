# Patient Management Application

**Task #2 (Design and coding test)** of the Xtramile Solutions Java Engineer Test: a patient CRUD
application with a grid, search, server side pagination and an Australian address model.

**Java 17 / Spring Boot 4.1 / Spring Data JPA / Flyway / H2** back end,
**Angular 21** (standalone, signals, zoneless) front end with Angular Material,
built with **Maven** and npm, covered by **181 tests** (135 back end, 46 front end).

---

## Run it

Needs JDK 17+ and Node 20.19+. No database to install: H2 runs in memory, seeded with 42 patients.

```bash
make dev        # API + web app together, Ctrl+C stops both
```

Or without `make`, in two terminals:

```bash
cd backend  && ./mvnw spring-boot:run
cd frontend && npm install && npm start
```

- **http://localhost:4200** the application
- http://localhost:8080/api/v1/patients the API
- http://localhost:8080/swagger-ui.html interactive API reference
- http://localhost:8080/h2-console database console (`jdbc:h2:mem:patientdb`, user `sa`, no password)

```bash
make test       # both suites; or ./mvnw test and npm run test:ci
make stack-up   # optional: the whole app built and run in Docker, for a sanity check
```

---

## Requirements checklist

| From the brief | Where |
| --- | --- |
| Spring Boot, Spring JPA, Spring REST API, in Java | `backend/src/main/java/com/xtramile/patient` |
| Angular front end | `frontend/src/app` |
| Gradle or Maven | Maven, wrapper committed |
| Unit tests for the REST API and Service layers | `PatientControllerTest`, `PatientServiceTest`, plus repository and full stack suites |
| PID, name, date of birth, gender, phone | `domain/Patient` - PID is server allocated, immutable and unique; phone normalised to E.164; gender aligned to HL7 FHIR |
| Australian address | `domain/AustralianAddress` + `AustralianState`, with a postcode validated against its state's ranges |
| Grid, create, update, delete | `PatientList` and `PatientForm`, over `POST` / `PUT` / `DELETE /api/v1/patients` |
| Search by PID or name | `?search=` across PID, first name, last name and "first last" |
| Server side pagination | `?page=&size=&sort=`, paged in the database, sort restricted to an allow list |
| Other technologies welcome | Flyway, springdoc OpenAPI, Docker Compose, Makefile, Vitest |

---

## Architecture

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

## API

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

## Commands

`make` on its own prints every target. The ones worth knowing:

```bash
make dev        # run the API and the web app
make test       # both test suites
make status     # what is currently running
make stop       # stop every process and container this project starts
make stack-up   # build and run the whole app in Docker
```
