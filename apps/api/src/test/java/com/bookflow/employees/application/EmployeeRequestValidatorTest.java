package com.bookflow.employees.application;

import com.bookflow.employees.api.CreateEmployeeRequest;
import com.bookflow.employees.api.UpdateEmployeeRequest;
import com.bookflow.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeRequestValidatorTest {
    private final EmployeeRequestValidator validator = new EmployeeRequestValidator();

    @Test
    void normalizesCodeToTrimmedUppercase() {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setCode("  nv-01 ");
        request.setFullName("  Nguyen Van A ");

        EmployeeRequestValidator.Values values = validator.create(request);

        assertThat(values.code()).isEqualTo("NV-01");
        assertThat(values.fullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void rejectsEmptyPartialUpdate() {
        assertThatThrownBy(() -> validator.update(new UpdateEmployeeRequest()))
                .isInstanceOf(RequestValidationException.class);
    }
}
