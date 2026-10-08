package com.sanedge.cart.entity;

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
@MongoEntity(collection = "carts")
public class Cart extends BaseModel {

    private ObjectId userId;

    private ObjectId productId;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private Integer price;

    @NotBlank
    @Size(max = 255)
    private String image;

    @NotNull
    private Integer quantity;

    @NotNull
    private Integer weight;
}
