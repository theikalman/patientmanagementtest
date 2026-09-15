# Patient Management Application

Answer to **Task #2 (Design and coding test)** of the Xtramile Solutions Java Engineer Test.

A full stack patient CRUD application: a **Spring Boot 4 / Spring Data JPA / REST** back end
in Java, an **Angular 21** front end, built with **Maven**, covered by **177 automated tests**
(131 back end, 46 front end).

This README is deliberately long. The task asked for design and coding, so alongside the "how to
run it" instructions every significant decision is written down with the alternatives that were
considered and why they were not chosen.

---

## Table of contents

1. [What was asked and where it is implemented](#1-what-was-asked-and-where-it-is-implemented)
2. [Quick start](#2-quick-start)
3. [Architecture at a glance](#3-architecture-at-a-glance)
4. [Technology choices](#4-technology-choices)
5. [Back end design](#5-back-end-design)
   - [Domain model](#51-domain-model)
   - [Identity: `id` versus `pid`](#52-identity-id-versus-pid)
   - [Database schema, constraints and indexes](#53-database-schema-constraints-and-indexes)
   - [Validation](#54-validation)
   - [Search and pagination](#55-search-and-pagination)
   - [Concurrency: preventing lost updates](#56-concurrency-preventing-lost-updates)
   - [Error handling](#57-error-handling)
   - [Layering and transactions](#58-layering-and-transactions)
   - [Configuration and profiles](#59-configuration-and-profiles)
6. [REST API reference](#6-rest-api-reference)
7. [Front end design](#7-front-end-design)
8. [Testing strategy](#8-testing-strategy)
9. [Bugs found and fixed while building this](#9-bugs-found-and-fixed-while-building-this)
10. [Deliberate non-goals and what production would need](#10-deliberate-non-goals-and-what-production-would-need)
11. [Project layout](#11-project-layout)
12. [Commands reference](#12-commands-reference)

---

## 1. What was asked and where it is implemented

| Requirement from the brief | Status | Where |
| --- | --- | --- |
| Spring Boot, Spring JPA, Spring REST API | Done | `backend/src/main/java/com/xtramile/patient` |
| Back end written in Java | Done | Java 17 |
| Front end written in Angular | Done | `frontend/src/app` (Angular 21, standalone, signals) |
| Build with Gradle or Maven | Done | Maven, wrapper committed (`backend/mvnw`) |
| Unit tests for REST API and Service layers | Done | `PatientControllerTest` (web), `PatientServiceTest` (service), plus repository and full stack tests |
| Other frameworks or technologies welcome | Done | Flyway, Angular Material, springdoc OpenAPI, H2, Vitest |
| Field: PID (Patient Identity) | Done | `Patient.pid`, server allocated, unique, immutable |
| Fields: first name, last name | Done | `Patient.firstName` / `lastName` |
| Field: date of birth | Done | `Patient.dateOfBirth` (`LocalDate`, must be in the past) |
| Field: gender | Done | `Gender` enum, HL7 aligned |
| Field: phone no | Done | `Patient.phoneNo`, normalised to E.164 |
| Australian address: address, suburb, state, postcode | Done | `AustralianAddress` embeddable + `AustralianState` enum |
| Grid to display the list of patient data | Done | `PatientList` component, Material table |
| Create new patient data | Done | `POST /api/v1/patients` + `PatientForm` |
| Update existing patient data | Done | `PUT /api/v1/patients/{id}` + `PatientForm` |
| Delete existing patient data | Done | `DELETE /api/v1/patients/{id}` + confirmation dialog |
| Search feature by PID or patient name | Done | `?search=` matched against PID, first name, last name and "first last" |
| Server side pagination | Done | `?page=&size=&sort=`, paged in the database, `PageResponse` envelope |

---

## 2. Quick start

### Prerequisites

| Tool | Version used | Notes |
| --- | --- | --- |
| JDK | 17 | Any JDK 17 or newer. `JAVA_HOME` must point at it. |
| Node.js | 24.11 | Angular 21 needs `^20.19 \|\| ^22.12 \|\| >=24`. |
| Maven | not required | The Maven wrapper (`./mvnw`) downloads it. |

No database to install: the application runs on an in-memory H2 database seeded with 42 demo
patients.

### Run the back end

```bash
cd backend
./mvnw spring-boot:run
```

| URL | What it is |
| --- | --- |
| http://localhost:8080/api/v1/patients | The API |
| http://localhost:8080/swagger-ui.html | Interactive API reference |
| http://localhost:8080/h2-console | Database console (JDBC URL `jdbc:h2:mem:patientdb`, user `sa`, no password) |
| http://localhost:8080/actuator/health | Health check |

### Run the front end

In a second terminal:

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200. The dev server proxies `/api` to `http://localhost:8080`, so the
browser only ever sees one origin.

### Run the tests

```bash
cd backend  && ./mvnw test     # 131 tests
cd frontend && npm run test:ci #  46 tests
```

---

## 3. Architecture at a glance

```
Browser
  |
  |  HTTP, JSON, same origin (dev: Angular dev-server proxies /api)
  v
+-------------------------------------------------------------+
|  Angular 21 (standalone, signals, zoneless)                  |
|                                                              |
|  PatientList ----+                                           |
|  PatientForm ----+--> PatientApi --> HttpClient              |
|                                        |                     |
|                                   errorInterceptor           |
|                                   (-> ApiError)              |
+-------------------------------------------------------------+
  |
  v
+-------------------------------------------------------------+
|  Spring Boot 4 / Java 17                                     |
|                                                              |
|  PatientController          thin: bind, validate, status     |
|  GlobalExceptionHandler     every error -> RFC 9457          |
|         |                                                    |
|  PatientService             use cases, transactions, rules   |
|         |                                                    |
|  PatientRepository          Spring Data JPA                  |
|  PidGenerator               database sequence                |
|         |                                                    |
|  Hibernate 7 -> H2 (schema owned by Flyway)                  |
+-------------------------------------------------------------+
```

The dependency direction is strictly downwards. The controller knows the service; the service
knows the repository; nothing points back up. That is what makes it possible to unit test the
service without a servlet container and the controller without a database.

---

## 4. Technology choices

### Spring Boot 4.1.1 (not 3.x)

Spring Initializr no longer offers a 3.x line, and 4.1.1 is the current release. It brings
Spring Framework 7, Hibernate 7, Jackson 3 and a restructured starter layout
(`spring-boot-starter-webmvc` rather than `spring-boot-starter-web`, and per-slice test starters
such as `spring-boot-starter-webmvc-test`). Everything used here is stable API.

**Trade-off considered:** pinning back to Spring Boot 3.5 would be the more conservative choice
and is still widely deployed. It was rejected because submitting a project on a superseded major
version invites the question "why", and nothing in this application needs a 3.x-only feature.

### Maven (not Gradle)

Either was allowed. Maven was chosen because its declarative POM is faster to review than a build
script, and the Spring Boot parent POM manages the whole dependency matrix. The wrapper is
committed so the project builds on a machine with no Maven installed.

### Angular 21 (not 22)

Angular 22 requires Node `^22.22.3 || ^24.15.0 || >=26`; the toolchain here runs Node 24.11, which
is below that floor. Angular 21 accepts `>=24.0.0` and is otherwise the same modern stack:
standalone components, signals, zoneless change detection, the new control flow syntax, Vitest.
Upgrading is `ng update` once the Node version moves.

### Angular Material

The brief asks for a *grid*. Material provides a table, a server side paginator, a sort header, a
datepicker, dialogs and snackbars that are accessible and consistent out of the box. Hand rolling
those would have spent the time budget on widgets instead of on the application.

### H2 in-memory (not PostgreSQL)

A reviewer must be able to clone the repository and run it. H2 removes the "install a database
first" step entirely. The code is not tied to it:

- the schema is plain SQL in a Flyway migration;
- the one dialect specific thing, reading the next value from a sequence, is resolved at startup
  from the JDBC metadata (`PidGeneratorConfig`), with a PostgreSQL case already written;
- `application-prod.yml` takes its data source from the environment.

Switching to PostgreSQL is a dependency, a URL and a follow-up migration for the expression
indexes noted in [5.3](#53-database-schema-constraints-and-indexes).

### Flyway (not `ddl-auto: update`)

Generated DDL is convenient and unusable in production: it cannot express a rename, it gives no
review point for an index change, and the output differs between Hibernate versions. The schema
here is hand written in `V1__create_patient_table.sql` and Hibernate runs with
`ddl-auto: validate`, so the application refuses to start if the entity mapping and the migrated
schema have drifted apart. That turns a class of silent production bug into a startup failure and
a failing build.

### No Lombok

Java 17 records cover the DTOs, which is where most of the boilerplate would have been. The entity
deliberately exposes intention revealing methods (`applyDemographics`) rather than blanket setters,
which Lombok's `@Data` would have generated and which would have made the "PID never changes"
invariant unenforceable. Avoiding an annotation processor also removes a class of IDE setup
problems for whoever opens this next.

### Hand written mapper (not MapStruct or ModelMapper)

With one aggregate the mapping is a few dozen lines that a reviewer can read top to bottom. It is
plain, unit testable code with no generated sources and no reflection.

---

## 5. Back end design

### 5.1 Domain model

```
Patient  (@Entity, table: patient)
 |
 +-- id            Long              surrogate primary key, database generated
 +-- pid           String            business identity, unique, immutable  "PAT-000042"
 +-- firstName     String
 +-- lastName      String
 +-- dateOfBirth   LocalDate
 +-- gender        Gender            enum: MALE | FEMALE | OTHER | UNKNOWN
 +-- phoneNo       String            E.164, "+61412345678"
 +-- address       AustralianAddress (@Embedded value object)
 |                  +-- street
 |                  +-- suburb
 |                  +-- state       AustralianState enum
 |                  +-- postcode
 +-- version       Long              @Version, optimistic locking
 +-- createdAt     Instant           @CreatedDate
 +-- updatedAt     Instant           @LastModifiedDate
```

**Why the address is embedded rather than a separate table.** An address here has no identity of
its own: it is always owned by exactly one patient and is replaced wholesale when it changes.
Embedding keeps the columns on the `patient` table, so rendering the grid needs no join, while
Java still gets a cohesive value object with value based `equals`/`hashCode`. If a patient later
needed several addresses (postal, residential, next of kin) this becomes a `@OneToMany` and the
value object is reused as the element type.

**Why `Gender` is an enum with those four values.** They are the HL7 FHIR `administrative-gender`
value set. Healthcare systems interoperate; picking the standard value set now means this service
can be mapped onto a FHIR or HL7 v2 feed later without a data migration. `UNKNOWN` matters in
practice: an unconscious admission has a gender, it just is not recorded yet, and that is different
from `OTHER`.

**Why the entity has no public setters.** `applyDemographics(...)` replaces every client editable
attribute in one call. A half applied update is therefore impossible, and `pid` simply has no
mutator, so its immutability is enforced by the type rather than by a convention the next developer
has to know about.

**Why `equals`/`hashCode` use `pid`, not `id`.** The surrogate key is null until the entity is
persisted, so an identity based on it changes mid-lifecycle and breaks any `HashSet` the entity was
put into beforehand. The business key is assigned at construction and never changes.

**Why `age` is derived, not stored.** A stored age is wrong the day after it is written. It is
computed from `dateOfBirth` against an injected `Clock`, which also makes it assertable in a unit
test without the test becoming time dependent.

### 5.2 Identity: `id` versus `pid`

Two identifiers exist on purpose.

| | `id` | `pid` |
| --- | --- | --- |
| Purpose | Row addressing, joins, URLs | The identity a human uses |
| Shape | `BIGINT IDENTITY` | `PAT-000042` |
| Who assigns it | The database | `SequencePidGenerator`, from a dedicated sequence |
| Mutable | n/a | Never |
| In the API | `/api/v1/patients/{id}` | `/api/v1/patients/by-pid/{pid}` |

**The PID is allocated by the server, and the create request has no `pid` field at all.** Letting a
client choose it would mean trusting the client for uniqueness, and would make typos permanent on
the one field that is supposed to be permanent. The controller test
`ignoresClientSuppliedPid` asserts that a `pid` smuggled into the request body is ignored.

**Why a database sequence and not the alternatives:**

| Approach | Why not |
| --- | --- |
| `MAX(pid) + 1` | Two concurrent creates read the same maximum and collide. |
| A counter row with `SELECT ... FOR UPDATE` | Correct, but holds a row lock for the length of the transaction and serialises all creates. |
| UUID | Unique, but cannot be read out over the phone or written on a wristband. |
| `"PAT-" + id` after insert | Requires a second `UPDATE` and forces the column to be nullable at insert time, which weakens the `NOT NULL` constraint. |
| **Database sequence** | The database serialises allocation, no lock is held, and the result is short and human readable. |

Sequences are not transactional, so a rolled back create burns a number and the PIDs have gaps.
That is intentional: gap free numbering would mean serialising every insert, and a PID only has to
be *unique*, not *contiguous*.

The dialect specific part is isolated. `PidGeneratorConfig` reads the database product name from
the JDBC metadata at startup and picks Spring's `H2SequenceMaxValueIncrementer` or
`PostgresSequenceMaxValueIncrementer`. Supporting another engine is one more `case`.

Uniqueness is ultimately guaranteed by `CONSTRAINT uk_patient_pid UNIQUE (pid)`. The sequence keeps
the happy path collision free; the constraint is what makes it true.

### 5.3 Database schema, constraints and indexes

```sql
CREATE SEQUENCE patient_pid_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE patient (
    id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pid           VARCHAR(20)  NOT NULL,
    first_name    VARCHAR(60)  NOT NULL,
    last_name     VARCHAR(60)  NOT NULL,
    date_of_birth DATE         NOT NULL,
    gender        VARCHAR(10)  NOT NULL,
    phone_no      VARCHAR(20)  NOT NULL,
    street        VARCHAR(200) NOT NULL,
    suburb        VARCHAR(100) NOT NULL,
    state         VARCHAR(3)   NOT NULL,
    postcode      VARCHAR(4)   NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT uk_patient_pid      UNIQUE (pid),
    CONSTRAINT ck_patient_gender   CHECK (gender IN ('MALE','FEMALE','OTHER','UNKNOWN')),
    CONSTRAINT ck_patient_state    CHECK (state IN ('ACT','NSW','NT','QLD','SA','TAS','VIC','WA')),
    CONSTRAINT ck_patient_postcode CHECK (LENGTH(postcode) = 4)
);

CREATE INDEX ix_patient_name ON patient (last_name, first_name);
CREATE INDEX ix_patient_dob  ON patient (date_of_birth);
```

**Why the `CHECK` constraints on enum columns.** The enums are stored as strings
(`@Enumerated(STRING)`) rather than ordinals, because an ordinal silently re-maps every row the day
somebody inserts a value into the middle of the enum. Strings are readable in the database but
accept anything, so the `CHECK` puts the value set back under the database's control: a bad row
cannot be written by a migration, a bulk import or a hand typed `UPDATE`, not just by this
application.

**Why there is no `CHECK` that the date of birth is in the past.** It would have to reference
`CURRENT_DATE`, which is not immutable; PostgreSQL rejects such a constraint outright, and where it
is accepted it is only evaluated at write time anyway. The rule is enforced by `@Past`.

**Indexes.**

- `ix_patient_name (last_name, first_name)` matches the grid's default ordering, so the database
  can satisfy the `ORDER BY` from the index instead of sorting every page.
- `ix_patient_dob` supports sorting by date of birth and any future "born between" report.
- `uk_patient_pid` is both the uniqueness guarantee and the index for `GET /patients/by-pid/{pid}`.

**What is deliberately missing.** Search is a case insensitive `LIKE` with a leading wildcard
(`%jane%`), which no B-tree index can serve. The natural next step is a lower-cased expression
index:

```sql
CREATE INDEX ix_patient_last_name_lower ON patient (LOWER(last_name));
```

H2 does not support expression indexes, so rather than add DDL that only works on one engine it is
written up in a comment in the migration and here. On PostgreSQL the full answer is those
expression indexes for prefix search plus a `pg_trgm` GIN index for the leading wildcard case; at
a larger scale still, a dedicated search index. At demo volumes the sequential scan is
irrelevant, and pretending otherwise would be premature optimisation, but the limitation is real
and is documented rather than hidden.

### 5.4 Validation

Validation lives in three places, each doing a different job.

**1. Bean Validation on the request DTOs** rejects malformed input before any business code runs.
Messages are written for a human: `"first name is required"`, not `"must not be blank"`.

**2. Two custom constraints** encode the domain rules the brief implies.

`@AustralianPhone` + `PhoneNumbers`. People type the same number many ways:
`0412 345 678`, `(02) 9876-5432`, `+61 412 345 678`. Storing those verbatim makes exact match
search and de-duplication impossible, so every number is canonicalised to E.164 on the way in and
rendered back in local format on the way out:

| Typed | Stored | Displayed |
| --- | --- | --- |
| `0412 345 678` | `+61412345678` | `0412 345 678` |
| `(02) 9876 5432` | `+61298765432` | `(02) 9876 5432` |
| `+61 412 345 678` | `+61412345678` | `0412 345 678` |

Normalisation is idempotent, which is tested, so re-saving a patient cannot corrupt the number.

`@PostcodeMatchesState`. A `\d{4}` pattern cannot catch the single most common error on an
Australian address form: picking the wrong state from the dropdown. Each `AustralianState` carries
its Australia Post postcode ranges, and the class level constraint checks the postcode against
them:

| State | Ranges |
| --- | --- |
| ACT | 0200-0299, 2600-2618, 2900-2920 |
| NSW | 1000-1999, 2000-2599, 2619-2899, 2921-2999 |
| NT | 0800-0899, 0900-0999 |
| QLD | 4000-4999, 9000-9999 |
| SA | 5000-5799, 5800-5999 |
| TAS | 7000-7799, 7800-7999 |
| VIC | 3000-3999, 8000-8999 |
| WA | 6000-6797, 6800-6999 |

Keeping the ranges on the enum rather than in a validator `switch` means the reference data lives
in one place and the validator stays trivial. A test asserts that no two states claim the same
postcode, which is the kind of mistake that is easy to make and invisible afterwards.

The violation is reported against the `postcode` property, not the object, so the Angular form can
highlight the exact input:

```json
{ "field": "address.postcode",
  "message": "postcode 2000 is not allocated to VIC (valid ranges: [3000-3999, 8000-8999])" }
```

Each constraint also stays in its lane: a postcode that is not four digits is left to `@Pattern`
rather than being reported twice, because showing a user two errors for one mistake is worse than
showing one.

**3. Database constraints** are the last line. Everything above can be bypassed by a bulk import or
a hand written `UPDATE`; `NOT NULL`, `UNIQUE` and `CHECK` cannot.

### 5.5 Search and pagination

**Search.** One `search` parameter is matched, case insensitively, against PID, first name, last
name, and the concatenation `"first last"`. The brief asks for "PID or patient name", and a user
who types `jane citizen` expects a hit even though no single column contains that string:

```sql
WHERE LOWER(p.pid)       LIKE :term
   OR LOWER(p.firstName) LIKE :term
   OR LOWER(p.lastName)  LIKE :term
   OR LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE :term
```

Written as explicit JPQL rather than a derived method name, because the derived equivalent
(`findByPidContainingIgnoreCaseOrFirstNameContainingIgnoreCase...`) is unreadable and still cannot
express the full name match.

The term is lower-cased and wrapped in wildcards in the service, in one place, and **LIKE
metacharacters are escaped** so that a user searching for `50%` does not match every row.

**Pagination happens in the database.** `Pageable` goes into the repository and Spring Data issues
`LIMIT`/`OFFSET` plus a separate `COUNT`. The application never loads the table and slices it.

Two guard rails sit in front of it:

- **Page size is capped at 100.** Without the cap, `?size=1000000` is a denial of service made of
  one query string.
- **Sort properties are restricted to an allow list**: `pid`, `firstName`, `lastName`,
  `dateOfBirth`, `gender`, `createdAt`, `updatedAt`. Anything else is a `400` with the list of what
  *is* allowed. Without the allow list an unknown property reaches Hibernate and surfaces as an
  opaque `500`, and every mapped property silently becomes part of the public API.

When no sort is requested a deterministic default (`lastName, firstName`) is applied, because an
unordered paginated query can return the same row on two different pages.

**The response envelope is our own type,** not Spring Data's `Page`:

```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 42,
  "totalPages": 5, "first": true, "last": false }
```

`PageImpl` serialises to an implementation defined shape that Spring Boot itself warns about, and
it is not part of Spring Data's public contract. Declaring `PageResponse` gives the Angular client
a contract that will not move underneath it on the next upgrade.

### 5.6 Concurrency: preventing lost updates

Two users open the same patient. One corrects the phone number, the other corrects the address.
Both save. With a naive implementation the second write silently discards the first: the
**lost update** problem.

The patient carries a JPA `@Version` column, the API returns it on every read, and `PUT` **requires**
the client to send back the version it last read. The service compares before writing:

```
GET  /api/v1/patients/44        -> { ..., "version": 0 }
PUT  /api/v1/patients/44  { ..., "version": 0 }   -> 200, version becomes 1
PUT  /api/v1/patients/44  { ..., "version": 0 }   -> 409 Conflict
```

```json
{ "status": 409, "title": "Concurrent modification",
  "detail": "Patient 44 has been modified by another user: you are editing version 0 but the
             current version is 1. Reload the patient and re-apply your changes.",
  "expectedVersion": 0, "actualVersion": 1 }
```

The check is made explicitly in the service **and** by the `@Version` column. That is not
redundant:

- the explicit comparison produces a clear 409 naming both versions, which is what the UI shows;
- the column closes the narrow race between that read and the flush, when another transaction
  commits in between. `saveAndFlush` forces the `UPDATE` to happen inside the service method so
  that `OptimisticLockingFailureException` is translated by our handler instead of escaping during
  a post-commit flush.

`version` is `@NotNull` on the update DTO, so a client cannot opt out of concurrency checking by
omitting it.

**Why `version` in the body rather than an `ETag`/`If-Match` header.** `If-Match` is the more
RESTful spelling of the same idea. The version is carried in the representation instead because it
is simpler for a typed client to round trip a field it already has than to read a header, and
because it keeps the contract visible in the OpenAPI schema. The trade-off is noted; either is
defensible.

### 5.7 Error handling

Every error the API can produce is defined in one `@RestControllerAdvice` and returned as an
RFC 9457 problem document (`application/problem+json`).

| Situation | Status | `title` |
| --- | --- | --- |
| Patient id or PID does not exist | 404 | Patient not found |
| Bean Validation failure | 400 | Validation failed (plus an `errors` array) |
| Unparseable body, bad enum, bad date | 400 | Malformed request body |
| Path or query parameter of the wrong type | 400 | Invalid parameter |
| Sort property not on the allow list | 400 | Invalid sort |
| Version mismatch, or optimistic lock failure | 409 | Concurrent modification |
| Unique or not-null violation | 409 | Data conflict |
| Anything else | 500 | Internal server error |

Validation failures carry a per field array, sorted by field name so the response is deterministic:

```json
{ "status": 400, "title": "Validation failed",
  "detail": "The request contains 4 invalid field(s). See 'errors' for details.",
  "errors": [
    { "field": "address.postcode", "message": "postcode 2000 is not allocated to VIC (valid ranges: [3000-3999, 8000-8999])", "rejectedValue": "2000" },
    { "field": "dateOfBirth",      "message": "date of birth must be in the past", "rejectedValue": "2099-01-01" },
    { "field": "firstName",        "message": "first name is required", "rejectedValue": "" },
    { "field": "phoneNo",          "message": "must be a valid Australian phone number, for example 0412 345 678 or (02) 9876 5432", "rejectedValue": "12345" }
  ] }
```

A UI that can only say "validation failed" forces the user to guess which box is wrong. This shape
is what lets the Angular form put each message under the control that caused it.

Three things this buys, all of them tested:

1. **Nothing internal leaks.** The catch-all logs the exception in full for operators and returns a
   generic message; `server.error.include-stacktrace: never` and `include-message: never` close the
   default Spring error page as a second route. A test asserts that an
   `IllegalStateException("connection pool exhausted at com.zaxxer")` does not put `zaxxer` in the
   response body.
2. **Controllers and services throw meaningful domain exceptions** instead of assembling
   `ResponseEntity` objects, so the business code reads as business code.
3. **The client has one error shape to handle.**

### 5.8 Layering and transactions

`PatientController` binds and validates the request, calls exactly one service method, and chooses
a status code. It contains no business logic, which is what makes the service testable without a
servlet container.

`PatientService` holds the use cases. Anything a second delivery mechanism would otherwise have to
repeat (a batch importer, an HL7 feed, a message listener) lives here: PID allocation, phone
normalisation, trimming, the version check, the paging guard rails.

The service is annotated `@Transactional(readOnly = true)` at class level, and individual write
operations opt into a read/write transaction. An accidental write from a query path therefore fails
loudly instead of silently committing, and read paths give the driver and the database the hint
that no write is coming.

`spring.jpa.open-in-view` is **disabled**. The default (`true`) keeps a database connection open
for the whole request so that lazy associations still resolve while the view renders. That hides
N+1 queries, holds connections far longer than necessary, and makes the transaction boundary
invisible. Here entities are mapped to DTOs inside the service transaction, so nothing lazy escapes
it.

### 5.9 Configuration and profiles

| Profile | Purpose |
| --- | --- |
| `demo` (default) | H2 in memory, 42 seed patients, H2 console, Swagger UI, SQL logging |
| `test` | H2 in memory, **no** seed data, used by the automated tests |
| `prod` | Data source from the environment, no seed data, no H2 console, no Swagger UI |

**Seed data is a separate Flyway location.** `db/migration` holds the schema; `db/seed` holds the
fixtures, and only the `demo` profile adds it to `spring.flyway.locations`. Tests therefore never
depend on demo rows, and a production deployment gets the schema without them. A test asserts that
no `V900` fixture migration was applied under the `test` profile.

The fixtures are realistic on purpose: every seeded phone number is a valid Australian number and
every postcode belongs to the state next to it, so the demo data would survive the same validation
the API applies to user input.

`application-prod.yml` is committed even though the assessment only ever runs `demo`, because the
intended deployment posture is part of the design: secrets from the environment, no console, no
interactive docs, CORS empty because both applications share an origin.

---

## 6. REST API reference

Base path `/api/v1`. Interactive documentation at `/swagger-ui.html` while the application runs.

| Method | Path | Purpose | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET` | `/patients` | List, search, page, sort | 200 | 400 |
| `GET` | `/patients/{id}` | One patient by surrogate id | 200 | 400, 404 |
| `GET` | `/patients/by-pid/{pid}` | One patient by business PID | 200 | 404 |
| `POST` | `/patients` | Create; PID allocated by the server | 201 + `Location` | 400 |
| `PUT` | `/patients/{id}` | Replace; requires `version` | 200 | 400, 404, 409 |
| `DELETE` | `/patients/{id}` | Delete | 204 | 404 |
| `GET` | `/reference-data/genders` | Dropdown values | 200 | |
| `GET` | `/reference-data/states` | Dropdown values | 200 | |

Query parameters on `GET /patients`:

| Parameter | Default | Notes |
| --- | --- | --- |
| `page` | `0` | Zero based |
| `size` | `10` | Capped at 100 |
| `sort` | `lastName,asc` | Allow list only; `,asc` or `,desc` |
| `search` | none | Matched against PID, first name, last name, "first last" |

**The reference data endpoints exist so the UI does not hard code the value sets.** If a state or a
gender is ever added, the dropdowns pick it up without a front end redeploy, and the two sides
cannot drift apart.

### Worked examples

```bash
# List, second page of 5, sorted by date of birth descending
curl 'http://localhost:8080/api/v1/patients?page=1&size=5&sort=dateOfBirth,desc'

# Search by name or PID
curl 'http://localhost:8080/api/v1/patients?search=jane'
curl 'http://localhost:8080/api/v1/patients?search=PAT-000012'

# Create. Note: no "pid" field; the server allocates it.
curl -X POST http://localhost:8080/api/v1/patients \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Citizen","dateOfBirth":"1985-04-12",
       "gender":"FEMALE","phoneNo":"(02) 9876 5432",
       "address":{"street":"12 Wallaby Way","suburb":"Sydney","state":"NSW","postcode":"2000"}}'

# Update. "version" must be the value last read.
curl -X PUT http://localhost:8080/api/v1/patients/43 \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Doe","dateOfBirth":"1985-04-12",
       "gender":"FEMALE","phoneNo":"0412 345 678",
       "address":{"street":"1 Collins Street","suburb":"Melbourne","state":"VIC","postcode":"3000"},
       "version":0}'

# Delete
curl -X DELETE http://localhost:8080/api/v1/patients/43
```

---

## 7. Front end design

### Structure

```
src/app/
  app.ts / app.config.ts / app.routes.ts   shell, providers, lazy routes
  core/
    api/patient-api.ts                     the only place that knows the API URLs
    models/                                wire types mirroring the server DTOs
    interceptors/error.interceptor.ts      every HTTP failure -> one ApiError
    util/dates.ts, au-date-adapter.ts      date handling at the UI boundary
  features/patients/
    patient-list/                          the grid
    patient-form/                          create and edit
  shared/confirm-dialog/                   reusable confirmation
```

### Server side everything

The grid holds **one page at a time**. Paging, sorting and searching are query parameters, applied
by the database, so the grid behaves identically with 42 patients and with 4,000,000. The Material
paginator is bound to the server's `totalElements`, not to the length of the rendered array, which
is what a test asserts explicitly.

The table renders from a plain array rather than a `MatTableDataSource`, because the data source
exists to do client side paging, sorting and filtering, which is exactly what must not happen here.

### One query signal

Search, page, sort and reload all funnel through a single `query` signal, and one `effect` turns a
change in it into a request. Keeping a single source of truth is what stops the classic grid bug
where the page index and the filter get out of step and the user sees page 4 of a result set that
now has one page. Changing the search term or the sort explicitly resets the page to 0 for the same
reason.

### Debounced search

Typing "jane" would otherwise fire four requests. `debounceTime(300)` plus `distinctUntilChanged()`
makes the server load proportional to intent rather than to typing speed. Tested with fake timers:
four keystrokes produce one request.

### The server has the final say on validation

Client side validators mirror the server's rules so obvious mistakes are caught without a round
trip, but they are a convenience, never the guarantee. When the API rejects a save, each field
violation is projected back onto the matching form control:

```ts
const control = this.form.get(violation.field);  // e.g. "address.postcode"
control.setErrors({ ...control.errors, server: violation.message });
```

That is what makes a rule that only exists on the server behave like any other field error in the
UI. Pick VIC with postcode 2000 and the message appears under the postcode input, naming the valid
ranges, instead of in a banner the user has to translate into "which box do I fix?".

Those server errors are cleared as soon as anything in the form changes, because a server verdict
describes one specific payload. Without that, correcting the *state* would leave the stale
rejection sitting on the *postcode*, which reads as if the fix had not worked. (This was a real bug
found while testing the flow in the browser; see [section 9](#9-bugs-found-and-fixed-while-building-this).)

### Errors

One HTTP interceptor converts every failure into a single `ApiError` type, whether the server sent
a problem document, an unexpected HTML error page, or nothing at all because it is not running. So
components write `error: (e: ApiError) => ...` and always have a message that is safe to show. A
connection refused becomes "Cannot reach the server. Check that the API is running on
http://localhost:8080" rather than "status 0".

### Angular 21 idioms used

- **Standalone components** throughout; no `NgModule`.
- **Signals** for component state, with `computed` for derived values.
- **Zoneless change detection**, the Angular 21 default: updates are driven by signals rather than
  by zone.js monkey patching.
- **New control flow** (`@if`, `@for`) rather than `*ngIf` / `*ngFor`.
- **`inject()`** rather than constructor parameter injection.
- **`withComponentInputBinding()`**, so the `:id` route parameter arrives as a component input and
  the form never touches `ActivatedRoute`.
- **Lazy `loadComponent` routes**, so the initial bundle is the grid only; the form, datepicker and
  dialog arrive on demand.
- **No `@angular/animations`**: Angular Material 21 animates with CSS, so the package is not a
  dependency at all.

### Accessibility and responsiveness

Icon-only buttons carry `aria-label`s naming the patient (`"Delete Jane Citizen"`); the delete
dialog takes focus and is dismissible with Escape; the progress bar has a reserved height so the
table does not jump when loading starts; the table scrolls horizontally on a phone rather than
compressing the address column into unreadable wrapping; the form grid collapses from two columns
to one with `auto-fit` and no media query. Verified at 375px.

---

## 8. Testing strategy

**177 tests.** Each level has a distinct job, and none of them duplicates another.

### Back end, 131 tests

| Suite | Kind | What it proves |
| --- | --- | --- |
| `PatientServiceTest` (23) | Pure Mockito, no Spring | The business rules: PID comes from the generator not the client, phones are normalised, names trimmed, a stale version writes **nothing**, search terms are escaped, page size is capped, unknown sort properties are rejected |
| `PatientControllerTest` (24) | `@WebMvcTest` slice | Status codes, JSON shape, request binding, the `Location` header, and that every error becomes the right problem document |
| `PatientRepositoryTest` (9) | `@DataJpaTest`, real schema | The search JPQL, including the full name match; that paging happens in the database; that `UNIQUE (pid)` is enforced by the database |
| `PatientApiIntegrationTest` (7) | `@SpringBootTest`, full stack | That the layers are actually wired together: a real create/read/update/delete cycle, sequential PIDs, `@Version` incrementing, real server side paging |
| `PhoneNumbersTest` (27) | Parameterised | 12 input formats normalise to one canonical value, 8 invalid ones are rejected, normalisation is idempotent |
| `PostcodeMatchesStateValidatorTest` (38) | Real Bean Validation engine | 19 valid and 8 invalid state/postcode pairs, that the violation points at the right field, that no two states claim the same postcode, and that format errors are not double reported |
| `PatientServiceApplicationTests` (3) | Context | That Flyway ran, that `ddl-auto: validate` agreed with the migrated schema, and that no fixtures leaked into the test profile |

A few choices worth calling out:

- **The mapper is a real instance in the service tests, not a mock.** It is pure, side effect free
  logic; mocking it would make the tests assert on mock interactions rather than on real output.
- **The clock is fixed** (`Clock.fixed(...)`), so the assertion `age == 41` cannot start failing on
  a birthday.
- **The negative assertions carry the weight.** `rejectsStaleVersion` checks not only that an
  exception is thrown but that `saveAndFlush` was never called and the entity was not mutated. A
  test that only checks the exception would pass even if the data had already been corrupted.
- **`ddl-auto: validate` in the test profile** means every `@SpringBootTest` run is also a check
  that the entity mapping still matches the migration.

### Front end, 46 tests

| Suite | What it proves |
| --- | --- |
| `patient-api.spec` (11) | Method, URL and query parameters for every endpoint; that a blank search term is omitted rather than sent as an empty filter; that the create body has no `pid` |
| `error.interceptor.spec` (6) | Problem documents, field violations, conflict versions, a non-JSON error body and an unreachable server all collapse into one `ApiError` |
| `au-date-adapter.spec` (10) | Day first parsing, separator styles, two digit year expansion, and that `31/02` is rejected rather than rolled over to 3 March |
| `dates.spec` (6) | Round trips that do not drift a day across timezones |
| `patient-list.spec` (10) | That paging is genuinely server side (the server's total is displayed, the API is called again on a page change), that typing is debounced into one request, that a delete is confirmed first |
| `app.spec` (3) | The shell renders |

### What is not covered

No end-to-end browser suite (Playwright or Cypress). The flows were instead driven manually in a
browser during development, which is how the two bugs in the next section were found. For a
production system the create/edit/delete happy paths belong in an e2e suite running in CI; for a
timeboxed assessment the layered tests above give better coverage per minute.

---

## 9. Bugs found and fixed while building this

Both were found by actually driving the application in a browser rather than by reading the code,
which is the argument for doing that at least once before calling something done.

**1. Dates typed into the form were recorded as the wrong day.** Angular Material's
`NativeDateAdapter` *formats* according to `MAT_DATE_LOCALE`, so with `en-AU` it displays
`dd/mm/yyyy`. But its `parse()` falls back to `Date.parse()`, which reads `12/04/1985` as **4
December** in the US convention. The field then re-renders as `04/12/1985` and looks plausible, so
the wrong birthday is stored silently. `AuDateAdapter` overrides `parse()` to read day first, and
rejects impossible dates such as `31/02/2023` instead of letting the `Date` constructor roll them
over to 3 March. Ten tests cover it.

**2. A server validation error stuck to the wrong field after the user fixed it.** Submitting
postcode 2000 with state VIC correctly put the error on the postcode input. Changing the **state**
to NSW and saving again left the old message visible, as though the correction had not worked,
because nothing cleared errors that had come from the server. The form now clears them on any
value change, since a server verdict applies to one specific payload.

A third issue was caught by the build rather than the browser: the first version of the migration
used lower-cased expression indexes, which H2 does not support. Rather than silently dropping them,
the limitation and the PostgreSQL follow-up are written into the migration and into
[section 5.3](#53-database-schema-constraints-and-indexes).

---

## 10. Deliberate non-goals and what production would need

Things consciously left out, with the reasoning, so their absence is read as a decision rather than
an oversight.

| Not implemented | Why | What production would do |
| --- | --- | --- |
| **Authentication and authorisation** | Not in the brief, and a half implemented auth layer is worse than none. | Spring Security with OAuth2/OIDC; the API is already versioned and stateless, so this is additive. Patient data is sensitive, so this would be the first thing added. |
| **Audit trail of *who* changed a record** | Requires an authenticated principal, which does not exist yet. | `@CreatedBy` / `@LastModifiedBy` with an `AuditorAware` fed by the security context; Hibernate Envers for full history. The `createdAt` / `updatedAt` half is already in place. |
| **Soft delete** | The brief says "delete existing patient data", so delete means delete. | Clinical records are rarely hard deleted. A `deleted_at` column plus a default filter, or an archive table, would be the real answer, and it changes the uniqueness story for PID. |
| **Rate limiting** | Infrastructure concern. | API gateway or Bucket4j. |
| **Full text search** | Sequential scan is irrelevant at this volume. | Expression indexes and `pg_trgm` on PostgreSQL, then a dedicated search index. Documented in [5.3](#53-database-schema-constraints-and-indexes). |
| **Caching** | Nothing here is read-heavy enough to justify the invalidation complexity. | Reference data is the obvious first candidate; it changes about never. |
| **End-to-end browser tests** | Time budget; layered tests give more coverage per minute. | Playwright over the CRUD happy paths in CI. |
| **Containerisation** | The brief asks for a runnable project, and `./mvnw spring-boot:run` plus `npm start` is fewer moving parts to review. | A multi-stage Dockerfile building the Angular bundle into the Spring Boot jar's static resources, so one artefact serves both and CORS disappears entirely. |
| **CI pipeline** | Out of scope. | GitHub Actions running both test suites on every push; both are already single commands. |

---

## 11. Project layout

```
.
+-- README.md                     this file
+-- backend/
|   +-- mvnw, mvnw.cmd, .mvn/     Maven wrapper (no local Maven needed)
|   +-- pom.xml
|   +-- src/main/java/com/xtramile/patient/
|   |   +-- PatientServiceApplication.java
|   |   +-- config/               JPA auditing + Clock, PID incrementer, CORS, OpenAPI
|   |   +-- domain/               Patient, AustralianAddress, AustralianState, Gender
|   |   +-- repository/           PatientRepository (Spring Data JPA + search JPQL)
|   |   +-- service/              PatientService, PidGenerator, domain exceptions
|   |   +-- validation/           @AustralianPhone, @PostcodeMatchesState, PhoneNumbers
|   |   +-- web/                  controllers, GlobalExceptionHandler, DTOs, mapper
|   +-- src/main/resources/
|   |   +-- application.yml       base config
|   |   +-- application-demo.yml  local: seed data, verbose logging
|   |   +-- application-prod.yml  production shaped config
|   |   +-- db/migration/         V1 schema, owned by Flyway
|   |   +-- db/seed/              V900 demo fixtures, demo profile only
|   +-- src/test/                 131 tests + application-test.yml
+-- frontend/
    +-- package.json, angular.json, proxy.conf.json
    +-- src/app/
        +-- core/                 API client, models, interceptor, date utilities
        +-- features/patients/    patient-list (grid), patient-form (create/edit)
        +-- shared/               confirm dialog
```

---

## 12. Commands reference

### Back end

```bash
cd backend

./mvnw spring-boot:run                              # run (demo profile, seeded)
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod   # needs DB_URL, DB_USERNAME, DB_PASSWORD
./mvnw test                                         # 131 tests
./mvnw test -Dtest=PatientServiceTest               # one suite
./mvnw clean package                                # build the jar
java -jar target/patient-service-0.0.1-SNAPSHOT.jar # run the jar
```

### Front end

```bash
cd frontend

npm install
npm start          # dev server on :4200, proxying /api to :8080
npm run build      # production bundle into dist/
npm run test:ci    # 46 tests, single run
npm test           # watch mode
npm run format     # Prettier
```

### Handy API calls

```bash
curl 'http://localhost:8080/api/v1/patients?size=5'
curl 'http://localhost:8080/api/v1/patients?search=jane'
curl  http://localhost:8080/api/v1/patients/1
curl  http://localhost:8080/v3/api-docs        # OpenAPI document
curl  http://localhost:8080/actuator/health
```
