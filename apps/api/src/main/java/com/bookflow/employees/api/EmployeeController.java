package com.bookflow.employees.api;

import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.employees.application.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/businesses/{businessId}/employees")
@Tag(name = "Employees", description = "Nhân viên và gán nhân viên vào chi nhánh")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Tạo nhân viên")
    @ApiResponses({@ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<EmployeeResponse> create(@PathVariable UUID businessId, @RequestBody CreateEmployeeRequest request, Authentication authentication) {
        EmployeeResponse response = EmployeeResponse.from(service.create(user(authentication), businessId, request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, URI.create("/api/v1/businesses/" + businessId + "/employees/" + response.id()).toString())
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Danh sách nhân viên active")
    public List<EmployeeResponse> list(@PathVariable UUID businessId, Authentication authentication) {
        return service.list(user(authentication), businessId).stream().map(EmployeeResponse::from).toList();
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Chi tiết nhân viên active")
    public EmployeeResponse get(@PathVariable UUID businessId, @PathVariable UUID employeeId, Authentication authentication) {
        return EmployeeResponse.from(service.get(user(authentication), businessId, employeeId));
    }

    @PatchMapping("/{employeeId}")
    @Operation(summary = "Cập nhật từng phần nhân viên")
    public EmployeeResponse update(@PathVariable UUID businessId, @PathVariable UUID employeeId, @RequestBody UpdateEmployeeRequest request, Authentication authentication) {
        return EmployeeResponse.from(service.update(user(authentication), businessId, employeeId, request));
    }

    @DeleteMapping("/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive nhân viên")
    public void archive(@PathVariable UUID businessId, @PathVariable UUID employeeId, Authentication authentication) {
        service.archive(user(authentication), businessId, employeeId);
    }

    @PutMapping("/{employeeId}/branches/{branchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gán nhân viên vào chi nhánh")
    public void assign(@PathVariable UUID businessId, @PathVariable UUID employeeId, @PathVariable UUID branchId, Authentication authentication) {
        service.assignBranch(user(authentication), businessId, employeeId, branchId);
    }

    @DeleteMapping("/{employeeId}/branches/{branchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bỏ gán nhân viên khỏi chi nhánh")
    public void unassign(@PathVariable UUID businessId, @PathVariable UUID employeeId, @PathVariable UUID branchId, Authentication authentication) {
        service.unassignBranch(user(authentication), businessId, employeeId, branchId);
    }

    @GetMapping("/{employeeId}/branches")
    @Operation(summary = "Danh sách branch ID đang gán cho nhân viên")
    public List<UUID> branches(@PathVariable UUID businessId, @PathVariable UUID employeeId, Authentication authentication) {
        return service.branches(user(authentication), businessId, employeeId);
    }

    private UUID user(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new CurrentBusinessUserUnavailableException();
        try { return UUID.fromString(authentication.getName()); }
        catch (IllegalArgumentException exception) { throw new CurrentBusinessUserUnavailableException(); }
    }
}
