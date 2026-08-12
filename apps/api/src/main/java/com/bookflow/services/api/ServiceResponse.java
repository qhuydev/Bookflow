package com.bookflow.services.api;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;
public record ServiceResponse(UUID id,UUID businessId,String name,String description,BigDecimal price,String currency,Integer durationMinutes,Integer bufferBeforeMinutes,Integer bufferAfterMinutes,String status,List<UUID> branchIds,List<UUID> employeeIds,Instant createdAt,Instant updatedAt) {}
