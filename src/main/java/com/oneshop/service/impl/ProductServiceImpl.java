package com.oneshop.service.impl;

import com.oneshop.dto.request.BrandRequest;
import com.oneshop.dto.request.CategoryRequest;
import com.oneshop.dto.request.ProductRequest;
import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.dto.response.ProductImageResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.entity.Brand;
import com.oneshop.entity.Category;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductImage;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.CatalogMapper;
import com.oneshop.mapper.ProductMapper;
import com.oneshop.repository.BrandRepository;
import com.oneshop.repository.CategoryRepository;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.service.CloudinaryService;
import com.oneshop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final String PRODUCT_FOLDER = "products";
    private static final String BRAND_FOLDER = "brands";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CloudinaryService cloudinaryService;
    private final ProductMapper productMapper;
    private final CatalogMapper catalogMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductImageRepository productImageRepository,
                              CategoryRepository categoryRepository, BrandRepository brandRepository,
                              CloudinaryService cloudinaryService, ProductMapper productMapper,
                              CatalogMapper catalogMapper) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.cloudinaryService = cloudinaryService;
        this.productMapper = productMapper;
        this.catalogMapper = catalogMapper;
    }

    // ------------------------------------------------------------------ Client

    @Override
    public Page<ProductResponse> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = productRepository.searchVisible(criteria.likePattern(), criteria.categoryId(),
                criteria.brandId(), pageable);
        Map<Long, String> primaryImages = getPrimaryImageUrls(page.getContent().stream().map(Product::getId).toList());
        return page.map(product -> productMapper.toResponse(product, primaryImages.get(product.getId())));
    }

    @Override
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        return searchProducts(ProductSearchCriteria.none(), pageable);
    }

    @Override
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findVisibleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm #" + id));
        return productMapper.toResponse(product, getPrimaryImageUrls(List.of(id)).get(id));
    }

    @Override
    public List<ProductImageResponse> getProductImages(Long productId) {
        return productImageRepository.findByProductIdOrderByPrimaryDescSortOrderAscIdAsc(productId).stream()
                .map(catalogMapper::toResponse)
                .toList();
    }

    @Override
    public Map<Long, String> getPrimaryImageUrls(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productImageRepository.findByProductIdInAndPrimaryTrue(productIds).stream()
                .collect(Collectors.toMap(image -> image.getProduct().getId(), ProductImage::getImageUrl,
                        (first, second) -> first));
    }

    @Override
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByStatusOrderByName(VisibilityStatus.ACTIVE).stream()
                .map(catalogMapper::toResponse).toList();
    }

    @Override
    public List<BrandResponse> getActiveBrands() {
        return brandRepository.findByStatusOrderByName(VisibilityStatus.ACTIVE).stream()
                .map(catalogMapper::toResponse).toList();
    }

    // ------------------------------------------------------------------ Admin: Category

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderByName().stream().map(catalogMapper::toResponse).toList();
    }

    @Override
    public CategoryResponse getCategory(Long id) {
        return catalogMapper.toResponse(category(id));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByName(name)) {
            throw new BadRequestException("name", "Tên danh mục đã tồn tại");
        }
        Category category = new Category();
        apply(category, name, request);
        return catalogMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = category(id);
        String name = request.getName().trim();
        if (categoryRepository.existsByNameAndIdNot(name, id)) {
            throw new BadRequestException("name", "Tên danh mục đã tồn tại");
        }
        apply(category, name, request);
        return catalogMapper.toResponse(category);
    }

    private static void apply(Category category, String name, CategoryRequest request) {
        category.setName(name);
        category.setDescription(trimToNull(request.getDescription()));
        category.setStatus(request.getStatus());
    }

    // ------------------------------------------------------------------ Admin: Brand

    @Override
    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAllByOrderByName().stream().map(catalogMapper::toResponse).toList();
    }

    @Override
    public BrandResponse getBrand(Long id) {
        return catalogMapper.toResponse(brand(id));
    }

    @Override
    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String name = request.getName().trim();
        if (brandRepository.existsByName(name)) {
            throw new BadRequestException("name", "Tên thương hiệu đã tồn tại");
        }
        Brand brand = new Brand();
        apply(brand, name, request);
        return catalogMapper.toResponse(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public BrandResponse updateBrand(Long id, BrandRequest request) {
        Brand brand = brand(id);
        String name = request.getName().trim();
        if (brandRepository.existsByNameAndIdNot(name, id)) {
            throw new BadRequestException("name", "Tên thương hiệu đã tồn tại");
        }
        apply(brand, name, request);
        return catalogMapper.toResponse(brand);
    }

    private static void apply(Brand brand, String name, BrandRequest request) {
        brand.setName(name);
        brand.setDescription(trimToNull(request.getDescription()));
        brand.setStatus(request.getStatus());
    }

    @Override
    @Transactional
    public BrandResponse uploadBrandLogo(Long id, MultipartFile file) {
        Brand brand = brand(id);
        String previousPublicId = brand.getLogoPublicId();

        ImageUploadResult uploaded = cloudinaryService.uploadImage(file, BRAND_FOLDER);
        try {
            brand.setLogoUrl(uploaded.url());
            brand.setLogoPublicId(uploaded.publicId());
            brandRepository.saveAndFlush(brand);
        } catch (RuntimeException ex) {
            deleteQuietly(uploaded.publicId());
            throw ex;
        }
        // the new logo is stored; a leftover old file must not undo that
        deleteQuietly(previousPublicId);
        return catalogMapper.toResponse(brand);
    }

    @Override
    @Transactional
    public BrandResponse removeBrandLogo(Long id) {
        Brand brand = brand(id);
        String publicId = brand.getLogoPublicId();
        brand.setLogoUrl(null);
        brand.setLogoPublicId(null);
        brandRepository.saveAndFlush(brand);
        cloudinaryService.deleteImage(publicId);
        return catalogMapper.toResponse(brand);
    }

    // ------------------------------------------------------------------ Admin: Product

    @Override
    public Page<AdminProductResponse> searchAllProducts(String keyword, Pageable pageable) {
        return productRepository.searchAll(ProductSearchCriteria.ofKeyword(keyword).likePattern(), pageable)
                .map(catalogMapper::toAdminResponse);
    }

    @Override
    public List<AdminProductResponse> getAssignableProducts() {
        return productRepository.findByStatusNotOrderByName(ProductStatus.INACTIVE).stream()
                .map(catalogMapper::toAdminResponse).toList();
    }

    @Override
    public AdminProductResponse getProductForAdmin(Long id) {
        return catalogMapper.toAdminResponse(product(id));
    }

    @Override
    @Transactional
    public AdminProductResponse createProduct(ProductRequest request) {
        String sku = normalizeSku(request.getSku());
        if (productRepository.existsBySku(sku)) {
            throw new BadRequestException("sku", "Mã SKU đã tồn tại");
        }
        Product product = new Product();
        apply(product, sku, request);
        return catalogMapper.toAdminResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public AdminProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = product(id);
        String sku = normalizeSku(request.getSku());
        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new BadRequestException("sku", "Mã SKU đã tồn tại");
        }
        apply(product, sku, request);
        return catalogMapper.toAdminResponse(product);
    }

    private void apply(Product product, String sku, ProductRequest request) {
        product.setSku(sku);
        product.setName(request.getName().trim());
        product.setDescription(trimToNull(request.getDescription()));
        product.setStatus(request.getStatus());
        product.setCategory(categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BadRequestException("categoryId", "Danh mục không tồn tại")));
        product.setBrand(brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new BadRequestException("brandId", "Thương hiệu không tồn tại")));
    }

    /** SKU codes are compared as typed by SQL Server (case-insensitive collation); only surrounding blanks go. */
    private static String normalizeSku(String sku) {
        return sku.trim();
    }

    // ------------------------------------------------------------------ Admin: Product images

    @Override
    @Transactional
    public ProductImageResponse addProductImage(Long productId, MultipartFile file) {
        Product product = product(productId);
        List<ProductImage> existing = productImageRepository.findByProductIdOrderBySortOrderAsc(productId);

        ImageUploadResult uploaded = cloudinaryService.uploadImage(file, PRODUCT_FOLDER);
        try {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(uploaded.url());
            image.setPublicId(uploaded.publicId());
            image.setPrimary(existing.stream().noneMatch(ProductImage::isPrimary));
            image.setSortOrder(existing.stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1);
            return catalogMapper.toResponse(productImageRepository.saveAndFlush(image));
        } catch (RuntimeException ex) {
            // do not leave a file on Cloudinary that the database does not know about
            deleteQuietly(uploaded.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void setPrimaryProductImage(Long productId, Long imageId) {
        ProductImage chosen = image(productId, imageId);
        productImageRepository.findByProductIdOrderBySortOrderAsc(productId)
                .forEach(image -> image.setPrimary(image.getId().equals(chosen.getId())));
    }

    @Override
    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        ProductImage image = image(productId, imageId);
        String publicId = image.getPublicId();
        boolean wasPrimary = image.isPrimary();

        productImageRepository.delete(image);
        productImageRepository.flush();
        if (wasPrimary) {
            productImageRepository.findByProductIdOrderBySortOrderAsc(productId).stream().findFirst()
                    .ifPresent(next -> next.setPrimary(true));
        }
        // last step: if Cloudinary refuses, the transaction rolls back and the row is kept with its file
        cloudinaryService.deleteImage(publicId);
    }

    // ------------------------------------------------------------------ helpers

    private Category category(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục #" + id));
    }

    private Brand brand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu #" + id));
    }

    private Product product(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm #" + id));
    }

    private ProductImage image(Long productId, Long imageId) {
        return productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh #" + imageId));
    }

    private void deleteQuietly(String publicId) {
        try {
            cloudinaryService.deleteImage(publicId);
        } catch (RuntimeException ex) {
            log.warn("Could not delete Cloudinary image {}: {}", publicId, ex.getMessage());
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
