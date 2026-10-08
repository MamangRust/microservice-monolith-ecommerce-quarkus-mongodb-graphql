package com.sanedge.banner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.banner.domain.requests.CreateBannerRequest;
import com.sanedge.banner.domain.requests.UpdateBannerRequest;
import com.sanedge.banner.domain.response.BannerResponse;
import com.sanedge.banner.domain.response.BannerResponseDeleteAt;
import com.sanedge.banner.entity.Banner;
import com.sanedge.banner.repository.BannerCommandRepository;
import com.sanedge.banner.repository.BannerQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceAlreadyExistsException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class BannerCommandServiceImplTest {

        private static final String ID_1 = "507f1f77bcf86cd799439011";
        private static final String ID_999 = "507f1f77bcf86cd799439099";

        @Mock
        private BannerQueryRepository bannerQueryRepository;

        @Mock
        private BannerCommandRepository bannerCommandRepository;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        private BannerCommandServiceImpl bannerCommandService;

        @BeforeEach
        void setUp() {
                lenient().doAnswer(invokeSupplier())
                                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
                lenient().doAnswer(invokeSupplier())
                                .when(tracingMetrics)
                                                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

                bannerCommandService = new BannerCommandServiceImpl(
                                bannerQueryRepository,
                                bannerCommandRepository,
                                redisService,
                                tracingMetrics);
        }

        @Test
        void createBanner_Success() {
                CreateBannerRequest request = CreateBannerRequest.builder()
                                .name("Summer Sale Banner")
                                .startDate("2026-06-01")
                                .endDate("2026-08-31")
                                .startTime("09:00")
                                .endTime("18:00")
                                .isActive(true)
                                .build();

                Banner savedBanner = createTestBanner(ID_1, "Summer Sale Banner");
                savedBanner.setStartDate(LocalDate.parse("2026-06-01"));
                savedBanner.setEndDate(LocalDate.parse("2026-08-31"));
                savedBanner.setStartTime(LocalTime.parse("09:00"));
                savedBanner.setEndTime(LocalTime.parse("18:00"));
                savedBanner.setIsActive(true);

                lenient().when(bannerQueryRepository.findByName("Summer Sale Banner"))
                                .thenReturn(Uni.createFrom().nullItem());
                lenient().when(bannerCommandRepository.persist(any(Banner.class)))
                                .thenReturn(Uni.createFrom().item(savedBanner));

                ApiResponse<BannerResponse> result = bannerCommandService.createBanner(request)
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Banner created successfully!");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getName()).isEqualTo("Summer Sale Banner");
                verify(bannerQueryRepository).findByName("Summer Sale Banner");
                verify(bannerCommandRepository).persist(any(Banner.class));
        }

        @Test
        void createBanner_AlreadyExists_ThrowsException() {
                CreateBannerRequest request = CreateBannerRequest.builder()
                                .name("Existing Banner")
                                .startDate("2026-06-01")
                                .endDate("2026-08-31")
                                .startTime("09:00")
                                .endTime("18:00")
                                .isActive(true)
                                .build();

                Banner existingBanner = createTestBanner(ID_1, "Existing Banner");
                lenient().when(bannerQueryRepository.findByName("Existing Banner"))
                                .thenReturn(Uni.createFrom().item(existingBanner));

                assertThatThrownBy(() -> bannerCommandService.createBanner(request).await().indefinitely())
                                .isInstanceOf(ResourceAlreadyExistsException.class)
                                .hasMessageContaining("Banner with name 'Existing Banner' already exists");
        }

        @Test
        void updateBanner_Success() {
                String bannerId = ID_1;
                UpdateBannerRequest request = UpdateBannerRequest.builder()
                                .id(bannerId)
                                .name("Updated Banner")
                                .startDate("2026-07-01")
                                .endDate("2026-09-30")
                                .startTime("10:00")
                                .endTime("20:00")
                                .isActive(false)
                                .build();

                Banner existingBanner = createTestBanner(bannerId, "Old Banner");
                lenient().when(bannerCommandRepository.findById(bannerId))
                                .thenReturn(Uni.createFrom().item(existingBanner));
                lenient().when(bannerCommandRepository.update(any(Banner.class)))
                                .thenReturn(Uni.createFrom().item(existingBanner));
                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<BannerResponse> result = bannerCommandService.updateBanner(request)
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Banner updated successfully!");
                verify(redisService).deleteReactive("banner:" + bannerId);
        }

        @Test
        void updateBanner_NotFound_ThrowsException() {
                String bannerId = ID_999;
                UpdateBannerRequest request = UpdateBannerRequest.builder()
                                .id(bannerId)
                                .name("Updated Banner")
                                .startDate("2026-07-01")
                                .endDate("2026-09-30")
                                .startTime("10:00")
                                .endTime("20:00")
                                .isActive(false)
                                .build();

                lenient().when(bannerCommandRepository.findById(bannerId))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> bannerCommandService.updateBanner(request).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Banner not found");
        }

        @Test
        void updateBanner_NullId_ThrowsException() {
                UpdateBannerRequest request = UpdateBannerRequest.builder()
                                .id(null)
                                .name("Updated Banner")
                                .startDate("2026-07-01")
                                .endDate("2026-09-30")
                                .startTime("10:00")
                                .endTime("20:00")
                                .isActive(false)
                                .build();

                assertThatThrownBy(() -> bannerCommandService.updateBanner(request).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("banner_id is required");
        }

        @Test
        void trashedBanner_Success() {
                String bannerId = ID_1;
                Banner trashedBanner = createTestBanner(bannerId, "Trashed Banner");
                trashedBanner.setDeletedAt(Instant.now());

                lenient().when(bannerCommandRepository.trash(bannerId))
                                .thenReturn(Uni.createFrom().item(trashedBanner));
                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<BannerResponseDeleteAt> result = bannerCommandService.trashedBanner(bannerId)
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Banner trashed successfully!");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getDeletedAt()).isNotNull();
                verify(redisService).deleteReactive("banner:" + bannerId);
        }

        @Test
        void trashedBanner_NotFound_ThrowsException() {
                String bannerId = ID_999;

                lenient().when(bannerCommandRepository.trash(bannerId))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> bannerCommandService.trashedBanner(bannerId).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Trashed banner not found with id: " + bannerId);
        }

        @Test
        void restoreBanner_Success() {
                String bannerId = ID_1;
                Banner restoredBanner = createTestBanner(bannerId, "Restored Banner");

                lenient().when(bannerCommandRepository.restore(bannerId))
                                .thenReturn(Uni.createFrom().item(restoredBanner));
                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<BannerResponseDeleteAt> result = bannerCommandService.restoreBanner(bannerId)
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Banner restored successfully!");
                verify(redisService).deleteReactive("banner:" + bannerId);
        }

        @Test
        void restoreBanner_NotFound_ThrowsException() {
                String bannerId = ID_999;

                lenient().when(bannerCommandRepository.restore(bannerId))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> bannerCommandService.restoreBanner(bannerId).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Restore banner not found with id: " + bannerId);
        }

        @Test
        void deleteBannerPermanent_Success() {
                String bannerId = ID_1;
                Banner banner = createTestBanner(bannerId, "To Delete");

                lenient().when(bannerCommandRepository.deletePermanent(bannerId))
                                .thenReturn(Uni.createFrom().item(banner));
                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<Void> result = bannerCommandService.deleteBannerPermanent(bannerId)
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Banner deleted permanently!");
                verify(redisService).deleteReactive("banner:" + bannerId);
        }

        @Test
        void deleteBannerPermanent_NotFound_ThrowsException() {
                String bannerId = ID_999;

                lenient().when(bannerCommandRepository.deletePermanent(bannerId))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> bannerCommandService.deleteBannerPermanent(bannerId).await().indefinitely())
                                .isInstanceOf(InvalidRequestException.class)
                                .hasMessageContaining("Banner not found or must be trashed before permanent deletion");
        }

        @Test
        void restoreAllBanner_Success() {
                lenient().when(bannerCommandRepository.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = bannerCommandService.restoreAllBanner()
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("All banners restored successfully!");
        }

        @Test
        void restoreAllBanner_NoTrashedBanners_ThrowsException() {
                lenient().when(bannerCommandRepository.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(false));

                assertThatThrownBy(() -> bannerCommandService.restoreAllBanner().await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("No trashed banners found");
        }

        @Test
        void deleteAllBannerPermanent_Success() {
                lenient().when(bannerCommandRepository.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = bannerCommandService.deleteAllBannerPermanent()
                                .await().indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("All banners permanently deleted!");
        }

        @Test
        void deleteAllBannerPermanent_NoTrashedBanners_ThrowsException() {
                lenient().when(bannerCommandRepository.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(false));

                assertThatThrownBy(() -> bannerCommandService.deleteAllBannerPermanent().await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("No trashed banners found");
        }

        private Banner createTestBanner(String id, String name) {
                Banner banner = new Banner();
                banner.id = new ObjectId(id);
                banner.setName(name);
                banner.setStartDate(LocalDate.now());
                banner.setEndDate(LocalDate.now().plusMonths(1));
                banner.setStartTime(LocalTime.of(9, 0));
                banner.setEndTime(LocalTime.of(18, 0));
                banner.setIsActive(true);
                banner.setCreatedAt(Instant.now());
                banner.setUpdatedAt(Instant.now());
                return banner;
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
