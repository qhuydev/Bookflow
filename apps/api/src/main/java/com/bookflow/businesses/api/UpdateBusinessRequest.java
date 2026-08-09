package com.bookflow.businesses.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashSet;
import java.util.Set;

/** Nullable fields represent omitted fields in a partial update. */
public final class UpdateBusinessRequest {
    private String name;
    private String slug;
    private String type;
    private String timeZone;
    private String currencyCode;
    private String cancellationPolicy;
    private Integer maxBookingAdvanceDays;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getCancellationPolicy() { return cancellationPolicy; }
    public void setCancellationPolicy(String cancellationPolicy) { this.cancellationPolicy = cancellationPolicy; }
    public Integer getMaxBookingAdvanceDays() { return maxBookingAdvanceDays; }
    public void setMaxBookingAdvanceDays(Integer maxBookingAdvanceDays) { this.maxBookingAdvanceDays = maxBookingAdvanceDays; }
    public Set<String> unknownFields() { return Set.copyOf(unknownFields); }
    public boolean hasKnownField() {
        return name != null || slug != null || type != null || timeZone != null || currencyCode != null
                || cancellationPolicy != null || maxBookingAdvanceDays != null;
    }

    @JsonAnySetter
    void captureUnknownField(String field, Object ignoredValue) { unknownFields.add(field); }
}
