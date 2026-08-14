package com.bookflow.schedules.api;

import com.bookflow.schedules.availability.AvailabilityQueryService;
import com.bookflow.schedules.availability.AvailabilityQueryService.PublicAvailability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/public/businesses/{slug}/availability")
@Tag(name = "Public Availability", description = "Khung giờ có thể đặt công khai theo business slug")
public class PublicAvailabilityController {
    private final AvailabilityQueryService service;

    public PublicAvailabilityController(AvailabilityQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Tìm slot có thể đặt theo chi nhánh, dịch vụ, ngày và nhân viên tùy chọn")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kết quả availability, có thể có slots rỗng",
                    content = @Content(schema = @Schema(implementation = PublicAvailability.class))),
            @ApiResponse(responseCode = "400", description = "Query parameter thiếu hoặc sai định dạng"),
            @ApiResponse(responseCode = "404", description = "Resource public không tồn tại hoặc relationship không hợp lệ")
    })
    public PublicAvailability availability(
            @PathVariable String slug,
            @RequestParam UUID branchId,
            @RequestParam UUID serviceId,
            @Parameter(description = "Nhân viên cụ thể; bỏ qua để aggregate mọi nhân viên đủ điều kiện")
            @RequestParam(required = false) UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.availability(slug, branchId, serviceId, employeeId, date);
    }
}
