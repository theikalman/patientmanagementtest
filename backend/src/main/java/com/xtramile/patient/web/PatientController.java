package com.xtramile.patient.web;

import com.xtramile.patient.service.PatientService;
import com.xtramile.patient.web.dto.CreatePatientRequest;
import com.xtramile.patient.web.dto.PageResponse;
import com.xtramile.patient.web.dto.PatientResponse;
import com.xtramile.patient.web.dto.UpdatePatientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient CRUD endpoints.
 *
 * <p>The controller is deliberately thin: bind and validate the request, call one service method,
 * choose a status code. It holds no business logic, which is what makes the service layer
 * testable without a servlet container.
 *
 * <p>The path is versioned ({@code /api/v1}) from day one. Adding a version later means breaking
 * every existing client; starting with one costs nothing.
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Create, read, update, delete and search patient records")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "List patients, with server side pagination, sorting and free text search",
            description = """
                    Returns one page of patients. When `search` is supplied the term is matched,
                    case insensitively, against PID, first name, last name and "first last".
                    Sorting is restricted to an allow list: pid, firstName, lastName, dateOfBirth,
                    gender, createdAt, updatedAt. Page size is capped at 100.
                    """)
    public PageResponse<PatientResponse> list(
            @Parameter(description = "Free text term matched against PID or patient name", example = "jane")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {
        return service.search(search, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one patient by surrogate id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient found"),
            @ApiResponse(responseCode = "404", description = "No such patient", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public PatientResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-pid/{pid}")
    @Operation(summary = "Fetch one patient by business PID, for example PAT-000042")
    public PatientResponse getByPid(@PathVariable String pid) {
        return service.findByPid(pid);
    }

    /**
     * Creates a patient and returns 201 with a {@code Location} header pointing at the new
     * resource, as required by the HTTP semantics for POST.
     */
    @PostMapping
    @Operation(summary = "Create a new patient; the PID is allocated by the server")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientResponse created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/patients/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace an existing patient",
            description = "The request must echo the `version` last read by the client. "
                    + "If the record changed in the meantime the request is rejected with 409.")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePatientRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a patient")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
