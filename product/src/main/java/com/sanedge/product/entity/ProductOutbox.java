package com.sanedge.product.entity;

import java.time.Instant;

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
@MongoEntity(collection = "product_outbox")
public class ProductOutbox extends BaseModel {

    private String eventId;

    private String topic;

    private String eventKey;

    private String payload;

    private String status = "PENDING";

    private int attempts;

    private Instant nextAttemptAt;

    private Instant claimedAt;

    private String claimToken;

    private String lastError;
}
