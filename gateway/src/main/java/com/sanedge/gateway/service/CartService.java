package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.CartDto.CreateCartRequest;
import com.sanedge.gateway.dto.CartDto.CreateCartResponse;
import com.sanedge.gateway.dto.CartDto.FindAllCartResponse;
import com.sanedge.gateway.dto.CartDto.FindByIdCartResponse;
import com.sanedge.gateway.dto.CartDto.SimpleStatusMessageResponse;
import io.smallrye.mutiny.Uni;
import java.util.List;

public interface CartService {
    Uni<FindAllCartResponse> listCarts(String userId, int page, int size, String search);

    Uni<CreateCartResponse> createCart(CreateCartRequest body);

    Uni<SimpleStatusMessageResponse> deleteCart(String cartId, String userId);

    Uni<SimpleStatusMessageResponse> deleteAllCarts(String userId, List<String> cartIds);
}
