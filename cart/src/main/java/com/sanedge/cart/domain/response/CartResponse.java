package com.sanedge.cart.domain.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.sanedge.cart.entity.Cart;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String id;
    private String userId;
    private String productId;
    private String name;
    private Integer price;
    private String image;
    private Integer quantity;
    private Integer weight;
    private String createdAt;
    private String updatedAt;

    public static CartResponse from(Cart cart) {
        return CartResponse.builder()
                .id(cart.id != null ? cart.id.toHexString() : null)
                .userId(cart.getUserId() != null ? cart.getUserId().toHexString() : null)
                .productId(cart.getProductId() != null ? cart.getProductId().toHexString() : null)
                .name(cart.getName())
                .price(cart.getPrice())
                .image(cart.getImage())
                .quantity(cart.getQuantity())
                .weight(cart.getWeight())
                .createdAt(cart.getCreatedAt() != null ? cart.getCreatedAt().toString() : null)
                .updatedAt(cart.getUpdatedAt() != null ? cart.getUpdatedAt().toString() : null)
                .build();
    }
}
