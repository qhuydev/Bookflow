package com.bookflow.branches.application;

import com.bookflow.branches.api.CreateBranchRequest;
import com.bookflow.branches.api.UpdateBranchRequest;
import com.bookflow.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BranchRequestValidatorTest {
    private final BranchRequestValidator validator = new BranchRequestValidator();
    @Test void normalizesCodeAndUsesBusinessTimeZoneWhenOmitted() {
        CreateBranchRequest r=new CreateBranchRequest(); r.setCode(" q1-main "); r.setName("  Quận 1 "); r.setAddressLine1(" 1 Main "); r.setCity(" HCM "); r.setCountryCode(" vn ");
        var value=validator.validateCreate(r,"Asia/Ho_Chi_Minh");
        assertThat(value.code()).isEqualTo("Q1-MAIN"); assertThat(value.timeZone()).isEqualTo("Asia/Ho_Chi_Minh"); assertThat(value.countryCode()).isEqualTo("VN");
    }
    @Test void rejectsEmptyUpdateAndInvalidValues() {
        assertThatThrownBy(() -> validator.validateUpdate(new UpdateBranchRequest())).isInstanceOf(RequestValidationException.class);
        CreateBranchRequest r=new CreateBranchRequest(); r.setCode("bad code"); r.setName(" "); r.setAddressLine1("x"); r.setCity("x"); r.setCountryCode("ZZZ"); r.setEmail("not-email");
        assertThatThrownBy(() -> validator.validateCreate(r,"UTC")).isInstanceOf(RequestValidationException.class);
    }
}
