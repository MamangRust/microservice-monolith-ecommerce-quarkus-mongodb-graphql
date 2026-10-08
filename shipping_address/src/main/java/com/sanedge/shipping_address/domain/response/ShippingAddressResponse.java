package com.sanedge.shipping_address.domain.response;

import com.sanedge.shipping_address.entity.ShippingAddress;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddressResponse {

    private String id;
    private String orderId;
    private String alamat;
    private String provinsi;
    private String negara;
    private String kota;
    private String shippingMethod;
    private Integer shippingCost;
    private String createdAt;
    private String updatedAt;

    public static ShippingAddressResponse from(ShippingAddress entity) {
        return ShippingAddressResponse.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .orderId(entity.getOrderId() != null ? entity.getOrderId().toHexString() : null)
                .alamat(entity.getAlamat())
                .provinsi(entity.getProvinsi())
                .negara(entity.getNegara())
                .kota(entity.getKota())
                .shippingMethod(entity.getShippingMethod())
                .shippingCost(entity.getShippingCost())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}