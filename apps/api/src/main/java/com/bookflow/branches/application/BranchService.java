package com.bookflow.branches.application;

import com.bookflow.branches.api.CreateBranchRequest;
import com.bookflow.branches.api.UpdateBranchRequest;
import com.bookflow.branches.domain.Branch;
import com.bookflow.branches.repository.BranchRepository;
import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!test")
public class BranchService {
    private final TenantAuthorizationService authorization; private final BranchRequestValidator validator; private final BranchRepository repository;
    public BranchService(TenantAuthorizationService authorization, BranchRequestValidator validator, BranchRepository repository) { this.authorization=authorization; this.validator=validator; this.repository=repository; }
    @Transactional public Branch create(UUID userId, UUID businessId, CreateBranchRequest request) {
        var business=authorization.requirePermission(userId, businessId, TenantPermission.BRANCH_MANAGE);
        return repository.create(businessId, userId, validator.validateCreate(request, business.business().timeZone())).orElseThrow(ResourceNotFoundException::new);
    }
    public List<Branch> list(UUID userId, UUID businessId) { authorization.requirePermission(userId,businessId,TenantPermission.BRANCH_VIEW); return repository.findActiveByTenant(businessId); }
    public Branch get(UUID userId, UUID businessId, UUID branchId) { authorization.requirePermission(userId,businessId,TenantPermission.BRANCH_VIEW); return repository.findActiveByTenantAndId(businessId,branchId).orElseThrow(ResourceNotFoundException::new); }
    @Transactional public Branch update(UUID userId, UUID businessId, UUID branchId, UpdateBranchRequest request) { authorization.requirePermission(userId,businessId,TenantPermission.BRANCH_MANAGE); return repository.updateActive(businessId,branchId,userId,validator.validateUpdate(request)).orElseThrow(ResourceNotFoundException::new); }
    @Transactional public void archive(UUID userId, UUID businessId, UUID branchId) { authorization.requirePermission(userId,businessId,TenantPermission.BRANCH_MANAGE); if (!repository.archiveActive(businessId,branchId,userId) && !repository.existsByTenantAndId(businessId,branchId)) throw new ResourceNotFoundException(); }
}
