package com.bookflow.branches.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CreateBranchRequest {
    private String code, name, addressLine1, addressLine2, ward, district, city, postalCode, countryCode, phone, email, timeZone;
    private final Set<String> unknownFields = new LinkedHashSet<>();
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getName() { return name; } public void setName(String value) { name = value; }
    public String getAddressLine1() { return addressLine1; } public void setAddressLine1(String value) { addressLine1 = value; }
    public String getAddressLine2() { return addressLine2; } public void setAddressLine2(String value) { addressLine2 = value; }
    public String getWard() { return ward; } public void setWard(String value) { ward = value; }
    public String getDistrict() { return district; } public void setDistrict(String value) { district = value; }
    public String getCity() { return city; } public void setCity(String value) { city = value; }
    public String getPostalCode() { return postalCode; } public void setPostalCode(String value) { postalCode = value; }
    public String getCountryCode() { return countryCode; } public void setCountryCode(String value) { countryCode = value; }
    public String getPhone() { return phone; } public void setPhone(String value) { phone = value; }
    public String getEmail() { return email; } public void setEmail(String value) { email = value; }
    public String getTimeZone() { return timeZone; } public void setTimeZone(String value) { timeZone = value; }
    public Set<String> unknownFields() { return Set.copyOf(unknownFields); }
    @JsonAnySetter void captureUnknownField(String field, Object ignored) { unknownFields.add(field); }
}
