package com.bookflow.schedules.application;

import com.bookflow.businesses.authorization.*;
import com.bookflow.employees.repository.EmployeeRepository;
import com.bookflow.schedules.api.*;
import com.bookflow.schedules.domain.*;
import com.bookflow.schedules.repository.ScheduleRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Profile("!test")
public class ScheduleService {
    private final TenantAuthorizationService authorization;
    private final EmployeeRepository employees;
    private final ScheduleRepository repository;
    private final ScheduleRequestValidator validator;

    public ScheduleService(TenantAuthorizationService authorization, EmployeeRepository employees,
                           ScheduleRepository repository, ScheduleRequestValidator validator) {
        this.authorization=authorization; this.employees=employees; this.repository=repository; this.validator=validator;
    }

    @Transactional public WorkingScheduleRule createRule(UUID user, UUID tenant, UUID employee, WorkingRuleRequest request) {
        manage(user,tenant); requireEmployee(tenant,employee);
        var values=validator.createRule(request); requireAssignment(tenant,employee,values.branchId());
        repository.lockRuleKey(tenant,employee,values.branchId(),values.weekday());
        if(repository.ruleOverlaps(tenant,employee,values,null)) throw new ScheduleConflictException();
        return repository.createRule(tenant,employee,values);
    }
    public List<WorkingScheduleRule> listRules(UUID user,UUID tenant,UUID employee){view(user,tenant);requireEmployee(tenant,employee);return repository.listRules(tenant,employee);}
    public WorkingScheduleRule getRule(UUID user,UUID tenant,UUID employee,UUID rule){view(user,tenant);requireEmployee(tenant,employee);return rule(tenant,employee,rule);}
    @Transactional public WorkingScheduleRule updateRule(UUID user,UUID tenant,UUID employee,UUID id,WorkingRuleRequest request){
        manage(user,tenant);requireEmployee(tenant,employee);var current=rule(tenant,employee,id);var values=validator.updateRule(request,current);requireAssignment(tenant,employee,values.branchId());
        validator.requireBreaksWithinRule(values,repository.listBreaks(tenant,id));
        repository.lockRuleKey(tenant,employee,values.branchId(),values.weekday());
        if(repository.ruleOverlaps(tenant,employee,values,id)) throw new ScheduleConflictException();
        return repository.updateRule(tenant,employee,id,values).orElseThrow(ResourceNotFoundException::new);
    }
    @Transactional public void deleteRule(UUID user,UUID tenant,UUID employee,UUID id){manage(user,tenant);requireEmployee(tenant,employee);if(!repository.deleteRule(tenant,employee,id))throw new ResourceNotFoundException();}

    @Transactional public ScheduleBreak createBreak(UUID user,UUID tenant,UUID employee,UUID ruleId,ScheduleBreakRequest request){
        manage(user,tenant);requireEmployee(tenant,employee);var rule=rule(tenant,employee,ruleId);repository.lockRuleKey(tenant,employee,rule.branchId(),rule.weekday());
        var values=validator.createBreak(request,rule);if(repository.breakOverlaps(tenant,ruleId,values,null))throw new ScheduleConflictException();return repository.createBreak(tenant,ruleId,values);
    }
    public List<ScheduleBreak> listBreaks(UUID user,UUID tenant,UUID employee,UUID ruleId){view(user,tenant);requireEmployee(tenant,employee);rule(tenant,employee,ruleId);return repository.listBreaks(tenant,ruleId);}
    public ScheduleBreak getBreak(UUID user,UUID tenant,UUID employee,UUID ruleId,UUID breakId){view(user,tenant);requireEmployee(tenant,employee);rule(tenant,employee,ruleId);return scheduleBreak(tenant,ruleId,breakId);}
    @Transactional public ScheduleBreak updateBreak(UUID user,UUID tenant,UUID employee,UUID ruleId,UUID breakId,ScheduleBreakRequest request){
        manage(user,tenant);requireEmployee(tenant,employee);var rule=rule(tenant,employee,ruleId);var current=scheduleBreak(tenant,ruleId,breakId);repository.lockRuleKey(tenant,employee,rule.branchId(),rule.weekday());
        var values=validator.updateBreak(request,current,rule);if(repository.breakOverlaps(tenant,ruleId,values,breakId))throw new ScheduleConflictException();return repository.updateBreak(tenant,ruleId,breakId,values).orElseThrow(ResourceNotFoundException::new);
    }
    @Transactional public void deleteBreak(UUID user,UUID tenant,UUID employee,UUID ruleId,UUID breakId){manage(user,tenant);requireEmployee(tenant,employee);rule(tenant,employee,ruleId);if(!repository.deleteBreak(tenant,ruleId,breakId))throw new ResourceNotFoundException();}

    @Transactional public ScheduleException createException(UUID user,UUID tenant,UUID employee,ScheduleExceptionRequest request){manage(user,tenant);requireEmployee(tenant,employee);var values=validator.createException(request);requireAssignment(tenant,employee,values.branchId());return repository.createException(tenant,employee,values);}
    public List<ScheduleException> listExceptions(UUID user,UUID tenant,UUID employee){view(user,tenant);requireEmployee(tenant,employee);return repository.listExceptions(tenant,employee);}
    public ScheduleException getException(UUID user,UUID tenant,UUID employee,UUID id){view(user,tenant);requireEmployee(tenant,employee);return exception(tenant,employee,id);}
    @Transactional public ScheduleException updateException(UUID user,UUID tenant,UUID employee,UUID id,ScheduleExceptionRequest request){manage(user,tenant);requireEmployee(tenant,employee);var current=exception(tenant,employee,id);var values=validator.updateException(request,current);requireAssignment(tenant,employee,values.branchId());return repository.updateException(tenant,employee,id,values).orElseThrow(ResourceNotFoundException::new);}
    @Transactional public void deleteException(UUID user,UUID tenant,UUID employee,UUID id){manage(user,tenant);requireEmployee(tenant,employee);if(!repository.deleteException(tenant,employee,id))throw new ResourceNotFoundException();}

    private void view(UUID user,UUID tenant){authorization.requirePermission(user,tenant,TenantPermission.SCHEDULE_VIEW);}
    private void manage(UUID user,UUID tenant){authorization.requirePermission(user,tenant,TenantPermission.SCHEDULE_MANAGE);}
    private void requireEmployee(UUID tenant,UUID employee){if(employees.active(tenant,employee).isEmpty())throw new ResourceNotFoundException();}
    private void requireAssignment(UUID tenant,UUID employee,UUID branch){if(!employees.activePair(tenant,employee,branch)||!employees.branchIds(tenant,employee).contains(branch))throw new ResourceNotFoundException();}
    private WorkingScheduleRule rule(UUID tenant,UUID employee,UUID id){return repository.findRule(tenant,employee,id).orElseThrow(ResourceNotFoundException::new);}
    private ScheduleBreak scheduleBreak(UUID tenant,UUID rule,UUID id){return repository.findBreak(tenant,rule,id).orElseThrow(ResourceNotFoundException::new);}
    private ScheduleException exception(UUID tenant,UUID employee,UUID id){return repository.findException(tenant,employee,id).orElseThrow(ResourceNotFoundException::new);}
}
