package com.bookflow.services.api;
import java.math.BigDecimal;
public record ServiceRequest(String name,String description,BigDecimal price,String currency,Integer durationMinutes,Integer bufferBeforeMinutes,Integer bufferAfterMinutes) {}
