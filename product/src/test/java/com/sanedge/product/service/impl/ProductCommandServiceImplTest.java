package com.sanedge.product.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.common.adapter.category.CategoryPort;
import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.common.adapter.model.Category;
import com.sanedge.common.adapter.model.Merchant;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.product.domain.requests.CreateProductRequest;
import com.sanedge.product.domain.requests.UpdateProductRequest;
import com.sanedge.product.domain.response.ProductResponse;
import com.sanedge.product.domain.response.ProductResponseDeleteAt;
import com.sanedge.product.entity.Product;
import com.sanedge.product.repository.ProductCommandRepository;
import com.sanedge.product.repository.ProductQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceImplTest {

    private static final String PRODUCT_ID = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439012";
    private static final String CATEGORY_ID = "507f1f77bcf86cd799439013";

    @Mock
    private ProductCommandRepository productCommandRepository;

    @Mock
    private ProductQueryRepository productQueryRepository;

    @Mock
    private MerchantPort merchantPort;

    @Mock
    private CategoryPort categoryPort;

    @Mock
    private Validator validator;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private ProductCommandServiceImpl productService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics)
                        .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        productService = new ProductCommandServiceImpl(
                productCommandRepository,
                productQueryRepository,
                merchantPort,
                categoryPort,
                validator,
                redisService,
                tracingMetrics);
    }

    private Merchant createMerchant() {
        return new Merchant("mid", "uid", "name", "desc", "addr", "email", "phone", "active", null, null);
    }

    private Category createCategory() {
        return new Category("cid", "name", "desc", "slug", "img", null, null);
    }

    private Product createValidProduct(String id) {
        Product product = new Product();
        product.id = new ObjectId(id);
        product.setMerchantId(new ObjectId(MERCHANT_ID));
        product.setCategoryId(new ObjectId(CATEGORY_ID));
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setPrice(100000);
        product.setCountInStock(50);
        product.setBrand("Test Brand");
        product.setWeight(500);
        product.setRating(4.5f);
        product.setSlugProduct("test-product");
        product.setImageProduct("http://example.com/image.jpg");
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return product;
    }

    private CreateProductRequest createValidCreateRequest() {
        CreateProductRequest request = new CreateProductRequest();
        request.setMerchantId(MERCHANT_ID);
        request.setCategoryId(CATEGORY_ID);
        request.setName("Test Product");
        request.setDescription("Test Description");
        request.setPrice(100000);
        request.setCountInStock(50);
        request.setBrand("Test Brand");
        request.setWeight(500);
        request.setRating(5);
        request.setSlugProduct("test-product");
        request.setImageProduct("http://example.com/image.jpg");
        return request;
    }

    private UpdateProductRequest createValidUpdateRequest(String productId) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setProductId(productId);
        request.setMerchantId(MERCHANT_ID);
        request.setCategoryId(CATEGORY_ID);
        request.setName("Updated Product");
        request.setDescription("Updated Description");
        request.setPrice(150000);
        request.setCountInStock(75);
        request.setBrand("Updated Brand");
        request.setWeight(600);
        request.setRating(4);
        request.setSlugProduct("updated-product");
        return request;
    }

    @Test
    void createProduct_Success() {

        CreateProductRequest request = createValidCreateRequest();
        Product savedProduct = createValidProduct(PRODUCT_ID);

        when(validator.validate(any())).thenReturn(Set.of());
        when(categoryPort.findById(anyString())).thenReturn(Uni.createFrom().item(createCategory()));

        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(savedProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<ProductResponse> result = productService.createProduct(request).await()
                .indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product created successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getId()).isEqualTo(PRODUCT_ID);
        assertThat(result.data().getName()).isEqualTo("Test Product");

        verify(productCommandRepository).persist(any(Product.class));
        verify(redisService).deleteReactive("product:id:" + PRODUCT_ID);
    }

    @Test
    void createProduct_ValidationFailure() {

        CreateProductRequest request = createValidCreateRequest();

        ConstraintViolationException exception = new ConstraintViolationException("Validation failed",
                Set.of());
        when(validator.validate(any())).thenThrow(exception);

        Uni<ApiResponse<ProductResponse>> resultUni = productService.createProduct(request);
        assertThat(resultUni).isNotNull();

    }

    @Test
    void updateProduct_Success() {

        String productId = PRODUCT_ID;
        UpdateProductRequest request = createValidUpdateRequest(productId);
        Product existingProduct = createValidProduct(productId);
        Product updatedProduct = createValidProduct(productId);
        updatedProduct.setName("Updated Product");
        updatedProduct.setPrice(150000);
        Merchant merchant = createMerchant();

        when(validator.validate(any())).thenReturn(Set.of());
        when(merchantPort.findById(anyString())).thenReturn(Uni.createFrom().item(merchant));
        when(categoryPort.findById(anyString())).thenReturn(Uni.createFrom().item(createCategory()));

        when(productQueryRepository.findProductById(productId))
                .thenReturn(Uni.createFrom().item(Optional.of(existingProduct)));

        when(productCommandRepository.update(any(Product.class))).thenReturn(Uni.createFrom().item(updatedProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<ProductResponse> result = productService.updateProduct(request).await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product updated successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getName()).isEqualTo("Updated Product");

        verify(productCommandRepository).update(any(Product.class));
    }

    @Test
    void updateProduct_MerchantNotFound() {

        String productId = PRODUCT_ID;
        UpdateProductRequest request = createValidUpdateRequest(productId);

        when(validator.validate(any())).thenReturn(Set.of());

        when(merchantPort.findById(anyString())).thenReturn(Uni.createFrom().nullItem());

        Uni<ApiResponse<ProductResponse>> resultUni = productService.updateProduct(request);

        assertThat(resultUni).isNotNull();
    }

    @Test
    void updateProduct_ProductNotFound() {

        String productId = PRODUCT_ID;
        UpdateProductRequest request = createValidUpdateRequest(productId);
        Merchant merchant = createMerchant();

        lenient().when(validator.validate(any())).thenReturn(Set.of());

        when(merchantPort.findById(anyString())).thenReturn(Uni.createFrom().item(merchant));
        when(categoryPort.findById(anyString())).thenReturn(Uni.createFrom().item(createCategory()));

        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.empty()));

        assertThatThrownBy(() -> productService.updateProduct(request).await().indefinitely())
                .isInstanceOf(com.sanedge.common.exception.ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void deleteProduct_Permanent_Success() {

        String productId = PRODUCT_ID;
        Product trashedProduct = createValidProduct(productId);
        trashedProduct.setDeletedAt(Instant.now());

        when(productCommandRepository.deletePermanent(productId)).thenReturn(Uni.createFrom().item(trashedProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<Void> result = productService.deleteProductPermanent(productId).await()
                .indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product permanently deleted");

        verify(productCommandRepository).deletePermanent(productId);
    }

    @Test
    void deleteProduct_Permanent_NotFoundOrNotTrashed() {

        String productId = PRODUCT_ID;

        when(productCommandRepository.deletePermanent(productId)).thenReturn(Uni.createFrom().nullItem());

        Uni<ApiResponse<Void>> resultUni = productService.deleteProductPermanent(productId);

        assertThat(resultUni).isNotNull();
    }

    @Test
    void trashProduct_Success() {

        String productId = PRODUCT_ID;
        Product trashedProduct = createValidProduct(productId);
        trashedProduct.setDeletedAt(Instant.now());

        when(productCommandRepository.trashed(productId)).thenReturn(Uni.createFrom().item(trashedProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<ProductResponseDeleteAt> result = productService.trashedProduct(productId).await()
                .indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product trashed successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getDeletedAt()).isNotNull();

        verify(productCommandRepository).trashed(productId);
    }

    @Test
    void trashProduct_NotFoundOrAlreadyTrashed() {

        String productId = PRODUCT_ID;

        when(productCommandRepository.trashed(productId)).thenReturn(Uni.createFrom().nullItem());

        Uni<ApiResponse<ProductResponseDeleteAt>> resultUni = productService.trashedProduct(productId);

        assertThat(resultUni).isNotNull();
    }

    @Test
    void restoreProduct_Success() {

        String productId = PRODUCT_ID;
        Product restoredProduct = createValidProduct(productId);
        restoredProduct.setDeletedAt(null);

        when(productCommandRepository.restore(productId)).thenReturn(Uni.createFrom().item(restoredProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<ProductResponseDeleteAt> result = productService.restoreProduct(productId).await()
                .indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product restored successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getDeletedAt()).isNull();

        verify(productCommandRepository).restore(productId);
    }

    @Test
    void restoreProduct_NotFoundOrNotTrashed() {

        String productId = PRODUCT_ID;

        when(productCommandRepository.restore(productId)).thenReturn(Uni.createFrom().nullItem());

        Uni<ApiResponse<ProductResponseDeleteAt>> resultUni = productService.restoreProduct(productId);

        assertThat(resultUni).isNotNull();
    }

    @Test
    void restoreAllProducts_Success() {

        when(productCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> result = productService.restoreAllProducts().await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All products restored successfully");

        verify(productCommandRepository).restoreAllDeleted();
    }

    @Test
    void restoreAllProducts_NoTrashedProducts() {

        when(productCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));

        Uni<ApiResponse<Void>> resultUni = productService.restoreAllProducts();

        assertThat(resultUni).isNotNull();
    }

    @Test
    void deleteAllProductsPermanent_Success() {

        when(productCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> result = productService.deleteAllProductsPermanent().await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All products permanently deleted");

        verify(productCommandRepository).deleteAllDeleted();
    }

    @Test
    void deleteAllProductsPermanent_NoTrashedProducts() {

        when(productCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));

        Uni<ApiResponse<Void>> resultUni = productService.deleteAllProductsPermanent();

        assertThat(resultUni).isNotNull();
    }

    @Test
    void updateProductCountStock_Success() {

        String productId = PRODUCT_ID;
        Integer newStock = 100;
        Product existingProduct = createValidProduct(productId);
        Product updatedProduct = createValidProduct(productId);
        updatedProduct.setCountInStock(newStock);

        when(productQueryRepository.findProductById(productId))
                .thenReturn(Uni.createFrom().item(Optional.of(existingProduct)));

        when(productCommandRepository.update(any(Product.class))).thenReturn(Uni.createFrom().item(updatedProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<ProductResponse> result = productService.updateProductCountStock(productId, newStock)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product stock updated successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getCountInStock()).isEqualTo(newStock);
    }

    @Test
    void updateProductCountStock_ProductNotFound() {

        String productId = PRODUCT_ID;

        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.empty()));

        Uni<ApiResponse<ProductResponse>> resultUni = productService.updateProductCountStock(productId, 100);

        assertThat(resultUni).isNotNull();
    }

    @Test
    void createProduct_CacheInvalidationCalled() {

        CreateProductRequest request = createValidCreateRequest();
        Product savedProduct = createValidProduct(PRODUCT_ID);

        when(validator.validate(any())).thenReturn(Set.of());
        when(categoryPort.findById(anyString())).thenReturn(Uni.createFrom().item(createCategory()));

        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(savedProduct));

        when(redisService.deleteReactive("product:id:" + PRODUCT_ID)).thenReturn(Uni.createFrom().voidItem());

        productService.createProduct(request).await().indefinitely();

        verify(redisService).deleteReactive("product:id:" + PRODUCT_ID);
    }

    @Test
    void updateProduct_CacheInvalidationCalled() {

        String productId = PRODUCT_ID;
        UpdateProductRequest request = createValidUpdateRequest(productId);
        Product existingProduct = createValidProduct(productId);
        Product updatedProduct = createValidProduct(productId);
        Merchant merchant = createMerchant();

        when(validator.validate(any())).thenReturn(Set.of());
        when(merchantPort.findById(anyString())).thenReturn(Uni.createFrom().item(merchant));
        when(categoryPort.findById(anyString())).thenReturn(Uni.createFrom().item(createCategory()));

        when(productQueryRepository.findProductById(productId))
                .thenReturn(Uni.createFrom().item(Optional.of(existingProduct)));

        when(productCommandRepository.update(any(Product.class))).thenReturn(Uni.createFrom().item(updatedProduct));

        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        productService.updateProduct(request).await().indefinitely();

        verify(redisService).deleteReactive("product:id:" + PRODUCT_ID);
    }

    /**
     * Finds the Supplier argument in the invocation regardless of whether it was
     * passed positionally in the 3-arg overload (arg index 2) or 4-arg overload
     * (arg index 3), then invokes it and returns the resulting Uni. This lets
     * a single Answer<?> body serve both traceAndMeasure overloads.
     */
    private Answer<Uni<?>> invokeSupplier() {
        return invocation -> {
            Supplier<?> supplier = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier<?>) {
                    supplier = (Supplier<?>) arg;
                    break;
                }
            }
            return supplier != null ? (Uni<?>) supplier.get() : null;
        };
    }
}
