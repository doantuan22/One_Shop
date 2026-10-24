package com.oneshop.service;

import com.oneshop.dto.request.ProductRequest;
import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.ProductCardResponse;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.entity.Brand;
import com.oneshop.entity.Category;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.mapper.CatalogMapper;
import com.oneshop.mapper.ProductMapper;
import com.oneshop.repository.BrandRepository;
import com.oneshop.repository.CategoryRepository;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceV3Test {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    private ProductMapper productMapper;
    private CatalogMapper catalogMapper;
    private ProductService productService;

    private Category activeCategory;
    private Brand activeBrand;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper();
        catalogMapper = new CatalogMapper();
        productService = new ProductServiceImpl(
                productRepository,
                productImageRepository,
                categoryRepository,
                brandRepository,
                com.oneshop.UnitImageTransactions.coordinator(cloudinaryService),
                productMapper,
                catalogMapper
        );

        activeCategory = new Category();
        ReflectionTestUtils.setField(activeCategory, "id", 1L);
        activeCategory.setName("Chăm sóc da");
        activeCategory.setStatus(VisibilityStatus.ACTIVE);

        activeBrand = new Brand();
        ReflectionTestUtils.setField(activeBrand, "id", 2L);
        activeBrand.setName("Cocoon");
        activeBrand.setStatus(VisibilityStatus.ACTIVE);
    }

    @Test
    @DisplayName("ProductMapper maps unified price and originalPrice from Product entity")
    void productMapper_mapsPriceAndOriginalPrice() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setSku("COCOON-SERUM-30");
        product.setName("Tinh chất bí đao Cocoon 30ml");
        product.setDescription("Serum giảm mụn");
        product.setCategory(activeCategory);
        product.setBrand(activeBrand);
        product.setPrice(new BigDecimal("129000"));
        product.setOriginalPrice(new BigDecimal("145000"));

        ProductResponse response = productMapper.toResponse(product, "https://example.com/img.jpg");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("COCOON-SERUM-30");
        assertThat(response.price()).isEqualByComparingTo("129000");
        assertThat(response.originalPrice()).isEqualByComparingTo("145000");
        assertThat(response.categoryName()).isEqualTo("Chăm sóc da");
        assertThat(response.brandName()).isEqualTo("Cocoon");
    }

    @Test
    @DisplayName("Admin createProduct saves unified price and originalPrice to Product")
    void createProduct_savesPriceAndOriginalPrice() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(activeCategory));
        when(brandRepository.findById(2L)).thenReturn(Optional.of(activeBrand));
        when(productRepository.existsBySku("NEW-SKU-01")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        ProductRequest request = new ProductRequest();
        request.setCategoryId(1L);
        request.setBrandId(2L);
        request.setSku("NEW-SKU-01");
        request.setName("Sản phẩm mới");
        request.setDescription("Mô tả");
        request.setStatus(ProductStatus.ACTIVE);
        request.setPrice(new BigDecimal("250000"));
        request.setOriginalPrice(new BigDecimal("300000"));

        AdminProductResponse response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.price()).isEqualByComparingTo("250000");
        assertThat(response.originalPrice()).isEqualByComparingTo("300000");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product persisted = captor.getValue();
        assertThat(persisted.getPrice()).isEqualByComparingTo("250000");
        assertThat(persisted.getOriginalPrice()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("Admin updateProduct updates unified price and originalPrice")
    void updateProduct_updatesPriceAndOriginalPrice() {
        Product existing = new Product();
        ReflectionTestUtils.setField(existing, "id", 5L);
        existing.setSku("EXISTING-SKU");
        existing.setName("Tên cũ");
        existing.setCategory(activeCategory);
        existing.setBrand(activeBrand);
        existing.setPrice(new BigDecimal("100000"));

        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("EXISTING-SKU", 5L)).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(activeCategory));
        when(brandRepository.findById(2L)).thenReturn(Optional.of(activeBrand));

        ProductRequest request = new ProductRequest();
        request.setCategoryId(1L);
        request.setBrandId(2L);
        request.setSku("EXISTING-SKU");
        request.setName("Tên mới");
        request.setStatus(ProductStatus.ACTIVE);
        request.setPrice(new BigDecimal("180000"));
        request.setOriginalPrice(new BigDecimal("220000"));

        AdminProductResponse response = productService.updateProduct(5L, request);

        assertThat(response.price()).isEqualByComparingTo("180000");
        assertThat(response.originalPrice()).isEqualByComparingTo("220000");
        assertThat(existing.getPrice()).isEqualByComparingTo("180000");
        assertThat(existing.getOriginalPrice()).isEqualByComparingTo("220000");
    }

    @Test
    @DisplayName("CatalogItemResponse uses Product.price as single unified online price")
    void catalogItemResponse_usesProductPrice() {
        ProductResponse product = new ProductResponse(1L, "SKU-01", "Serum", "Desc", "Cat", "Brand",
                "img.jpg", new BigDecimal("129000"), new BigDecimal("150000"));

        StoreAvailabilityResponse store1 = new StoreAvailabilityResponse(10L, 1L, "Chi nhánh 1",
                new BigDecimal("129000"), true);
        StoreAvailabilityResponse store2 = new StoreAvailabilityResponse(20L, 2L, "Chi nhánh 2",
                new BigDecimal("129000"), false);

        CatalogItemResponse item = new CatalogItemResponse(product, null, List.of(store1, store2));

        assertThat(item.lowestPrice()).isEqualByComparingTo("129000");
        assertThat(item.samePriceEverywhere()).isTrue();
    }

    @Test
    @DisplayName("ProductCardResponse sets unified price and fromPrice is always false in V3")
    void productCardResponse_unifiedPriceAndNoFromPrice() {
        ProductResponse product = new ProductResponse(1L, "SKU-01", "Serum", "Desc", "Cat", "Brand",
                "img.jpg", new BigDecimal("129000"), new BigDecimal("150000"));

        StoreAvailabilityResponse store1 = new StoreAvailabilityResponse(10L, 1L, "Chi nhánh 1",
                new BigDecimal("129000"), true);
        StoreAvailabilityResponse store2 = new StoreAvailabilityResponse(20L, 2L, "Chi nhánh 2",
                new BigDecimal("129000"), true);

        CatalogItemResponse item = new CatalogItemResponse(product, null, List.of(store1, store2));

        ProductCardResponse card = ProductCardResponse.from(item);

        assertThat(card.productId()).isEqualTo(1L);
        assertThat(card.price()).isEqualByComparingTo("129000");
        assertThat(card.fromPrice()).isFalse();
        assertThat(card.inStock()).isTrue();
        assertThat(card.inStockStoreCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("A legacy StoreProduct price cannot override or supply the chain-wide online price")
    void catalogNeverFallsBackToLegacyStorePrice() {
        ProductResponse productWithoutPrice = new ProductResponse(1L, "SKU-01", "Serum", "Desc", "Cat", "Brand",
                "img.jpg", null, null);
        StoreAvailabilityResponse legacyStorePrice = new StoreAvailabilityResponse(10L, 1L, "Chi nhánh 1",
                new BigDecimal("1"), true);
        CatalogItemResponse item = new CatalogItemResponse(productWithoutPrice, null, List.of(legacyStorePrice));

        assertThat(item.lowestPrice()).isNull();
        assertThat(ProductCardResponse.from(item).price()).isNull();
        assertThat(ProductCardResponse.from(item).inStock()).isTrue();
    }

    @Test
    @DisplayName("availability and admin stock views use Product.price instead of a legacy StoreProduct price")
    void storeProductMapperUsesOnlyProductPrice() {
        com.oneshop.entity.Store store = new com.oneshop.entity.Store();
        store.setName("OneShop Thủ Đức");
        Product product = new Product();
        product.setSku("SKU-01");
        product.setName("Serum");
        product.setPrice(new BigDecimal("129000"));
        com.oneshop.entity.StoreProduct storeProduct = new com.oneshop.entity.StoreProduct();
        storeProduct.setStore(store);
        storeProduct.setProduct(product);
        storeProduct.setPrice(new BigDecimal("1"));
        storeProduct.setQuantity(3);

        var mapper = new com.oneshop.mapper.StoreProductMapper();

        assertThat(mapper.toAvailability(storeProduct).price()).isEqualByComparingTo("129000");
        assertThat(mapper.toStockView(storeProduct).price()).isEqualByComparingTo("129000");
        product.setPrice(null);
        assertThat(mapper.toAvailability(storeProduct).price()).isNull();
        assertThat(mapper.toStockView(storeProduct).price()).isNull();
    }

    @Test
    @DisplayName("ProductDetailResponse computes inStock across whole chain")
    void productDetailResponse_computesInStockAcrossStores() {
        ProductResponse product = new ProductResponse(1L, "SKU-01", "Serum", "Desc", "Cat", "Brand",
                "img.jpg", new BigDecimal("129000"), null);

        StoreAvailabilityResponse outOfStockStore = new StoreAvailabilityResponse(10L, 1L, "Chi nhánh 1",
                new BigDecimal("129000"), false);
        StoreAvailabilityResponse inStockStore = new StoreAvailabilityResponse(20L, 2L, "Chi nhánh 2",
                new BigDecimal("129000"), true);

        // When no store selected, atSelectedStore is null and otherStores has all stores
        ProductDetailResponse chainDetail = new ProductDetailResponse(product, List.of(), null,
                List.of(outOfStockStore, inStockStore));

        assertThat(chainDetail.inStock()).isTrue();
        assertThat(chainDetail.inStockStoreCount()).isEqualTo(1);

        // When all stores out of stock
        ProductDetailResponse allOutOfStock = new ProductDetailResponse(product, List.of(), null,
                List.of(outOfStockStore));
        assertThat(allOutOfStock.inStock()).isFalse();
        assertThat(allOutOfStock.inStockStoreCount()).isEqualTo(0);
    }
}
