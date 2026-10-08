package com.sanedge.review.entity;

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
@MongoEntity(collection = "reviews")
public class Review extends BaseModel {

    private ObjectId userId;

    private ObjectId productId;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    private String comment;

    @NotNull
    private Integer rating;
}
