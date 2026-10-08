package com.sanedge.banner.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.banner.domain.requests.FindAllBannerRequest;
import com.sanedge.banner.domain.response.BannerResponse;
import com.sanedge.banner.domain.response.BannerResponseDeleteAt;
import com.sanedge.banner.entity.Banner;
import com.sanedge.banner.service.BannerQueryService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PaginationMeta;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class BannerQueryGrpcHandlerTest {

        private static final String ID_1 = "507f1f77bcf86cd799439011";
        private static final String ID_999 = "507f1f77bcf86cd799439099";

        @Mock
        private BannerQueryService bannerQueryService;

        private BannerQueryGrpcHandler bannerQueryGrpcHandler;

        @BeforeEach
        void setUp() throws Exception {
                bannerQueryGrpcHandler = new BannerQueryGrpcHandler();
                Field serviceField = BannerQueryGrpcHandler.class.getDeclaredField("bannerQueryService");
                serviceField.setAccessible(true);
                serviceField.set(bannerQueryGrpcHandler, bannerQueryService);
        }

        @Test
        void findAll_Success() {
                pb.banner.BannerQuery.FindAllBannerRequest grpcRequest = pb.banner.BannerQuery.FindAllBannerRequest
                                .newBuilder()
                                .setPage(1)
                                .setPageSize(10)
                                .setSearch("")
                                .build();

                Banner banner = createTestBanner(ID_1, "Test Banner");
                BannerResponse bannerResponse = BannerResponse.from(banner);
                PaginationMeta paginationMeta = new PaginationMeta(1, 10, 1, 1);
                ApiResponsePagination<List<BannerResponse>> serviceResponse = new ApiResponsePagination<>(
                                "success",
                                "Banners retrieved successfully",
                                List.of(bannerResponse),
                                paginationMeta);

                lenient().when(bannerQueryService.findAllPaginated(any(FindAllBannerRequest.class)))
                                .thenReturn(Uni.createFrom().item(serviceResponse));

                pb.banner.BannerCommon.ApiResponsePaginationBanner response = bannerQueryGrpcHandler
                                .findAll(grpcRequest).await().indefinitely();

                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getMessage()).isEqualTo("Banners retrieved successfully");
                assertThat(response.getDataList()).hasSize(1);
                assertThat(response.getData(0).getName()).isEqualTo("Test Banner");
        }

        @Test
        void findById_Success() {
                pb.banner.BannerCommon.FindByIdBannerRequest grpcRequest = pb.banner.BannerCommon.FindByIdBannerRequest
                                .newBuilder()
                                .setId(ID_1)
                                .build();

                Banner banner = createTestBanner(ID_1, "Test Banner");
                ApiResponse<BannerResponse> serviceResponse = ApiResponse.success("Banner found",
                                BannerResponse.from(banner));

                lenient().when(bannerQueryService.findById(ID_1))
                                .thenReturn(Uni.createFrom().item(serviceResponse));

                pb.banner.BannerCommon.ApiResponseBanner response = bannerQueryGrpcHandler.findById(grpcRequest).await()
                                .indefinitely();

                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getMessage()).isEqualTo("Banner found");
                assertThat(response.getData().getName()).isEqualTo("Test Banner");
        }

        @Test
        void findById_NotFound_ReturnsStatusNotFound() {
                pb.banner.BannerCommon.FindByIdBannerRequest grpcRequest = pb.banner.BannerCommon.FindByIdBannerRequest
                                .newBuilder()
                                .setId(ID_999)
                                .build();

                lenient().when(bannerQueryService.findById(ID_999))
                                .thenReturn(Uni.createFrom()
                                                .failure(new NotFoundException("Banner not found with id: " + ID_999)));

                assertThatThrownBy(() -> bannerQueryGrpcHandler.findById(grpcRequest).await().indefinitely())
                                .isInstanceOf(StatusRuntimeException.class)
                                .satisfies(e -> {
                                        StatusRuntimeException sre = (StatusRuntimeException) e;
                                        assertThat(sre.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
                                        assertThat(sre.getStatus().getDescription())
                                                        .contains("Banner not found with id: " + ID_999);
                                });
        }

        @Test
        void findByActive_Success() {
                pb.banner.BannerQuery.FindAllBannerRequest grpcRequest = pb.banner.BannerQuery.FindAllBannerRequest
                                .newBuilder()
                                .setPage(1)
                                .setPageSize(10)
                                .setSearch("")
                                .build();

                Banner banner = createTestBanner(ID_1, "Active Banner");
                banner.setIsActive(true);
                BannerResponseDeleteAt bannerResponse = BannerResponseDeleteAt.from(banner);
                PaginationMeta paginationMeta = new PaginationMeta(1, 10, 1, 1);
                ApiResponsePagination<List<BannerResponseDeleteAt>> serviceResponse = new ApiResponsePagination<>(
                                "success",
                                "Active banners retrieved successfully",
                                List.of(bannerResponse),
                                paginationMeta);

                lenient().when(bannerQueryService.findActivePaginated(any(FindAllBannerRequest.class)))
                                .thenReturn(Uni.createFrom().item(serviceResponse));

                pb.banner.BannerCommon.ApiResponsePaginationBannerDeleteAt response = bannerQueryGrpcHandler
                                .findByActive(grpcRequest).await().indefinitely();

                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getMessage()).isEqualTo("Active banners retrieved successfully");
                assertThat(response.getDataList()).hasSize(1);
                assertThat(response.getData(0).getName()).isEqualTo("Active Banner");
        }

        @Test
        void findByTrashed_Success() {
                pb.banner.BannerQuery.FindAllBannerRequest grpcRequest = pb.banner.BannerQuery.FindAllBannerRequest
                                .newBuilder()
                                .setPage(1)
                                .setPageSize(10)
                                .setSearch("")
                                .build();

                Banner banner = createTestBanner(ID_1, "Trashed Banner");
                banner.setDeletedAt(Instant.now());
                BannerResponseDeleteAt bannerResponse = BannerResponseDeleteAt.from(banner);
                PaginationMeta paginationMeta = new PaginationMeta(1, 10, 1, 1);
                ApiResponsePagination<List<BannerResponseDeleteAt>> serviceResponse = new ApiResponsePagination<>(
                                "success",
                                "Trashed banners retrieved successfully",
                                List.of(bannerResponse),
                                paginationMeta);

                lenient().when(bannerQueryService.findTrashedPaginated(any(FindAllBannerRequest.class)))
                                .thenReturn(Uni.createFrom().item(serviceResponse));

                pb.banner.BannerCommon.ApiResponsePaginationBannerDeleteAt response = bannerQueryGrpcHandler
                                .findByTrashed(grpcRequest).await().indefinitely();

                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getMessage()).isEqualTo("Trashed banners retrieved successfully");
                assertThat(response.getDataList()).hasSize(1);
                assertThat(response.getData(0).getName()).isEqualTo("Trashed Banner");
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
}
