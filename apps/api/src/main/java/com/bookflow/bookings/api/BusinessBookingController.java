package com.bookflow.bookings.api;

import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.bookings.application.BookingLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/businesses/{businessId}/bookings/{bookingId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bookings", description = "Quáº£n lÃ½ lifecycle booking theo tenant")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Request khÃ´ng há»£p lá»‡", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Thiáº¿u hoáº·c sai access token", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Thiáº¿u CSRF hoáº·c quyá»n tenant", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Booking/tenant Ä‘Æ°á»£c áº©n", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "State conflict hoáº·c SLOT_UNAVAILABLE", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
})
public class BusinessBookingController {
    private final BookingLifecycleService service;

    public BusinessBookingController(BookingLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Há»§y booking bá»Ÿi business", description = "OWNER/ADMIN; báº¯t buá»™c Bearer JWT vÃ  CSRF.")
    public void cancel(
            @PathVariable UUID businessId,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) CancelBookingRequest request,
            Authentication authentication
    ) {
        service.cancelAsBusiness(user(authentication), businessId, bookingId, request);
    }

    @PostMapping("/reschedule")
    @Operation(summary = "Äá»•i lá»‹ch booking bá»Ÿi business", description = "Giá»¯ nguyÃªn snapshot; server tÃ­nh láº¡i occupied range. Báº¯t buá»™c Bearer JWT vÃ  CSRF.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    public BookingResponse reschedule(
            @PathVariable UUID businessId,
            @PathVariable UUID bookingId,
            @RequestBody RescheduleBookingRequest request,
            Authentication authentication
    ) {
        return service.rescheduleAsBusiness(
                user(authentication), businessId, bookingId, request
        );
    }

    private UUID user(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CurrentBusinessUserUnavailableException();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new CurrentBusinessUserUnavailableException();
        }
    }
}
