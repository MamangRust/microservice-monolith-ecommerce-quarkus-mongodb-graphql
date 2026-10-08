package com.sanedge.role.handler;

import com.google.protobuf.Empty;
import com.sanedge.role.domain.response.RoleResponse;
import com.sanedge.role.domain.response.RoleResponseDeleteAt;
import com.sanedge.role.service.RoleCommandService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.role.MutinyRoleCommandServiceGrpc;
import pb.role.RoleCommand.CreateRoleRequest;
import pb.role.RoleCommand.UpdateRoleRequest;
import pb.role.RoleCommon.ApiResponseRole;
import pb.role.RoleCommon.ApiResponseRoleAll;
import pb.role.RoleCommon.ApiResponseRoleDelete;
import pb.role.RoleCommon.ApiResponseRoleDeleteAt;
import pb.role.RoleCommon.FindByIdRoleRequest;
import com.sanedge.common.utils.IdValidator;

@GrpcService
@Singleton
public class RoleCommandGrpcHandler extends MutinyRoleCommandServiceGrpc.RoleCommandServiceImplBase {
    @Inject
    RoleCommandService roleCommandService;

    @Override
    public Uni<ApiResponseRole> createRole(CreateRoleRequest request) {
        com.sanedge.role.domain.requests.CreateRoleRequest domainReq = new com.sanedge.role.domain.requests.CreateRoleRequest();
        domainReq.setName(request.getName());
        return roleCommandService.create(domainReq)
                .map(res -> buildApiResponse(res.data()))
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRole> updateRole(UpdateRoleRequest request) {
        Uni<ApiResponseRole> invalidId = IdValidator.invalidObjectId("id", request.getId());
        if (invalidId != null) {
            return invalidId;
        }
        com.sanedge.role.domain.requests.UpdateRoleRequest domainReq = new com.sanedge.role.domain.requests.UpdateRoleRequest();
        domainReq.setRoleId(request.getId());
        domainReq.setName(request.getName());
        return roleCommandService.update(domainReq)
                .map(res -> buildApiResponse(res.data()))
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRoleDeleteAt> trashedRole(FindByIdRoleRequest request) {
        Uni<ApiResponseRoleDeleteAt> invalidId = IdValidator.invalidObjectId("Role id", request.getRoleId());
        if (invalidId != null) {
            return invalidId;
        }
        return roleCommandService.trash(request.getRoleId())
                .map(res -> buildApiResponseWithDeletedAt(res.data()))
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRoleDeleteAt> restoreRole(FindByIdRoleRequest request) {
        Uni<ApiResponseRoleDeleteAt> invalidId = IdValidator.invalidObjectId("Role id", request.getRoleId());
        if (invalidId != null) {
            return invalidId;
        }
        return roleCommandService.restore(request.getRoleId())
                .map(res -> buildApiResponseWithDeletedAt(res.data()))
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRoleDelete> deleteRolePermanent(FindByIdRoleRequest request) {
        Uni<ApiResponseRoleDelete> invalidId = IdValidator.invalidObjectId("Role id", request.getRoleId());
        if (invalidId != null) {
            return invalidId;
        }
        return roleCommandService.deletePermanent(request.getRoleId())
                .map(res -> ApiResponseRoleDelete.newBuilder()
                        .setStatus(res.status())
                        .setMessage(res.message())
                        .build())
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRoleAll> restoreAllRole(Empty request) {
        return roleCommandService.restoreAllTrashedRoles()
                .map(res -> ApiResponseRoleAll.newBuilder()
                        .setStatus(res.status())
                        .setMessage(res.message())
                        .build())
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<ApiResponseRoleAll> deleteAllRolePermanent(Empty request) {
        return roleCommandService.deleteAllTrashedRoles()
                .map(res -> ApiResponseRoleAll.newBuilder()
                        .setStatus(res.status())
                        .setMessage(res.message())
                        .build())
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<pb.role.RoleCommon.ApiResponseUserRole> assignRoleToUser(
            pb.role.RoleCommon.AssignRoleToUserRequest request) {
        Uni<pb.role.RoleCommon.ApiResponseUserRole> invalidUser = IdValidator.invalidObjectId("User id",
                request.getUserId());
        if (invalidUser != null) {
            return invalidUser;
        }
        Uni<pb.role.RoleCommon.ApiResponseUserRole> invalidRole = IdValidator.invalidObjectId("Role id",
                request.getRoleId());
        if (invalidRole != null) {
            return invalidRole;
        }
        return roleCommandService.assignRoleToUser(request.getUserId(), request.getRoleId())
                .map(res -> {
                    pb.role.RoleCommon.UserRoleResponse.Builder builder = pb.role.RoleCommon.UserRoleResponse
                            .newBuilder()
                            .setUserId(res.data() != null && res.data().getUserId() != null
                                    ? res.data().getUserId()
                                    : "");
                    if (res.data() != null && res.data().getRoleId() != null) {
                        builder.setRoleId(res.data().getRoleId());
                    }
                    return pb.role.RoleCommon.ApiResponseUserRole.newBuilder()
                            .setStatus(res.status())
                            .setMessage(res.message())
                            .setData(builder)
                            .build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    public Uni<Empty> removeRoleFromUser(pb.role.RoleCommon.RemoveRoleFromUserRequest request) {
        Uni<Empty> invalidUser = IdValidator.invalidObjectId("User id", request.getUserId());
        if (invalidUser != null) {
            return invalidUser;
        }
        Uni<Empty> invalidRole = IdValidator.invalidObjectId("Role id", request.getRoleId());
        if (invalidRole != null) {
            return invalidRole;
        }
        return roleCommandService.removeRoleFromUser(request.getUserId(), request.getRoleId())
                .map(res -> Empty.getDefaultInstance())
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    private pb.role.RoleCommon.RoleResponse toProto(RoleResponse r) {
        if (r == null) {
            return pb.role.RoleCommon.RoleResponse.getDefaultInstance();
        }
        pb.role.RoleCommon.RoleResponse.Builder builder = pb.role.RoleCommon.RoleResponse.newBuilder()
                .setId(r.getId())
                .setName(r.getName());
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        return builder.build();
    }

    private pb.role.RoleCommon.RoleResponseDeleteAt toProto(RoleResponseDeleteAt r) {
        if (r == null) {
            return pb.role.RoleCommon.RoleResponseDeleteAt.getDefaultInstance();
        }
        pb.role.RoleCommon.RoleResponseDeleteAt.Builder builder = pb.role.RoleCommon.RoleResponseDeleteAt.newBuilder()
                .setId(r.getId())
                .setName(r.getName());
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        if (r.getDeletedAt() != null) {
            builder.setDeletedAt(com.google.protobuf.StringValue.of(r.getDeletedAt()));
        }
        return builder.build();
    }

    private ApiResponseRole buildApiResponse(RoleResponse response) {
        return ApiResponseRole.newBuilder()
                .setStatus("success")
                .setMessage("Success")
                .setData(toProto(response))
                .build();
    }

    private ApiResponseRoleDeleteAt buildApiResponseWithDeletedAt(RoleResponseDeleteAt response) {
        return ApiResponseRoleDeleteAt.newBuilder()
                .setStatus("success")
                .setMessage("Success")
                .setData(toProto(response))
                .build();
    }
}
