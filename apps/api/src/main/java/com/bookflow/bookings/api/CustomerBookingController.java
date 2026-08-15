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
@RequestMapping("/api/v1/customer/bookings/{bookingId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer Bookings", description = "Lifecycle cho booking gáº¯n vá»›i customer account")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Request khÃ´ng há»£p lá»‡", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Thiáº¿u hoáº·c sai access token", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "CSRF token thiáº¿u hoáº·c sai", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Booking khÃ´ng thuá»™c customer", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "State conflict hoáº·c SLOT_UNAVAILABLE", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
})
public class CustomerBookingController {
    private final BookingLifecycleService service;

    public CustomerBookingController(BookingLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Customer há»§y booking", description = "Chá»‰ booking cÃ³ customer_user_id khá»›p JWT; báº¯t buá»™c CSRF.")
    public void cancel(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) CancelBookingRequest request,
            Authentication authentication
    ) {
        service.cancelAsCustomer(user(authentication), bookingId, request);
    }

    @PostMapping("/reschedule")
    @Operation(summary = "Customer Ä‘á»•i lá»‹ch booking", description = "Chá»‰ booking cÃ³ customer_user_id khá»›p JWT; báº¯t buá»™c CSRF.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    public BookingResponse reschedule(
            @PathVariable UUID bookingId,
            @RequestBody RescheduleBookingRequest request,
            Authentication authentication
    ) {
        return service.rescheduleAsCustomer(user(authentication), bookingId, request);
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
