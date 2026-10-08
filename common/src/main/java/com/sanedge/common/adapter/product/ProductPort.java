package com.sanedge.common.adapter.product;

import java.util.List;

import com.sanedge.common.adapter.model.Product;
import com.sanedge.common.adapter.support.Paged;

import io.smallrye.mutiny.Uni;

/**
 * Port product: baca + tulis (stok) + enumerasi. Setara
 * {@code product.QueryRepository}/{@code CommandRepository}/{@code BulkRepository}
 * di Go.
 */
public interface ProductPort {

    /** @throws com.sanedge.common.exception.ResourceNotFoundException bila tidak ada. */
    Uni<Product> findById(String productId);

    Uni<List<String>> findIdsByMerchant(String merchantId);

    Uni<Product> updateCountStock(String productId, int stock);

    Uni<Product> adjustStock(String productId, int delta);

    Uni<Paged<Product>> findAll(int page, int pageSize);
}
