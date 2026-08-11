package com.bookflow.branches.api;

import com.bookflow.branches.application.BranchService;
import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/businesses/{businessId}/branches")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {
    private final BranchService service;
    public BranchController(BranchService service) { this.service=service; }
    @PostMapping @Operation(summary="Tạo chi nhánh")
    @ApiResponses({@ApiResponse(responseCode="201", content=@Content(schema=@Schema(implementation=BranchResponse.class))), @ApiResponse(responseCode="400", content=@Content(schema=@Schema(implementation=ProblemDetail.class))), @ApiResponse(responseCode="401", content=@Content(schema=@Schema(implementation=ProblemDetail.class))), @ApiResponse(responseCode="403", content=@Content(schema=@Schema(implementation=ProblemDetail.class))), @ApiResponse(responseCode="404", content=@Content(schema=@Schema(implementation=ProblemDetail.class))), @ApiResponse(responseCode="409", content=@Content(schema=@Schema(implementation=ProblemDetail.class)))})
    public ResponseEntity<BranchResponse> create(@PathVariable UUID businessId, @RequestBody CreateBranchRequest request, Authentication auth) {
        BranchResponse response=BranchResponse.from(service.create(user(auth),businessId,request));
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, URI.create("/api/v1/businesses/"+businessId+"/branches/"+response.id()).toString()).body(response);
    }
    @GetMapping @Operation(summary="Danh sách chi nhánh active")
    public List<BranchResponse> list(@PathVariable UUID businessId, Authentication auth) { return service.list(user(auth),businessId).stream().map(BranchResponse::from).toList(); }
    @GetMapping("/{branchId}") @Operation(summary="Chi tiết chi nhánh active")
    public BranchResponse get(@PathVariable UUID businessId,@PathVariable UUID branchId,Authentication auth) { return BranchResponse.from(service.get(user(auth),businessId,branchId)); }
    @PatchMapping("/{branchId}") @Operation(summary="Cập nhật từng phần chi nhánh")
    public BranchResponse update(@PathVariable UUID businessId,@PathVariable UUID branchId,@RequestBody UpdateBranchRequest request,Authentication auth) { return BranchResponse.from(service.update(user(auth),businessId,branchId,request)); }
    @DeleteMapping("/{branchId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Archive chi nhánh")
    public void archive(@PathVariable UUID businessId,@PathVariable UUID branchId,Authentication auth) { service.archive(user(auth),businessId,branchId); }
    private UUID user(Authentication auth) { if(auth==null||!auth.isAuthenticated()) throw new CurrentBusinessUserUnavailableException(); try { return UUID.fromString(auth.getName()); } catch(IllegalArgumentException ex) { throw new CurrentBusinessUserUnavailableException(); } }
}
