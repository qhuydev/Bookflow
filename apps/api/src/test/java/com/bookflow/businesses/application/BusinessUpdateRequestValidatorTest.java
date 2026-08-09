package com.bookflow.businesses.application;

import com.bookflow.businesses.api.UpdateBusinessRequest;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.CancellationPolicy;
import com.bookflow.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessUpdateRequestValidatorTest {

    private final BusinessUpdateRequestValidator validator = new BusinessUpdateRequestValidator();

    @Test
    void normalizesOnlyTheFieldsIncludedInPartialUpdate() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("  BookFlow Salon  ");
        request.setSlug("  BookFlow-Salon  ");
        request.setType("spa");
        request.setTimeZone("Asia/Ho_Chi_Minh");
        request.setCurrencyCode(" vnd ");
        request.setCancellationPolicy("moderate");
        request.setMaxBookingAdvanceDays(30);

        BusinessUpdateRequestValidator.ValidatedBusinessUpdate update = validator.validate(request);

        assertThat(update.name()).isEqualTo("BookFlow Salon");
        assertThat(update.slug()).isEqualTo("bookflow-salon");
        assertThat(update.type()).isEqualTo(BusinessType.SPA);
        assertThat(update.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(update.currencyCode()).isEqualTo("VND");
        assertThat(update.cancellationPolicy()).isEqualTo(CancellationPolicy.MODERATE);
        assertThat(update.maxBookingAdvanceDays()).isEqualTo(30);
    }

    @Test
    void rejectsEmptyOrInvalidPartialUpdates() {
        UpdateBusinessRequest empty = new UpdateBusinessRequest();
        assertThatThrownBy(() -> validator.validate(empty)).isInstanceOf(RequestValidationException.class);

        UpdateBusinessRequest invalid = new UpdateBusinessRequest();
        invalid.setSlug("not a slug");
        invalid.setTimeZone("UTC+7");
        invalid.setCurrencyCode("invalid");
        invalid.setMaxBookingAdvanceDays(366);
        assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(RequestValidationException.class);
    }
}
