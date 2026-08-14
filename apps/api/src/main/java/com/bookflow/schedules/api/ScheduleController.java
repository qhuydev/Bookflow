package com.bookflow.schedules.api;

import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.schedules.application.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/businesses/{businessId}/employees/{employeeId}")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Schedules",description="Working rules, breaks and employee schedule exceptions")
@ApiResponses({
        @ApiResponse(responseCode="400",description="Request or date/time is invalid",content=@Content(schema=@Schema(implementation=ProblemDetail.class))),
        @ApiResponse(responseCode="401",description="Authentication required",content=@Content(schema=@Schema(implementation=ProblemDetail.class))),
        @ApiResponse(responseCode="403",description="Membership role cannot perform this operation",content=@Content(schema=@Schema(implementation=ProblemDetail.class))),
        @ApiResponse(responseCode="404",description="Tenant-scoped resource not found",content=@Content(schema=@Schema(implementation=ProblemDetail.class))),
        @ApiResponse(responseCode="409",description="Schedule interval conflicts with existing data",content=@Content(schema=@Schema(implementation=ProblemDetail.class)))
})
public class ScheduleController {
    private final ScheduleService service;
    public ScheduleController(ScheduleService service){this.service=service;}

    @PostMapping("/schedule-rules") @Operation(summary="Tạo working schedule rule")
    public ResponseEntity<WorkingRuleResponse> createRule(@PathVariable UUID businessId,@PathVariable UUID employeeId,@RequestBody WorkingRuleRequest request,Authentication auth){
        var response=WorkingRuleResponse.from(service.createRule(user(auth),businessId,employeeId,request));
        return ResponseEntity.created(URI.create("/api/v1/businesses/"+businessId+"/employees/"+employeeId+"/schedule-rules/"+response.id())).body(response);
    }
    @GetMapping("/schedule-rules") @Operation(summary="Liệt kê working schedule rules")
    public List<WorkingRuleResponse> listRules(@PathVariable UUID businessId,@PathVariable UUID employeeId,Authentication auth){return service.listRules(user(auth),businessId,employeeId).stream().map(WorkingRuleResponse::from).toList();}
    @GetMapping("/schedule-rules/{ruleId}") @Operation(summary="Xem working schedule rule")
    public WorkingRuleResponse getRule(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,Authentication auth){return WorkingRuleResponse.from(service.getRule(user(auth),businessId,employeeId,ruleId));}
    @PatchMapping("/schedule-rules/{ruleId}") @Operation(summary="Cập nhật working schedule rule")
    public WorkingRuleResponse updateRule(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,@RequestBody WorkingRuleRequest request,Authentication auth){return WorkingRuleResponse.from(service.updateRule(user(auth),businessId,employeeId,ruleId,request));}
    @DeleteMapping("/schedule-rules/{ruleId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Xóa working schedule rule")
    public void deleteRule(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,Authentication auth){service.deleteRule(user(auth),businessId,employeeId,ruleId);}

    @PostMapping("/schedule-rules/{ruleId}/breaks") @Operation(summary="Tạo break trong working rule")
    public ResponseEntity<ScheduleBreakResponse> createBreak(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,@RequestBody ScheduleBreakRequest request,Authentication auth){
        var response=ScheduleBreakResponse.from(service.createBreak(user(auth),businessId,employeeId,ruleId,request));
        return ResponseEntity.created(URI.create("/api/v1/businesses/"+businessId+"/employees/"+employeeId+"/schedule-rules/"+ruleId+"/breaks/"+response.id())).body(response);
    }
    @GetMapping("/schedule-rules/{ruleId}/breaks") @Operation(summary="Liệt kê breaks của working rule")
    public List<ScheduleBreakResponse> listBreaks(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,Authentication auth){return service.listBreaks(user(auth),businessId,employeeId,ruleId).stream().map(ScheduleBreakResponse::from).toList();}
    @GetMapping("/schedule-rules/{ruleId}/breaks/{breakId}") @Operation(summary="Xem schedule break")
    public ScheduleBreakResponse getBreak(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,@PathVariable UUID breakId,Authentication auth){return ScheduleBreakResponse.from(service.getBreak(user(auth),businessId,employeeId,ruleId,breakId));}
    @PatchMapping("/schedule-rules/{ruleId}/breaks/{breakId}") @Operation(summary="Cập nhật schedule break")
    public ScheduleBreakResponse updateBreak(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,@PathVariable UUID breakId,@RequestBody ScheduleBreakRequest request,Authentication auth){return ScheduleBreakResponse.from(service.updateBreak(user(auth),businessId,employeeId,ruleId,breakId,request));}
    @DeleteMapping("/schedule-rules/{ruleId}/breaks/{breakId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Xóa schedule break")
    public void deleteBreak(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID ruleId,@PathVariable UUID breakId,Authentication auth){service.deleteBreak(user(auth),businessId,employeeId,ruleId,breakId);}

    @PostMapping("/schedule-exceptions") @Operation(summary="Tạo schedule exception")
    public ResponseEntity<ScheduleExceptionResponse> createException(@PathVariable UUID businessId,@PathVariable UUID employeeId,@RequestBody ScheduleExceptionRequest request,Authentication auth){
        var response=ScheduleExceptionResponse.from(service.createException(user(auth),businessId,employeeId,request));
        return ResponseEntity.created(URI.create("/api/v1/businesses/"+businessId+"/employees/"+employeeId+"/schedule-exceptions/"+response.id())).body(response);
    }
    @GetMapping("/schedule-exceptions") @Operation(summary="Liệt kê schedule exceptions")
    public List<ScheduleExceptionResponse> listExceptions(@PathVariable UUID businessId,@PathVariable UUID employeeId,Authentication auth){return service.listExceptions(user(auth),businessId,employeeId).stream().map(ScheduleExceptionResponse::from).toList();}
    @GetMapping("/schedule-exceptions/{exceptionId}") @Operation(summary="Xem schedule exception")
    public ScheduleExceptionResponse getException(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID exceptionId,Authentication auth){return ScheduleExceptionResponse.from(service.getException(user(auth),businessId,employeeId,exceptionId));}
    @PatchMapping("/schedule-exceptions/{exceptionId}") @Operation(summary="Cập nhật schedule exception")
    public ScheduleExceptionResponse updateException(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID exceptionId,@RequestBody ScheduleExceptionRequest request,Authentication auth){return ScheduleExceptionResponse.from(service.updateException(user(auth),businessId,employeeId,exceptionId,request));}
    @DeleteMapping("/schedule-exceptions/{exceptionId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Xóa schedule exception")
    public void deleteException(@PathVariable UUID businessId,@PathVariable UUID employeeId,@PathVariable UUID exceptionId,Authentication auth){service.deleteException(user(auth),businessId,employeeId,exceptionId);}

    private UUID user(Authentication auth){if(auth==null||!auth.isAuthenticated())throw new CurrentBusinessUserUnavailableException();try{return UUID.fromString(auth.getName());}catch(IllegalArgumentException ex){throw new CurrentBusinessUserUnavailableException();}}
}
