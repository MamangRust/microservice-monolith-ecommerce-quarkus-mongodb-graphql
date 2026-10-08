package com.sanedge.slider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.slider.domain.requests.CreateSliderRequest;
import com.sanedge.slider.domain.requests.UpdateSliderRequest;
import com.sanedge.slider.domain.response.SliderResponse;
import com.sanedge.slider.domain.response.SliderResponseDeleteAt;
import com.sanedge.slider.entity.Slider;
import com.sanedge.slider.repository.SliderCommandRepository;
import com.sanedge.slider.repository.SliderQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class SliderCommandServiceImplTest {

        private static final String ID_1 = "507f1f77bcf86cd799439011";
        private static final String ID_3 = "507f1f77bcf86cd799439013";
        private static final String ID_5 = "507f1f77bcf86cd799439015";
        private static final String ID_999 = "507f1f77bcf86cd799439099";

        @Mock
        private SliderCommandRepository sliderCommandRepository;

        @Mock
        private SliderQueryRepository sliderQueryRepository;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        private SliderCommandServiceImpl sliderService;
        private Validator validator;

        @BeforeEach
        void setUp() throws Exception {

                ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
                validator = factory.getValidator();
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics)
                        .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                sliderService = new SliderCommandServiceImpl(
                                sliderCommandRepository,
                                sliderQueryRepository,
                                validator,
                                redisService,
                                tracingMetrics);
        }

        @Test
        @DisplayName("createSlider - should successfully create a slider")
        void createSlider_Success() {

                CreateSliderRequest request = new CreateSliderRequest();
                request.setNama("Summer Sale Slider");
                request.setFilePath("/images/summer-sale.jpg");

                Slider savedSlider = createTestSlider(ID_1, "Summer Sale Slider", "/images/summer-sale.jpg");

                when(sliderCommandRepository.persist(any(Slider.class)))
                                .thenReturn(Uni.createFrom().item(savedSlider));

                ApiResponse<SliderResponse> result = sliderService.createSlider(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Slider created successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getName()).isEqualTo("Summer Sale Slider");
                assertThat(result.data().getImage()).isEqualTo("/images/summer-sale.jpg");

                verify(sliderCommandRepository).persist(any(Slider.class));
        }

        @Test
        @DisplayName("createSlider - should set all fields correctly on entity")
        void createSlider_AllFieldsSetCorrectly() {

                CreateSliderRequest request = new CreateSliderRequest();
                request.setNama("Promo Slider");
                request.setFilePath("/images/promo.jpg");

                when(sliderCommandRepository.persist(any(Slider.class)))
                                .thenAnswer(invocation -> {
                                        Slider slider = invocation.getArgument(0);

                                        assertThat(slider.getName()).isEqualTo("Promo Slider");
                                        assertThat(slider.getImage()).isEqualTo("/images/promo.jpg");

                                        slider.id = new ObjectId(ID_5);
                                        return Uni.createFrom().item(slider);
                                });

                ApiResponse<SliderResponse> result = sliderService.createSlider(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                verify(sliderCommandRepository).persist(any(Slider.class));
        }

        @Test
        @DisplayName("updateSlider - should successfully update a slider")
        void updateSlider_Success() {

                Slider existingSlider = createTestSlider(ID_1, "Old Slider Name", "/images/old.jpg");

                Slider updatedSlider = createTestSlider(ID_1, "New Slider Name", "/images/new.jpg");

                UpdateSliderRequest request = new UpdateSliderRequest();
                request.setId(ID_1);
                request.setNama("New Slider Name");
                request.setFilePath("/images/new.jpg");

                when(sliderCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(existingSlider));
                when(sliderCommandRepository.update(any(Slider.class)))
                                .thenReturn(Uni.createFrom().item(updatedSlider));

                ApiResponse<SliderResponse> result = sliderService.updateSlider(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Slider updated successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getName()).isEqualTo("New Slider Name");

                verify(sliderCommandRepository).findById(ID_1);
                verify(sliderCommandRepository).update(any(Slider.class));
        }

        @Test
        @DisplayName("updateSlider - should update only name when filePath is null")
        void updateSlider_PartialUpdate() {

                Slider existingSlider = createTestSlider(ID_1, "Old Slider Name", "/images/old.jpg");

                UpdateSliderRequest request = new UpdateSliderRequest();
                request.setId(ID_1);
                request.setNama("Updated Slider Name");
                request.setFilePath(null);

                when(sliderCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(existingSlider));
                when(sliderCommandRepository.update(any(Slider.class)))
                                .thenAnswer(invocation -> {
                                        Slider slider = invocation.getArgument(0);

                                        assertThat(slider.getImage()).isEqualTo("/images/old.jpg");
                                        assertThat(slider.getName()).isEqualTo("Updated Slider Name");
                                        return Uni.createFrom().item(slider);
                                });

                ApiResponse<SliderResponse> result = sliderService.updateSlider(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
        }

        @Test
        @DisplayName("updateSlider - should fail when slider not found")
        void updateSlider_NotFound() {

                UpdateSliderRequest request = new UpdateSliderRequest();
                request.setId(ID_999);
                request.setNama("Updated Slider");
                request.setFilePath("/images/updated.jpg");

                when(sliderCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> sliderService.updateSlider(request).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Slider not found");

                verify(sliderCommandRepository).findById(ID_999);
        }

        @Test
        @DisplayName("trashedSlider - should successfully trash a slider")
        void trashedSlider_Success() {

                Slider trashedSlider = createTestSlider(ID_1, "Trashed Slider", "/images/slider.jpg");
                trashedSlider.setDeletedAt(Instant.now());

                when(sliderCommandRepository.trashed(anyString()))
                                .thenReturn(Uni.createFrom().item(trashedSlider));

                ApiResponse<SliderResponseDeleteAt> result = sliderService.trashedSlider(ID_1)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Slider trashed successfully!");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getId()).isEqualTo(ID_1);
                assertThat(result.data().getDeletedAt()).isNotNull();

                verify(sliderCommandRepository).trashed(ID_1);
        }

        @Test
        @DisplayName("trashedSlider - should fail when slider not found or already trashed")
        void trashedSlider_NotFound() {

                when(sliderCommandRepository.trashed(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> sliderService.trashedSlider(ID_999).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Slider not found or already trashed");

                verify(sliderCommandRepository).trashed(ID_999);
        }

        @Test
        @DisplayName("restoreSlider - should successfully restore a trashed slider")
        void restoreSlider_Success() {

                Slider restoredSlider = createTestSlider(ID_1, "Restored Slider", "/images/slider.jpg");

                when(sliderCommandRepository.restore(anyString()))
                                .thenReturn(Uni.createFrom().item(restoredSlider));

                ApiResponse<SliderResponseDeleteAt> result = sliderService.restoreSlider(ID_1)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Slider restored successfully!");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getId()).isEqualTo(ID_1);

                verify(sliderCommandRepository).restore(ID_1);
        }

        @Test
        @DisplayName("restoreSlider - should fail when slider not found or not trashed")
        void restoreSlider_NotFound() {

                when(sliderCommandRepository.restore(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> sliderService.restoreSlider(ID_999).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Slider not found or not trashed");

                verify(sliderCommandRepository).restore(ID_999);
        }

        @Test
        @DisplayName("deleteSliderPermanent - should successfully delete a trashed slider permanently")
        void deleteSliderPermanent_Success() {

                Slider trashedSlider = createTestSlider(ID_1, "To Delete", "/images/slider.jpg");
                trashedSlider.setDeletedAt(Instant.now().minusSeconds(86400));

                when(sliderCommandRepository.deletePermanent(anyString()))
                                .thenReturn(Uni.createFrom().item(trashedSlider));

                ApiResponse<Void> result = sliderService.deleteSliderPermanent(ID_1)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Slider permanently deleted!");

                verify(sliderCommandRepository).deletePermanent(ID_1);
        }

        @Test
        @DisplayName("deleteSliderPermanent - should fail when slider not found or not trashed")
        void deleteSliderPermanent_NotTrashed() {

                when(sliderCommandRepository.deletePermanent(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> sliderService.deleteSliderPermanent(ID_999).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Slider not found or not trashed");

                verify(sliderCommandRepository).deletePermanent(ID_999);
        }

        @Test
        @DisplayName("restoreAllSliders - should successfully restore all trashed sliders")
        void restoreAllSliders_Success() {

                when(sliderCommandRepository.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = sliderService.restoreAllSliders()
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("All sliders restored successfully!");

                verify(sliderCommandRepository).restoreAllDeleted();
        }

        @Test
        @DisplayName("restoreAllSliders - should fail when no trashed sliders found")
        void restoreAllSliders_NoSlidersFound() {

                when(sliderCommandRepository.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(false));

                assertThatThrownBy(() -> sliderService.restoreAllSliders().await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("No trashed sliders found");

                verify(sliderCommandRepository).restoreAllDeleted();
        }

        @Test
        @DisplayName("deleteAllSlidersPermanent - should successfully delete all trashed sliders permanently")
        void deleteAllSlidersPermanent_Success() {

                when(sliderCommandRepository.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = sliderService.deleteAllSlidersPermanent()
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("All sliders permanently deleted!");

                verify(sliderCommandRepository).deleteAllDeleted();
        }

        @Test
        @DisplayName("deleteAllSlidersPermanent - should fail when no trashed sliders found")
        void deleteAllSlidersPermanent_NoSlidersFound() {

                when(sliderCommandRepository.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(false));

                assertThatThrownBy(() -> sliderService.deleteAllSlidersPermanent().await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("No trashed sliders found");

                verify(sliderCommandRepository).deleteAllDeleted();
        }

        @Test
        @DisplayName("createSlider - should invalidate cache after successful creation")
        void createSlider_InvalidatesCache() {

                CreateSliderRequest request = new CreateSliderRequest();
                request.setNama("Cache Test Slider");
                request.setFilePath("/images/cache-test.jpg");

                Slider savedSlider = createTestSlider(ID_1, "Cache Test Slider", "/images/cache-test.jpg");

                when(sliderCommandRepository.persist(any(Slider.class)))
                                .thenReturn(Uni.createFrom().item(savedSlider));

                sliderService.createSlider(request).await().indefinitely();

                verify(redisService).deleteReactive("slider:all:*");
                verify(redisService).deleteReactive("slider:active:*");
                verify(redisService).deleteReactive("slider:trashed:*");
        }

        @Test
        @DisplayName("trashedSlider - should invalidate cache after successful trash")
        void trashedSlider_InvalidatesCache() {

                Slider trashedSlider = createTestSlider(ID_5, "Test Slider", "/images/test.jpg");
                trashedSlider.setDeletedAt(Instant.now());

                when(sliderCommandRepository.trashed(anyString()))
                                .thenReturn(Uni.createFrom().item(trashedSlider));

                sliderService.trashedSlider(ID_5).await().indefinitely();

                verify(redisService).deleteReactive("slider:all:*");
                verify(redisService).deleteReactive("slider:active:*");
                verify(redisService).deleteReactive("slider:trashed:*");
        }

        @Test
        @DisplayName("restoreSlider - should invalidate cache after successful restore")
        void restoreSlider_InvalidatesCache() {

                Slider restoredSlider = createTestSlider(ID_3, "Test Slider", "/images/test.jpg");

                when(sliderCommandRepository.restore(anyString()))
                                .thenReturn(Uni.createFrom().item(restoredSlider));

                sliderService.restoreSlider(ID_3).await().indefinitely();

                verify(redisService).deleteReactive("slider:all:*");
                verify(redisService).deleteReactive("slider:active:*");
                verify(redisService).deleteReactive("slider:trashed:*");
        }

        private Slider createTestSlider(String id, String name, String image) {
                Slider slider = new Slider();
                slider.setName(name);
                slider.setImage(image);
                Instant now = Instant.now();
                slider.setCreatedAt(now);
                slider.setUpdatedAt(now);
                slider.id = new ObjectId(id);
                return slider;
        }

    /**
     * Finds the Supplier argument in the invocation regardless of whether it was
     * passed positionally in the 3-arg overload (arg index 2) or 4-arg overload
     * (arg index 3), then invokes it and returns the resulting Uni. This lets
     * a single Answer<?> body serve both traceAndMeasure overloads.
     */
    private Answer<Uni<?>> invokeSupplier() {
        return invocation -> {
            Supplier<?> supplier = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier<?>) {
                    supplier = (Supplier<?>) arg;
                    break;
                }
            }
            return supplier != null ? (Uni<?>) supplier.get() : null;
        };
    }
}
