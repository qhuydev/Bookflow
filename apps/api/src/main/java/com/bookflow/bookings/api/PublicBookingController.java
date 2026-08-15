package com.bookflow.bookings.api;

import com.bookflow.bookings.application.BookingCreationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/public/businesses/{slug}/bookings")
@Tag(name = "Public Bookings", description = "Tạo booking công khai từ một slot đã chọn")
public class PublicBookingController {
    private final BookingCreationService service;

    public PublicBookingController(BookingCreationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Tạo booking công khai",
            description = "Yêu cầu CSRF cookie/header. Có thể bỏ employeeId để server tự chọn nhân viên phù hợp. Server tự tính giá, thời lượng, buffer, thời điểm kết thúc, trạng thái và expiry."
    )
    @Parameter(
            name = "Idempotency-Key",
            in = ParameterIn.HEADER,
            required = true,
            description = "Khóa retry case-sensitive, 8-200 ký tự an toàn",
            example = "booking-demo-20260820-0900"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = CreateBookingRequest.class),
                    examples = @ExampleObject(value = """
                            {"branchId":"00000000-0000-0000-0000-000000000001","serviceId":"00000000-0000-0000-0000-000000000002","start":"2026-08-20T09:00:00+07:00","customer":{"name":"Khách Demo","email":"demo.customer@example.test","phone":"+84900000000"}}
                            """)
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking được tạo",
                    content = @Content(schema = @Schema(implementation = CreateBookingResponse.class))),
            @ApiResponse(responseCode = "200", description = "Replay idempotent trả lại booking đã tạo",
                    content = @Content(schema = @Schema(implementation = CreateBookingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request hoặc Idempotency-Key không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "CSRF token thiếu hoặc không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Public resource/assignment không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "SLOT_UNAVAILABLE hoặc IDEMPOTENCY_KEY_REUSED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<CreateBookingResponse> create(
            @PathVariable String slug,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateBookingRequest request
    ) {
        var result = service.create(slug, idempotencyKey, request);
        URI location = URI.create("/api/v1/public/businesses/" + slug + "/bookings/"
                + result.response().bookingId());
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.LOCATION, location.toString())
                    .body(result.response());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, location.toString())
                .body(result.response());
    }
}
