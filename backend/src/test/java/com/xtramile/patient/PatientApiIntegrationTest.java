package com.xtramile.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xtramile.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End to end tests over the real stack: HTTP in, Tomcat's request mapping, validation, service,
 * Hibernate, Flyway migrated H2, and back out as JSON.
 *
 * <p>The slice tests prove each layer in isolation; this one proves they are wired together -
 * that the PID sequence really allocates, that {@code @Version} really increments, and that the
 * schema Hibernate validates against is the schema Flyway created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Patient API (full stack)")
class PatientApiIntegrationTest {

    private static final String BASE = "/api/v1/patients";

    private static final String JANE = """
            {
              "firstName": "Jane",
              "lastName": "Citizen",
              "dateOfBirth": "1985-04-12",
              "gender": "FEMALE",
              "phoneNo": "(02) 9876 5432",
              "address": {
                "street": "12 Wallaby Way",
                "suburb": "Sydney",
                "state": "NSW",
                "postcode": "2000"
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientRepository repository;

    @BeforeEach
    void cleanSlate() {
        // The test profile loads no fixtures, but each test still starts from a known state.
        repository.deleteAll();
    }

    @Test
    @DisplayName("create, read, update and delete a patient through the real stack")
    void fullLifecycle() throws Exception {
        // CREATE -------------------------------------------------------------
        MvcResult created = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JANE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pid").value(org.hamcrest.Matchers.matchesPattern("PAT-\\d{6}")))
                .andExpect(jsonPath("$.phoneNo").value("+61298765432"))
                .andExpect(jsonPath("$.phoneNoLocal").value("(02) 9876 5432"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        long id = jsonLong(created, "id");
        String pid = jsonString(created, "pid");

        // READ ---------------------------------------------------------------
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value(pid))
                .andExpect(jsonPath("$.address.formatted").value("12 Wallaby Way, Sydney NSW 2000"));

        mockMvc.perform(get(BASE + "/by-pid/" + pid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // UPDATE -------------------------------------------------------------
        String update = """
                {
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "dateOfBirth": "1985-04-12",
                  "gender": "FEMALE",
                  "phoneNo": "0412 345 678",
                  "address": {
                    "street": "1 Collins Street",
                    "suburb": "Melbourne",
                    "state": "VIC",
                    "postcode": "3000"
                  },
                  "version": 0
                }
                """;

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.pid").value(pid))          // PID is immutable
                .andExpect(jsonPath("$.version").value(1))        // @Version incremented
                .andExpect(jsonPath("$.address.state").value("VIC"));

        // The same body replayed is now stale.
        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.expectedVersion").value(0))
                .andExpect(jsonPath("$.actualVersion").value(1));

        // DELETE -------------------------------------------------------------
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("consecutive creates receive distinct, sequential PIDs")
    void pidsAreUniqueAndSequential() throws Exception {
        String first = jsonString(create(JANE), "pid");
        String second = jsonString(create(JANE.replace("Citizen", "Smith")), "pid");

        assertThat(first).isNotEqualTo(second);
        assertThat(pidNumber(second)).isEqualTo(pidNumber(first) + 1);
    }

    @Test
    @DisplayName("server side pagination really pages: page 2 holds different rows to page 1")
    void pagesServerSide() throws Exception {
        for (int i = 0; i < 12; i++) {
            create(JANE.replace("\"Citizen\"", "\"Surname%02d\"".formatted(i)));
        }

        MvcResult page0 = mockMvc.perform(get(BASE).param("page", "0").param("size", "5").param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andReturn();

        MvcResult page2 = mockMvc.perform(get(BASE).param("page", "2").param("size", "5").param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andReturn();

        assertThat(jsonString(page0, "content[0].lastName")).isEqualTo("Surname00");
        assertThat(jsonString(page2, "content[0].lastName")).isEqualTo("Surname10");
    }

    @Test
    @DisplayName("page size is capped so a client cannot ask for the whole table")
    void capsPageSize() throws Exception {
        create(JANE);

        mockMvc.perform(get(BASE).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("search finds a patient by PID and by name, and filters everything else out")
    void searchesByPidAndName() throws Exception {
        String pid = jsonString(create(JANE), "pid");
        create(JANE.replace("Jane", "Robert").replace("Citizen", "Smith"));

        mockMvc.perform(get(BASE).param("search", pid.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pid").value(pid));

        mockMvc.perform(get(BASE).param("search", "smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Smith"));

        mockMvc.perform(get(BASE).param("search", "jane citizen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(BASE).param("search", "nobody-here"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("validation rejects a bad request before anything is written")
    void validationRejectsBeforePersisting() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(JANE.replace("\"NSW\"", "\"VIC\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("address.postcode"));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("reference data is served from the server's own enums")
    void servesReferenceData() throws Exception {
        mockMvc.perform(get("/api/v1/reference-data/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.value == 'NSW')].label").value("New South Wales (NSW)"));

        mockMvc.perform(get("/api/v1/reference-data/genders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.value == 'MALE')].label").value("Male"));
    }

    // -- helpers -------------------------------------------------------------

    private MvcResult create(String body) throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String jsonString(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$." + path).toString();
    }

    private static long jsonLong(MvcResult result, String path) throws Exception {
        return Long.parseLong(jsonString(result, path));
    }

    private static int pidNumber(String pid) {
        return Integer.parseInt(pid.substring("PAT-".length()));
    }
}
