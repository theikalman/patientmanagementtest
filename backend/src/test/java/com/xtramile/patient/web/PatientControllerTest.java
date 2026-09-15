package com.xtramile.patient.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xtramile.patient.domain.AustralianState;
import com.xtramile.patient.domain.Gender;
import com.xtramile.patient.service.PatientService;
import com.xtramile.patient.service.exception.InvalidSortPropertyException;
import com.xtramile.patient.service.exception.PatientNotFoundException;
import com.xtramile.patient.service.exception.StalePatientDataException;
import com.xtramile.patient.web.dto.AddressResponse;
import com.xtramile.patient.web.dto.CreatePatientRequest;
import com.xtramile.patient.web.dto.PageResponse;
import com.xtramile.patient.web.dto.PatientResponse;
import com.xtramile.patient.web.dto.UpdatePatientRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests for the HTTP layer.
 *
 * <p>{@code @WebMvcTest} boots only the web slice - the controller, the
 * {@code GlobalExceptionHandler}, Jackson and validation - with the service mocked out. That is
 * the point: these tests assert on status codes, JSON shape, binding and error translation, and
 * they stay green regardless of what the service or the database do. Business behaviour is
 * proven in {@code PatientServiceTest}, and the two wired together in
 * {@code PatientApiIntegrationTest}.
 */
@WebMvcTest(PatientController.class)
@DisplayName("PatientController (web layer)")
class PatientControllerTest {

    private static final String BASE = "/api/v1/patients";

    private static final String VALID_BODY = """
            {
              "firstName": "Jane",
              "lastName": "Citizen",
              "dateOfBirth": "1985-04-12",
              "gender": "FEMALE",
              "phoneNo": "0412 345 678",
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

    @MockitoBean
    private PatientService service;

    private static PatientResponse sampleResponse() {
        return new PatientResponse(
                1L, "PAT-000001", "Jane", "Citizen", "Jane Citizen",
                LocalDate.of(1985, 4, 12), 41, Gender.FEMALE,
                "+61412345678", "0412 345 678",
                new AddressResponse("12 Wallaby Way", "Sydney", AustralianState.NSW,
                        "New South Wales", "2000", "12 Wallaby Way, Sydney NSW 2000"),
                0L, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("GET /api/v1/patients")
    class ListPatients {

        @Test
        @DisplayName("returns the pagination envelope the Angular grid expects")
        void returnsPageEnvelope() throws Exception {
            when(service.search(isNull(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(sampleResponse()), 0, 10, 1, 1, true, true));

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.content[0].pid").value("PAT-000001"))
                    .andExpect(jsonPath("$.content[0].fullName").value("Jane Citizen"))
                    .andExpect(jsonPath("$.content[0].address.formatted").value("12 Wallaby Way, Sydney NSW 2000"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("passes page, size and sort through to the service")
        void bindsPageable() throws Exception {
            when(service.search(isNull(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(), 2, 25, 0, 0, false, true));

            mockMvc.perform(get(BASE).param("page", "2").param("size", "25").param("sort", "lastName,desc"))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(service).search(isNull(), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(25);
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().getOrderFor("lastName"))
                    .isNotNull()
                    .extracting(org.springframework.data.domain.Sort.Order::isDescending)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("defaults to a page size of 10 so the grid has a sensible first render")
        void defaultsPageSize() throws Exception {
            when(service.search(isNull(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

            mockMvc.perform(get(BASE)).andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(service).search(isNull(), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("forwards the search term")
        void forwardsSearchTerm() throws Exception {
            when(service.search(eq("jane"), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

            mockMvc.perform(get(BASE).param("search", "jane")).andExpect(status().isOk());

            verify(service).search(eq("jane"), any(Pageable.class));
        }

        @Test
        @DisplayName("turns an unsupported sort property into 400, not 500")
        void rejectsUnknownSort() throws Exception {
            when(service.search(isNull(), any(Pageable.class)))
                    .thenThrow(new InvalidSortPropertyException("password", List.of("lastName", "pid")));

            mockMvc.perform(get(BASE).param("sort", "password,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Invalid sort"))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("password")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/patients/{id}")
    class GetOne {

        @Test
        void returnsPatient() throws Exception {
            when(service.findById(1L)).thenReturn(sampleResponse());

            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.pid").value("PAT-000001"))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.dateOfBirth").value("1985-04-12"));
        }

        @Test
        @DisplayName("returns an RFC 9457 problem document on 404")
        void returnsProblemOn404() throws Exception {
            when(service.findById(99L)).thenThrow(PatientNotFoundException.byId(99L));

            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Patient not found"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/patients/99"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("returns 400 when the id is not a number, and never reaches the service")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(get(BASE + "/not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid parameter"));

            verify(service, never()).findById(any());
        }

        @Test
        void findsByBusinessPid() throws Exception {
            when(service.findByPid("PAT-000001")).thenReturn(sampleResponse());

            mockMvc.perform(get(BASE + "/by-pid/PAT-000001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pid").value("PAT-000001"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/patients")
    class CreatePatient {

        @Test
        @DisplayName("returns 201 with a Location header pointing at the new resource")
        void createsAndReturnsLocation() throws Exception {
            when(service.create(any(CreatePatientRequest.class))).thenReturn(sampleResponse());

            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/patients/1"))
                    .andExpect(jsonPath("$.pid").value("PAT-000001"));
        }

        @Test
        @DisplayName("rejects a body with missing required fields, listing every offending field")
        void rejectsMissingFields() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors[*].field",
                            org.hamcrest.Matchers.hasItems(
                                    "firstName", "lastName", "dateOfBirth", "gender", "phoneNo", "address")));

            verify(service, never()).create(any());
        }

        @Test
        @DisplayName("rejects a phone number that is not a valid Australian number")
        void rejectsInvalidPhone() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("0412 345 678", "12345")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("phoneNo"));
        }

        @Test
        @DisplayName("rejects a postcode that does not belong to the chosen state")
        void rejectsPostcodeStateMismatch() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("\"NSW\"", "\"VIC\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("address.postcode"))
                    .andExpect(jsonPath("$.errors[0].message",
                            org.hamcrest.Matchers.containsString("not allocated to VIC")));
        }

        @Test
        @DisplayName("rejects a date of birth in the future")
        void rejectsFutureDob() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("1985-04-12", "2999-01-01")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("dateOfBirth"));
        }

        @Test
        @DisplayName("rejects an unknown gender without leaking a Jackson stack trace")
        void rejectsUnknownEnum() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("FEMALE", "ROBOT")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request body"))
                    .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("Exception"))));
        }

        @Test
        @DisplayName("rejects malformed JSON")
        void rejectsMalformedJson() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request body"));
        }

        @Test
        @DisplayName("ignores a client supplied pid: the server owns that field")
        void ignoresClientSuppliedPid() throws Exception {
            when(service.create(any(CreatePatientRequest.class))).thenReturn(sampleResponse());

            String bodyWithPid = VALID_BODY.replace("{\n  \"firstName\"",
                    "{\n  \"pid\": \"PAT-999999\",\n  \"firstName\"");

            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bodyWithPid))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pid").value("PAT-000001"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/patients/{id}")
    class UpdatePatient {

        private static final String UPDATE_BODY = """
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

