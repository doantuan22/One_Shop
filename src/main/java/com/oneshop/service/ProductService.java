package com.oneshop.service;

import com.oneshop.dto.request.BrandRequest;
import com.oneshop.dto.request.CategoryRequest;
import com.oneshop.dto.request.ProductRequest;
import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.ProductImageResponse;
import com.oneshop.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Catalog data shared by the whole chain: Category, Brand, Product (one sellable SKU, BR-01) and their images.
 *
 * <p>Nothing here knows a price or a quantity: those belong to a Store and live in {@link StoreProductService}.
 * Nothing is ever hard-deleted; a status hides it instead (BR-17). Images are stored on Cloudinary and only their URL
 * and public id are kept in the database.
 */
public interface ProductService {

    // ---- Client: only what a customer may see (ACTIVE product in an ACTIVE Category and Brand) ----

    /** Chain-wide search by keyword (name or SKU), Category and Brand. */
    Page<ProductResponse> searchProducts(ProductSearchCriteria criteria, Pageable pageable);

    Page<ProductResponse> getActiveProducts(Pageable pageable);

    /** @throws com.oneshop.exception.ResourceNotFoundException if the product does not exist or is not visible */
    ProductResponse getProduct(Long id);

    /** Images of a product, primary first. */
    List<ProductImageResponse> getProductImages(Long productId);

    /** Primary image URL of each product that has one: a single query for a whole catalog page. */
    Map<Long, String> getPrimaryImageUrls(Collection<Long> productIds);

    List<CategoryResponse> getActiveCategories();

    List<BrandResponse> getActiveBrands();

    // ---- Admin: Category ----

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategory(Long id);

    /** @throws com.oneshop.exception.BadRequestException if the name is already used */
    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long id, CategoryRequest request);

    // ---- Admin: Brand ----

    List<BrandResponse> getAllBrands();

    BrandResponse getBrand(Long id);

    /** @throws com.oneshop.exception.BadRequestException if the name is already used */
    BrandResponse createBrand(BrandRequest request);

    BrandResponse updateBrand(Long id, BrandRequest request);

    /** Uploads the logo to Cloudinary, stores its URL/public id and removes the previous logo from Cloudinary. */
    BrandResponse uploadBrandLogo(Long id, MultipartFile file);

    BrandResponse removeBrandLogo(Long id);

    // ---- Admin: Product (every status) ----

    Page<AdminProductResponse> searchAllProducts(String keyword, Pageable pageable);

    /** Products that can be put on sale at a Store (everything except INACTIVE), for the StoreProduct form. */
    List<AdminProductResponse> getAssignableProducts();

    AdminProductResponse getProductForAdmin(Long id);

    /** @throws com.oneshop.exception.BadRequestException if the SKU is already used or Category/Brand is unknown */
    AdminProductResponse createProduct(ProductRequest request);

    AdminProductResponse updateProduct(Long id, ProductRequest request);

    // ---- Admin: Product images ----

    /** Uploads to Cloudinary and stores URL/public id. The first image of a product becomes its primary image. */
    ProductImageResponse addProductImage(Long productId, MultipartFile file);

    void setPrimaryProductImage(Long productId, Long imageId);

    /** Removes the image row and the file on Cloudinary; another image becomes primary if needed. */
    void deleteProductImage(Long productId, Long imageId);
}
