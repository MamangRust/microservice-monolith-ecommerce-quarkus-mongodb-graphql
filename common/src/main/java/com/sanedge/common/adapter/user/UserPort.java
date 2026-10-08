package com.sanedge.common.adapter.user;

import com.sanedge.common.adapter.model.User;

import io.smallrye.mutiny.Uni;

/**
 * Port user: baca + tulis (atas nama auth). Setara
 * {@code user.QueryRepository}/{@code CommandRepository} di Go.
 */
public interface UserPort {

    /** @throws com.sanedge.common.exception.ResourceNotFoundException bila tidak ada. */
    Uni<User> findById(String id);

    /** Termasuk password hash, untuk verifikasi kredensial. */
    Uni<User> findByEmail(String email);

    /** Alias {@link #findByEmail(String)} (perilaku login lama). */
    Uni<User> findByEmailAndVerify(String email);

    Uni<User> findByVerificationCode(String code);

    Uni<User> createUser(RegisterData data);

    Uni<User> updateIsVerified(String userId, boolean isVerified);

    Uni<User> updatePassword(String userId, String password);

    Uni<Void> deleteUserPermanent(String userId);

    record RegisterData(
            String firstname,
            String lastname,
            String email,
            String password,
            String confirmPassword) {
    }
}
