package com.bookflow.businesses.api;

import com.bookflow.businesses.application.BusinessCreationService;
import com.bookflow.businesses.application.BusinessQueryService;
import com.bookflow.businesses.application.BusinessConfigurationService;
import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.businesses.domain.Business;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/businesses")
@Profile("!test")
@Tag(name = "Businesses")
@SecurityRequirement(name = "bearerAuth")
public class BusinessController {

    private final BusinessCreationService businessCreationService;
    private final BusinessQueryService businessQueryService;
    private final BusinessConfigurationService businessConfigurationService;

    public BusinessController(BusinessCreationService businessCreationService, BusinessQueryService businessQueryService,
                              BusinessConfigurationService businessConfigurationService) {
        this.businessCreationService = businessCreationService;
        this.businessQueryService = businessQueryService;
        this.businessConfigurationService = businessConfigurationService;
    }

    @GetMapping
    @Operation(summary = "Liệt kê business đang hoạt động của user hiện tại")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active businesses visible to the current user"),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<BusinessResponse> listBusinesses(Authentication authentication) {
        return businessQueryService.listForCurrentUser(currentUserId(authentication)).stream()
                .map(BusinessResponse::from)
                .toList();
    }

    @GetMapping("/{businessId}")
    @Operation(summary = "Xem business đang hoạt động nếu user hiện tại có membership")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Business visible to the current user", content = @Content(schema = @Schema(implementation = BusinessResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid business ID", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Business is not visible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public BusinessResponse getBusiness(@PathVariable UUID businessId, Authentication authentication) {
        return BusinessResponse.from(businessQueryService.getForCurrentUser(currentUserId(authentication), businessId));
    }

    @PatchMapping("/{businessId}")
    @Operation(summary = "Cập nhật một phần cấu hình business")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Business configuration updated", content = @Content(schema = @Schema(implementation = BusinessResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid update request or business ID", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "CSRF missing or membership role is insufficient", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Business is not visible", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Business slug already exists", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public BusinessResponse updateBusiness(@PathVariable UUID businessId, @RequestBody UpdateBusinessRequest request,
                                           Authentication authentication) {
        return BusinessResponse.from(businessConfigurationService.update(currentUserId(authentication), businessId, request));
    }

    @PostMapping
    @Operation(summary = "Tạo business và OWNER membership cho user hiện tại")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Business created", content = @Content(schema = @Schema(implementation = BusinessResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid business request", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Business slug already exists", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<BusinessResponse> createBusiness(
            @RequestBody CreateBusinessRequest request,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);
        Business business = businessCreationService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, URI.create("/api/v1/businesses/" + business.id()).toString())
                .body(BusinessResponse.ownerBusiness(business));
    }

    private UUID currentUserId(Authentication authentication) {
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
