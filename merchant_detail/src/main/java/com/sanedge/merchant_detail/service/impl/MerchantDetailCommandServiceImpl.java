package com.sanedge.merchant_detail.service.impl;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.merchant_detail.domain.response.MerchantDetailResponse;
import com.sanedge.merchant_detail.domain.response.MerchantDetailResponseDeleteAt;
import com.sanedge.merchant_detail.entity.MerchantDetail;
import com.sanedge.merchant_detail.repository.MerchantDetailCommandRepository;
import com.sanedge.merchant_detail.repository.MerchantDetailQueryRepository;
import com.sanedge.merchant_detail.service.MerchantDetailCommandService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pb.merchant_detail.MerchantDetailCommand.CreateMerchantDetailRequest;
import pb.merchant_detail.MerchantDetailCommand.UpdateMerchantDetailRequest;

@ApplicationScoped
public class MerchantDetailCommandServiceImpl implements MerchantDetailCommandService {
        private static final Logger logger = LoggerFactory.getLogger(MerchantDetailCommandServiceImpl.class);

        private final MerchantDetailQueryRepository merchantDetailQueryRepository;
        private final MerchantDetailCommandRepository merchantDetailCommandRepository;
        private final RedisService redisService;
        private final TracingMetrics tracingMetrics;
        private final MerchantPort merchantPort;

        @Inject
        public MerchantDetailCommandServiceImpl(MerchantDetailQueryRepository merchantDetailQueryRepository,
                        MerchantDetailCommandRepository merchantDetailCommandRepository,
                        RedisService redisService,
                        TracingMetrics tracingMetrics,
                        MerchantPort merchantPort) {
                this.merchantDetailQueryRepository = merchantDetailQueryRepository;
                this.merchantDetailCommandRepository = merchantDetailCommandRepository;
                this.redisService = redisService;
                this.tracingMetrics = tracingMetrics;
                this.merchantPort = merchantPort;
        }

