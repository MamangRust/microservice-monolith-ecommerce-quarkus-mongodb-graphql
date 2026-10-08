package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.CategoryDto.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.gateway.dto.CategoryDto.CategoriesYearlyTotalPriceResponse;
import com.sanedge.gateway.dto.CategoryDto.CategoryMonthPriceResponse;
import com.sanedge.gateway.dto.CategoryDto.CategoryYearPriceResponse;
import com.sanedge.gateway.dto.CategoryDto.CreateCategoryRequest;
import com.sanedge.gateway.dto.CategoryDto.CreateCategoryResponse;
import com.sanedge.gateway.dto.CategoryDto.FindAllCategoryResponse;
import com.sanedge.gateway.dto.CategoryDto.FindByIdCategoryResponse;
import com.sanedge.gateway.dto.CategoryDto.SimpleStatusMessageResponse;
import com.sanedge.gateway.dto.CategoryDto.UpdateCategoryRequest;
import com.sanedge.gateway.dto.CategoryDto.UpdateCategoryResponse;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public interface CategoryService {
    Uni<FindAllCategoryResponse> listCategories(int page, int size, String search);
    Uni<FindAllCategoryResponse> listActiveCategories(int page, int size, String search);
    Uni<FindAllCategoryResponse> listTrashedCategories(int page, int size, String search);
    Uni<FindByIdCategoryResponse> getCategory(String id);
    Uni<CreateCategoryResponse> createCategory(CreateCategoryRequest body);
    Uni<UpdateCategoryResponse> updateCategory(String id, UpdateCategoryRequest body);
    Uni<UpdateCategoryResponse> uploadCategory(String id, FileUpload file);
    Uni<FindByIdCategoryResponse> deleteCategory(String id);
    Uni<FindByIdCategoryResponse> restoreCategory(String id);
    Uni<SimpleStatusMessageResponse> deleteCategoryPermanent(String id);
    Uni<SimpleStatusMessageResponse> restoreAllCategories();
    Uni<SimpleStatusMessageResponse> deleteAllCategoriesPermanent();
    
    // Stats
    Uni<CategoriesMonthlyTotalPriceResponse> getCategoryMonthTotalPrice(int year, int month);
    Uni<CategoriesYearlyTotalPriceResponse> getCategoryYearTotalPrice(int year);
    Uni<CategoriesMonthlyTotalPriceResponse> getCategoryMonthTotalPriceByMerchant(String merchantId, int year, int month);
    Uni<CategoriesYearlyTotalPriceResponse> getCategoryYearTotalPriceByMerchant(String merchantId, int year);
    Uni<CategoriesMonthlyTotalPriceResponse> getCategoryMonthTotalPriceById(String categoryId, int year, int month);
    Uni<CategoriesYearlyTotalPriceResponse> getCategoryYearTotalPriceById(String categoryId, int year);
    Uni<CategoryMonthPriceResponse> getCategoryMonthPrice(int year);
    Uni<CategoryYearPriceResponse> getCategoryYearPrice(int year);
    Uni<CategoryMonthPriceResponse> getCategoryMonthPriceByMerchant(String merchantId, int year);
    Uni<CategoryYearPriceResponse> getCategoryYearPriceByMerchant(String merchantId, int year);
    Uni<CategoryMonthPriceResponse> getCategoryMonthPriceById(String categoryId, int year);
    Uni<CategoryYearPriceResponse> getCategoryYearPriceById(String categoryId, int year);
}