        @Test
        void updatesAndReturns200() throws Exception {
            when(service.update(eq(1L), any(UpdatePatientRequest.class))).thenReturn(sampleResponse());

            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("requires the version, so a client cannot opt out of concurrency checking")
        void requiresVersion() throws Exception {
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY.replace(",\n  \"version\": 0", "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("version"));

            verify(service, never()).update(any(), any());
        }

        @Test
        @DisplayName("maps a stale version to 409 and tells the client both version numbers")
        void mapsStaleVersionTo409() throws Exception {
            when(service.update(eq(1L), any(UpdatePatientRequest.class)))
                    .thenThrow(new StalePatientDataException(1L, 0L, 3L));

            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Concurrent modification"))
                    .andExpect(jsonPath("$.expectedVersion").value(0))
                    .andExpect(jsonPath("$.actualVersion").value(3));
        }

        @Test
        void mapsMissingPatientTo404() throws Exception {
            when(service.update(eq(99L), any(UpdatePatientRequest.class)))
                    .thenThrow(PatientNotFoundException.byId(99L));

            mockMvc.perform(put(BASE + "/99").contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/patients/{id}")
    class DeletePatient {

        @Test
        @DisplayName("returns 204 with no body")
        void deletesAndReturns204() throws Exception {
            doNothing().when(service).delete(1L);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(service).delete(1L);
        }

        @Test
        void returns404WhenMissing() throws Exception {
            doThrow(PatientNotFoundException.byId(99L)).when(service).delete(99L);

            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    @DisplayName("an unexpected failure becomes a generic 500 that leaks nothing internal")
    void unexpectedFailureIsSanitised() throws Exception {
        when(service.findById(1L)).thenThrow(new IllegalStateException("connection pool exhausted at com.zaxxer"));

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("zaxxer"))));
    }
}
