package com.oneshop;

import com.oneshop.dto.request.CategoryRequest;
import com.oneshop.dto.request.ProductRequest;
import com.oneshop.dto.request.ProductSearchCriteria;
import com.oneshop.dto.request.StoreProductRequest;
import com.oneshop.dto.request.StoreRequest;
import com.oneshop.dto.response.AdminProductResponse;
import com.oneshop.dto.response.BrandResponse;
import com.oneshop.dto.response.CatalogItemResponse;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.dto.response.ProductDetailResponse;
import com.oneshop.dto.response.ProductImageResponse;
import com.oneshop.dto.response.StoreAvailabilityResponse;
import com.oneshop.dto.response.StoreProductStockResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Brand;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.InventoryMovement;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.entity.ProductImage;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.BrandRepository;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.CloudinaryService;
import com.oneshop.service.ProductService;
import com.oneshop.service.StoreProductService;
import com.oneshop.service.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 6 on the real SQL Server with the canonical fixture {@code database/02_seed.sql}: catalog of the whole chain
 * and of one Store, Product Detail, Store selection rules and the Admin operations (TC-01, TC-02, TC-03, TC-17, TC-18).
 *
 * <p>Skipped without a configured database. Every test runs in a transaction that is rolled back, so the database is
 * left exactly as it was. Only Cloudinary is replaced by a mock: no file leaves the machine.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Transactional
class CatalogDatabaseIntegrationTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 50);
    private static final String ADMIN = "admin@example.com";

    // seed data
    private static final String SERUM = "SIMPLE-PURIFY-150";      // sold at 4 ACTIVE Stores, out of stock at Quận 7
    private static final String TONER = "CERAVE-HYDRA-236";       // StoreProduct INACTIVE at Quận 10
    private static final String FOAM = "SIMPLE-MICELLAR-150";       // not sold at Gò Vấp
    private static final String RETIRED = "VASELINE-LIP-ROSY-7";      // Product INACTIVE
    private static final String THU_DUC = "OS-THUDUC";
    private static final String GO_VAP = "OS-GOVAP";
    private static final String QUAN_7 = "OS-QUAN7";
    private static final String QUAN_10 = "OS-QUAN10";
    private static final String HAI_CHAU = "OS-HAICHAU";        // Store INACTIVE

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private List<String> visibleSkus(Long store) {return jdbc.queryForList("select p.sku from dbo.products p join dbo.categories c on c.category_id=p.category_id join dbo.brands b on b.brand_id=p.brand_id where p.status='ACTIVE' and c.status='ACTIVE' and b.status='ACTIVE'"+(store==null?"":" and exists(select 1 from dbo.store_products sp where sp.product_id=p.product_id and sp.store_id="+store+" and sp.status='ACTIVE')"),String.class);}

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private StoreProductService storeProductService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreProductRepository storeProductRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    private Long productId(String sku) {
        return productRepository.findBySku(sku).orElseThrow().getId();
    }

    private Long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    private static CatalogItemResponse item(List<CatalogItemResponse> items, String sku) {
        return items.stream().filter(i -> i.product().sku().equals(sku)).findFirst().orElseThrow();
    }

    private static Map<String, StoreAvailabilityResponse> byStore(List<StoreAvailabilityResponse> stores) {
        return stores.stream().collect(Collectors.toMap(StoreAvailabilityResponse::storeName, Function.identity()));
    }

    // ================================================================= TC-01: whole chain

    @Test
    void tc01_chainCatalogShowsEachSkuOnceWithPriceAndAvailabilityPerStore() {
        var catalog=storeProductService.getChainCatalog(ProductSearchCriteria.none(),FIRST_PAGE).getContent();assertThat(catalog).extracting(i->i.product().sku()).containsExactlyInAnyOrderElementsOf(visibleSkus(null)).doesNotHaveDuplicates();for(var i:catalog){assertThat(i.atSelectedStore()).isNull();assertThat(i.lowestPrice()).isEqualByComparingTo(productRepository.findById(i.product().id()).orElseThrow().getPrice());for(var a:i.stores())assertThat(a.price()).isEqualByComparingTo(i.lowestPrice());assertThat(i.samePriceEverywhere()).isTrue();}
    }

    @Test
    void chainCatalogLeavesOutWhatIsNotOnSale() {
        var catalog=storeProductService.getChainCatalog(ProductSearchCriteria.none(),FIRST_PAGE).getContent();assertThat(catalog).extracting(i->i.product().sku()).doesNotContain(RETIRED);assertThat(catalog).flatExtracting(CatalogItemResponse::stores).extracting(StoreAvailabilityResponse::storeName).doesNotContain("OneShop Hải Châu");var stock=storeProductRepository.findByStoreIdAndProductId(storeId(THU_DUC),productId(SERUM)).orElseThrow();stock.setStatus(ActiveStatus.INACTIVE);entityManager.flush();assertThat(storeProductService.getProductDetail(productId(SERUM),null).otherStores()).extracting(StoreAvailabilityResponse::storeId).doesNotContain(storeId(THU_DUC));
    }

    @Test
    void chainCatalogSearchesByNameAndSkuAndFiltersByCategoryAndBrand() {
        assertThat(skus(new ProductSearchCriteria("purifying gel wash",null,null))).containsExactly(SERUM);assertThat(skus(new ProductSearchCriteria("cerave-hydra",null,null))).containsExactly(TONER);assertThat(skus(new ProductSearchCriteria(" MATTE INK ",null,null))).containsExactlyInAnyOrder("MAYB-MATTEINK-15","MAYB-MATTEINK-20","MAYB-MATTEINK-65");for(String text:List.of("không-có-sản-phẩm-này","%","_"))assertThat(skus(new ProductSearchCriteria(text,null,null))).isEmpty();var product=productRepository.findBySku(SERUM).orElseThrow();assertThat(skus(new ProductSearchCriteria(null,null,product.getBrand().getId()))).containsExactlyInAnyOrderElementsOf(jdbc.queryForList("select sku from dbo.products where status='ACTIVE' and brand_id=?",String.class,product.getBrand().getId()));assertThat(skus(new ProductSearchCriteria("purifying gel wash",product.getCategory().getId(),product.getBrand().getId()))).containsExactly(SERUM);
    }

    private List<String> skus(ProductSearchCriteria criteria) {
        return storeProductService.getChainCatalog(criteria, FIRST_PAGE).getContent().stream()
                .map(i -> i.product().sku()).toList();
    }

    @Test
    void catalogIsPaged() {
        var firstPage = storeProductService.getChainCatalog(ProductSearchCriteria.none(), PageRequest.of(0, 2));
        var secondPage = storeProductService.getChainCatalog(ProductSearchCriteria.none(), PageRequest.of(1, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(visibleSkus(null).size());
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2)
                .extracting(i -> i.product().sku())
                .doesNotContainAnyElementsOf(firstPage.getContent().stream().map(i -> i.product().sku()).toList());
    }

    // ================================================================= TC-02: one Store

    @Test
    void tc02_storeCatalogOnlyReflectsTheStoreProductsOfThatStore() {
        for(String code:List.of(THU_DUC,GO_VAP,QUAN_7,QUAN_10)){long store=storeId(code);var rows=storeProductService.getStoreCatalog(store,ProductSearchCriteria.none(),FIRST_PAGE).getContent();assertThat(rows).extracting(i->i.product().sku()).containsExactlyInAnyOrderElementsOf(visibleSkus(store));for(var row:rows){assertThat(row.stores()).isEmpty();assertThat(row.atSelectedStore().storeId()).isEqualTo(store);assertThat(row.atSelectedStore().price()).isEqualByComparingTo(productRepository.findById(row.product().id()).orElseThrow().getPrice());}}
    }

    @Test
    void storeCatalogKeepsTheStoreContextWhenSearchingAndFiltering() {
        long store=storeId(QUAN_10);var rows=storeProductService.getStoreCatalog(store,ProductSearchCriteria.none(),FIRST_PAGE).getContent();assertThat(rows).extracting(i->i.product().sku()).containsExactlyInAnyOrderElementsOf(visibleSkus(store));for(var r:rows)assertThat(storeProductService.getStoreCatalog(store,new ProductSearchCriteria(r.product().sku(),null,null),FIRST_PAGE).getContent()).extracting(i->i.product().sku()).containsExactly(r.product().sku());assertThat(storeProductService.getStoreCatalog(store,new ProductSearchCriteria("INNI-TONER",null,null),FIRST_PAGE)).isEmpty();
    }


    // ================================================================= home page "discover products"

    @Test
    void randomPickOfAStoreIsInStockThereWithoutDuplicatesAndLimited() {
        for(long seed=1;seed<=10;seed++){var rows=storeProductService.getRandomSellable(storeId(GO_VAP),8,seed);assertThat(rows).hasSize(Math.min(8,jdbc.queryForObject("select count(*) from dbo.store_products where store_id=? and status='ACTIVE' and quantity>reserved_quantity",Integer.class,storeId(GO_VAP))));assertThat(rows).extracting(i->i.product().id()).doesNotHaveDuplicates();assertThat(rows).allSatisfy(i->{assertThat(i.atSelectedStore().storeId()).isEqualTo(storeId(GO_VAP));assertThat(i.atSelectedStore().inStock()).isTrue();assertThat(i.atSelectedStore().price()).isEqualByComparingTo(productRepository.findById(i.product().id()).orElseThrow().getPrice());});}assertThat(storeProductService.getRandomSellable(storeId(HAI_CHAU),8,1)).isEmpty();assertThat(storeProductService.getRandomSellable(storeId(GO_VAP),0,1)).isEmpty();
    }

    @Test
    void randomPickIsAFunctionOfTheSeed() {
        Long goVap = storeId(GO_VAP);
        List<Long> first = storeProductService.getRandomSellable(goVap, 3, 42L).stream().map(i -> i.product().id()).toList();
        List<Long> again = storeProductService.getRandomSellable(goVap, 3, 42L).stream().map(i -> i.product().id()).toList();

        assertThat(again).as("same seed, same pick and same order").isEqualTo(first);
        long distinctPicks = java.util.stream.LongStream.rangeClosed(1, 30)
                .mapToObj(seed -> storeProductService.getRandomSellable(goVap, 3, seed).stream()
                        .map(i -> i.product().id()).toList())
                .distinct().count();
        assertThat(distinctPicks).as("different seeds give different picks").isGreaterThan(1);

        List<Long> chainFirst = storeProductService.getRandomSellable(null, 3, 42L).stream().map(i -> i.product().id()).toList();
        assertThat(storeProductService.getRandomSellable(null, 3, 42L).stream().map(i -> i.product().id()).toList())
                .isEqualTo(chainFirst);
    }

    @Test
    void randomPickOfTheChainIsSkusSomeActiveStoreHasInStock() {
        var rows=storeProductService.getRandomSellable(null,8,5);assertThat(rows).hasSize(8).extracting(i->i.product().sku()).doesNotHaveDuplicates().doesNotContain(RETIRED);assertThat(rows).allSatisfy(i->{assertThat(i.atSelectedStore()).isNull();assertThat(i.stores()).anyMatch(StoreAvailabilityResponse::inStock);assertThat(i.stores()).extracting(StoreAvailabilityResponse::storeName).doesNotContain("OneShop Hải Châu");assertThat(i.lowestPrice()).isEqualByComparingTo(productRepository.findById(i.product().id()).orElseThrow().getPrice());});assertThat(storeProductService.getRandomSellable(null,3,5)).hasSize(3);
    }

    @Test
    void brandProductCountsCountVisibleProductsOnly() {
        var counts=productService.countVisibleProductsByBrand();for(var b:productService.getActiveBrands())assertThat(counts.getOrDefault(b.id(),0L)).isEqualTo(jdbc.queryForObject("select count_big(*) from dbo.products p join dbo.categories c on c.category_id=p.category_id where p.brand_id=? and p.status='ACTIVE' and c.status='ACTIVE'",Long.class,b.id()));
    }

    @Test
    void storeCatalogOfAnInactiveOrUnknownStoreIsEmpty() {
        assertThat(storeProductService.getStoreCatalog(storeId(HAI_CHAU), ProductSearchCriteria.none(), FIRST_PAGE))
                .isEmpty();
        assertThat(storeProductService.getStoreCatalog(-1L, ProductSearchCriteria.none(), FIRST_PAGE)).isEmpty();
    }

    // ================================================================= TC-03: Product Detail

    @Test
    void tc03_productDetailFollowsTheSelectedStoreAndLeavesCartsAlone() {
        var before=cartSnapshot();var central=storeProductService.getProductDetail(productId(SERUM),null);for(String code:List.of(THU_DUC,GO_VAP,QUAN_7)){long store=storeId(code);var d=storeProductService.getProductDetail(productId(SERUM),store);if(d.atSelectedStore()!=null){assertThat(d.atSelectedStore().storeId()).isEqualTo(store);assertThat(d.atSelectedStore().price()).isEqualByComparingTo(productRepository.findById(productId(SERUM)).orElseThrow().getPrice());}assertThat(d.otherStores()).extracting(StoreAvailabilityResponse::storeId).doesNotContain(store);}entityManager.flush();entityManager.clear();assertThat(cartSnapshot()).isEqualTo(before);
    }

    private Map<Long, String> cartSnapshot() {
        return cartItemRepository.findAll().stream().collect(Collectors.toMap(CartItem::getId,
                item -> item.getProduct().getId() + "x" + item.getQuantity()));
    }

    @Test
    void productDetailWithoutSelectedStoreListsEveryStoreSellingTheSku() {
        var detail=storeProductService.getProductDetail(productId(SERUM),null);assertThat(detail.atSelectedStore()).isNull();assertThat(detail.otherStores()).extracting(StoreAvailabilityResponse::storeId).containsExactlyInAnyOrderElementsOf(jdbc.queryForList("select sp.store_id from dbo.store_products sp join dbo.stores s on s.store_id=sp.store_id where sp.product_id=? and sp.status='ACTIVE' and s.status='ACTIVE'",Long.class,productId(SERUM)));assertThat(detail.product().brandName()).isEqualTo("Simple");
    }

    @Test
    void productDetailAtAStoreThatDoesNotSellTheSku() {
        var d=storeProductService.getProductDetail(productId(FOAM),storeId(GO_VAP));assertThat(d.atSelectedStore()).isNull();assertThat(d.otherStores()).extracting(StoreAvailabilityResponse::storeId).doesNotContain(storeId(GO_VAP));assertThat(productRepository.findById(productId(FOAM))).isPresent();
    }

    @Test
    void productThatIsNotVisibleHasNoDetailPage() {
        assertThatThrownBy(() -> storeProductService.getProductDetail(productId(RETIRED), null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> storeProductService.getProductDetail(-1L, null))
                .isInstanceOf(ResourceNotFoundException.class);

        // HIDDEN works like INACTIVE for the Client
        Long serum = productId(SERUM);
        productRepository.findById(serum).orElseThrow().setStatus(ProductStatus.HIDDEN);
        entityManager.flush();
        assertThatThrownBy(() -> storeProductService.getProductDetail(serum, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(skus(ProductSearchCriteria.none())).doesNotContain(SERUM);
        assertThat(storeProductService.getStoreCatalog(storeId(THU_DUC), ProductSearchCriteria.none(), FIRST_PAGE)
                .getContent()).extracting(i -> i.product().sku()).doesNotContain(SERUM);
    }

    @Test
    void hiddenCategoryOrBrandHidesItsProductsFromClients() {
        CategoryResponse hair = productService.getAllCategories().stream()
                .filter(c -> c.name().equals("Trang điểm")).findFirst().orElseThrow();
        CategoryRequest request = new CategoryRequest();
        request.setName(hair.name());
        request.setDescription(hair.description());
        request.setStatus(VisibilityStatus.HIDDEN);

        productService.updateCategory(hair.id(), request);
        entityManager.flush();

        assertThat(skus(ProductSearchCriteria.none())).doesNotContain("VASELINE-LIP-ORIG-48");
        assertThat(productService.getActiveCategories()).extracting(CategoryResponse::name).doesNotContain("Trang điểm");
        // still there for the Admin: nothing was deleted
        assertThat(productService.getAllCategories()).extracting(CategoryResponse::name).contains("Trang điểm");
        assertThat(productRepository.findBySku("VASELINE-LIP-ORIG-48")).isPresent();
    }

    // ================================================================= BR-15: no exact quantity for Clients

    @Test
    void clientViewsCarryNoQuantity() {
        for (Class<?> clientView : List.of(StoreAvailabilityResponse.class, CatalogItemResponse.class,
                ProductDetailResponse.class)) {
            assertThat(Arrays.stream(clientView.getRecordComponents()).map(RecordComponent::getName))
                    .as(clientView.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("quantity"));
        }
        // the Admin view does carry it
        StoreProductStockResponse admin = storeProductService
                .searchStoreProducts(storeId(THU_DUC), productId(SERUM), FIRST_PAGE).getContent().get(0);
        assertThat(admin.quantity()).isEqualTo(40);
    }

    // ================================================================= selected Store / TC-18

    @Test
    void tc18_inactiveStoreCannotBeSelectedOrSoldFromButItsDataStays() {
        Long haiChau = storeId(HAI_CHAU);

        assertThat(storeService.findSelectableStore(haiChau)).isEmpty();
        assertThatThrownBy(() -> storeService.requireSelectableStore(haiChau)).isInstanceOf(BadRequestException.class);
        assertThat(storeService.getActiveStores()).extracting(StoreResponse::code).doesNotContain(HAI_CHAU);

        // the data is still there: Store Finder shows it as not operating, the Admin still manages it
        assertThat(storeService.findStores("Đà Nẵng", null)).extracting(StoreResponse::code).containsExactly(HAI_CHAU);
        assertThat(storeService.findStores(null, null).stream().filter(s -> s.code().equals(HAI_CHAU)).findFirst()
                .orElseThrow().status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(storeService.getAllStores()).extracting(StoreResponse::code).contains(HAI_CHAU);
        assertThat(storeProductService.searchStoreProducts(haiChau, null, FIRST_PAGE).getContent()).isNotEmpty();
    }

    @Test
    void tc18_deactivatingAStoreTakesItOutOfSaleWithoutDeletingAnything() {
        Long quan10 = storeId(QUAN_10);
        long storeProductsBefore = storeProductRepository.count();
        assertThat(storeService.findSelectableStore(quan10)).isPresent();

        StoreResponse current = storeService.getStore(quan10);
        StoreRequest request = storeRequest(current.code(), current.name());
        request.setStatus(ActiveStatus.INACTIVE);
        storeService.updateStore(quan10, request);
        entityManager.flush();
        entityManager.clear();

        assertThat(storeService.findSelectableStore(quan10)).isEmpty();
        assertThat(storeProductService.getStoreCatalog(quan10, ProductSearchCriteria.none(), FIRST_PAGE)).isEmpty();
        assertThat(storeProductService.getProductDetail(productId(SERUM), null).otherStores())
                .extracting(StoreAvailabilityResponse::storeName).doesNotContain("OneShop Quận 10");
        // no hard delete: Store and its StoreProducts are still in the database
        assertThat(storeRepository.findById(quan10)).isPresent();
        assertThat(storeProductRepository.count()).isEqualTo(storeProductsBefore);
        assertThat(storeProductRepository.findByStoreId(quan10)).isNotEmpty();
    }

    @Test
    void selectedStoreMustExistAndBeActive() {
        assertThat(storeService.findSelectableStore(storeId(THU_DUC))).get()
                .extracting(StoreResponse::name).isEqualTo("OneShop Thủ Đức");
        assertThat(storeService.findSelectableStore(null)).isEmpty();
        assertThat(storeService.findSelectableStore(-1L)).isEmpty();
        assertThat(storeService.findSelectableStore(Long.MAX_VALUE)).isEmpty();
        assertThatThrownBy(() -> storeService.requireSelectableStore(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void storeFinderFiltersByProvinceAndArea() {
        assertThat(storeService.getProvinceCities()).containsExactlyInAnyOrder("TP. Hồ Chí Minh", "Đà Nẵng", "Hà Nội");
        assertThat(storeService.getAreas("TP. Hồ Chí Minh"))
                .containsExactlyInAnyOrder("Thủ Đức", "Gò Vấp", "Quận 7", "Quận 10");
        assertThat(storeService.getAreas(null)).hasSize(6);

        assertThat(storeService.findStores(null, null)).hasSize(6);
        assertThat(storeService.findStores("TP. Hồ Chí Minh", "")).hasSize(4);
        assertThat(storeService.findStores("TP. Hồ Chí Minh", "Gò Vấp")).extracting(StoreResponse::code)
                .containsExactly(GO_VAP);
        assertThat(storeService.findStores(null, "Quận 7")).extracting(StoreResponse::code).containsExactly(QUAN_7);
        assertThat(storeService.findStores("Đà Nẵng", "Gò Vấp")).isEmpty();

        StoreResponse quan10 = storeService.findStores(null, "Quận 10").get(0);
        assertThat(quan10.deliveryEnabled()).isFalse();
        assertThat(quan10.pickupEnabled()).isTrue();
        assertThat(quan10.openingHours()).isNotBlank();
        assertThat(quan10.phone()).isNotBlank();
    }

    // ================================================================= Admin: Product / SKU

    @Test
    void adminCreatesAndUpdatesAProduct() {
        AdminProductResponse created = productService.createProduct(productRequest("  P6-TEST-001 ", "Son thử nghiệm #01"));
        entityManager.flush();

        assertThat(created.sku()).isEqualTo("P6-TEST-001");
        assertThat(created.status()).isEqualTo(ProductStatus.ACTIVE);
        // a SKU nobody sells yet is in the chain catalog, with no Store
        assertThat(item(storeProductService.getChainCatalog(new ProductSearchCriteria("P6-TEST", null, null), FIRST_PAGE)
                .getContent(), "P6-TEST-001").stores()).isEmpty();

        ProductRequest update = productRequest("P6-TEST-001", "Son thử nghiệm #01 (mới)");
        update.setStatus(ProductStatus.INACTIVE);
        productService.updateProduct(created.id(), update);
        entityManager.flush();

        assertThat(productService.getProductForAdmin(created.id()).name()).isEqualTo("Son thử nghiệm #01 (mới)");
        assertThat(skus(new ProductSearchCriteria("P6-TEST", null, null))).isEmpty();
        assertThat(productService.searchAllProducts("P6-TEST", FIRST_PAGE).getContent())
                .extracting(AdminProductResponse::status).containsExactly(ProductStatus.INACTIVE);
    }

    @Test
    void duplicateSkuIsRejected() {
        long before = productRepository.count();

        assertThatThrownBy(() -> productService.createProduct(productRequest(SERUM, "Trùng SKU")))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getField()).isEqualTo("sku");
        assertThatThrownBy(() -> productService.createProduct(productRequest(" simple-purify-150 ", "Trùng SKU khác kiểu chữ")))
                .isInstanceOf(BadRequestException.class);
        // changing another product's SKU to an existing one is refused too
        assertThatThrownBy(() -> productService.updateProduct(productId(TONER), productRequest(SERUM, "Đổi SKU")))
                .isInstanceOf(BadRequestException.class);
        // keeping its own SKU is fine
        productService.updateProduct(productId(SERUM), productRequest(SERUM, "Simple Purifying Gel Wash 150ml"));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void productNeedsAnExistingCategoryAndBrand() {
        ProductRequest request = productRequest("P6-TEST-002", "Sản phẩm");
        request.setCategoryId(-1L);

        assertThatThrownBy(() -> productService.createProduct(request)).isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getField()).isEqualTo("categoryId");
    }

    @Test
    void anActiveProductWithPriceZeroIsRefusedByTheServiceToo() {
        long before = productRepository.count();
        ProductRequest free = productRequest("P0-ZERO-001", "Giá không đồng");
        free.setPrice(BigDecimal.ZERO);
        assertThatThrownBy(() -> productService.createProduct(free)).isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getField()).isEqualTo("price");
        // editing an existing sellable product down to 0 is refused as well
        ProductRequest zeroed = productRequest(SERUM, "Simple Purifying Gel Wash 150ml");
        zeroed.setPrice(BigDecimal.ZERO);
        assertThatThrownBy(() -> productService.updateProduct(productId(SERUM), zeroed)).isInstanceOf(BadRequestException.class);
        // a product that is not on sale may still be saved without a price
        free.setStatus(com.oneshop.entity.ProductStatus.INACTIVE);
        productService.createProduct(free);
        entityManager.flush();
        assertThat(productRepository.count()).isEqualTo(before + 1);
    }

    private ProductRequest productRequest(String sku, String name) {
        ProductRequest request = new ProductRequest();
        request.setSku(sku);
        request.setName(name);
        request.setDescription("Dùng cho kiểm thử Phase 6");
        request.setPrice(new BigDecimal("100000")); // an ACTIVE product needs a real price (0 is refused)
        request.setCategoryId(productService.getAllCategories().get(0).id());
        request.setBrandId(productService.getAllBrands().get(0).id());
        return request;
    }

    // ================================================================= Admin: Store

    @Test
    void adminCreatesAStoreAndCodeMustBeUnique() {
        StoreResponse created = storeService.createStore(storeRequest("OS-P6TEST", "OneShop Kiểm Thử"));
        entityManager.flush();

        assertThat(storeService.findSelectableStore(created.id())).isPresent();
        assertThat(storeService.findStores("Cần Thơ", "Ninh Kiều")).extracting(StoreResponse::code)
                .containsExactly("OS-P6TEST");

        assertThatThrownBy(() -> storeService.createStore(storeRequest("OS-P6TEST", "Trùng mã")))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getField()).isEqualTo("code");
        assertThatThrownBy(() -> storeService.updateStore(created.id(), storeRequest(THU_DUC, "Đổi mã trùng")))
                .isInstanceOf(BadRequestException.class);
    }

    private static StoreRequest storeRequest(String code, String name) {
        StoreRequest request = new StoreRequest();
        request.setCode(code);
        request.setName(name);
        request.setAddress("1 Đường Kiểm Thử");
        request.setProvinceCity("Cần Thơ");
        request.setArea("Ninh Kiều");
        request.setPhone("02920000001");
        request.setOpeningHours("08:00 - 21:00");
        return request;
    }

    // ================================================================= Admin: StoreProduct

    @Test
    void adminPutsASkuOnSaleAtAStoreWithItsOwnPriceQuantityAndStatus() {
        Long foam = productId(FOAM);
        Long goVap = storeId(GO_VAP);
        assertThat(storeProductService.getProductDetail(foam, goVap).atSelectedStore()).isNull();

        StoreProductStockResponse created = storeProductService.createStoreProduct(
                storeProductRequest(goVap, foam, "171000", 5, ActiveStatus.ACTIVE), ADMIN);
        entityManager.flush();
        entityManager.clear();

        assertThat(created.quantity()).isEqualTo(5);
        StoreAvailabilityResponse offer = storeProductService.getProductDetail(foam, goVap).atSelectedStore();
        assertThat(offer.price()).isEqualByComparingTo("138000");
        assertThat(offer.inStock()).isTrue();
        // the opening quantity is an audited stock movement
        InventoryMovement opening = lastMovement(created.storeProductId());
        assertThat(opening.getType()).isEqualTo(InventoryMovementType.STOCK_ADJUST);
        assertThat(opening.getQuantityBefore()).isZero();
        assertThat(opening.getQuantityAfter()).isEqualTo(5);
        assertThat(opening.getStaff().getEmail()).isEqualTo(ADMIN);

        // sold out: still listed at the Store, as out of stock
        storeProductService.updateStoreProduct(created.storeProductId(),
                storeProductRequest(null, null, "175000", 0, ActiveStatus.ACTIVE), ADMIN);
        entityManager.flush();
        entityManager.clear();
        offer = storeProductService.getProductDetail(foam, goVap).atSelectedStore();
        assertThat(offer.price()).isEqualByComparingTo("138000");
        assertThat(offer.inStock()).isFalse();
        assertThat(lastMovement(created.storeProductId()).getQuantityChange()).isEqualTo(-5);

        // switched off at this Store: gone for the Client, still there for the Admin
        storeProductService.updateStoreProduct(created.storeProductId(),
                storeProductRequest(null, null, "175000", 0, ActiveStatus.INACTIVE), ADMIN);
        entityManager.flush();
        entityManager.clear();
        assertThat(storeProductService.getProductDetail(foam, goVap).atSelectedStore()).isNull();
        assertThat(storeProductService.getStoreProduct(created.storeProductId()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void aPostedStorePriceIsIgnoredAndWritesNoStockMovement() {
        var stock=storeProductService.searchStoreProducts(storeId(THU_DUC),productId(SERUM),FIRST_PAGE).getContent().getFirst();long n=movementRepository.count();var canonical=productRepository.findById(productId(SERUM)).orElseThrow().getPrice();var legacyBefore=storeProductRepository.findById(stock.storeProductId()).orElseThrow().getPrice();storeProductService.updateStoreProduct(stock.storeProductId(),storeProductRequest(null,null,"119000",stock.quantity(),ActiveStatus.ACTIVE),ADMIN);entityManager.flush();entityManager.clear();assertThat(movementRepository.count()).isEqualTo(n);assertThat(storeProductService.getProductDetail(productId(SERUM),storeId(THU_DUC)).atSelectedStore().price()).isEqualByComparingTo(canonical);assertThat(storeProductRepository.findById(stock.storeProductId()).orElseThrow().getPrice()).as("a Store has no price of its own: the posted 119000 is never stored").isEqualTo(legacyBefore);assertThat(storeProductService.getStoreProduct(stock.storeProductId()).price()).as("read-only view shows Product.price").isEqualByComparingTo(canonical);
    }

    @Test
    void duplicateStoreProductIsRejected() {
        long before=storeProductRepository.count();var stock=storeProductRepository.findByStoreIdAndProductId(storeId(THU_DUC),productId(SERUM)).orElseThrow();assertThatThrownBy(()->storeProductService.createStoreProduct(storeProductRequest(storeId(THU_DUC),productId(SERUM),"100000",1,ActiveStatus.ACTIVE),ADMIN)).isInstanceOf(BadRequestException.class);stock.setStatus(ActiveStatus.INACTIVE);entityManager.flush();assertThatThrownBy(()->storeProductService.createStoreProduct(storeProductRequest(storeId(THU_DUC),productId(SERUM),"100000",1,ActiveStatus.ACTIVE),ADMIN)).isInstanceOf(BadRequestException.class);assertThat(storeProductRepository.count()).isEqualTo(before);
    }

    @Test
    void storeProductNeedsAnExistingStoreAndProductAndAValidQuantity() {
        assertThatThrownBy(() -> storeProductService.createStoreProduct(
                storeProductRequest(-1L, productId(SERUM), "1", 1, ActiveStatus.ACTIVE), ADMIN))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storeProductService.createStoreProduct(
                storeProductRequest(storeId(GO_VAP), -1L, "1", 1, ActiveStatus.ACTIVE), ADMIN))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storeProductService.createStoreProduct(
                storeProductRequest(storeId(GO_VAP), productId(FOAM), "1", -3, ActiveStatus.ACTIVE), ADMIN))
                .isInstanceOf(BadRequestException.class);
    }

    private static StoreProductRequest storeProductRequest(Long storeId, Long productId, String price, int quantity,
                                                           ActiveStatus status) {
        StoreProductRequest request = new StoreProductRequest();
        request.setStoreId(storeId);
        request.setProductId(productId);
        request.setPrice(new BigDecimal(price));
        request.setQuantity(quantity);
        request.setStatus(status);
        return request;
    }

    private InventoryMovement lastMovement(Long storeProductId) {
        return movementRepository.findByStoreProductIdOrderByCreatedAtDesc(storeProductId, PageRequest.of(0, 20))
                .getContent().stream().max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow();
    }

    // ================================================================= TC-17: images on Cloudinary

    @Test
    void tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId() {
        Long serum = productId(SERUM);
        productImageRepository.deleteAll(productImageRepository.findByProductIdOrderBySortOrderAsc(serum));
        entityManager.flush();
        byte[] bytes = "not-a-real-png".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "serum.png", "image/png", bytes);
        when(cloudinaryService.uploadImage(any(), eq("products"))).thenReturn(
                new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/oneshop/products/serum-1.png",
                        "oneshop/products/serum-1"),
                new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/oneshop/products/serum-2.png",
                        "oneshop/products/serum-2"));

        ProductImageResponse first = productService.addProductImage(serum, file);
        ProductImageResponse second = productService.addProductImage(serum, file);
        entityManager.flush();
        entityManager.clear();

        // the file itself was handed to Cloudinary ...
        verify(cloudinaryService, times(2)).uploadImage(file, "products");
        // ... and the row holds the URL and the public id
        ProductImage stored = productImageRepository.findById(first.id()).orElseThrow();
        assertThat(stored.getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/oneshop/products/serum-1.png");
        assertThat(stored.getPublicId()).isEqualTo("oneshop/products/serum-1");
        assertThat(first.primary()).as("first image is the primary one").isTrue();
        assertThat(second.primary()).isFalse();
        // no table of the database can hold image bytes at all
        Number binaryColumns = (Number) entityManager.createNativeQuery("""
                select count(*) from INFORMATION_SCHEMA.COLUMNS
                where TABLE_SCHEMA = 'dbo' and DATA_TYPE in ('varbinary', 'binary', 'image')""").getSingleResult();
        assertThat(binaryColumns.intValue()).isZero();

        // the Client sees the primary image in the catalog and all images in the detail
        assertThat(item(storeProductService.getChainCatalog(new ProductSearchCriteria(SERUM, null, null), FIRST_PAGE)
                .getContent(), SERUM).product().imageUrl()).endsWith("serum-1.png");
        assertThat(storeProductService.getProductDetail(serum, null).images()).hasSize(2);

        // choosing another primary image
        productService.setPrimaryProductImage(serum, second.id());
        entityManager.flush();
        entityManager.clear();
        assertThat(productService.getProductImages(serum)).filteredOn(ProductImageResponse::primary)
                .extracting(ProductImageResponse::id).containsExactly(second.id());

        // deleting schedules the removal of the Cloudinary file in the same SQL transaction; the file itself is only
        // deleted after commit (ImageTransactionCoordinator), which this rolled-back test never reaches:
        // the commit path is covered by QaFixImageLifecycleDatabaseIntegrationTest
        productService.deleteProductImage(serum, second.id());
        entityManager.flush();
        entityManager.clear();
        assertThat(pendingCleanupTasks("oneshop/products/serum-2")).isEqualTo(1);
        verify(cloudinaryService, never()).deleteImage("oneshop/products/serum-2");
        assertThat(productService.getProductImages(serum)).extracting(ProductImageResponse::id, ProductImageResponse::primary)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(first.id(), true));
    }

    @Test
    void imageOfAnotherProductCannotBeAddressedThroughAProduct() {
        when(cloudinaryService.uploadImage(any(), eq("products")))
                .thenReturn(new ImageUploadResult("https://res.cloudinary.com/demo/a.png", "oneshop/products/a"));
        ProductImageResponse serumImage = productService.addProductImage(productId(SERUM),
                new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}));
        entityManager.flush();

        assertThatThrownBy(() -> productService.deleteProductImage(productId(TONER), serumImage.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cloudinaryService, never()).deleteImage(any());
    }

    @Test
    void failedUploadStoresNothing() {
        long before = productImageRepository.count();
        when(cloudinaryService.uploadImage(any(), any())).thenThrow(new ImageStorageException("Cloudinary is not configured"));

        assertThatThrownBy(() -> productService.addProductImage(productId(SERUM),
                new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})))
                .isInstanceOf(ImageStorageException.class);

        assertThat(productImageRepository.count()).isEqualTo(before);
    }

    @Test
    void tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved() {
        Brand cocoon = brandRepository.findAll().stream().filter(b -> b.getName().equals("CeraVe")).findFirst().orElseThrow();
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2});
        when(cloudinaryService.uploadImage(any(), eq("brands"))).thenReturn(
                new ImageUploadResult("https://res.cloudinary.com/demo/brands/cocoon-1.png", "oneshop/brands/cocoon-1"),
                new ImageUploadResult("https://res.cloudinary.com/demo/brands/cocoon-2.png", "oneshop/brands/cocoon-2"));

        BrandResponse withLogo = productService.uploadBrandLogo(cocoon.getId(), file);
        entityManager.flush();
        entityManager.clear();
        assertThat(withLogo.logoUrl()).endsWith("cocoon-1.png");
        assertThat(brandRepository.findById(cocoon.getId()).orElseThrow().getLogoPublicId())
                .isEqualTo("oneshop/brands/cocoon-1");

        // replacing the logo deletes the previous file on Cloudinary
        productService.uploadBrandLogo(cocoon.getId(), file);
        entityManager.flush();
        // the old file is queued for removal in this transaction and deleted only after commit
        assertThat(pendingCleanupTasks("oneshop/brands/cocoon-1")).isEqualTo(1);
        verify(cloudinaryService, never()).deleteImage("oneshop/brands/cocoon-1");

        productService.removeBrandLogo(cocoon.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(pendingCleanupTasks("oneshop/brands/cocoon-2")).isEqualTo(1);
        verify(cloudinaryService, never()).deleteImage("oneshop/brands/cocoon-2");
        Brand cleared = brandRepository.findById(cocoon.getId()).orElseThrow();
        assertThat(cleared.getLogoUrl()).isNull();
        assertThat(cleared.getLogoPublicId()).isNull();
    }

    private long pendingCleanupTasks(String publicId) {
        return ((Number) entityManager.createNativeQuery("select count(*) from dbo.image_cleanup_tasks where public_id = ?1")
                .setParameter(1, publicId).getSingleResult()).longValue();
    }

    @Test
    void aCloudinaryOutageNeverBlocksDeletingTheImageRow() {
        when(cloudinaryService.uploadImage(any(), eq("products")))
                .thenReturn(new ImageUploadResult("https://res.cloudinary.com/demo/b.png", "oneshop/products/b"));
        Long serum = productId(SERUM);
        ProductImageResponse image = productService.addProductImage(serum,
                new MockMultipartFile("file", "b.png", "image/png", new byte[]{1}));
        entityManager.flush();
        doThrow(new ImageStorageException("Cloudinary unavailable")).when(cloudinaryService).deleteImage("oneshop/products/b");

        // the provider is only called after commit and its failure is journaled for retry
        // (QaFixImageLifecycleDatabaseIntegrationTest#failedAfterCommitDeleteDoesNotUndoCommittedMetadata),
        // so the request itself succeeds and the cleanup task is queued
        productService.deleteProductImage(serum, image.id());
        entityManager.flush();
        assertThat(pendingCleanupTasks("oneshop/products/b")).isEqualTo(1);
    }
}
