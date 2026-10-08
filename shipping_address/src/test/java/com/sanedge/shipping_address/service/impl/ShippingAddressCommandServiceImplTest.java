package com.sanedge.shipping_address.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.shipping_address.entity.ShippingAddress;
import com.sanedge.shipping_address.repository.ShippingAddressCommandRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.shipping_address.domain.response.ShippingAddressResponse;
import com.sanedge.shipping_address.domain.response.ShippingAddressResponseDeleteAt;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class ShippingAddressCommandServiceImplTest {

        private static final String ID_1 = "507f1f77bcf86cd799439011";
        private static final String ID_3 = "507f1f77bcf86cd799439013";
        private static final String ID_5 = "507f1f77bcf86cd799439015";
        private static final String ID_999 = "507f1f77bcf86cd799439099";

        private static final String ORDER_ID_1 = "507f1f77bcf86cd799439021";
        private static final String ORDER_ID_2 = "507f1f77bcf86cd799439022";
        private static final String ORDER_ID_100 = "507f1f77bcf86cd799439100";

        @Mock
        private ShippingAddressCommandRepository shippingAddressCommandRepository;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        private ShippingAddressCommandImplService shippingAddressService;

        @BeforeEach
        void setUp() throws Exception {
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics)
                        .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

                shippingAddressService = new ShippingAddressCommandImplService(
                                shippingAddressCommandRepository,
                                redisService,
                                tracingMetrics);
        }

        @Test
        @DisplayName("create - should successfully create a shipping address")
        void create_Success() {

                pb.shipping_address.ShippingAddressCommand.CreateShippingAddressRequest request = pb.shipping_address.ShippingAddressCommand.CreateShippingAddressRequest
                                .newBuilder()
                                .setOrderId(ORDER_ID_1)
                                .setAlamat("Jl. Merdeka No.1")
                                .setProvinsi("DKI Jakarta")
                                .setKota("Jakarta Pusat")
                                .setCourier("JNE")
                                .setShippingMethod("REG")
                                .setShippingCost(15000)
                                .setNegara("Indonesia")
                                .build();

                ShippingAddress savedAddress = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. Merdeka No.1",
                                "DKI Jakarta",
                                "Jakarta Pusat", "JNE", "REG", 15000, "Indonesia");

                when(shippingAddressCommandRepository.persist(any(ShippingAddress.class)))
                                .thenReturn(Uni.createFrom().item(savedAddress));

                ApiResponse<ShippingAddressResponse> result = shippingAddressService.create(request).await()
                                .indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Shipping address created successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getOrderId()).isEqualTo(ORDER_ID_1);
                assertThat(result.data().getAlamat()).isEqualTo("Jl. Merdeka No.1");
                assertThat(result.data().getProvinsi()).isEqualTo("DKI Jakarta");
                assertThat(result.data().getKota()).isEqualTo("Jakarta Pusat");
                assertThat(result.data().getShippingMethod()).isEqualTo("REG");
                assertThat(result.data().getShippingCost()).isEqualTo(15000);
                assertThat(result.data().getNegara()).isEqualTo("Indonesia");

                verify(shippingAddressCommandRepository).persist(any(ShippingAddress.class));
        }

        @Test
        @DisplayName("create - should set all fields correctly on entity")
        void create_AllFieldsSetCorrectly() {

                pb.shipping_address.ShippingAddressCommand.CreateShippingAddressRequest request = pb.shipping_address.ShippingAddressCommand.CreateShippingAddressRequest
                                .newBuilder()
                                .setOrderId(ORDER_ID_100)
                                .setAlamat("Jl. Sudirman No.50")
                                .setProvinsi("Jawa Barat")
                                .setKota("Bandung")
                                .setCourier("TIKI")
                                .setShippingMethod("YES")
                                .setShippingCost(25000)
                                .setNegara("Indonesia")
                                .build();

                when(shippingAddressCommandRepository.persist(any(ShippingAddress.class)))
                                .thenAnswer(invocation -> {
                                        ShippingAddress address = invocation.getArgument(0);

                                        assertThat(address.getOrderId()).isEqualTo(new ObjectId(ORDER_ID_100));
                                        assertThat(address.getAlamat()).isEqualTo("Jl. Sudirman No.50");
                                        assertThat(address.getProvinsi()).isEqualTo("Jawa Barat");
                                        assertThat(address.getKota()).isEqualTo("Bandung");
                                        assertThat(address.getCourier()).isEqualTo("TIKI");
                                        assertThat(address.getShippingMethod()).isEqualTo("YES");
                                        assertThat(address.getShippingCost()).isEqualTo(25000);
                                        assertThat(address.getNegara()).isEqualTo("Indonesia");

                                        address.id = new ObjectId(ID_5);
                                        return Uni.createFrom().item(address);
                                });

                ApiResponse<ShippingAddressResponse> result = shippingAddressService.create(request).await()
                                .indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                verify(shippingAddressCommandRepository).persist(any(ShippingAddress.class));
        }

        @Test
        @DisplayName("update - should successfully update a shipping address")
        void update_Success() {

                String shippingId = ID_1;
                ShippingAddress existingAddress = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. lama", "DKI Jakarta",
                                "Jakarta Pusat", "JNE", "REG", 15000, "Indonesia");

                ShippingAddress updatedAddress = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. Baru", "Jawa Barat",
                                "Bandung", "TIKI", "YES", 25000, "Indonesia");

                pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest request = pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest
                                .newBuilder()
                                .setShippingId(shippingId)
                                .setOrderId(ORDER_ID_1)
                                .setAlamat("Jl. Baru")
                                .setProvinsi("Jawa Barat")
                                .setKota("Bandung")
                                .setCourier("TIKI")
                                .setShippingMethod("YES")
                                .setShippingCost(25000)
                                .setNegara("Indonesia")
                                .build();

                when(shippingAddressCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(existingAddress));
                when(shippingAddressCommandRepository.update(any(ShippingAddress.class)))
                                .thenReturn(Uni.createFrom().item(updatedAddress));
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<ShippingAddressResponse> result = shippingAddressService.update(request).await()
                                .indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Shipping address updated successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getAlamat()).isEqualTo("Jl. Baru");
                assertThat(result.data().getProvinsi()).isEqualTo("Jawa Barat");
                assertThat(result.data().getKota()).isEqualTo("Bandung");

                verify(shippingAddressCommandRepository).findById(ID_1);
                verify(shippingAddressCommandRepository).update(any(ShippingAddress.class));
                verify(redisService).deleteReactive("shipping:id:" + ID_1);
        }

        @Test
        @DisplayName("update - should fail when shipping address not found")
        void update_NotFound() {

                pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest request = pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest
                                .newBuilder()
                                .setShippingId(ID_999)
                                .setAlamat("Jl. Baru")
                                .build();

                when(shippingAddressCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> shippingAddressService.update(request).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Shipping address not found with id=" + ID_999);

                verify(shippingAddressCommandRepository).findById(ID_999);
        }

        @Test
        @DisplayName("update - should update all fields correctly")
        void update_AllFieldsUpdatedCorrectly() {

                String shippingId = ID_1;
                ShippingAddress existingAddress = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. lama", "DKI Jakarta",
                                "Jakarta Pusat", "JNE", "REG", 15000, "Indonesia");

                pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest request = pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest
                                .newBuilder()
                                .setShippingId(shippingId)
                                .setOrderId(ORDER_ID_2)
                                .setAlamat("New Alamat")
                                .setProvinsi("New Provinsi")
                                .setKota("New Kota")
                                .setCourier("New Courier")
                                .setShippingMethod("New Method")
                                .setShippingCost(50000)
                                .setNegara("New Negara")
                                .build();

                when(shippingAddressCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(existingAddress));
                when(shippingAddressCommandRepository.update(any(ShippingAddress.class)))
                                .thenAnswer(invocation -> {
                                        ShippingAddress address = invocation.getArgument(0);

                                        assertThat(address.getOrderId()).isEqualTo(new ObjectId(ORDER_ID_2));
                                        assertThat(address.getAlamat()).isEqualTo("New Alamat");
                                        assertThat(address.getProvinsi()).isEqualTo("New Provinsi");
                                        assertThat(address.getKota()).isEqualTo("New Kota");
                                        assertThat(address.getCourier()).isEqualTo("New Courier");
                                        assertThat(address.getShippingMethod()).isEqualTo("New Method");
                                        assertThat(address.getShippingCost()).isEqualTo(50000);
                                        assertThat(address.getNegara()).isEqualTo("New Negara");
                                        return Uni.createFrom().item(address);
                                });
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<ShippingAddressResponse> result = shippingAddressService.update(request).await()
                                .indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
        }

        @Test
        @DisplayName("trash - should successfully trash a shipping address")
        void trash_Success() {

                String shippingId = ID_1;
                ShippingAddress address = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. Merdeka No.1", "DKI Jakarta",
                                "Jakarta Pusat", "JNE", "REG", 15000, "Indonesia");

                when(shippingAddressCommandRepository.trash(anyString()))
                                .thenReturn(Uni.createFrom().item(address));
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<ShippingAddressResponseDeleteAt> result = shippingAddressService.trash(shippingId).await()
                                .indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Shipping address trashed successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getId()).isEqualTo(ID_1);

                verify(shippingAddressCommandRepository).trash(ID_1);
                verify(redisService).deleteReactive("shipping:id:" + ID_1);
        }

        @Test
        @DisplayName("trash - should fail when shipping address not found or already trashed")
        void trash_NotFound() {

                String shippingId = ID_999;

                when(shippingAddressCommandRepository.trash(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> shippingAddressService.trash(shippingId).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Shipping address not found or already trashed");

                verify(shippingAddressCommandRepository).trash(ID_999);
        }

        @Test
        @DisplayName("restore - should successfully restore a trashed shipping address")
        void restore_Success() {

                String shippingId = ID_1;
                ShippingAddress address = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. Merdeka No.1", "DKI Jakarta",
                                "Jakarta Pusat", "JNE", "REG", 15000, "Indonesia");
                address.setDeletedAt(Instant.now().minusSeconds(86400));

                when(shippingAddressCommandRepository.restore(anyString()))
                                .thenReturn(Uni.createFrom().item(address));
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<ShippingAddressResponseDeleteAt> result = shippingAddressService.restore(shippingId).await()
                                .indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Shipping address restored successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getId()).isEqualTo(ID_1);

                verify(shippingAddressCommandRepository).restore(ID_1);
                verify(redisService).deleteReactive("shipping:id:" + ID_1);
        }

        @Test
        @DisplayName("restore - should fail when shipping address not found or not trashed")
        void restore_NotFound() {

                String shippingId = ID_999;

                when(shippingAddressCommandRepository.restore(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> shippingAddressService.restore(shippingId).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Shipping address not found or not trashed");

                verify(shippingAddressCommandRepository).restore(ID_999);
        }

        @Test
        @DisplayName("deletePermanently - should successfully delete a trashed shipping address permanently")
        void deletePermanently_Success() {

                String shippingId = ID_1;
                ShippingAddress trashedAddress = createTestShippingAddress(ID_1, ORDER_ID_1, "Jl. Merdeka No.1",
                                "DKI Jakarta",
                                "Jakarta Pusat", "JNE", "REG", 15000, "Indonesia");
                trashedAddress.setDeletedAt(Instant.now().minusSeconds(86400));

                when(shippingAddressCommandRepository.deletePermanent(anyString()))
                                .thenReturn(Uni.createFrom().item(trashedAddress));
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<Void> result = shippingAddressService.deletePermanently(shippingId).await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Shipping address permanently deleted");

                verify(shippingAddressCommandRepository).deletePermanent(ID_1);
                verify(redisService).deleteReactive("shipping:id:" + ID_1);
        }

        @Test
        @DisplayName("deletePermanently - should fail when shipping address not found or not trashed")
        void deletePermanently_NotTrashed() {

                String shippingId = ID_999;

                when(shippingAddressCommandRepository.deletePermanent(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> shippingAddressService.deletePermanently(shippingId).await().indefinitely())
                                .isInstanceOf(InvalidRequestException.class)
                                .hasMessageContaining(
                                                "Shipping address not found or must be trashed before permanent deletion");

                verify(shippingAddressCommandRepository).deletePermanent(ID_999);
        }

        @Test
        @DisplayName("restoreAll - should successfully restore all trashed shipping addresses")
        void restoreAll_Success() {

                when(shippingAddressCommandRepository.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = shippingAddressService.restoreAll().await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("All shipping addresses restored successfully");

                verify(shippingAddressCommandRepository).restoreAllDeleted();
        }

        @Test
        @DisplayName("restoreAll - should fail when no trashed addresses found")
        void restoreAll_NoAddressesFound() {

                when(shippingAddressCommandRepository.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(false));

                assertThatThrownBy(() -> shippingAddressService.restoreAll().await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("No trashed shipping addresses found");

                verify(shippingAddressCommandRepository).restoreAllDeleted();
        }

        @Test
        @DisplayName("deleteAllPermanent - should successfully delete all trashed shipping addresses")
        void deleteAllPermanent_Success() {

                when(shippingAddressCommandRepository.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = shippingAddressService.deleteAllPermanent().await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("All shipping addresses permanently deleted");

                verify(shippingAddressCommandRepository).deleteAllDeleted();
        }

        @Test
        @DisplayName("deleteAllPermanent - should fail when no trashed addresses found")
        void deleteAllPermanent_NoAddressesFound() {

                when(shippingAddressCommandRepository.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(false));

                assertThatThrownBy(() -> shippingAddressService.deleteAllPermanent().await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("No trashed shipping addresses found");

                verify(shippingAddressCommandRepository).deleteAllDeleted();
        }

        @Test
        @DisplayName("trash - should invalidate cache after successful trash")
        void trash_InvalidatesCache() {

                String shippingId = ID_5;
                ShippingAddress address = createTestShippingAddress(ID_5, ORDER_ID_1, "Jl. Test", "DKI Jakarta",
                                "Jakarta", "JNE", "REG", 15000, "Indonesia");

                when(shippingAddressCommandRepository.trash(anyString()))
                                .thenReturn(Uni.createFrom().item(address));
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                shippingAddressService.trash(shippingId).await().indefinitely();

                verify(redisService).deleteReactive("shipping:id:" + ID_5);
        }

        @Test
        @DisplayName("update - should invalidate cache after successful update")
        void update_InvalidatesCache() {

                String shippingId = ID_3;
                ShippingAddress existingAddress = createTestShippingAddress(ID_3, ORDER_ID_1, "Jl. lama", "DKI Jakarta",
                                "Jakarta", "JNE", "REG", 15000, "Indonesia");
                ShippingAddress updatedAddress = createTestShippingAddress(ID_3, ORDER_ID_1, "Jl. Baru", "DKI Jakarta",
                                "Jakarta", "JNE", "REG", 15000, "Indonesia");

                pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest request = pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest
                                .newBuilder()
                                .setShippingId(shippingId)
                                .setAlamat("Jl. Baru")
                                .build();

                when(shippingAddressCommandRepository.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(existingAddress));
                when(shippingAddressCommandRepository.update(any(ShippingAddress.class)))
                                .thenReturn(Uni.createFrom().item(updatedAddress));
                when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                shippingAddressService.update(request).await().indefinitely();

                verify(redisService).deleteReactive("shipping:id:" + ID_3);
        }

        private ShippingAddress createTestShippingAddress(String id, String orderId, String alamat,
                        String provinsi, String kota, String courier, String shippingMethod,
                        Integer shippingCost, String negara) {
                ShippingAddress address = new ShippingAddress();
                address.setOrderId(orderId != null ? new ObjectId(orderId) : null);
                address.setAlamat(alamat);
                address.setProvinsi(provinsi);
                address.setKota(kota);
                address.setCourier(courier);
                address.setShippingMethod(shippingMethod);
                address.setShippingCost(shippingCost);
                address.setNegara(negara);
                Instant now = Instant.now();
                address.setCreatedAt(now);
                address.setUpdatedAt(now);
                address.id = new ObjectId(id);
                return address;
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
