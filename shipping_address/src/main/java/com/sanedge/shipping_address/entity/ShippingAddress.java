package com.sanedge.shipping_address.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "shipping_addresses")
public class ShippingAddress extends BaseModel {

    @NotNull
    private ObjectId orderId;

    @NotBlank
    private String alamat;

    @NotBlank
    @Size(max = 255)
    private String provinsi;

    @NotBlank
    @Size(max = 255)
    private String negara;

    @NotBlank
    @Size(max = 255)
    private String kota;

    @NotBlank
    @Size(max = 100)
    private String courier;

    @NotBlank
    @Size(max = 255)
    private String shippingMethod;

    @NotNull
    private Integer shippingCost;
}
