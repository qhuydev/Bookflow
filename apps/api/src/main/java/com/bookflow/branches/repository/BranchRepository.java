package com.bookflow.branches.repository;

import com.bookflow.branches.application.BranchRequestValidator.ValidatedBranchCreate;
import com.bookflow.branches.application.BranchRequestValidator.ValidatedBranchUpdate;
import com.bookflow.branches.domain.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository {
    Optional<Branch> create(UUID tenantId, UUID userId, ValidatedBranchCreate request);
    List<Branch> findActiveByTenant(UUID tenantId);
    Optional<Branch> findActiveByTenantAndId(UUID tenantId, UUID branchId);
    Optional<Branch> updateActive(UUID tenantId, UUID branchId, UUID userId, ValidatedBranchUpdate request);
    boolean archiveActive(UUID tenantId, UUID branchId, UUID userId);
    boolean existsByTenantAndId(UUID tenantId, UUID branchId);
}
