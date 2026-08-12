package com.bookflow.publiccatalog.api;

import com.bookflow.publiccatalog.application.PublicCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/public/businesses/{slug}")
@Tag(name = "Public Catalog", description = "Danh mục công khai, không yêu cầu đăng nhập")
public class PublicCatalogController {
    private final PublicCatalogService service;

    public PublicCatalogController(PublicCatalogService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Hồ sơ business công khai")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PublicBusiness.class))), @ApiResponse(responseCode = "404", description = "Business không tồn tại hoặc không active")})
    public PublicBusiness profile(@PathVariable String slug) { return service.business(slug); }

    @GetMapping("/branches")
    @Operation(summary = "Danh sách chi nhánh active công khai")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PublicBranch.class))), @ApiResponse(responseCode = "404")})
    public List<PublicBranch> branches(@PathVariable String slug) { return service.branches(slug); }

    @GetMapping("/services")
    @Operation(summary = "Danh sách dịch vụ active công khai")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PublicService.class))), @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404")})
    public List<PublicService> services(@PathVariable String slug, @Parameter(description = "Chi nhánh active thuộc business") @RequestParam(required = false) UUID branchId) { return service.services(slug, branchId); }

    @GetMapping("/employees")
    @Operation(summary = "Nhân viên active theo chi nhánh và dịch vụ tùy chọn")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PublicEmployee.class))), @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404")})
    public List<PublicEmployee> employees(@PathVariable String slug, @Parameter(required = true, description = "Chi nhánh active thuộc business") @RequestParam UUID branchId, @Parameter(description = "Dịch vụ active đã gán vào branch") @RequestParam(required = false) UUID serviceId) { return service.employees(slug, branchId, serviceId); }

    public record PublicBusiness(String slug, String name, String timeZone, String currency) {}
    public record PublicBranch(UUID id, String code, String name, String addressLine1, String city, String timeZone) {}
    public record PublicService(UUID id, String name, String description, BigDecimal price, String currency, Integer durationMinutes, Integer bufferBeforeMinutes, Integer bufferAfterMinutes) {}
    public record PublicEmployee(UUID id, String fullName, String bio) {}
}
