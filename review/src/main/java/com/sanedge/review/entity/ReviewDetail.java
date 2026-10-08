package com.sanedge.review.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "review_details")
public class ReviewDetail extends BaseModel {

    private ObjectId reviewId;

    @NotBlank
    @Size(max = 20)
    private String type;

    @NotBlank
    private String url;

    private String caption;
}
