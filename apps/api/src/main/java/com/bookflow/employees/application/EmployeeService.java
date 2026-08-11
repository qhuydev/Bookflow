package com.bookflow.employees.application;

import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import com.bookflow.employees.api.CreateEmployeeRequest;
import com.bookflow.employees.api.UpdateEmployeeRequest;
import com.bookflow.employees.domain.Employee;
import com.bookflow.employees.repository.EmployeeRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Profile("!test")
public class EmployeeService {
    private final TenantAuthorizationService authorization;
    private final EmployeeRepository repository;
    private final EmployeeRequestValidator validator;

    public EmployeeService(TenantAuthorizationService authorization, EmployeeRepository repository, EmployeeRequestValidator validator) {
        this.authorization = authorization;
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public Employee create(UUID userId, UUID businessId, CreateEmployeeRequest request) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_MANAGE);
        return repository.create(businessId, userId, validator.create(request)).orElseThrow(ResourceNotFoundException::new);
    }

    public List<Employee> list(UUID userId, UUID businessId) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_VIEW);
        return repository.active(businessId);
    }

    public Employee get(UUID userId, UUID businessId, UUID employeeId) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_VIEW);
        return repository.active(businessId, employeeId).orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public Employee update(UUID userId, UUID businessId, UUID employeeId, UpdateEmployeeRequest request) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_MANAGE);
        return repository.update(businessId, employeeId, userId, validator.update(request)).orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public void archive(UUID userId, UUID businessId, UUID employeeId) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_MANAGE);
        if (!repository.archive(businessId, employeeId, userId) && !repository.exists(businessId, employeeId)) {
            throw new ResourceNotFoundException();
        }
    }

    @Transactional
    public void assignBranch(UUID userId, UUID businessId, UUID employeeId, UUID branchId) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_MANAGE);
        if (!repository.assign(businessId, employeeId, branchId, userId)
                && !repository.activePair(businessId, employeeId, branchId)) {
            throw new ResourceNotFoundException();
        }
    }

    @Transactional
    public void unassignBranch(UUID userId, UUID businessId, UUID employeeId, UUID branchId) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_MANAGE);
        if (!repository.activePair(businessId, employeeId, branchId)) {
            throw new ResourceNotFoundException();
        }
        repository.unassign(businessId, employeeId, branchId, userId);
    }

    public List<UUID> branches(UUID userId, UUID businessId, UUID employeeId) {
        authorization.requirePermission(userId, businessId, TenantPermission.EMPLOYEE_VIEW);
        if (repository.active(businessId, employeeId).isEmpty()) {
            throw new ResourceNotFoundException();
        }
        return repository.branchIds(businessId, employeeId);
    }
}
