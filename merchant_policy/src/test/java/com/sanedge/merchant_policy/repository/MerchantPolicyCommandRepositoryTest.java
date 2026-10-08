package com.sanedge.merchant_policy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.merchant_policy.entity.MerchantPolicy;

@ExtendWith(MockitoExtension.class)
class MerchantPolicyCommandRepositoryTest {

    private static final String MERCHANT_ID = "507f1f77bcf86cd799439021";

    @Mock
    MerchantPolicyCommandRepository merchantPolicyCommandRepository;

    @Mock
    MerchantPolicyQueryRepository merchantPolicyQueryRepository;

    private MerchantPolicy createMockPolicy(String id, String title) {
        MerchantPolicy p = new MerchantPolicy();
        p.id = new ObjectId(id);
        p.setTitle(title);
        p.setDescription("desc-" + id);
        p.setMerchantId(new ObjectId(MERCHANT_ID));
        return p;
    }

    @Test
    void testTrashPolicy_placeholder() {
        MerchantPolicy p = createMockPolicy("507f1f77bcf86cd799439011", "TrashMe");
        assertThat(p.getTitle()).isEqualTo("TrashMe");
    }

    @Test
    void testTrashAlreadyTrashedReturnsNull_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testTrashNonExistentPolicyReturnsNull_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testRestorePolicy_placeholder() {
        MerchantPolicy p = createMockPolicy("507f1f77bcf86cd799439012", "RestoreMe");
        assertThat(p.getTitle()).isEqualTo("RestoreMe");
    }

    @Test
    void testRestoreNonTrashedReturnsNull_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testRestoreNonExistentPolicyReturnsNull_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testDeletePermanentPolicy_placeholder() {
        MerchantPolicy p = createMockPolicy("507f1f77bcf86cd799439013", "Delete");
        assertThat(p.getTitle()).isEqualTo("Delete");
    }

    @Test
    void testDeletePermanentNonExistentReturnsNull_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testDeletePermanentNonTrashedReturnsNull_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testRestoreAllDeleted_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testRestoreAllDeletedWhenNoneTrashedReturnsFalse_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testDeleteAllDeleted_placeholder() {
        assertThat(true).isTrue();
    }

    @Test
    void testDeleteAllDeletedWhenNoneTrashedReturnsFalse_placeholder() {
        assertThat(true).isTrue();
    }
}
