package com.oneshop.service.impl;

import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.request.StoreProductRequest;
import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.ProductResponse;
import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.entity.Product;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.ProductMapper;
import com.oneshop.mapper.StoreProductMapper;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.InventoryService;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StoreProductServiceImpl implements StoreProductService {

    private static final String OPENING_STOCK_NOTE = "Tồn kho ban đầu khi mở bán tại chi nhánh";
    private static final String ADMIN_ADJUST_NOTE = "Quản trị viên điều chỉnh tồn kho";
    private static final String DUPLICATE_MESSAGE = "Sản phẩm này đã được gắn với chi nhánh đã chọn";

    private final StoreProductRepository storeProductRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final StoreProductMapper storeProductMapper;
    private final ProductMapper productMapper;

    public StoreProductServiceImpl(StoreProductRepository storeProductRepository, StoreRepository storeRepository,
                                   ProductRepository productRepository, ProductService productService,
                                   InventoryService inventoryService, StoreProductMapper storeProductMapper,
                                   ProductMapper productMapper) {
        this.storeProductRepository = storeProductRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.storeProductMapper = storeProductMapper;
        this.productMapper = productMapper;
    }

    // ------------------------------------------------------------------ Client

    @Override
    public Page<CatalogItemResponse> getChainCatalog(ProductSearchCriteria criteria, Pageable pageable) {
        Page<ProductResponse> products = productService.searchProducts(criteria, pageable);
        List<Long> productIds = products.getContent().stream().map(ProductResponse::id).toList();

        // one query for the availability of the whole page
        Map<Long, List<StoreAvailabilityResponse>> storesByProduct = productIds.isEmpty()
                ? Map.of()
                : storeProductRepository.findSellableByProductIds(productIds).stream()
                        .collect(Collectors.groupingBy(sp -> sp.getProduct().getId(),
                                Collectors.mapping(storeProductMapper::toAvailability, Collectors.toList())));

        return products.map(product ->
                new CatalogItemResponse(product, null, storesByProduct.getOrDefault(product.id(), List.of())));
    }

    @Override
    public Page<CatalogItemResponse> getStoreCatalog(Long storeId, ProductSearchCriteria criteria, Pageable pageable) {
        Page<StoreProduct> page = storeProductRepository.searchSellableInStore(storeId, criteria.likePattern(),
                criteria.categoryId(), criteria.brandId(), pageable);
        Map<Long, String> primaryImages = productService.getPrimaryImageUrls(
                page.getContent().stream().map(sp -> sp.getProduct().getId()).toList());

        return page.map(sp -> {
            Product product = sp.getProduct();
            return new CatalogItemResponse(productMapper.toResponse(product, primaryImages.get(product.getId())),
                    storeProductMapper.toAvailability(sp), List.of());
        });
    }

    @Override
    public ProductDetailResponse getProductDetail(Long productId, Long selectedStoreId) {
        ProductResponse product = productService.getProduct(productId);
        List<StoreAvailabilityResponse> sellable = storeProductRepository.findSellableByProductIds(List.of(productId))
                .stream().map(storeProductMapper::toAvailability).toList();

        StoreAvailabilityResponse atSelectedStore = sellable.stream()
                .filter(availability -> availability.storeId().equals(selectedStoreId))
                .findFirst().orElse(null);
        List<StoreAvailabilityResponse> otherStores = sellable.stream()
                .filter(availability -> !availability.storeId().equals(selectedStoreId))
                .toList();
        return new ProductDetailResponse(product, productService.getProductImages(productId), atSelectedStore,
                otherStores);
    }

    // ------------------------------------------------------------------ Admin

    @Override
    public Page<StoreProductStockResponse> searchStoreProducts(Long storeId, Long productId, Pageable pageable) {
        return storeProductRepository.searchForAdmin(storeId, productId, pageable).map(storeProductMapper::toStockView);
    }

    @Override
    public StoreProductStockResponse getStoreProduct(Long id) {
        return storeProductMapper.toStockView(storeProduct(id));
    }

    @Override
    @Transactional
    public StoreProductStockResponse createStoreProduct(StoreProductRequest request, String adminEmail) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new BadRequestException("storeId", "Chi nhánh không tồn tại"));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BadRequestException("productId", "Sản phẩm không tồn tại"));
        if (storeProductRepository.existsByStoreIdAndProductId(store.getId(), product.getId())) {
            throw new BadRequestException("productId", DUPLICATE_MESSAGE);
        }

        StoreProduct storeProduct = new StoreProduct();
        storeProduct.setStore(store);
        storeProduct.setProduct(product);
        storeProduct.setPrice(request.getPrice());
        storeProduct.setStatus(request.getStatus());
        // starts empty: the opening quantity is booked below as an InventoryMovement, like every later change
        storeProduct.setQuantity(0);
        try {
            storeProductRepository.saveAndFlush(storeProduct);
        } catch (DataIntegrityViolationException ex) {
            // UQ_store_products_store_product: another request created the same pair in the meantime
            throw new BadRequestException("productId", DUPLICATE_MESSAGE);
        }
        inventoryService.adjustStock(storeProduct.getId(), request.getQuantity(), adminEmail,
                noteOr(request.getNote(), OPENING_STOCK_NOTE));
        return storeProductMapper.toStockView(storeProduct);
    }

    @Override
    @Transactional
    public StoreProductStockResponse updateStoreProduct(Long id, StoreProductRequest request, String adminEmail) {
        StoreProduct storeProduct = storeProduct(id);
        inventoryService.adjustStock(id, request.getQuantity(), adminEmail,
                noteOr(request.getNote(), ADMIN_ADJUST_NOTE));
        storeProduct.setPrice(request.getPrice());
        storeProduct.setStatus(request.getStatus());
        return storeProductMapper.toStockView(storeProduct);
    }

    private StoreProduct storeProduct(Long id) {
        return storeProductRepository.findWithStoreAndProductById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm tại chi nhánh #" + id));
    }

    private static String noteOr(String note, String fallback) {
        return StringUtils.hasText(note) ? note : fallback;
    }
}
