package com.sanedge.auth.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.sanedge.auth.domain.requests.RegisterRequest;
import com.sanedge.auth.domain.requests.ResetPasswordRequest;
import com.sanedge.auth.entity.RefreshToken;
import com.sanedge.auth.entity.ResetToken;
import com.sanedge.auth.entity.AuthOutbox;
import com.sanedge.auth.repository.AuthOutboxRepository;
import com.sanedge.auth.repository.RefreshTokenRepository;
import com.sanedge.auth.repository.ResetTokenRepository;
import com.sanedge.common.adapter.model.Role;
import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.role.RolePort;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.adapter.user_role.UserRolePort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.utils.JwtUtil;
import com.sanedge.common.utils.PasswordUtil;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    @Inject
    UserPort userPort;

    @Inject
    RolePort rolePort;

    @Inject
    UserRolePort userRolePort;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    ResetTokenRepository resetTokenRepository;

    @Inject
    RedisService redisService;

    @Inject
    KafkaService kafkaService;

    @Inject
    AuthOutboxRepository authOutboxRepository;

    @Inject
    JwtUtil jwtUtil;

    @Inject
    PasswordUtil passwordUtil;

    @Inject
    TracingMetrics tracingMetrics;

    public Uni<User> register(RegisterRequest req) {
        String firstName = req.getFirstName();
        String lastName = req.getLastName();
        String email = req.getEmail();
        String password = req.getPassword();

        return tracingMetrics.traceAndMeasure("registerUser", "register", () -> {
            return userPort.findByEmail(email)
                    .<User>chain(existing -> Uni.createFrom()
                            .failure(new RuntimeException("User with this email already exists")))
                    .onFailure(ResourceNotFoundException.class)
                    .recoverWithUni(notFound -> userPort.createUser(
                            new UserPort.RegisterData(firstName, lastName, email, password, password)))
                    .chain(user -> {
                        String verificationCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

                        return redisService.setWithExpirationReactive("verification:" + email, verificationCode, 900)
                                .chain(() -> redisService.setWithExpirationReactive(
                                        "verification_code:" + verificationCode,
                                        email, 900))
                                .chain(() -> sendWelcomeEmail(user, verificationCode))
                                .chain(() -> rolePort.findByName("ROLE_USER")
                                        .map(Role::id)
                                        .onFailure().recoverWithItem(err -> {
                                            LOGGER.warn(
                                                    "Failed to resolve ROLE_USER during registration, skipping role assignment: {}",
                                                    err.getMessage());
                                            return "";
                                        })
                                        .chain(roleId -> {
                                            if (roleId == null || roleId.isEmpty()) {
                                                // No role assigned; login's role lookup falls back to ROLE_USER.
                                                return Uni.createFrom().item(user);
                                            }
                                            return userRolePort.assignRoleToUser(user.id(), roleId)
                                                    .replaceWith(user);
                                        }));
                    });
        });
    }

    public Uni<String[]> login(String email, String password) {
        String failedAttemptsKey = "failed_login:" + email;
        String lockKey = "account_locked:" + email;

        return tracingMetrics.traceAndMeasure("loginUser", "login", () -> {
            return redisService.existsReactive(lockKey)
                    .chain(locked -> {
                        if (locked) {
                            return Uni.createFrom()
                                    .failure(new RuntimeException("Account is locked due to too many failed attempts"));
                        }
                        return userPort.findByEmailAndVerify(email)
                                .onFailure(ResourceNotFoundException.class)
                                .recoverWithItem((User) null)
                                .chain(user -> {
                                    if (user == null || !passwordUtil.verifyPassword(password, user.password())) {
                                        return handleFailedLogin(email, failedAttemptsKey, lockKey);
                                    }

                                    return fetchUserRoles(user.id())
                                            .chain(roles -> {
                                                String accessToken = jwtUtil.generateToken(user.email(), roles,
                                                        user.id());
                                                String refreshTokenStr = jwtUtil.generateRefreshToken(user.email(),
                                                        user.id());

                                                RefreshToken rt = new RefreshToken();
                                                rt.setUserId(user.id());
                                                rt.setToken(refreshTokenStr);
                                                Instant now = Instant.now();
                                                rt.setExpiration(now.plusMillis(jwtUtil.getRefreshExpirationMs()));
                                                rt.setCreatedAt(now);
                                                rt.setUpdatedAt(now);

                                                return redisService.deleteReactive(failedAttemptsKey)
                                                        .chain(() -> refreshTokenRepository.deleteByUserId(user.id()))
                                                        .chain(() -> refreshTokenRepository.persist(rt))
                                                        .map(v -> new String[] { accessToken, refreshTokenStr });
                                            });
                                });
                    });
        });
    }

    public Uni<String[]> refresh(String refreshTokenStr) {
        return tracingMetrics.traceAndMeasure("refreshToken", "refresh", () -> {
            if (!jwtUtil.validateToken(refreshTokenStr)) {
                return Uni.createFrom().failure(new RuntimeException("Invalid or expired refresh token"));
            }

            return refreshTokenRepository.findByToken(refreshTokenStr)
                    .chain(rt -> {
                        if (rt == null || rt.getExpiration().isBefore(Instant.now())) {
                            return Uni.createFrom()
                                    .failure(new RuntimeException("Refresh token is invalid or expired"));
                        }

                        return userPort.findById(rt.getUserId())
                                .chain(user -> fetchUserRoles(user.id())
                                        .chain(roles -> {
                                            String newAccessToken = jwtUtil.generateToken(user.email(), roles,
                                                    user.id());
                                            String newRefreshTokenStr = jwtUtil.generateRefreshToken(user.email(),
                                                    user.id());

                                            rt.setToken(newRefreshTokenStr);
                                            rt.setExpiration(Instant.now()
                                                    .plusMillis(jwtUtil.getRefreshExpirationMs()));
                                            rt.setUpdatedAt(Instant.now());

                                            return refreshTokenRepository.persist(rt)
                                                    .map(v -> new String[] { newAccessToken, newRefreshTokenStr });
                                        }));
                    });
        });
    }

    public Uni<Void> forgotPassword(String email) {
        return tracingMetrics.traceAndMeasure("forgotPassword", "forgot_password", () -> {
            return userPort.findByEmail(email)
                    .chain(user -> {
                        String token = UUID.randomUUID().toString();

                        ResetToken resetToken = new ResetToken();
                        resetToken.setUserId(user.id());
                        resetToken.setToken(token);
                        Instant now = Instant.now();
                        resetToken.setExpiration(now.plusMillis(900000)); // 15 mins
                        resetToken.setCreatedAt(now);
                        resetToken.setUpdatedAt(now);

                        return resetTokenRepository.deleteByUserId(user.id())
                                .chain(() -> resetTokenRepository.persist(resetToken))
                                .chain(() -> sendForgotPasswordEmail(user, token));
                    });
        });
    }

    public Uni<Void> resetPassword(ResetPasswordRequest req) {
        String token = req.getToken();
        String password = req.getPassword();
        String confirmPassword = req.getConfirmPassword();

        return tracingMetrics.traceAndMeasure("resetPassword", "reset_password", () -> {
            if (!password.equals(confirmPassword)) {
                return Uni.createFrom().failure(new RuntimeException("Passwords do not match"));
            }

            return resetTokenRepository.findByToken(token)
                    .chain(rt -> {
                        if (rt == null || rt.getExpiration().isBefore(Instant.now())) {
                            return Uni.createFrom().failure(new RuntimeException("Invalid or expired reset token"));
                        }

                        return userPort.findById(rt.getUserId())
                                .chain(user -> userPort.updatePassword(user.id(), password))
                                .chain(updated -> resetTokenRepository.delete(rt))
                                .replaceWithVoid();
                    });
        });
    }

    public Uni<Void> logout(String refreshTokenStr) {
        return tracingMetrics.traceAndMeasure("logout", "logout",
                () -> refreshTokenRepository.deleteByToken(refreshTokenStr)
                        .replaceWithVoid());
    }

    public Uni<Void> verifyEmailByCode(String code) {
        return tracingMetrics.traceAndMeasure("verifyEmailByCode", "verify_email", () -> {
            String key = "verification_code:" + code;
            return redisService.getReactive(key)
                    .chain(email -> {
                        if (email == null) {
                            return Uni.createFrom()
                                    .failure(new RuntimeException("Invalid or expired verification code"));
                        }
                        return redisService.deleteReactive(key)
                                .chain(() -> redisService.deleteReactive("verification:" + email))
                                .replaceWithVoid();
                    });
        });
    }

    public Uni<User> getMe(String userId) {
        return tracingMetrics.traceAndMeasure("getMe", "get_me",
                () -> userPort.findById(userId));
    }

    /**
     * Loads the user's real role names from the role service so the JWT carries
     * actual roles (e.g. ROLE_ADMIN) instead of a hardcoded ROLE_USER.
     * Falls back to ROLE_USER when no roles are assigned or the role service is
     * unavailable, so login/refresh never break.
     */
    private Uni<List<String>> fetchUserRoles(String userId) {
        return userRolePort.findByUserId(userId)
                .map(roles -> {
                    List<String> names = new ArrayList<>();
                    if (roles != null) {
                        for (Role r : roles) {
                            String name = r.name();
                            if (name != null && !name.isBlank()) {
                                names.add(name);
                            }
                        }
                    }
                    return names.isEmpty() ? Collections.singletonList("ROLE_USER") : names;
                })
                .onFailure().recoverWithItem(err -> {
                    LOGGER.warn("Failed to load roles for user {}, falling back to ROLE_USER: {}",
                            userId, err.getMessage());
                    return Collections.singletonList("ROLE_USER");
                });
    }

    private Uni<String[]> handleFailedLogin(String email, String failedAttemptsKey, String lockKey) {
        return redisService.getReactive(failedAttemptsKey)
                .chain(attemptsStr -> {
                    int currentAttempts = attemptsStr == null ? 0 : Integer.parseInt(attemptsStr);
                    int newAttempts = currentAttempts + 1;
                    if (newAttempts >= 5) {
                        return redisService.setWithExpirationReactive(lockKey, "true", 3600) // lock 1 hr
                                .chain(() -> redisService.deleteReactive(failedAttemptsKey))
                                .chain(() -> Uni.createFrom().failure(
                                        new RuntimeException("Account is locked due to too many failed attempts")));
                    } else {
                        return redisService
                                .setWithExpirationReactive(failedAttemptsKey, String.valueOf(newAttempts), 600) // 10
                                                                                                                // mins
                                .chain(() -> Uni.createFrom().failure(
                                        new RuntimeException("Invalid credentials. Attempt " + newAttempts + " of 5")));
                    }
                });
    }

    private Uni<Void> sendWelcomeEmail(User user, String code) {
        String subject = "Welcome to Quarkus Modular Monolith";
        String body = String.format(
                "Hello %s %s,\n\nWelcome to our platform! Use the following code to verify your email address:\n\n%s\n\nRegards,\nSupport Team",
                user.firstname(), user.lastname(), code);

        JsonObject payload = new JsonObject()
                .put("email", user.email())
                .put("subject", subject)
                .put("body", body);

        return enqueueOutbox("email-service-topic-auth-register", user.email(), payload);
    }

    private Uni<Void> sendForgotPasswordEmail(User user, String token) {
        String subject = "Reset Password Verification";
        String body = String.format(
                "Hello %s %s,\n\nYou have requested a password reset. Use the following token to reset your password:\n\n%s\n\nThis token will expire in 15 minutes.\n\nRegards,\nSupport Team",
                user.firstname(), user.lastname(), token);

        JsonObject payload = new JsonObject()
                .put("email", user.email())
                .put("subject", subject)
                .put("body", body);

        return enqueueOutbox("email-service-topic-auth-forgot-password", user.email(), payload);
    }

    private Uni<Void> enqueueOutbox(String topic, String email, JsonObject payload) {
        String eventId = UUID.randomUUID().toString();
        JsonObject eventPayload = payload.copy()
                .put("event_id", eventId)
                .put("schema_version", 1)
                .put("event_type", topic)
                .put("occurred_at", java.time.Instant.now().toString());

        if (authOutboxRepository == null) {
            return kafkaService.sendExistingEvent(topic, email, eventPayload);
        }

        AuthOutbox event = new AuthOutbox();
        event.setEventId(eventId);
        event.setTopic(topic);
        event.setEventKey(email);
        event.setPayload(eventPayload.encode());
        Instant now = Instant.now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return authOutboxRepository.persist(event).replaceWithVoid();
    }
}