        private Uni<Void> invalidateCache(String merchantDetailId) {
                if (merchantDetailId != null) {
                        return redisService.deleteReactive("merchantdetail:id:" + merchantDetailId)
                                        .replaceWith(Uni.createFrom().voidItem());
                }
                return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<ApiResponse<MerchantDetailResponse>> createMerchant(CreateMerchantDetailRequest req) {
                Attributes attrs = Attributes.builder()
                                .put("merchant.id", req.getMerchantId())
                                .build();

                logger.info("Creating merchant detail: {}", req);

                return tracingMetrics.traceAndMeasure("createMerchantDetail", "create_detail", attrs,
                                () -> merchantPort.findById(req.getMerchantId())
                                                .chain(merchant -> {
                                                                MerchantDetail entity = new MerchantDetail();
                                                                entity.setMerchantId(new ObjectId(req.getMerchantId()));
                                                                entity.setDisplayName(req.getDisplayName());
                                                                entity.setShortDescription(req.getShortDescription());
                                                                entity.setWebsiteUrl(req.getWebsiteUrl());
                                                                entity.setCoverImageUrl(req.getCoverImageUrl());
                                                                entity.setLogoUrl(req.getLogoUrl());
                                                                Instant now = Instant.now();
                                                                entity.setCreatedAt(now);
                                                                entity.setUpdatedAt(now);

                                                                return merchantDetailCommandRepository.persist(entity)
                                                                                .chain(saved -> {
                                                                                        MerchantDetailResponse response = MerchantDetailResponse.from(saved);

                                                                                        return invalidateCache(saved.id != null ? saved.id.toHexString() : null)
                                                                                                        .map(v -> {
                                                                                                                logger.info("Successfully created merchant detail with ID: {}",
                                                                                                                                saved.id);
                                                                                                                return ApiResponse.success(
                                                                                                                                "Merchant detail created successfully!",
                                                                                                                                response);
                                                                                                        });
                                                                                });
                                                }));
        }

        @Override
        public Uni<ApiResponse<MerchantDetailResponse>> updateMerchant(UpdateMerchantDetailRequest req) {
                Attributes attrs = Attributes.builder()
                                .put("detail.id", req.getMerchantDetailId())
                                .build();

                logger.info("Updating merchant detail id={}", req.getMerchantDetailId());

                return tracingMetrics.traceAndMeasure("updateMerchantDetail", "update_detail", attrs,
                                () -> merchantDetailQueryRepository.findById(req.getMerchantDetailId())
                                                .chain(existing -> {
                                                        if (existing == null) {
                                                                logger.warn("Merchant detail not found with id {}",
                                                                                req.getMerchantDetailId());
                                                                throw new ResourceNotFoundException(
                                                                                "Merchant detail not found with id "
                                                                                                + req.getMerchantDetailId());
                                                        }

                                                        existing.setDisplayName(req.getDisplayName());
                                                        existing.setShortDescription(req.getShortDescription());
                                                        existing.setWebsiteUrl(req.getWebsiteUrl());
                                                        existing.setCoverImageUrl(req.getCoverImageUrl());
                                                        existing.setLogoUrl(req.getLogoUrl());
                                                        existing.setUpdatedAt(Instant.now());

                                                        return merchantDetailCommandRepository.update(existing)
                                                                        .chain(saved -> {
                                                                                MerchantDetailResponse response = MerchantDetailResponse
                                                                                                .from(saved);

                                                                                return invalidateCache(saved.id != null ? saved.id.toHexString() : null)
                                                                                                .map(v -> {
                                                                                                        logger.info("Successfully updated merchant detail with ID: {}",
                                                                                                                        saved.id);
                                                                                                        return ApiResponse
                                                                                                                        .success("Merchant detail updated successfully!",
                                                                                                                                        response);
                                                                                                });
                                                                        });
                                                }));
        }

        @Override
        public Uni<ApiResponse<MerchantDetailResponseDeleteAt>> trashedMerchant(String merchantID) {
                Attributes attrs = Attributes.builder()
                                .put("detail.id", merchantID)
                                .build();

                logger.info("Trashing merchant detail id={}", merchantID);

                return tracingMetrics.traceAndMeasure("trashedMerchantDetail", "trash_detail", attrs,
                                () -> merchantDetailCommandRepository.trashed(merchantID)
                                                .chain(detail -> {
                                                        if (detail == null) {
                                                                logger.warn("Failed to trash merchant detail - not found with ID: {}",
                                                                                merchantID);
                                                                throw new ResourceNotFoundException(
                                                                                "Merchant detail not found or already trashed");
                                                        }

                                                        MerchantDetailResponseDeleteAt response = MerchantDetailResponseDeleteAt
                                                                        .from(detail);

                                                        return invalidateCache(merchantID)
                                                                        .map(v -> {
                                                                                logger.info("Successfully trashed merchant detail with ID: {}",
                                                                                                merchantID);
                                                                                return ApiResponse.success(
                                                                                                "Merchant detail trashed successfully!",
                                                                                                response);
                                                                        });
                                                }));
        }

        @Override
        public Uni<ApiResponse<MerchantDetailResponseDeleteAt>> restoreMerchant(String merchantID) {
                Attributes attrs = Attributes.builder()
                                .put("detail.id", merchantID)
                                .build();

                logger.info("Restoring merchant detail id={}", merchantID);

                return tracingMetrics.traceAndMeasure("restoreMerchantDetail", "restore_detail", attrs,
                                () -> merchantDetailCommandRepository.restore(merchantID)
                                                .chain(detail -> {
                                                        if (detail == null) {
                                                                logger.warn("Failed to restore merchant detail - not found with ID: {}",
                                                                                merchantID);
                                                                throw new ResourceNotFoundException(
                                                                                "Merchant detail not found or not trashed");
                                                        }

                                                        MerchantDetailResponseDeleteAt response = MerchantDetailResponseDeleteAt
                                                                        .from(detail);

                                                        return invalidateCache(merchantID)
                                                                        .map(v -> {
                                                                                logger.info("Successfully restored merchant detail with ID: {}",
                                                                                                merchantID);
                                                                                return ApiResponse.success(
                                                                                                "Merchant detail restored successfully!",
                                                                                                response);
                                                                        });
                                                }));
        }

        @Override
        public Uni<ApiResponse<Void>> deleteMerchantPermanent(String merchantID) {
                Attributes attrs = Attributes.builder()
                                .put("detail.id", merchantID)
                                .build();

                logger.warn("Permanently deleting merchant detail id={}", merchantID);

                return tracingMetrics.traceAndMeasure("deleteMerchantPermanent", "delete_detail_permanent", attrs, () -> {
                        return merchantDetailCommandRepository.deletePermanent(merchantID)
                                        .chain(deletedDetail -> {
                                                if (deletedDetail == null) {
                                                        logger.warn("Permanent delete failed - merchant detail not found or must be trashed before permanent deletion with id: {}",
                                                                        merchantID);
                                                        throw new InvalidRequestException(
                                                                        "Merchant detail not found or must be trashed before permanent deletion");
                                                }

                                                return invalidateCache(merchantID)
                                                                .map(v -> {
                                                                        logger.info("Successfully permanently deleted merchant detail with ID: {}",
                                                                                        merchantID);
                                                                        return ApiResponse.success("Merchant detail permanently deleted");
                                                                });
                                        });
                });
        }

        @Override
        public Uni<ApiResponse<Void>> restoreAllMerchant() {
                logger.info("Restoring all trashed merchant details");

                return tracingMetrics.traceAndMeasure("restoreAllMerchant", "restore_all_details", () -> {
                        return merchantDetailCommandRepository.restoreAllDeleted()
                                        .map(success -> {
                                                if (!success) {
                                                        throw new ResourceNotFoundException("No trashed merchant details found");
                                                }
                                                logger.info("Successfully restored all trashed merchant details");
                                                return ApiResponse.success("All trashed merchant details restored");
                                        });
                });
        }

        @Override
        public Uni<ApiResponse<Void>> deleteAllMerchantPermanent() {
                logger.warn("Permanently deleting all trashed merchant details");

                return tracingMetrics.traceAndMeasure("deleteAllMerchantPermanent", "delete_all_details_permanent", () -> {
                        return merchantDetailCommandRepository.deleteAllDeleted()
                                        .map(success -> {
                                                if (!success) {
                                                        throw new ResourceNotFoundException("No trashed merchant details found");
                                                }
                                                logger.info("Successfully permanently deleted all trashed merchant details");
                                                return ApiResponse.success("All trashed merchant details permanently deleted");
                                        });
                });
        }
}
