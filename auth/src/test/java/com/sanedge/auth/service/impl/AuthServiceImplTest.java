package com.sanedge.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.auth.domain.requests.RegisterRequest;
import com.sanedge.auth.domain.requests.ResetPasswordRequest;
import com.sanedge.auth.entity.AuthOutbox;
import com.sanedge.auth.entity.RefreshToken;
import com.sanedge.auth.entity.ResetToken;
import com.sanedge.auth.repository.AuthOutboxRepository;
import com.sanedge.auth.repository.RefreshTokenRepository;
import com.sanedge.auth.repository.ResetTokenRepository;
import com.sanedge.auth.service.AuthService;
import com.sanedge.auth.service.KafkaService;
import com.sanedge.common.adapter.model.Role;
import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.model.UserRole;
import com.sanedge.common.adapter.role.RolePort;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.adapter.user_role.UserRolePort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.utils.JwtUtil;
import com.sanedge.common.utils.PasswordUtil;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

        private static final String ROLE_ID = "507f1f77bcf86cd799439011";
        private static final String ROLE_ID_2 = "507f1f77bcf86cd799439012";
        private static final String USER_ID = "1";

        @Mock
        UserPort userPort;

        @Mock
        RolePort rolePort;

        @Mock
        UserRolePort userRolePort;

        @Mock
        RefreshTokenRepository refreshTokenRepository;

        @Mock
        ResetTokenRepository resetTokenRepository;

        @Mock
        RedisService redisService;

        @Mock
        KafkaService kafkaService;

        @Mock
        AuthOutboxRepository authOutboxRepository;

        @Mock
        JwtUtil jwtUtil;

        @Mock
        PasswordUtil passwordUtil;

        @Mock
        TracingMetrics tracingMetrics;

        @InjectMocks
        AuthService authServiceUnderTest;

        private RegisterRequest registerReq;
        private User domainUser;

        @BeforeEach
        void setUp() {
                registerReq = RegisterRequest.builder()
                                .firstName("John")
                                .lastName("Doe")
                                .email("john@example.com")
                                .password("SecurePass123!")
                                .build();

                domainUser = new User(USER_ID, "John", "Doe", "john@example.com", "hashed-pass",
                                Instant.now(), Instant.now());

                lenient().doAnswer(invokeSupplier())
                                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
                lenient().doAnswer(invokeSupplier())
                                .when(tracingMetrics)
                                                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

                lenient().when(redisService.existsReactive(anyString())).thenReturn(Uni.createFrom().item(false));
                lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                lenient().when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

                lenient().when(kafkaService.sendMessage(anyString(), anyString(), any(JsonObject.class)))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(kafkaService.sendExistingEvent(anyString(), anyString(), any(JsonObject.class)))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(authOutboxRepository.persist(any(AuthOutbox.class)))
                                .thenAnswer(inv -> Uni.createFrom().item((AuthOutbox) inv.getArgument(0)));

                lenient().when(userRolePort.findByUserId(anyString()))
                                .thenReturn(Uni.createFrom().item(List.of(
                                                new Role(ROLE_ID, "ROLE_USER", Instant.now(), Instant.now()))));

                lenient().when(jwtUtil.generateToken(anyString(), any(), anyString()))
                                .thenReturn("access-token-john");
                lenient().when(jwtUtil.generateRefreshToken(anyString(), anyString()))
                                .thenReturn("refresh-token-john");
                lenient().when(jwtUtil.validateToken(anyString())).thenReturn(true);
                lenient().when(jwtUtil.getRefreshExpirationMs()).thenReturn(3600000L);

                lenient().when(passwordUtil.verifyPassword(anyString(), anyString())).thenReturn(true);
        }

        @Test
        void registerUser_shouldSucceed() {
                when(userPort.findByEmail(anyString()))
                                .thenReturn(Uni.createFrom().failure(
                                                new ResourceNotFoundException("User not found")));
                when(userPort.createUser(any(UserPort.RegisterData.class)))
                                .thenReturn(Uni.createFrom().item(domainUser));

                Role role = new Role(ROLE_ID, "ROLE_USER", Instant.now(), Instant.now());
                when(rolePort.findByName(anyString())).thenReturn(Uni.createFrom().item(role));
                when(userRolePort.assignRoleToUser(anyString(), anyString()))
                                .thenReturn(Uni.createFrom().item(new UserRole("ur-1", USER_ID, ROLE_ID,
                                                Instant.now(), Instant.now())));

                User result = authServiceUnderTest.register(registerReq).await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.email()).isEqualTo("john@example.com");
                assertThat(result.firstname()).isEqualTo("John");
                verify(userPort).createUser(any(UserPort.RegisterData.class));
                verify(authOutboxRepository).persist(any(AuthOutbox.class));
        }

        @Test
        void registerUser_shouldFail_whenEmailAlreadyExists() {
                when(userPort.findByEmail(anyString()))
                                .thenReturn(Uni.createFrom().item(domainUser));

                assertThatThrownBy(() -> authServiceUnderTest.register(registerReq).await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("already exists");
        }

        @Test
        void login_shouldSucceed() {
                when(userPort.findByEmailAndVerify("john@example.com"))
                                .thenReturn(Uni.createFrom().item(domainUser));

                lenient().when(refreshTokenRepository.deleteByUserId(USER_ID)).thenReturn(Uni.createFrom().item(1L));
                lenient().when(refreshTokenRepository.persist(any(RefreshToken.class)))
                                .thenAnswer(inv -> Uni.createFrom().item((RefreshToken) inv.getArgument(0)));

                String[] tokens = authServiceUnderTest.login("john@example.com", "SecurePass123!").await()
                                .indefinitely();

                assertThat(tokens).hasSize(2);
                assertThat(tokens[0]).isEqualTo("access-token-john");
                assertThat(tokens[1]).isEqualTo("refresh-token-john");
        }

        @Test
        void login_shouldCarryRealRolesFromDatabase() {
                when(userPort.findByEmailAndVerify("john@example.com"))
                                .thenReturn(Uni.createFrom().item(domainUser));

                when(userRolePort.findByUserId(anyString()))
                                .thenReturn(Uni.createFrom().item(List.of(
                                                new Role(ROLE_ID, "ROLE_ADMIN", Instant.now(), Instant.now()),
                                                new Role(ROLE_ID_2, "ROLE_USER", Instant.now(), Instant.now()))));

                lenient().when(refreshTokenRepository.deleteByUserId(USER_ID)).thenReturn(Uni.createFrom().item(1L));
                lenient().when(refreshTokenRepository.persist(any(RefreshToken.class)))
                                .thenAnswer(inv -> Uni.createFrom().item((RefreshToken) inv.getArgument(0)));

                String[] tokens = authServiceUnderTest.login("john@example.com", "SecurePass123!").await()
                                .indefinitely();

                assertThat(tokens).hasSize(2);
                verify(jwtUtil).generateToken(eq("john@example.com"), eq(List.of("ROLE_ADMIN", "ROLE_USER")),
                                eq(USER_ID));
        }

        @Test
        void login_shouldFail_whenAccountLocked() {
                when(redisService.existsReactive(anyString())).thenReturn(Uni.createFrom().item(true));

                assertThatThrownBy(() -> authServiceUnderTest.login("john@example.com", "wrong").await()
                                .indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("locked");
        }

        @Test
        void login_shouldFail_withInvalidCredentials() {
                when(userPort.findByEmailAndVerify("john@example.com"))
                                .thenReturn(Uni.createFrom().item(domainUser));
                when(passwordUtil.verifyPassword(anyString(), anyString())).thenReturn(false);

                assertThatThrownBy(() -> authServiceUnderTest.login("john@example.com", "wrong").await()
                                .indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Invalid credentials");
        }

        @Test
        void refresh_shouldSucceed() {
                RefreshToken storedToken = new RefreshToken();
                storedToken.setToken("old-refresh-token");
                storedToken.setUserId(USER_ID);
                storedToken.setExpiration(Instant.now().plusSeconds(3600));

                when(refreshTokenRepository.findByToken("old-refresh-token"))
                                .thenReturn(Uni.createFrom().item(storedToken));
                when(refreshTokenRepository.persist(any(RefreshToken.class)))
                                .thenAnswer(inv -> Uni.createFrom().item((RefreshToken) inv.getArgument(0)));
                when(userPort.findById(USER_ID)).thenReturn(Uni.createFrom().item(domainUser));

                String[] tokens = authServiceUnderTest.refresh("old-refresh-token").await().indefinitely();

                assertThat(tokens).hasSize(2);
                assertThat(tokens[0]).isEqualTo("access-token-john");
                verify(refreshTokenRepository).persist(any(RefreshToken.class));
        }

        @Test
        void refresh_shouldFail_whenTokenInvalid() {
                when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

                assertThatThrownBy(() -> authServiceUnderTest.refresh("invalid-token").await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Invalid or expired");
        }

        @Test
        void refresh_shouldFail_whenTokenNotFound() {
                when(refreshTokenRepository.findByToken("unknown-token"))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> authServiceUnderTest.refresh("unknown-token").await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("invalid or expired");
        }

        @Test
        void forgotPassword_shouldSucceed() {
                when(userPort.findByEmail("john@example.com")).thenReturn(Uni.createFrom().item(domainUser));

                lenient().when(resetTokenRepository.deleteByUserId(USER_ID)).thenReturn(Uni.createFrom().item(1L));
                lenient().when(resetTokenRepository.persist(any(ResetToken.class)))
                                .thenAnswer(inv -> Uni.createFrom().item((ResetToken) inv.getArgument(0)));

                authServiceUnderTest.forgotPassword("john@example.com").await().indefinitely();

                verify(resetTokenRepository).persist(any(ResetToken.class));
                verify(authOutboxRepository).persist(any(AuthOutbox.class));
        }

        @Test
        void forgotPassword_shouldFail_whenUserNotFound() {
                when(userPort.findByEmail("unknown@example.com"))
                                .thenReturn(Uni.createFrom().failure(
                                                new ResourceNotFoundException("User not found")));

                assertThatThrownBy(() -> authServiceUnderTest.forgotPassword("unknown@example.com").await()
                                .indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("User not found");
        }

        @Test
        void resetPassword_shouldSucceed() {
                ResetToken resetToken = new ResetToken();
                resetToken.setToken("valid-reset-token");
                resetToken.setUserId(USER_ID);
                resetToken.setExpiration(Instant.now().plusSeconds(900));

                when(resetTokenRepository.findByToken("valid-reset-token"))
                                .thenReturn(Uni.createFrom().item(resetToken));
                when(userPort.findById(USER_ID)).thenReturn(Uni.createFrom().item(domainUser));
                when(userPort.updatePassword(eq(USER_ID), anyString()))
                                .thenReturn(Uni.createFrom().item(domainUser));
                lenient().when(resetTokenRepository.delete(any(ResetToken.class)))
                                .thenReturn(Uni.createFrom().voidItem());

                ResetPasswordRequest req = ResetPasswordRequest.builder()
                                .token("valid-reset-token")
                                .password("NewPass123!")
                                .confirmPassword("NewPass123!")
                                .build();

                authServiceUnderTest.resetPassword(req).await().indefinitely();

                verify(userPort).updatePassword(eq(USER_ID), anyString());
        }

        @Test
        void resetPassword_shouldFail_whenPasswordsMismatch() {
                ResetPasswordRequest req = ResetPasswordRequest.builder()
                                .token("some-token")
                                .password("Pass1!")
                                .confirmPassword("Pass2!")
                                .build();

                assertThatThrownBy(() -> authServiceUnderTest.resetPassword(req).await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("do not match");
        }

        @Test
        void resetPassword_shouldFail_whenTokenExpired() {
                ResetToken expiredToken = new ResetToken();
                expiredToken.setToken("expired-token");
                expiredToken.setUserId(USER_ID);
                expiredToken.setExpiration(Instant.now().minusSeconds(3600));

                when(resetTokenRepository.findByToken("expired-token"))
                                .thenReturn(Uni.createFrom().item(expiredToken));

                ResetPasswordRequest req = ResetPasswordRequest.builder()
                                .token("expired-token")
                                .password("NewPass123!")
                                .confirmPassword("NewPass123!")
                                .build();

                assertThatThrownBy(() -> authServiceUnderTest.resetPassword(req).await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("expired");
        }

        @Test
        void logout_shouldSucceed() {
                when(refreshTokenRepository.deleteByToken("refresh-token-to-revoke"))
                                .thenReturn(Uni.createFrom().item(1L));

                authServiceUnderTest.logout("refresh-token-to-revoke").await().indefinitely();

                verify(refreshTokenRepository).deleteByToken("refresh-token-to-revoke");
        }

        @Test
        void getMe_shouldReturnUser() {
                when(userPort.findById(USER_ID)).thenReturn(Uni.createFrom().item(domainUser));

                User result = authServiceUnderTest.getMe(USER_ID).await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.id()).isEqualTo(USER_ID);
                assertThat(result.email()).isEqualTo("john@example.com");
        }

        @Test
        void getMe_shouldFail_whenUserNotFound() {
                when(userPort.findById("999"))
                                .thenReturn(Uni.createFrom().failure(
                                                new ResourceNotFoundException("User not found: 999")));

                assertThatThrownBy(() -> authServiceUnderTest.getMe("999").await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("User not found");
        }

        @Test
        void verifyEmail_shouldSucceed() {
                when(redisService.getReactive("verification_code:ABC123"))
                                .thenReturn(Uni.createFrom().item("john@example.com"));

                authServiceUnderTest.verifyEmailByCode("ABC123").await().indefinitely();

                verify(redisService).deleteReactive("verification_code:ABC123");
        }

        @Test
        void verifyEmail_shouldFail_whenInvalidCode() {
                when(redisService.getReactive("verification_code:INVALID"))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> authServiceUnderTest.verifyEmailByCode("INVALID").await().indefinitely())
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Invalid or expired");
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
