package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.ProductDto.CreateProductRequest;
import com.sanedge.gateway.dto.ProductDto.CreateProductResponse;
import com.sanedge.gateway.dto.ProductDto.FindAllProductResponse;
import com.sanedge.gateway.dto.ProductDto.FindByIdProductResponse;
import com.sanedge.gateway.dto.ProductDto.SimpleStatusMessageResponse;
import com.sanedge.gateway.dto.ProductDto.UpdateProductRequest;
import com.sanedge.gateway.dto.ProductDto.UpdateProductResponse;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public interface ProductService {
    Uni<FindAllProductResponse> listProducts(int page, int size, String search);
    Uni<FindAllProductResponse> listActiveProducts(int page, int size, String search);
    Uni<FindAllProductResponse> listTrashedProducts(int page, int size, String search);
    Uni<FindByIdProductResponse> getProduct(String id);
    Uni<CreateProductResponse> createProduct(CreateProductRequest body);
    Uni<UpdateProductResponse> updateProduct(String id, UpdateProductRequest body);
    Uni<UpdateProductResponse> uploadProduct(String id, FileUpload file);
    Uni<FindByIdProductResponse> deleteProduct(String id);
    Uni<FindByIdProductResponse> restoreProduct(String id);
    Uni<SimpleStatusMessageResponse> deleteProductPermanent(String id);
    Uni<SimpleStatusMessageResponse> restoreAllProducts();
    Uni<SimpleStatusMessageResponse> deleteAllProductsPermanent();
}
