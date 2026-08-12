package com.bookflow.publiccatalog.api;

import com.bookflow.publiccatalog.api.PublicCatalogController.PublicBusiness;
import com.bookflow.publiccatalog.api.PublicCatalogController.PublicEmployee;
import com.bookflow.publiccatalog.application.PublicCatalogService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicCatalogControllerTest {
    @Test
    void delegatesPublicProfileToServiceAndResponseHasNoInternalFields() {
        PublicCatalogService service = mock(PublicCatalogService.class);
        PublicBusiness expected = new PublicBusiness("demo-salon", "Demo Salon", "Asia/Ho_Chi_Minh", "VND");
        when(service.business("DEMO-SALON")).thenReturn(expected);

        PublicCatalogController controller = new PublicCatalogController(service);

        assertThat(controller.profile("DEMO-SALON")).isSameAs(expected);
        verify(service).business("DEMO-SALON");
        assertThat(componentNames(PublicBusiness.class)).containsExactly("slug", "name", "timeZone", "currency");
        assertThat(componentNames(PublicEmployee.class)).containsExactly("id", "fullName", "bio");
    }

    @Test
    void forwardsBranchAndServiceFiltersWithoutAuthentication() {
        PublicCatalogService service = mock(PublicCatalogService.class);
        UUID branchId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        when(service.employees("demo", branchId, serviceId)).thenReturn(List.of());

        assertThat(new PublicCatalogController(service).employees("demo", branchId, serviceId)).isEmpty();
        verify(service).employees("demo", branchId, serviceId);
    }

    private List<String> componentNames(Class<? extends Record> type) {
        return List.of(type.getRecordComponents()).stream().map(RecordComponent::getName).toList();
    }
}
