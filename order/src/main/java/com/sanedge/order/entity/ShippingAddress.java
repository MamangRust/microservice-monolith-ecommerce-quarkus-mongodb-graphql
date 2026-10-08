package com.sanedge.order.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;

import io.quarkus.mongodb.panache.common.MongoEntity;
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

    private ObjectId orderId;

    private String alamat;

    private String provinsi;

    private String negara;

    private String kota;

    private String courier;

    private String shippingMethod;

    private Integer shippingCost;
}
