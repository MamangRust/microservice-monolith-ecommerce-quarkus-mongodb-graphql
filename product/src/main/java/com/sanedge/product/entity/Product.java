package com.sanedge.product.entity;

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
@MongoEntity(collection = "products")
public class Product extends BaseModel {

    private ObjectId merchantId;

    private ObjectId categoryId;

    private String name;

    private String description;

    private Integer price;

    private Integer countInStock;

    private String brand;

    private Integer weight;

    private Float rating;

    private String slugProduct;

    private String imageProduct;
}
