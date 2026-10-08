package com.sanedge.role.handler;

import java.util.stream.Collectors;

import com.sanedge.role.domain.requests.FindAllRoles;
import com.sanedge.role.service.RoleQueryService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.role.MutinyRoleQueryServiceGrpc;
import pb.role.RoleCommon.ApiResponsePaginationRole;
import pb.role.RoleCommon.ApiResponsePaginationRoleDeleteAt;
import pb.role.RoleCommon.ApiResponseRole;
import pb.role.RoleCommon.ApiResponsesRole;
import pb.role.RoleCommon.FindByIdRoleRequest;
import pb.role.RoleQuery.FindByIdUserRoleRequest;
import pb.role.RoleQuery.FindAllRoleRequest;
import com.sanedge.common.utils.IdValidator;

@GrpcService
@Singleton
public class RoleQueryGrpcHandler extends MutinyRoleQueryServiceGrpc.RoleQueryServiceImplBase {
    @Inject
    RoleQueryService roleQueryService;

    @Override
    public Uni<ApiResponsePaginationRole> findAllRole(FindAllRoleRequest protoReq) {
        FindAllRoles domainReq = new FindAllRoles();
        domainReq.setPage(protoReq.getPage());
        domainReq.setPageSize(protoReq.getPageSize());
        domainReq.setSearch(protoReq.getSearch());
        return roleQueryService.findAllPaginated(domainReq)
                .map(res -> {
                    ApiResponsePaginationRole.Builder builder = ApiResponsePaginationRole.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message());
                    if (res.data() != null) {
                        builder.addAllData(res.data().stream()
                                .map(this::toProto)
                                .collect(Collectors.toList()));
                    }
                    if (res.pagination() != null) {
                        builder.setPagination(toProto(res.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRole> findByIdRole(FindByIdRoleRequest protoReq) {
        Uni<ApiResponseRole> invalidId = IdValidator.invalidObjectId("Role id", protoReq.getRoleId());
        if (invalidId != null) {
            return invalidId;
        }
        return roleQueryService.findById(protoReq.getRoleId())
                .map(res -> {
                    ApiResponseRole.Builder builder = ApiResponseRole.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message());
                    if (res.data() != null) {
                        builder.setData(toProto(res.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof jakarta.ws.rs.NotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return GrpcErrorMapper.toStatusRuntimeException(e);
                });
    }

    @Override
    public Uni<ApiResponseRole> findByNameRole(pb.role.RoleQuery.FindByNameRoleRequest protoReq) {
        return roleQueryService.findByName(protoReq.getName())
                .map(res -> {
                    ApiResponseRole.Builder builder = ApiResponseRole.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message());
                    if (res.data() != null) {
                        builder.setData(toProto(res.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof jakarta.ws.rs.NotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return GrpcErrorMapper.toStatusRuntimeException(e);
                });
    }

    @Override
    public Uni<ApiResponsesRole> findByUserId(FindByIdUserRoleRequest protoReq) {
        Uni<ApiResponsesRole> invalidId = IdValidator.invalidObjectId("User id", protoReq.getUserId());
        if (invalidId != null) {
            return invalidId;
        }
        return roleQueryService.findByUserId(protoReq.getUserId())
                .map(res -> {
                    ApiResponsesRole.Builder builder = ApiResponsesRole.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message());
                    if (res.data() != null) {
                        builder.addAllData(res.data().stream()
                                .map(this::toProto)
                                .collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponsePaginationRoleDeleteAt> findByActive(FindAllRoleRequest protoReq) {
        FindAllRoles domainReq = new FindAllRoles();
        domainReq.setPage(protoReq.getPage());
        domainReq.setPageSize(protoReq.getPageSize());
        domainReq.setSearch(protoReq.getSearch());
        return roleQueryService.findActivePaginated(domainReq)
                .map(res -> {
                    ApiResponsePaginationRoleDeleteAt.Builder builder = ApiResponsePaginationRoleDeleteAt.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message());
                    if (res.data() != null) {
                        builder.addAllData(res.data().stream()
                                .map(this::toProtoDeleteAt)
                                .collect(Collectors.toList()));
                    }
                    if (res.pagination() != null) {
                        builder.setPagination(toProto(res.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponsePaginationRoleDeleteAt> findByTrashed(FindAllRoleRequest protoReq) {
        FindAllRoles domainReq = new FindAllRoles();
        domainReq.setPage(protoReq.getPage());
        domainReq.setPageSize(protoReq.getPageSize());
        domainReq.setSearch(protoReq.getSearch());
        return roleQueryService.findTrashedPaginated(domainReq)
                .map(res -> {
                    ApiResponsePaginationRoleDeleteAt.Builder builder = ApiResponsePaginationRoleDeleteAt.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message());
                    if (res.data() != null) {
                        builder.addAllData(res.data().stream()
                                .map(this::toProtoDeleteAt)
                                .collect(Collectors.toList()));
                    }
                    if (res.pagination() != null) {
                        builder.setPagination(toProto(res.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
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

    private pb.role.RoleCommon.RoleResponse toProto(com.sanedge.role.domain.response.RoleResponse r) {
        if (r == null) {
            return pb.role.RoleCommon.RoleResponse.getDefaultInstance();
        }
        return pb.role.RoleCommon.RoleResponse.newBuilder()
                .setId(r.getId())
                .setName(r.getName())
                .setCreatedAt(r.getCreatedAt())
                .setUpdatedAt(r.getUpdatedAt())
                .build();
    }

    private pb.role.RoleCommon.RoleResponseDeleteAt toProtoDeleteAt(
            com.sanedge.role.domain.response.RoleResponseDeleteAt r) {
        if (r == null) {
            return pb.role.RoleCommon.RoleResponseDeleteAt.getDefaultInstance();
        }
        pb.role.RoleCommon.RoleResponseDeleteAt.Builder builder = pb.role.RoleCommon.RoleResponseDeleteAt.newBuilder()
                .setId(r.getId())
                .setName(r.getName())
                .setCreatedAt(r.getCreatedAt())
                .setUpdatedAt(r.getUpdatedAt());
        if (r.getDeletedAt() != null) {
            builder.setDeletedAt(com.google.protobuf.StringValue.of(r.getDeletedAt()));
        }
        return builder.build();
    }
}
