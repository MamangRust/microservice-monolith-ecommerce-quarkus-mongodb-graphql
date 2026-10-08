package com.sanedge.common.utils;

import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.grpc.GrpcErrorMapper;

import io.smallrye.mutiny.Uni;

/**
 * Uniform guard for request IDs at the gRPC handler boundary (Fase 2
 * standardisasi invalid-ID validation).
 *
 * <p>Returns a failed {@code Uni} that is <em>already mapped</em> to a
 * {@code StatusRuntimeException(INVALID_ARGUMENT)}. Mapping happens inside the
 * helper because the guard short-circuits <em>before</em> the service-call
 * chain, so each handler's existing
 * {@code .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException)}
 * never sees it. The pre-mapped status is preserved by Quarkus gRPC and
 * becomes HTTP 400 at the gateway.</p>
 *
 * <p>Usage in a handler method:</p>
 * <pre>{@code
 * if (request.getProductId() <= 0) {
 *     return IdValidator.invalid("Product id");
 * }
 * }</pre>
 */
public final class IdValidator {

    private IdValidator() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Uni<T> invalid(String field) {
        return (Uni<T>) (Uni<?>) Uni.createFrom()
                .failure(new InvalidRequestException(field + " must be a positive integer"))
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    /**
     * Mongo/ObjectId counterpart of {@link #invalid(String)}. Returns a failed
     * {@code Uni} (already mapped to {@code INVALID_ARGUMENT}) when {@code value}
     * is {@code null} or not a valid 24-char hex ObjectId, or {@code null} when
     * the value is valid so the caller can continue.
     *
     * <pre>{@code
     * Uni<X> bad = IdValidator.invalidObjectId("Role id", request.getRoleId());
     * if (bad != null) {
     *     return bad;
     * }
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static <T> Uni<T> invalidObjectId(String field, String value) {
        if (value == null || !OBJECT_ID_PATTERN.matcher(value).matches()) {
            return (Uni<T>) (Uni<?>) Uni.createFrom()
                    .failure(new InvalidRequestException(field + " must be a valid id"))
                    .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
        }
        return null;
    }

    private static final java.util.regex.Pattern OBJECT_ID_PATTERN = java.util.regex.Pattern.compile("[0-9a-fA-F]{24}");
}
