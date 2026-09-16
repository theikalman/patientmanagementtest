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

## 4. Design decisions at a glance

The reasoning and the alternatives that were rejected are in comments at each site.

| Decision | In short |
| --- | --- |
| **PID separate from the primary key** | `id` addresses rows; `pid` (`PAT-000042`) is the human identity. Allocated by the server from a database sequence, immutable, `UNIQUE` in the schema. The create request has no `pid` field at all. |
| **Optimistic locking** | `PUT` requires the `version` last read. A concurrent edit gets a 409 naming both versions instead of silently overwriting someone's change. |
| **Server side pagination** | Paged in the database, never in memory. Page size capped at 100, sort restricted to an allow list, own `PageResponse` envelope rather than Spring Data's unstable `PageImpl` shape. |
| **Search** | One term matched against PID, first name, last name and the concatenated full name. LIKE metacharacters are escaped so `50%` does not match everything. |
| **RFC 9457 errors** | Every error defined in one `@RestControllerAdvice`. Validation failures carry a per field `errors` array, which is what lets the Angular form put each message under the right input. Nothing internal leaks. |
| **Flyway owns the schema** | Hand written DDL with explicit constraints and indexes; Hibernate runs `ddl-auto: validate`, so entity/schema drift is a startup failure rather than a silent bug. |
| **Phone normalisation** | `0412 345 678`, `(02) 9876-5432` and `+61 412 345 678` all canonicalise to E.164 on the way in and render locally on the way out, so search and de-duplication compare like with like. |
| **Postcode validated against its state** | A custom constraint checks the postcode against the Australia Post ranges for the selected state, catching the commonest address entry error that `\d{4}` cannot. |
| **Gender aligned to HL7 FHIR** | `MALE / FEMALE / OTHER / UNKNOWN`, so this can be mapped onto a clinical interoperability layer later without a data migration. |
| **Address as a value object** | `@Embedded`, so the grid needs no join, while Java still gets a cohesive type with value based equality. |
| **`open-in-view` disabled** | Entities are mapped to DTOs inside the service transaction, so N+1 queries cannot hide behind a request-scoped session. |
| **Reference data from the API** | The UI fetches its dropdown values, so the two sides cannot drift when a state or gender is added. |
| **Grid holds one page** | Paging, sorting and searching all flow through a single signal, so they cannot get out of step. Search is debounced into one request per pause. |
| **Server has the final say on validation** | Field violations from the API are projected back onto the matching form controls, so a server-only rule shows up on the right input rather than in a banner. |
| **No Lombok** | Records cover the DTOs; the entity exposes intention revealing methods instead of setters, which is what makes "the PID never changes" enforceable by the type. |

---

## 5. API

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

## 6. Tests

**181 tests**, each level with a distinct job and none duplicating another.

| Suite | Count | Proves |
| --- | --- | --- |
| `PatientServiceTest` | 23 | Business rules in isolation (Mockito, no Spring): PID allocation, phone normalisation, a stale version writing **nothing**, search escaping, paging guard rails |
| `PatientControllerTest` | 24 | `@WebMvcTest` slice: status codes, JSON shape, binding, and every error becoming the right problem document |
| `PatientRepositoryTest` | 9 | `@DataJpaTest` against the real Flyway schema: the search JPQL, database level paging, the `UNIQUE` constraint |
| `PatientApiIntegrationTest` | 11 | `@SpringBootTest` full stack: a real CRUD cycle, sequential PIDs, `@Version` incrementing, the OpenAPI document and its CORS rules |
| `PhoneNumbersTest`, `PostcodeMatchesStateValidatorTest`, context | 68 | Parameterised validation rules through the real Bean Validation engine |
| Front end (6 spec files) | 46 | API contract, error mapping, day-first date parsing, and that the grid really pages server side |

No end-to-end browser suite. The flows were driven manually in a browser instead, which is how
three of the six bugs found while building this turned up; the other three came from the container
build. For production those happy paths belong in Playwright in CI.

---

## 7. Layout

```
.
+-- README.md                  this file
+-- Makefile                   development commands; `make` lists them
+-- docker-compose.yml         Swagger UI, plus the whole stack under the "app" profile
+-- backend/
|   +-- Dockerfile             multi-stage: Maven build -> JRE runtime
|   +-- src/main/java/com/xtramile/patient/
|   |   +-- config/            JPA auditing + Clock, PID incrementer, CORS, OpenAPI
|   |   +-- domain/            Patient, AustralianAddress, AustralianState, Gender
|   |   +-- repository/        PatientRepository (Spring Data JPA + search JPQL)
|   |   +-- service/           PatientService, PidGenerator, domain exceptions
|   |   +-- validation/        @AustralianPhone, @PostcodeMatchesState, PhoneNumbers
|   |   +-- web/               controllers, GlobalExceptionHandler, DTOs, mapper
|   +-- src/main/resources/
|   |   +-- application{,-demo,-prod}.yml
|   |   +-- db/migration/      V1 schema, owned by Flyway
|   |   +-- db/seed/           V900 demo fixtures, demo profile only
|   +-- src/test/              135 tests
+-- frontend/
    +-- Dockerfile             multi-stage: npm build -> nginx runtime
    +-- nginx.conf             SPA fallback plus the /api proxy
    +-- src/app/
        +-- core/              API client, models, error interceptor, date utilities
        +-- features/patients/ patient-list (grid), patient-form (create/edit)
        +-- shared/            confirm dialog
```

---

## 8. Commands

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
