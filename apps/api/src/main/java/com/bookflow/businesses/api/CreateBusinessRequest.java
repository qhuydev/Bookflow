package com.bookflow.businesses.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps unknown client-controlled fields observable so the validator can reject them. */
public final class CreateBusinessRequest {
    private final String name;
    private final String slug;
    private final String type;
    private final String timeZone;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonCreator
    public CreateBusinessRequest(
            @JsonProperty("name") String name,
            @JsonProperty("slug") String slug,
            @JsonProperty("type") String type,
            @JsonProperty("timeZone") String timeZone
    ) {
        this.name = name;
        this.slug = slug;
        this.type = type;
        this.timeZone = timeZone;
    }

    public String name() { return name; }
    public String slug() { return slug; }
    public String type() { return type; }
    public String timeZone() { return timeZone; }
    public Set<String> unknownFields() { return Set.copyOf(unknownFields); }

    @JsonAnySetter
    void captureUnknownField(String field, Object ignoredValue) {
        unknownFields.add(field);
    }
}
