package com.sanedge.shipping_address.handler;

import com.sanedge.shipping_address.domain.requests.FindAllShippingAddress;
import com.sanedge.shipping_address.domain.response.ShippingAddressResponse;
import com.sanedge.shipping_address.domain.response.ShippingAddressResponseDeleteAt;
import com.sanedge.shipping_address.service.ShippingAddressQueryService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.shipping_address.MutinyShippingQueryServiceGrpc;
import pb.shipping_address.ShippingAddressQuery.FindAllShippingRequest;
import pb.shipping_address.ShippingAddressCommon.ApiResponseShipping;
import pb.shipping_address.ShippingAddressCommon.ApiResponsePaginationShipping;
import pb.shipping_address.ShippingAddressCommon.ApiResponsePaginationShippingDeleteAt;
import pb.shipping_address.ShippingAddressCommon.FindByIdShippingRequest;
import com.sanedge.common.utils.IdValidator;

@GrpcService
@Singleton
public class ShippingAddressQueryGrpcHandler extends MutinyShippingQueryServiceGrpc.ShippingQueryServiceImplBase {

    @Inject
    ShippingAddressQueryService shippingAddressQueryService;

    @Override
    public Uni<ApiResponsePaginationShipping> findAll(FindAllShippingRequest request) {
        FindAllShippingAddress req = new FindAllShippingAddress();
        req.setPage(request.getPage());
        req.setPageSize(request.getPageSize());
        req.setSearch(request.getSearch());

        return shippingAddressQueryService.findAll(req)
                .map(apiResp -> {
                    ApiResponsePaginationShipping.Builder builder = ApiResponsePaginationShipping.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (ShippingAddressResponse r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    if (apiResp.pagination() != null) {
                        builder.setPagination(toProto(apiResp.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseShipping> findById(FindByIdShippingRequest request) {
        Uni<ApiResponseShipping> invalidId = IdValidator.invalidObjectId("Shipping id", request.getId());
        if (invalidId != null) {
            return invalidId;
        }
        return shippingAddressQueryService.findById(request.getId())
                .map(apiResp -> {
                    ApiResponseShipping.Builder builder = ApiResponseShipping.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof com.sanedge.common.exception.ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return GrpcErrorMapper.toStatusRuntimeException(e);
                });
    }

    @Override
    public Uni<ApiResponseShipping> findByOrder(FindByIdShippingRequest request) {
        Uni<ApiResponseShipping> invalidId = IdValidator.invalidObjectId("Shipping id", request.getId());
        if (invalidId != null) {
            return invalidId;
        }
        return shippingAddressQueryService.findByOrder(request.getId())
                .map(apiResp -> {
                    ApiResponseShipping.Builder builder = ApiResponseShipping.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof com.sanedge.common.exception.ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return GrpcErrorMapper.toStatusRuntimeException(e);
                });
    }

    @Override
    public Uni<ApiResponsePaginationShippingDeleteAt> findByActive(FindAllShippingRequest request) {
        FindAllShippingAddress req = new FindAllShippingAddress();
        req.setPage(request.getPage());
        req.setPageSize(request.getPageSize());
        req.setSearch(request.getSearch());

        return shippingAddressQueryService.findByActive(req)
                .map(apiResp -> {
                    ApiResponsePaginationShippingDeleteAt.Builder builder = ApiResponsePaginationShippingDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (ShippingAddressResponseDeleteAt r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    if (apiResp.pagination() != null) {
                        builder.setPagination(toProto(apiResp.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponsePaginationShippingDeleteAt> findByTrashed(FindAllShippingRequest request) {
        FindAllShippingAddress req = new FindAllShippingAddress();
        req.setPage(request.getPage());
        req.setPageSize(request.getPageSize());
        req.setSearch(request.getSearch());

        return shippingAddressQueryService.findByTrashed(req)
                .map(apiResp -> {
                    ApiResponsePaginationShippingDeleteAt.Builder builder = ApiResponsePaginationShippingDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (ShippingAddressResponseDeleteAt r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    if (apiResp.pagination() != null) {
                        builder.setPagination(toProto(apiResp.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    private pb.shipping_address.ShippingAddressCommon.ShippingResponse toProto(ShippingAddressResponse r) {
        if (r == null) {
            return pb.shipping_address.ShippingAddressCommon.ShippingResponse.getDefaultInstance();
        }
        return pb.shipping_address.ShippingAddressCommon.ShippingResponse.newBuilder()
                .setId(r.getId())
                .setOrderId(r.getOrderId())
                .setAlamat(r.getAlamat() != null ? r.getAlamat() : "")
                .setProvinsi(r.getProvinsi() != null ? r.getProvinsi() : "")
                .setNegara(r.getNegara() != null ? r.getNegara() : "")
                .setKota(r.getKota() != null ? r.getKota() : "")
                .setShippingMethod(r.getShippingMethod() != null ? r.getShippingMethod() : "")
                .setShippingCost(r.getShippingCost())
                .setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt() : "")
                .setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : "")
                .build();
    }

    private pb.shipping_address.ShippingAddressCommon.ShippingResponseDeleteAt toProto(ShippingAddressResponseDeleteAt r) {
        if (r == null) {
            return pb.shipping_address.ShippingAddressCommon.ShippingResponseDeleteAt.getDefaultInstance();
        }
        pb.shipping_address.ShippingAddressCommon.ShippingResponseDeleteAt.Builder builder = pb.shipping_address.ShippingAddressCommon.ShippingResponseDeleteAt.newBuilder()
                .setId(r.getId())
                .setOrderId(r.getOrderId())
                .setAlamat(r.getAlamat() != null ? r.getAlamat() : "")
                .setProvinsi(r.getProvinsi() != null ? r.getProvinsi() : "")
                .setNegara(r.getNegara() != null ? r.getNegara() : "")
                .setKota(r.getKota() != null ? r.getKota() : "")
                .setShippingMethod(r.getShippingMethod() != null ? r.getShippingMethod() : "")
                .setShippingCost(r.getShippingCost())
                .setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt() : "")
                .setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : "");
        if (r.getDeletedAt() != null) {
            builder.setDeletedAt(com.google.protobuf.StringValue.of(r.getDeletedAt()));
        }
        return builder.build();
    }

    private pb.Api.PaginationMeta toProto(com.sanedge.common.domain.response.PaginationMeta m) {
        if (m == null) {
            return pb.Api.PaginationMeta.getDefaultInstance();
        }
        return pb.Api.PaginationMeta.newBuilder()
                .setCurrentPage(m.currentPage())
                .setPageSize(m.pageSize())
                .setTotalPages(m.totalPages())
                .setTotalRecords(m.totalRecords())
                .build();
    }
}
