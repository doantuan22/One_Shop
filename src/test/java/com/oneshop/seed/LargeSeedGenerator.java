package com.oneshop.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic generator of the large development seed (database/seed_large/*.sql).
 *
 * <p>Single self-contained file (JDK only) so that it runs both from the test classpath and as
 * {@code java src/test/java/com/oneshop/seed/LargeSeedGenerator.java [--out database/seed_large] [--allow-missing-images]}.
 * Same inputs (fixed seed + image manifest) always produce byte-identical output. Before anything is written the model is
 * checked against consistency rules 1-9 and the minimum volumes of the specification ({@link #validate}).
 *
 * <p>All times are minutes before "now" ("ago"); SQL uses {@code DATEADD(MINUTE, -n, @now)} so the data always covers
 * the last 120 days relative to the day it is loaded.
 */
public final class LargeSeedGenerator {

    public static final String PASSWORD_HASH = "$2a$10$xEjHDcYoM7aBZJf/2Hm.ZO3NPCKEr93cG2fro4NH5CrMY2NP6ScvW"; // same hash as 04_seed_data.sql
    public static final long SEED = 20261012L;
    public static final int DAY = 1440;
    public static final String PUBLIC_ID_ROOT = "oneshop/seed";
    static final String PICKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ---------------------------------------------------------------- model

    public static final class Brand { public long id; public String name, slug, desc, status = "ACTIVE"; }
    public static final class Category { public long id; public String name, slug, desc, status = "ACTIVE"; }
    public static final class Product {
        public long id, categoryId, brandId; public String sku, name, desc, status = "ACTIVE", imgType, edge;
        public int basePrice; public boolean historyOnly; public int storesWanted; public boolean outOfStockEverywhere;
    }
    public static final class Store {
        public long id; public String code, name, address, province, area, phone, hours, status = "ACTIVE";
        public boolean delivery = true, pickup = true, isNew; public int weight;
    }
    public static final class User {
        public long id, roleId; public String email, userName, fullName, phone, status = "ACTIVE"; public int createdAgo;
    }
    public static final class Assignment { public long userId, storeId; public String status = "ACTIVE"; public int assignedAgo; }
    public static final class Address { public long id, userId; public String receiver, phone, line; public boolean isDefault; public int createdAgo; }
    public static final class StoreProduct {
        public long id, storeId, productId; public int price, quantity, initial; public String status = "ACTIVE";
        public int oldPrice, priceChangeAgo; public int updatedAgo; public boolean targetZero; public boolean touched;
    }
    public static final class Cart { public long id, userId; public int createdAgo, updatedAgo; public List<long[]> items = new ArrayList<>(); }
    public static final class Item { public long storeProductId; public String name; public int unitPrice, qty; public int subtotal() { return unitPrice * qty; } }
    public static final class Hist { public String oldStatus, newStatus, note; public Long by; public int ago; }
    public static final class Payment { public String method, status, txn; public int amount, paidAgo = -1, createdAgo; }
    public static final class Movement {
        public long storeProductId, orderId; public String type, note; public int change, before, after, ago; public Long staffId; public int seq;
    }
    public static final class Order {
        public long id, checkoutId, userId, storeId; public String fulfillment, method, payStatus, status, receiver, phone, address, pickupCode;
        public int readyAgo = -1, pickedAgo = -1, createdAgo, updatedAgo, total;
        public String cancelKind; // FAIL_SYSTEM / CUSTOMER / null
        public List<Item> items = new ArrayList<>(); public List<Hist> history = new ArrayList<>(); public Payment payment;
    }
    public static final class Checkout { public long id, userId; public int total, createdAgo; public String status; public List<Order> orders = new ArrayList<>(); }
    public static final class Review {
        public long id, userId, productId, orderId; public int rating, createdAgo, repliedAgo; public String comment, status = "ACTIVE", reply; public Long repliedBy;
    }

    public static final class Model {
        public List<Brand> brands = new ArrayList<>();
        public List<Category> categories = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
        public List<Store> stores = new ArrayList<>();           // NEW stores only (ids >= 1000)
        public List<User> users = new ArrayList<>();
        public List<Assignment> assignments = new ArrayList<>();
        public List<Address> addresses = new ArrayList<>();
        public List<StoreProduct> storeProducts = new ArrayList<>();
        public List<Cart> carts = new ArrayList<>();
        public List<Checkout> checkouts = new ArrayList<>();
        public List<Movement> movements = new ArrayList<>();
        public List<Review> reviews = new ArrayList<>();
        /** image key -> source key (stock photo / logo / static file); public id by key. */
        public Map<String, String> imageSource = new LinkedHashMap<>();
        public Map<String, String> imagePublicId = new LinkedHashMap<>();
        public Map<String, List<String>> productImageKeys = new LinkedHashMap<>(); // sku -> image keys (first = primary)
        public Map<Long, Store> allStoresById = new LinkedHashMap<>();                 // 04 stores + new
        public Map<Long, String> productEdgeNote = new LinkedHashMap<>();

        public List<Order> orders() { return checkouts.stream().flatMap(c -> c.orders.stream()).collect(Collectors.toList()); }
        public Map<Long, StoreProduct> spById() { return storeProducts.stream().collect(Collectors.toMap(s -> s.id, s -> s)); }
        public Map<Long, Product> productById() { return products.stream().collect(Collectors.toMap(p -> p.id, p -> p)); }
    }

    // 04 facts (never modified): stores 1-5, staff 2,3,4,7, admin 1, store flags/status.
    static final long[] OLD_STORE_IDS = {1, 2, 3, 4, 5};
    static final Map<Long, long[]> OLD_STORE_STAFF = Map.of(1L, new long[]{2}, 2L, new long[]{3}, 3L, new long[]{4}, 4L, new long[]{7});
    static final Map<Long, boolean[]> OLD_STORE_FLAGS = Map.of(1L, new boolean[]{true, true}, 2L, new boolean[]{true, true},
            3L, new boolean[]{true, true}, 4L, new boolean[]{false, true}, 5L, new boolean[]{true, true});
    static final Set<Long> OLD_INACTIVE_STORES = Set.of(5L);
    static final long[][] OLD_PRODUCT_IMAGES = {{1}, {2}, {3}, {4}, {5}, {6}, {7}};

    // ---------------------------------------------------------------- generation

    private final Random rnd = new Random(SEED);
    private final Model m = new Model();
    private long nextOrderId = 1000, nextCheckoutId = 1000, nextMovementSeq = 0;

    public static Model generate() { return new LargeSeedGenerator().build(); }

    private int rint(int lo, int hi) { return lo + rnd.nextInt(hi - lo + 1); }
    private <T> T pick(List<T> l) { return l.get(rnd.nextInt(l.size())); }
    private static int round1000(double v) { return (int) (Math.round(v / 1000.0) * 1000); }

    private Model build() {
        buildBrandsCategories();
        buildProducts();
        buildStores();
        buildUsers();
        buildStoreProducts();
        buildOrders();
        buildInventory();
        buildCarts();
        buildReviews();
        buildImagePlan();
        return m;
    }

    // ---- brands / categories ----
    static final String[][] BRANDS = { // key, name, slug, desc
            {"LOREAL", "L'Oréal Paris", "loreal-paris", "Thương hiệu mỹ phẩm Pháp, phổ biến từ chăm sóc da đến trang điểm và tóc"},
            {"MAYBELLINE", "Maybelline New York", "maybelline-new-york", "Mỹ phẩm trang điểm đại chúng đến từ New York"},
            {"GARNIER", "Garnier", "garnier", "Chăm sóc da, tóc và làm sạch chiết xuất thiên nhiên"},
            {"NEUTROGENA", "Neutrogena", "neutrogena", "Sản phẩm chăm sóc da được da liễu khuyên dùng"},
            {"LRP", "La Roche-Posay", "la-roche-posay", "Dược mỹ phẩm cho da nhạy cảm"},
            {"CETAPHIL", "Cetaphil", "cetaphil", "Làm sạch và dưỡng ẩm dịu nhẹ cho da nhạy cảm"},
            {"CERAVE", "CeraVe", "cerave", "Dưỡng da với ceramide, phát triển cùng bác sĩ da liễu"},
            {"SENKA", "Senka", "senka", "Chăm sóc da Nhật Bản với tơ tằm"},
            {"KLAIRS", "Dear Klairs", "dear-klairs", "Chăm sóc da Hàn Quốc tối giản, dịu nhẹ"},
            {"SOMEBYMI", "Some By Mi", "some-by-mi", "Chăm sóc da Hàn Quốc nổi tiếng với dòng AHA BHA PHA"},
            {"SKIN1004", "SKIN1004", "skin1004", "Mỹ phẩm rau má Madagascar"},
            {"ANESSA", "Anessa", "anessa", "Kem chống nắng Nhật Bản"},
            {"NIVEA", "Nivea", "nivea", "Chăm sóc cơ thể và chống nắng"},
            {"DOVE", "Dove", "dove", "Sữa tắm, dầu gội và chăm sóc cơ thể dưỡng ẩm"},
            {"PANTENE", "Pantene", "pantene", "Chăm sóc tóc Pro-V"},
            {"ROMAND", "Romand", "romand", "Mỹ phẩm trang điểm Hàn Quốc, nổi bật với son tint"},
            {"3CE", "3CE", "3ce", "Mỹ phẩm trang điểm phong cách Hàn Quốc"},
            {"BBW", "Bath & Body Works", "bath-and-body-works", "Nước hoa toàn thân và chăm sóc cơ thể"},
            {"VS", "Victoria's Secret", "victorias-secret", "Xịt thơm toàn thân"},
    };
    final Map<String, Long> brandIds = new HashMap<>();
    final Map<String, Long> categoryIds = new HashMap<>();

    static final String[][] CATEGORIES = { // key, name, slug, desc
            {"BODY", "Chăm sóc cơ thể", "cham-soc-co-the", "Sữa tắm, kem dưỡng thể, dưỡng môi"},
            {"SUN", "Chống nắng", "chong-nang", "Kem chống nắng mặt và cơ thể"},
            {"PERF", "Nước hoa", "nuoc-hoa", "Xịt thơm toàn thân và nước hoa"},
            {"MASK", "Mặt nạ", "mat-na", "Mặt nạ giấy và mặt nạ dưỡng"},
            {"CLEAN", "Tẩy trang và làm sạch", "tay-trang-va-lam-sach", "Nước tẩy trang, sữa rửa mặt"},
    };

    private void buildBrandsCategories() {
        long id = 1000;
        for (String[] b : BRANDS) {
            Brand x = new Brand(); x.id = id++; x.name = b[1]; x.slug = b[2]; x.desc = b[3];
            m.brands.add(x); brandIds.put(b[0], x.id);
        }
        id = 1000;
        for (String[] c : CATEGORIES) {
            Category x = new Category(); x.id = id++; x.name = c[1]; x.slug = c[2]; x.desc = c[3];
            m.categories.add(x); categoryIds.put(c[0], x.id);
        }
        categoryIds.put("SKIN", 1L); categoryIds.put("MAKEUP", 2L); categoryIds.put("HAIR", 3L);
    }

    // ---- catalog: cat|brand|imgType|sku|name|price|description ----
    static final String[] CATALOG = {
        // Chăm sóc da
        "SKIN|KLAIRS|toner|KLAIRS-SUPPLE-180|Dear Klairs Supple Preparation Facial Toner 180ml|355000|Toner dịu nhẹ, cấp ẩm và cân bằng da sau bước làm sạch.",
        "SKIN|KLAIRS|serum|KLAIRS-VITC-35|Dear Klairs Freshly Juiced Vitamin Drop 35ml|395000|Serum vitamin C nhẹ dịu, hỗ trợ làm sáng và đều màu da.",
        "SKIN|KLAIRS|creamjar|KLAIRS-MIDNIGHT-30|Dear Klairs Midnight Blue Calming Cream 30ml|410000|Kem dưỡng làm dịu da kích ứng, dùng buổi tối.",
        "SKIN|SOMEBYMI|toner|SBM-AHABHA-TONER-150|Some By Mi AHA BHA PHA 30 Days Miracle Toner 150ml|289000|Toner chứa AHA, BHA, PHA hỗ trợ da mụn, lỗ chân lông to.",
        "SKIN|SOMEBYMI|serum|SBM-AHABHA-SERUM-50|Some By Mi AHA BHA PHA 30 Days Miracle Serum 50ml|329000|Serum hỗ trợ làm sạch sâu và giảm mụn ẩn.",
        "SKIN|SOMEBYMI|creamjar|SBM-TRUECICA-60|Some By Mi Snail Truecica Miracle Repair Cream 60g|349000|Kem dưỡng phục hồi da sau mụn với ốc sên và rau má.",
        "SKIN|SKIN1004|serum|S1004-CENTELLA-100|SKIN1004 Madagascar Centella Ampoule 100ml|395000|Ampoule rau má làm dịu da, phù hợp da nhạy cảm.",
        "SKIN|SKIN1004|toner|S1004-TONER-210|SKIN1004 Madagascar Centella Toning Toner 210ml|365000|Toner rau má dịu nhẹ, dùng hằng ngày.",
        "SKIN|LRP|serum|LRP-EFFACLAR-30|La Roche-Posay Effaclar Serum 30ml|690000|Serum hỗ trợ da dầu mụn, giảm bóng nhờn.",
        "SKIN|LRP|creamjar|LRP-CICAPLAST-40|La Roche-Posay Cicaplast Baume B5 40ml|285000|Kem phục hồi da kích ứng, khô và nứt nẻ.",
        "SKIN|LOREAL|serum|LOREAL-REVITALIFT-HA-30|L'Oréal Paris Revitalift 1.5% Hyaluronic Acid Serum 30ml|425000|Serum cấp nước chuyên sâu với hyaluronic acid.",
        "SKIN|NEUTROGENA|creamjar|NEU-HYDROBOOST-50|Neutrogena Hydro Boost Water Gel 50g|315000|Gel dưỡng cấp nước, thấm nhanh, không nhờn rít.",
        "SKIN|CERAVE|creamjar|CERAVE-MOIST-CREAM-177|CeraVe Moisturising Cream 177ml|345000|Kem dưỡng ẩm với ceramide phục hồi hàng rào bảo vệ da.",
        "SKIN|CERAVE|serum|CERAVE-RETINOL-30|CeraVe Skin Renewing Retinol Serum 30ml|495000|Serum retinol dịu nhẹ giúp mịn da, mờ thâm.",
        // Tẩy trang và làm sạch
        "CLEAN|CETAPHIL|cleanser|CETA-CLEANSER-125|Cetaphil Gentle Skin Cleanser 125ml|105000|Sữa rửa mặt dịu nhẹ cho da nhạy cảm. [INACTIVE-HISTORY]",
        "CLEAN|CETAPHIL|cleanser|CETA-CLEANSER-250|Cetaphil Gentle Skin Cleanser 250ml|165000|Sữa rửa mặt dịu nhẹ, chai 250ml.",
        "CLEAN|CETAPHIL|cleanser|CETA-CLEANSER-500|Cetaphil Gentle Skin Cleanser 500ml|285000|Sữa rửa mặt dịu nhẹ, chai lớn 500ml tiết kiệm.",
        "CLEAN|CERAVE|cleanser|CERAVE-FOAMING-236|CeraVe Foaming Facial Cleanser 236ml|335000|Sữa rửa mặt tạo bọt cho da dầu.",
        "CLEAN|CERAVE|cleanser|CERAVE-HYDRATING-236|CeraVe Hydrating Cleanser 236ml|335000|Sữa rửa mặt dưỡng ẩm cho da khô.",
        "CLEAN|SENKA|cleanser|SENKA-WHIP-120|Senka Perfect Whip Sữa Rửa Mặt 120g|75000|Sữa rửa mặt tạo bọt mịn với tơ tằm.",
        "CLEAN|NEUTROGENA|cleanser|NEU-DEEPCLEAN-175|Neutrogena Deep Clean Sữa Rửa Mặt 175g|125000|Làm sạch sâu bụi bẩn và dầu thừa.",
        "CLEAN|GARNIER|micellar|GARNIER-MICELLAR-BLUE-400|Garnier Micellar Cleansing Water Xanh 400ml|139000|Nước tẩy trang cho da dầu và hỗn hợp.",
        "CLEAN|GARNIER|micellar|GARNIER-MICELLAR-PINK-400|Garnier Micellar Cleansing Water Hồng 400ml|139000|Nước tẩy trang cho da nhạy cảm.",
        "CLEAN|LOREAL|micellar|LOREAL-MICELLAR-400|L'Oréal Paris 3-in-1 Micellar Water Refreshing 400ml|169000|Nước tẩy trang 3 trong 1 làm sạch, dưỡng ẩm và làm mới da.",
        "CLEAN|LRP|micellar|LRP-MICELLAR-400|La Roche-Posay Micellar Water Ultra Sensitive Skin 400ml|345000|Nước tẩy trang cho da rất nhạy cảm.",
        // Chống nắng
        "SUN|ANESSA|sunscreen|ANESSA-MILK-60|Anessa Perfect UV Sunscreen Skincare Milk SPF50+ PA++++ 60ml|545000|Kem chống nắng dạng sữa, chịu nước và mồ hôi.",
        "SUN|ANESSA|sunscreen|ANESSA-GEL-90|Anessa Perfect UV Sunscreen Skincare Gel SPF50+ 90g|545000|Kem chống nắng dạng gel mỏng nhẹ cho cả mặt và cơ thể.",
        "SUN|LRP|sunscreen|LRP-ANTHELIOS-50|La Roche-Posay Anthelios UVMune 400 Invisible Fluid 50ml|485000|Kem chống nắng phổ rộng cho da nhạy cảm.",
        "SUN|NEUTROGENA|sunscreen|NEU-ULTRASHEER-88|Neutrogena Ultra Sheer Dry-Touch SPF50+ 88ml|295000|Kem chống nắng khô thoáng, không nhờn rít.",
        "SUN|NIVEA|sunscreen|NIVEA-SUN-MOIST-50|Nivea Sun Protect & Moisture SPF50 50ml|185000|Kem chống nắng dưỡng ẩm. [EDGE-AMPERSAND]",
        "SUN|SENKA|sunscreen|SENKA-UV-ESSENCE-80|Senka Perfect UV Essence SPF50+ PA++++ 80g|135000|Tinh chất chống nắng nhẹ như nước. [EDGE-HTML]",
        "SUN|SKIN1004|sunscreen|S1004-AIRFIT-50|SKIN1004 Madagascar Centella Air-Fit Suncream Plus SPF50+ 50ml|325000|Kem chống nắng rau má, lên da nhẹ. [SINGLE-STORE]",
        "SUN|GARNIER|sunscreen|GARNIER-LIGHT-SPF50-40|Garnier Light Complete Serum Cream SPF50+ 40ml|215000|Kem chống nắng dưỡng sáng da.",
        // Mặt nạ
        "MASK|GARNIER|sheetmask|GARNIER-MASK-VITC-28|Garnier Mặt Nạ Giấy Vitamin C Sáng Da 28g|18000|Mặt nạ giấy dưỡng sáng da.",
        "MASK|GARNIER|sheetmask|GARNIER-MASK-HA-28|Garnier Mặt Nạ Giấy Hyaluronic Cấp Nước 28g|18000|Mặt nạ giấy cấp nước tức thì.",
        "MASK|GARNIER|sheetmask|GARNIER-MASK-GREENTEA-28|Garnier Mặt Nạ Giấy Trà Xanh Kiểm Dầu 28g|18000|Mặt nạ giấy kiểm soát dầu thừa. [OUT-OF-STOCK]",
        "MASK|KLAIRS|sheetmask|KLAIRS-MASK-RICHMOIST-25|Dear Klairs Rich Moist Soothing Mask 25ml|39000|Mặt nạ giấy làm dịu và cấp ẩm.",
        "MASK|SOMEBYMI|sheetmask|SBM-MASK-TRUECICA-20|Some By Mi Truecica Miracle Repair Mask 20g|35000|Mặt nạ giấy phục hồi da sau mụn.",
        "MASK|SKIN1004|sheetmask|S1004-MASK-CENTELLA-25|SKIN1004 Madagascar Centella Calming Mask 25g|42000|Mặt nạ giấy rau má làm dịu da.",
        "MASK|SENKA|sheetmask|SENKA-MASK-AQUA-3|Senka Perfect Aqua Mask Collagen 3 miếng|59000|Mặt nạ cấp nước hộp 3 miếng. [HIDDEN]",
        "MASK|NEUTROGENA|sheetmask|NEU-MASK-HYDRO-30|Neutrogena Hydro Boost Hydrating Sheet Mask 30ml|45000|Mặt nạ giấy cấp ẩm hyaluronic.",
        // Trang điểm
        "MAKEUP|ROMAND|liptint|ROMAND-JLT-01|Romand Juicy Lasting Tint #01|175000|Son tint bóng nhẹ, bền màu. Tông #01.",
        "MAKEUP|ROMAND|liptint|ROMAND-JLT-02|Romand Juicy Lasting Tint #02|175000|Son tint bóng nhẹ, bền màu. Tông #02.",
        "MAKEUP|ROMAND|liptint|ROMAND-JLT-03|Romand Juicy Lasting Tint #03|175000|Son tint bóng nhẹ, bền màu. Tông #03.",
        "MAKEUP|ROMAND|liptint|ROMAND-JLT-04|Romand Juicy Lasting Tint #04|175000|Son tint bóng nhẹ, bền màu. Tông #04.",
        "MAKEUP|ROMAND|liptint|ROMAND-JLT-05|Romand Juicy Lasting Tint #05|175000|Son tint bóng nhẹ, bền màu. Tông #05.",
        "MAKEUP|ROMAND|liptint|ROMAND-JLT-06|Romand Juicy Lasting Tint #06|175000|Son tint bóng nhẹ, bền màu. Tông #06.",
        "MAKEUP|ROMAND|liptint|ROMAND-ZVT-01|Romand Zero Velvet Tint #01|165000|Son tint lì mịn như nhung. Tông #01.",
        "MAKEUP|ROMAND|liptint|ROMAND-ZVT-02|Romand Zero Velvet Tint #02|165000|Son tint lì mịn như nhung. Tông #02, đã ngừng nhập. [HIDDEN-HISTORY]",
        "MAKEUP|3CE|liptint|3CE-VLT-TAUPE|3CE Velvet Lip Tint Taupe|215000|Son kem lì tông nâu đất.",
        "MAKEUP|3CE|liptint|3CE-VLT-ROSEBEIGE|3CE Velvet Lip Tint Rose Beige|215000|Son kem lì tông hồng be.",
        "MAKEUP|3CE|liptint|3CE-VLT-PINKBREAK|3CE Velvet Lip Tint Pink Break|215000|Son kem lì tông hồng cánh sen.",
        "MAKEUP|3CE|lipstick|3CE-SOFTMATTE-BRINKPINK|3CE Soft Matte Lipstick Brink Pink|345000|Son thỏi lì mềm mịn.",
        "MAKEUP|3CE|eyeshadow|3CE-MULTIEYE-PALETTE|3CE Multi Eye Color Palette|595000|Bảng phấn mắt nhiều ô màu. [SINGLE-STORE]",
        "MAKEUP|3CE|brush|3CE-BRUSH-BLUSH|3CE Cọ Má Hồng Mini|165000|Cọ má hồng đầu mềm, gọn nhẹ. [SINGLE-STORE]",
        "MAKEUP|MAYBELLINE|mascara|MAYBELLINE-LASH-SENS|Maybelline Lash Sensational Mascara|229000|Mascara làm dày và tơi mi.",
        "MAKEUP|MAYBELLINE|cushion|MAYBELLINE-FITME-CUSHION|Maybelline Fit Me Cushion Foundation 14g|285000|Phấn nước kiềm dầu, che phủ tự nhiên.",
        "MAKEUP|MAYBELLINE|cushion|MAYBELLINE-FITME-FOUND-30|Maybelline Fit Me Matte + Poreless Foundation 30ml|219000|Kem nền lì, mịn lỗ chân lông.",
        "MAKEUP|MAYBELLINE|liptint|MAYBELLINE-SUPERSTAY-INK|Maybelline Super Stay Matte Ink|185000|Son kem lì lâu trôi.",
        "MAKEUP|MAYBELLINE|lipstick|MAYBELLINE-COLORSENS-LIP|Maybelline Color Sensational Lipstick|175000|Son thỏi lên màu chuẩn, mềm môi.",
        "MAKEUP|MAYBELLINE|blush|MAYBELLINE-FITME-BLUSH|Maybelline Fit Me Blush|169000|Phấn má hồng lên màu tự nhiên.",
        "MAKEUP|LOREAL|cushion|LOREAL-INFALLIBLE-30|L'Oréal Paris Infallible 24H Fresh Wear Foundation 30ml|395000|Kem nền lâu trôi 24 giờ.",
        "MAKEUP|LOREAL|lipstick|LOREAL-COLORRICHE|L'Oréal Paris Color Riche Lipstick|265000|Son thỏi dưỡng ẩm, màu sắc rực rỡ.",
        "MAKEUP|LOREAL|mascara|LOREAL-LASHPARADISE|L'Oréal Paris Voluminous Lash Paradise Mascara|289000|Mascara làm dày mi, không vón cục.",
        // Chăm sóc tóc
        "HAIR|PANTENE|shampoo|PANTENE-SHAM-ANTIHAIRFALL-630|Pantene Dầu Gội Chắc Khỏe Giảm Rụng Tóc 630g|159000|Dầu gội giúp tóc chắc khỏe, giảm gãy rụng.",
        "HAIR|PANTENE|conditioner|PANTENE-COND-REPAIR-300|Pantene Dầu Xả Phục Hồi Hư Tổn 300ml|89000|Dầu xả phục hồi tóc hư tổn. [INACTIVE]",
        "HAIR|PANTENE|shampoo|PANTENE-SHAM-ANTIDANDRUFF-650|Pantene Dầu Gội Ngăn Gàu 650ml|149000|Dầu gội làm sạch gàu, mát da đầu.",
        "HAIR|PANTENE|hairmist|PANTENE-MIST-100|Pantene Xịt Dưỡng Tóc Pro-V 100ml|119000|Xịt dưỡng giúp tóc mềm mượt, giảm xơ rối.",
        "HAIR|DOVE|shampoo|DOVE-SHAM-REPAIR-640|Dove Dầu Gội Phục Hồi Hư Tổn 640g|179000|Dầu gội phục hồi tóc hư tổn, chai lớn.",
        "HAIR|DOVE|conditioner|DOVE-COND-SMOOTH-335|Dove Dầu Xả Dưỡng Tóc Suôn Mượt 335g|125000|Dầu xả giúp tóc suôn mượt.",
        "HAIR|LOREAL|shampoo|LOREAL-ELSEVE-SHAM-330|L'Oréal Paris Elseve Total Repair 5 Dầu Gội 330ml|135000|Dầu gội phục hồi tóc hư tổn 5 dấu hiệu.",
        "HAIR|LOREAL|conditioner|LOREAL-ELSEVE-COND-325|L'Oréal Paris Elseve Total Repair 5 Dầu Xả 325ml|135000|Dầu xả phục hồi tóc hư tổn.",
        "HAIR|LOREAL|hairmist|LOREAL-ELSEVE-OIL-100|L'Oréal Paris Elseve Extraordinary Oil Serum Dưỡng Tóc 100ml|195000|Tinh dầu dưỡng tóc mềm mượt, bóng khỏe.",
        "HAIR|GARNIER|shampoo|GARNIER-FRUCTIS-SHAM-370|Garnier Fructis Dầu Gội Vitamin Phục Hồi 370ml|99000|Dầu gội trái cây, phục hồi tóc yếu.",
        "HAIR|GARNIER|conditioner|GARNIER-ULTRADOUX-COND-200|Garnier Ultra Doux Dầu Xả Dưỡng Chất Bơ 200ml|85000|Dầu xả dưỡng chất bơ cho tóc khô.",
        // Chăm sóc cơ thể
        "BODY|NIVEA|bodylotion|NIVEA-LOTION-NIGHT-400|Nivea Sữa Dưỡng Thể Dưỡng Trắng Ban Đêm 400ml|155000|Sữa dưỡng thể giúp da sáng mịn ban đêm.",
        "BODY|NIVEA|bodylotion|NIVEA-LOTION-SATIN-400|Nivea Sữa Dưỡng Thể Satin Smooth 400ml|149000|Sữa dưỡng thể cho da mịn màng.",
        "BODY|NIVEA|lipbalm|NIVEA-LIPBALM-SOFTROSE-4|Nivea Son Dưỡng Môi Soft Rose 4.8g|65000|Son dưỡng môi hồng nhẹ. [OUT-OF-STOCK]",
        "BODY|DOVE|bodywash|DOVE-BODYWASH-DEEPMOIST-530|Dove Sữa Tắm Dưỡng Ẩm Chuyên Sâu 530g|169000|Sữa tắm dưỡng ẩm cho da mềm mại.",
        "BODY|DOVE|bodywash|DOVE-BODYWASH-REFILL-900|Dove Sữa Tắm Túi Refill 900g|199000|Sữa tắm dạng túi tiết kiệm. [SINGLE-STORE]",
        "BODY|DOVE|bodylotion|DOVE-LOTION-WHITE-250|Dove Kem Dưỡng Thể Dưỡng Trắng 250ml|135000|Kem dưỡng thể giúp da sáng đều màu.",
        "BODY|CETAPHIL|bodylotion|CETA-LOTION-250|Cetaphil Moisturizing Lotion 250ml|215000|Sữa dưỡng ẩm toàn thân cho da khô nhạy cảm.",
        "BODY|CETAPHIL|bodylotion|CETA-LOTION-500|Cetaphil Moisturizing Lotion 500ml|365000|Sữa dưỡng ẩm toàn thân, chai 500ml.",
        "BODY|CERAVE|bodylotion|CERAVE-LOTION-473|CeraVe Moisturising Lotion 473ml|425000|Sữa dưỡng ẩm ceramide, dùng cho mặt và cơ thể.",
        "BODY|NEUTROGENA|bodylotion|NEU-HANDCREAM-56|Neutrogena Norwegian Formula Hand Cream 56g|85000|Kem dưỡng da tay cho da khô nứt.",
        // Nước hoa
        "PERF|BBW|perfume|BBW-MIST-CHERRY-236|Bath & Body Works Body Mist Japanese Cherry Blossom 236ml|495000|Xịt thơm toàn thân hương hoa anh đào.",
        "PERF|BBW|perfume|BBW-MIST-VANILLA-236|Bath & Body Works Body Mist Warm Vanilla Sugar 236ml|495000|Xịt thơm toàn thân hương vani ngọt ấm.",
        "PERF|BBW|perfume|BBW-MIST-AQUA-236|Bath & Body Works Body Mist Aqua 236ml|495000|Xịt thơm toàn thân hương biển mát. [OUT-OF-STOCK]",
        "PERF|VS|perfume|VS-MIST-BAREVANILLA-250|Victoria's Secret Fine Fragrance Mist Bare Vanilla 250ml|520000|Xịt thơm hương vani nhẹ.",
        "PERF|VS|perfume|VS-MIST-LOVESPELL-250|Victoria's Secret Fine Fragrance Mist Love Spell 250ml|520000|Xịt thơm hương hoa trái. [SINGLE-STORE]",
        "PERF|VS|perfume|VS-MIST-PURESEDUCTION-250|Victoria's Secret Fine Fragrance Mist Pure Seduction 250ml|520000|Xịt thơm hương mận đỏ, đã ngừng kinh doanh. [INACTIVE-HISTORY]",
    };

    static final String LONG_NAME = "Neutrogena Bộ Chăm Sóc Da Toàn Diện Dành Cho Da Dầu Mụn Nhạy Cảm Gồm Sữa Rửa Mặt Làm Sạch Sâu, Nước Cân Bằng Không Cồn, "
            + "Tinh Chất Dưỡng Ẩm Phục Hồi Hàng Rào Bảo Vệ Da Và Kem Chống Nắng Phổ Rộng Dùng Hằng Ngày Phiên Bản Giới Hạn Hộp Quà Tặng 2026";

    private void buildProducts() {
        long id = 1000;
        for (String line : CATALOG) {
            String[] p = line.split("\\|", 7);
            Product x = new Product();
            x.id = id++; x.categoryId = categoryIds.get(p[0]); x.brandId = brandIds.get(p[1]); x.imgType = p[2]; x.sku = p[3]; x.name = p[4];
            x.basePrice = Integer.parseInt(p[5]); x.desc = p[6];
            String d = p[6];
            x.storesWanted = 3 + (int) ((id * 7 + x.sku.length()) % 5); // 3..7
            if (d.contains("[INACTIVE-HISTORY]")) { x.status = "INACTIVE"; x.historyOnly = true; }
            if (d.contains("[HIDDEN-HISTORY]")) { x.status = "HIDDEN"; x.historyOnly = true; }
            if (d.contains("[INACTIVE]")) x.status = "INACTIVE";
            if (d.contains("[HIDDEN]")) x.status = "HIDDEN";
            if (d.contains("[SINGLE-STORE]")) x.storesWanted = 1;
            if (d.contains("[OUT-OF-STOCK]")) x.outOfStockEverywhere = true;
            if (d.contains("[EDGE-AMPERSAND]")) x.edge = "ampersand";
            if (d.contains("[EDGE-HTML]")) x.edge = "html";
            x.desc = d.replaceAll(" ?\\[[A-Z-]+\\]", "").trim();
            if ("html".equals(x.edge)) {
                x.desc = "Tinh chất chống nắng. <b>In đậm</b> & <script>alert('xss')</script> \"trích dẫn\" - kiểm tra escape HTML.";
            }
            m.products.add(x);
        }
        // Edge case: very long name (<= 255 chars) as a normal ACTIVE product.
        Product lng = new Product();
        lng.id = id++; lng.categoryId = categoryIds.get("SKIN"); lng.brandId = brandIds.get("NEUTROGENA"); lng.imgType = "serum";
        lng.sku = "NEU-SET-LONGNAME-2026"; lng.name = LONG_NAME; lng.basePrice = 899000; lng.edge = "longname"; lng.storesWanted = 4;
        lng.desc = "Bộ sản phẩm có tên rất dài, dùng để kiểm tra bố cục và cắt chuỗi.";
        m.products.add(lng);
        for (Product p : m.products) if (p.edge != null) m.productEdgeNote.put(p.id, p.edge);
        if (LONG_NAME.length() > 255) throw new IllegalStateException("LONG_NAME too long: " + LONG_NAME.length());
        // keep HIDDEN-HISTORY flagged separately: mark historyOnly already set
    }

    // ---- stores ----
    private void buildStores() {
        String[][] n = {
                {"OS-CAUGIAY", "OneShop Cầu Giấy", "88 Cầu Giấy, P. Dịch Vọng, Q. Cầu Giấy", "Hà Nội", "Cầu Giấy", "02411110001", "08:30 - 22:00 hằng ngày", "1", "1", "ACTIVE", "10"},
                {"OS-DONGDA", "OneShop Đống Đa", "215 Xã Đàn, P. Nam Đồng, Q. Đống Đa", "Hà Nội", "Đống Đa", "02411110002", "09:00 - 21:30 hằng ngày", "0", "1", "ACTIVE", "7"},
                {"OS-SONTRA", "OneShop Sơn Trà", "62 Võ Nguyên Giáp, Q. Sơn Trà", "Đà Nẵng", "Sơn Trà", "02361110003", "08:30 - 22:00 hằng ngày", "1", "1", "ACTIVE", "9"},
                {"OS-NINHKIEU", "OneShop Ninh Kiều", "17 Hòa Bình, P. Tân An, Q. Ninh Kiều", "Cần Thơ", "Ninh Kiều", "02921110004", "08:00 - 21:00 hằng ngày", "1", "0", "INACTIVE", "6"},
        };
        long id = 1000;
        for (String[] s : n) {
            Store x = new Store();
            x.id = id++; x.code = s[0]; x.name = s[1]; x.address = s[2]; x.province = s[3]; x.area = s[4]; x.phone = s[5]; x.hours = s[6];
            x.delivery = s[7].equals("1"); x.pickup = s[8].equals("1"); x.status = s[9]; x.weight = Integer.parseInt(s[10]); x.isNew = true;
            m.stores.add(x);
        }
        for (long sid : OLD_STORE_IDS) {
            Store o = new Store();
            o.id = sid; o.status = OLD_INACTIVE_STORES.contains(sid) ? "INACTIVE" : "ACTIVE";
            o.delivery = OLD_STORE_FLAGS.get(sid)[0]; o.pickup = OLD_STORE_FLAGS.get(sid)[1];
            o.weight = switch ((int) sid) { case 1 -> 40; case 2 -> 12; case 3 -> 10; case 4 -> 6; default -> 5; };
            m.allStoresById.put(sid, o);
        }
        for (Store s : m.stores) m.allStoresById.put(s.id, s);
    }

    // ---- users ----
    static final String[] FAMILY = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý", "Phan"};
    static final String[] GIVEN = {"Thị Mai", "Văn Hùng", "Thu Hằng", "Minh Tuấn", "Ngọc Anh", "Quốc Bảo", "Thanh Tâm", "Hoài Nam", "Khánh Linh", "Đức Huy",
            "Phương Thảo", "Gia Hân", "Tuấn Kiệt", "Bích Ngọc", "Hồng Nhung", "Anh Khoa", "Thùy Dung", "Trọng Nghĩa", "Mỹ Duyên", "Kim Ngân",
            "Yến Nhi", "Bảo Châu", "Thành Long", "Diễm My", "Nhật Minh", "Cẩm Tú", "Hữu Phước", "Ánh Tuyết", "Xuân Mai", "Công Danh", "Lan Hương", "Đình Phong"};
    static final String[] STREETS = {"Lê Lợi", "Nguyễn Huệ", "Trần Hưng Đạo", "Hai Bà Trưng", "Lý Thường Kiệt", "Điện Biên Phủ", "Cách Mạng Tháng Tám",
            "Nguyễn Trãi", "Phan Đăng Lưu", "Pasteur", "Võ Thị Sáu", "Nguyễn Văn Cừ", "Hoàng Diệu", "Lạc Long Quân", "Âu Cơ", "Cộng Hòa"};
    static final String[] CITIES = {"TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "Hà Nội", "Đà Nẵng", "Cần Thơ"};

    static String ascii(String s) {
        String t = Normalizer.normalize(s.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
    }

    private User user(long id, long role, String email, String userName, String fullName, String phone, String status, int createdAgo) {
        User u = new User(); u.id = id; u.roleId = role; u.email = email; u.userName = userName; u.fullName = fullName; u.phone = phone;
        u.status = status; u.createdAgo = createdAgo; m.users.add(u); return u;
    }

    private void assign(long userId, long storeId, String status, int ago) {
        Assignment a = new Assignment(); a.userId = userId; a.storeId = storeId; a.status = status; a.assignedAgo = ago; m.assignments.add(a);
    }

    private void buildUsers() {
        // staff 1001..1013 (1013 has no assignment); admin 1090
        String[] staffNames = {"Nguyễn Thị Hạnh", "Trần Văn Phúc", "Lê Thị Oanh", "Phạm Quang Vinh", "Hoàng Thị Lụa", "Đặng Văn Sơn", "Vũ Thị Thanh",
                "Bùi Minh Đạt", "Ngô Thị Hoa", "Dương Văn Lâm", "Lý Thị Kiều", "Phan Văn Trí", "Võ Thị Nhàn"};
        for (int i = 0; i < staffNames.length; i++) {
            long id = 1001 + i;
            String slug = ascii(staffNames[i]);
            user(id, 2, "staff." + slug + "@seed.oneshop.test", "seed_staff" + String.format("%02d", i + 1), staffNames[i],
                    "09800000" + String.format("%02d", i + 1), "ACTIVE", 200 * DAY / 100 + 130 * DAY + i * 100);
        }
        // assignments (user -> store): every ACTIVE store has >= 1 staff, two staff cover two stores, one revoked, one unassigned
        assign(1001, 1000, "ACTIVE", 180 * DAY); assign(1002, 1000, "ACTIVE", 170 * DAY);
        assign(1003, 1001, "ACTIVE", 175 * DAY); assign(1004, 1001, "ACTIVE", 160 * DAY);
        assign(1005, 1002, "ACTIVE", 170 * DAY); assign(1006, 1002, "ACTIVE", 150 * DAY);
        assign(1007, 1, "ACTIVE", 190 * DAY);    assign(1007, 2, "ACTIVE", 150 * DAY);  // two stores
        assign(1008, 3, "ACTIVE", 190 * DAY);    assign(1008, 1000, "ACTIVE", 140 * DAY); // two stores
        assign(1009, 4, "ACTIVE", 185 * DAY);
        assign(1010, 5, "ACTIVE", 185 * DAY);    // staff kept on the closed store
        assign(1011, 1003, "ACTIVE", 150 * DAY); // staff of the closed store
        assign(1012, 3, "INACTIVE", 200 * DAY);  // revoked assignment (worked at store 3 before)
        // 1013: unassigned
        user(1090, 3, "admin.phu@seed.oneshop.test", "seed_admin2", "Quản Trị Phụ OneShop", "0900000090", "ACTIVE", 210 * DAY);
        for (int i = 0; i < 32; i++) {
            String name = FAMILY[i % FAMILY.length] + " " + GIVEN[i];
            String status = (i == 30) ? "INACTIVE" : "ACTIVE"; // 1 locked account
            user(1100 + i, 1, String.format("kh%02d.%s@seed.oneshop.test", i + 1, ascii(GIVEN[i]).replace('.', '-')),
                    String.format("seed_kh%02d", i + 1), name, "0970001" + String.format("%03d", i + 1), status, 125 * DAY + (i * 700) % (60 * DAY));
        }
        // addresses: 44 = 32 + 12 extra (customer 1100 has 4)
        long aid = 1000;
        int[] extra = {0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        List<Integer> owners = new ArrayList<>();
        for (int i = 0; i < 32; i++) owners.add(i);
        for (int e : extra) owners.add(e);
        Map<Integer, Integer> perUser = new HashMap<>();
        for (int idx : owners) {
            User u = m.users.stream().filter(x -> x.id == 1100 + idx).findFirst().orElseThrow();
            int k = perUser.merge(idx, 1, Integer::sum);
            Address a = new Address();
            a.id = aid++; a.userId = u.id; a.receiver = (k == 1 || k % 2 == 0) ? u.fullName : FAMILY[(idx + k) % FAMILY.length] + " " + GIVEN[(idx + k * 5) % GIVEN.length];
            a.phone = (k == 1) ? u.phone : "0971" + String.format("%06d", idx * 100 + k);
            a.line = (10 + idx * 3 + k * 7) + " " + STREETS[(idx + k) % STREETS.length] + ", P." + (1 + (idx + k) % 14) + ", " + CITIES[(idx + k) % CITIES.length];
            a.isDefault = k == 1; a.createdAgo = u.createdAgo - k * 100;
            m.addresses.add(a);
        }
    }

    // ---- store products ----
    private void buildStoreProducts() {
        List<Store> all = new ArrayList<>(m.allStoresById.values());
        long id = 1000;
        for (Product p : m.products) {
            List<Store> pool = new ArrayList<>(all);
            Collections.shuffle(pool, rnd);
            // inactive-history products are sold in closed/active mix; single-store products keep the first one
            int n = p.storesWanted;
            if (p.historyOnly) n = Math.max(n, 3);
            List<Store> chosen = pool.subList(0, Math.min(n, pool.size()));
            if (n >= 3 && chosen.stream().noneMatch(s -> s.id == 1)) { // keep the flagship stocked for most products
                if (rnd.nextInt(100) < 80) chosen.set(0, m.allStoresById.get(1L));
            }
            double base = p.basePrice;
            boolean first = true;
            for (Store s : new LinkedHashSet<>(chosen)) {
                StoreProduct sp = new StoreProduct();
                sp.id = id++; sp.storeId = s.id; sp.productId = p.id;
                double premium = first ? 0 : rnd.nextInt(81) / 1000.0; // 0..8%
                first = false;
                sp.price = round1000(base * (1 + premium));
                if (sp.price < 1000) sp.price = 1000;
                sp.status = "ACTIVE";
                if (!"ACTIVE".equals(p.status)) sp.status = "INACTIVE";
                else if (rnd.nextInt(100) < 5) sp.status = "INACTIVE"; // a few stopped lines
                int r = rnd.nextInt(100);
                sp.targetZero = r < 10 || p.outOfStockEverywhere || "INACTIVE".equals(sp.status);
                m.storeProducts.add(sp);
            }
        }
        // price changes after some purchases
        for (StoreProduct sp : m.storeProducts) {
            if (rnd.nextInt(100) < 22) {
                double f = rnd.nextBoolean() ? 0.92 : 1.07;
                sp.oldPrice = Math.max(1000, round1000(sp.price * f));
                if (sp.oldPrice == sp.price) sp.oldPrice = sp.price + 5000;
                sp.priceChangeAgo = rint(20 * DAY, 70 * DAY);
            }
        }
        // inactive-history products must have at least one sp (they do, n>=3)
    }

    // ---- orders ----
    static final String DEL = "DELIVERY", PICK = "STORE_PICKUP";

    private static final class Spec {
        String status, ful, method, cancelKind; // cancelKind: FAIL_SYSTEM | CUSTOMER
        boolean open() { return !status.equals("COMPLETED") && !status.equals("CANCELLED"); }
        String combo() { return ful + "/" + method; }
    }

    private Spec spec(String status, String ful, String method, String cancel) {
        Spec s = new Spec(); s.status = status; s.ful = ful; s.method = method; s.cancelKind = cancel; return s;
    }

    private List<Spec> specs() {
        List<Spec> l = new ArrayList<>();
        BiConsumerN add = (st, ful, meth, n, cancel) -> { for (int i = 0; i < n; i++) l.add(spec(st, ful, meth, cancel)); };
        add.go("COMPLETED", DEL, "COD", 30, null); add.go("COMPLETED", DEL, "ONLINE", 18, null);
        add.go("COMPLETED", PICK, "PAY_AT_STORE", 24, null); add.go("COMPLETED", PICK, "ONLINE", 16, null);
        add.go("CANCELLED", DEL, "ONLINE", 5, "FAIL_SYSTEM"); add.go("CANCELLED", PICK, "ONLINE", 3, "FAIL_SYSTEM");
        add.go("CANCELLED", DEL, "ONLINE", 2, "CUSTOMER"); add.go("CANCELLED", PICK, "ONLINE", 2, "CUSTOMER");
        add.go("CANCELLED", DEL, "COD", 4, "CUSTOMER"); add.go("CANCELLED", PICK, "PAY_AT_STORE", 3, "CUSTOMER");
        add.go("PENDING_PAYMENT", DEL, "ONLINE", 4, null); add.go("PENDING_PAYMENT", PICK, "ONLINE", 4, null);
        for (String st : new String[]{"CONFIRMED", "PREPARING"}) {
            add.go(st, DEL, "COD", 3, null); add.go(st, DEL, "ONLINE", 2, null);
            add.go(st, PICK, "PAY_AT_STORE", 2, null); add.go(st, PICK, "ONLINE", 2, null);
        }
        for (String st : new String[]{"PACKED", "SHIPPING"}) { add.go(st, DEL, "COD", 5, null); add.go(st, DEL, "ONLINE", 4, null); }
        add.go("READY_FOR_PICKUP", PICK, "PAY_AT_STORE", 5, null); add.go("READY_FOR_PICKUP", PICK, "ONLINE", 4, null);
        return l;
    }

    private interface BiConsumerN { void go(String st, String ful, String meth, int n, String cancel); }

    private static final class Group { List<Spec> specs = new ArrayList<>(); }

    private List<Group> grouping(List<Spec> all) {
        Collections.shuffle(all, rnd);
        List<Spec> open = all.stream().filter(Spec::open).collect(Collectors.toCollection(ArrayList::new));
        List<Spec> term = all.stream().filter(s -> !s.open()).collect(Collectors.toCollection(ArrayList::new));
        List<Group> groups = new ArrayList<>();
        // 8 mixed sessions (1 open + 1..2 terminal) -> PARTIAL checkouts
        for (int i = 0; i < 8 && !open.isEmpty(); i++) {
            Group g = new Group(); Set<String> used = new HashSet<>();
            Spec o = open.remove(open.size() - 1); g.specs.add(o); used.add(o.combo());
            int more = 1 + i % 2;
            for (int k = 0; k < more; k++) { Spec t = takeDistinct(term, used); if (t != null) g.specs.add(t); }
            groups.add(g);
        }
        // triples / doubles from terminal and open pools
        int[] triples = {8, 3}; // terminal, open
        for (int i = 0; i < 12; i++) groups.addAll(makeMulti(i < triples[0] ? term : open, 3));
        for (int i = 0; i < 28; i++) groups.addAll(makeMulti(i < 18 ? term : open, 2));
        for (Spec s : term) { Group g = new Group(); g.specs.add(s); groups.add(g); }
        for (Spec s : open) { Group g = new Group(); g.specs.add(s); groups.add(g); }
        Collections.shuffle(groups, rnd);
        return groups;
    }

    private List<Group> makeMulti(List<Spec> pool, int size) {
        Group g = new Group(); Set<String> used = new HashSet<>();
        for (int k = 0; k < size; k++) { Spec t = takeDistinct(pool, used); if (t != null) g.specs.add(t); }
        if (g.specs.isEmpty()) return List.of();
        return List.of(g);
    }

    private Spec takeDistinct(List<Spec> pool, Set<String> used) {
        for (int i = pool.size() - 1; i >= 0; i--) {
            if (!used.contains(pool.get(i).combo())) { used.add(pool.get(i).combo()); return pool.remove(i); }
        }
        return null;
    }

    private static final class Ev { String status; int delta; String actor; String note; Ev(String s, int d, String a, String n) { status = s; delta = d; actor = a; note = n; } }

    /** Lifecycle events of an order with relative minute deltas; actor: SYS | STAFF | CUSTOMER. */
    private List<Ev> lifecycle(Spec s) {
        List<Ev> e = new ArrayList<>();
        boolean online = s.method.equals("ONLINE");
        if (online) {
            e.add(new Ev("PENDING_PAYMENT", 0, "SYS", "Tạo đơn ONLINE"));
            if (s.status.equals("CANCELLED")) {
                if ("FAIL_SYSTEM".equals(s.cancelKind)) e.add(new Ev("CANCELLED", rint(3, 25), "SYS", "Thanh toán ONLINE thất bại; hoàn tồn kho"));
                else e.add(new Ev("CANCELLED", rint(10, 600), "CUSTOMER", "Khách hàng hủy đơn trước khi chuẩn bị hàng"));
                return e;
            }
            if (!s.status.equals("PENDING_PAYMENT")) e.add(new Ev("CONFIRMED", rint(1, 15), "SYS", "Thanh toán ONLINE thành công"));
        } else {
            e.add(new Ev("CONFIRMED", 0, "SYS", "Hệ thống tự xác nhận đơn hợp lệ"));
            if (s.status.equals("CANCELLED")) { e.add(new Ev("CANCELLED", rint(20, 900), "CUSTOMER", "Khách hàng hủy đơn trước khi chuẩn bị hàng")); return e; }
        }
        List<String> flow = s.ful.equals(DEL)
                ? List.of("PREPARING", "PACKED", "SHIPPING", "COMPLETED")
                : List.of("PREPARING", "READY_FOR_PICKUP", "COMPLETED");
        for (String st : flow) {
            if (e.get(e.size() - 1).status.equals(s.status)) break;
            if (e.get(e.size() - 1).status.equals("PENDING_PAYMENT")) break; // pending stays pending
            int d = switch (st) {
                case "PREPARING" -> rint(20, 300);
                case "PACKED" -> rint(30, 240);
                case "SHIPPING" -> rint(30, 600);
                case "READY_FOR_PICKUP" -> rint(30, 480);
                default -> s.ful.equals(DEL) ? rint(300, 3 * DAY) : rint(60, 2 * DAY);
            };
            String note = switch (st) {
                case "COMPLETED" -> s.ful.equals(DEL)
                        ? (s.method.equals("COD") ? "Giao hàng thành công, đã thu COD" : "Giao hàng thành công")
                        : (s.method.equals("PAY_AT_STORE") ? "Khách nhận và thanh toán tại cửa hàng" : "Khách nhận hàng tại cửa hàng");
                default -> null;
            };
            e.add(new Ev(st, d, "STAFF", note));
            if (st.equals(s.status)) break;
        }
        return e;
    }

    private void buildOrders() {
        List<Spec> specs = specs();
        List<Group> groups = grouping(specs);
        List<User> customers = m.users.stream().filter(u -> u.roleId == 1).collect(Collectors.toList());
        Map<Long, List<Address>> addrByUser = m.addresses.stream().collect(Collectors.groupingBy(a -> a.userId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<StoreProduct>> spByStore = m.storeProducts.stream().collect(Collectors.groupingBy(s -> s.storeId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Product> prodById = m.productById();
        Set<String> usedCodes = new HashSet<>();
        // products that should show up in historic orders
        List<Product> historyProducts = m.products.stream().filter(p -> p.historyOnly).collect(Collectors.toList());
        int historyUse = 0;
        int forcedIdx = 0;

        for (Group g : groups) {
            // 1) timing
            List<List<Ev>> lives = new ArrayList<>();
            int maxSpan = 0;
            boolean anyOpen = false;
            for (Spec s : g.specs) {
                List<Ev> ev = lifecycle(s);
                lives.add(ev);
                int span = ev.stream().mapToInt(x -> x.delta).sum();
                maxSpan = Math.max(maxSpan, span);
                anyOpen |= s.open();
            }
            // 2) stores (distinct per session; closed stores only for old terminal orders)
            List<Long> usedStores = new ArrayList<>();
            boolean wantsClosed = false;
            long[] storeFor = new long[g.specs.size()];
            for (int i = 0; i < g.specs.size(); i++) {
                Spec s = g.specs.get(i);
                List<Store> cands = new ArrayList<>();
                for (Store st : m.allStoresById.values()) {
                    if (usedStores.contains(st.id)) continue;
                    if (s.ful.equals(DEL) ? !st.delivery : !st.pickup) continue;
                    if (st.status.equals("INACTIVE") && (anyOpen || s.open())) continue;
                    cands.add(st);
                }
                int total = cands.stream().mapToInt(c -> c.weight).sum();
                int r = rnd.nextInt(total);
                Store chosen = cands.get(0);
                for (Store c : cands) { r -= c.weight; if (r < 0) { chosen = c; break; } }
                storeFor[i] = chosen.id; usedStores.add(chosen.id);
                if (chosen.status.equals("INACTIVE")) wantsClosed = true;
            }
            boolean usesHistoryProduct = false;
            // 3) session time
            int minAgo = maxSpan + 12;
            int t0;
            if (anyOpen) t0 = minAgo + rint(0, 5 * DAY);
            else if (wantsClosed) t0 = Math.max(minAgo, rint(50 * DAY, 119 * DAY));
            else t0 = Math.max(minAgo, rint(2 * DAY, 119 * DAY + 600));
            // history-product orders must be old (product was withdrawn since)
            User cust = pick(customers);
            if (cust.status.equals("INACTIVE")) { // locked account: only old, finished orders
                if (anyOpen) { cust = customers.get(0); }
                else t0 = Math.max(t0, 40 * DAY);
            }
            t0 = Math.max(t0, cust.createdAgo > 0 ? 0 : 0);
            Checkout co = new Checkout(); co.id = nextCheckoutId++; co.userId = cust.id; co.createdAgo = t0;
            for (int i = 0; i < g.specs.size(); i++) {
                Spec s = g.specs.get(i);
                List<Ev> ev = lives.get(i);
                Order o = new Order();
                o.id = nextOrderId++; o.checkoutId = co.id; o.userId = cust.id; o.storeId = storeFor[i];
                o.fulfillment = s.ful; o.method = s.method; o.status = s.status; o.cancelKind = s.cancelKind;
                o.createdAgo = t0 - (i == 0 ? 0 : rint(0, 2));
                // address / receiver
                Address a = pick(addrByUser.get(cust.id));
                o.receiver = a.receiver; o.phone = a.phone;
                o.address = s.ful.equals(DEL) ? a.line : null;
                // items
                Store st = m.allStoresById.get(o.storeId);
                boolean old = o.createdAgo > 60 * DAY;
                List<StoreProduct> cands = spByStore.getOrDefault(o.storeId, List.of()).stream().filter(sp -> {
                    Product p = prodById.get(sp.productId);
                    if (p.historyOnly) return old && s.status.equals("COMPLETED");
                    if (!"ACTIVE".equals(p.status)) return false; // INACTIVE/HIDDEN without history stay out of orders
                    if (sp.status.equals("INACTIVE")) return old && s.status.equals("COMPLETED");
                    return true;
                }).collect(Collectors.toList());
                if (cands.isEmpty()) cands = spByStore.get(o.storeId);
                int nItems = 1 + rnd.nextInt(100) / 40 + (rnd.nextInt(100) < 10 ? 1 : 0); // 1..3
                Set<Long> chosen = new HashSet<>();
                List<StoreProduct> picks = new ArrayList<>();
                // force the withdrawn products into some old completed orders
                if (old && s.status.equals("COMPLETED") && forcedIdx < 9) {
                    Product hp = historyProducts.get(forcedIdx % historyProducts.size());
                    Optional<StoreProduct> sp = spByStore.getOrDefault(o.storeId, List.of()).stream().filter(x -> x.productId == hp.id).findFirst();
                    if (sp.isPresent()) { picks.add(sp.get()); chosen.add(sp.get().id); forcedIdx++; }
                }
                for (int tries = 0; picks.size() < nItems && tries < 20; tries++) {
                    StoreProduct sp = pick(cands);
                    if (chosen.add(sp.id)) picks.add(sp);
                }
                for (StoreProduct sp : picks) {
                    Item it = new Item();
                    it.storeProductId = sp.id; it.name = prodById.get(sp.productId).name;
                    it.unitPrice = (sp.oldPrice > 0 && o.createdAgo > sp.priceChangeAgo) ? sp.oldPrice : sp.price;
                    it.qty = rnd.nextInt(100) < 80 ? rint(1, 2) : rint(3, 4);
                    o.items.add(it);
                }
                o.total = o.items.stream().mapToInt(Item::subtotal).sum();
                // history
                int ago = o.createdAgo;
                int elapsed = 0;
                Hist prev = null;
                List<User> staffOfStore = staffFor(o.storeId, o.createdAgo);
                for (Ev e : ev) {
                    elapsed += e.delta;
                    Hist h = new Hist();
                    h.oldStatus = prev == null ? null : prev.newStatus; h.newStatus = e.status; h.note = e.note;
                    h.ago = ago - elapsed;
                    h.by = switch (e.actor) {
                        case "SYS" -> null;
                        case "CUSTOMER" -> cust.id;
                        default -> pick(staffOfStore).id;
                    };
                    o.history.add(h); prev = h;
                    if (e.status.equals("READY_FOR_PICKUP")) o.readyAgo = h.ago;
                    if (e.status.equals("COMPLETED") && s.ful.equals(PICK)) o.pickedAgo = h.ago;
                }
                o.updatedAgo = o.history.get(o.history.size() - 1).ago;
                if (o.updatedAgo < 1) o.updatedAgo = 1;
                // payment
                Payment p = new Payment();
                p.method = s.method; p.amount = o.total; p.createdAgo = o.createdAgo;
                boolean paidOnline = s.method.equals("ONLINE") && o.history.stream().anyMatch(h -> h.newStatus.equals("CONFIRMED"));
                if (s.status.equals("CANCELLED")) {
                    p.status = "FAILED"; p.txn = txn(o.id);
                    o.payStatus = "FAIL_SYSTEM".equals(s.cancelKind) ? "FAILED" : "UNPAID";
                } else if (paidOnline) {
                    p.status = "SUCCESS"; p.txn = txn(o.id);
                    Hist c = o.history.stream().filter(h -> h.newStatus.equals("CONFIRMED")).findFirst().get();
                    p.paidAgo = c.ago; o.payStatus = "PAID";
                } else if (s.status.equals("COMPLETED")) { // COD / PAY_AT_STORE collected on completion
                    p.status = "SUCCESS"; p.txn = txn(o.id); p.paidAgo = o.updatedAgo; o.payStatus = "PAID";
                } else { p.status = "PENDING"; o.payStatus = "UNPAID"; }
                o.payment = p;
                // pickup code (READY_FOR_PICKUP and COMPLETED pickup orders)
                if (s.ful.equals(PICK) && o.readyAgo >= 0) {
                    String code;
                    do {
                        StringBuilder b = new StringBuilder();
                        for (int k = 0; k < 8; k++) b.append(PICKUP_ALPHABET.charAt(rnd.nextInt(PICKUP_ALPHABET.length())));
                        code = b.toString();
                    } while (!usedCodes.add(o.storeId + ":" + code));
                    o.pickupCode = code;
                    for (Hist h : o.history) if (h.newStatus.equals("READY_FOR_PICKUP")) h.note = "Sinh pickup_code " + code;
                }
                co.orders.add(o);
            }
            co.total = co.orders.stream().mapToInt(x -> x.total).sum();
            boolean allCancelled = co.orders.stream().allMatch(x -> x.status.equals("CANCELLED"));
            boolean allDone = co.orders.stream().allMatch(x -> x.status.equals("COMPLETED") || x.status.equals("CANCELLED"));
            boolean someDone = co.orders.stream().anyMatch(x -> x.status.equals("COMPLETED") || x.status.equals("CANCELLED"));
            co.status = allCancelled ? "CANCELLED" : allDone ? "COMPLETED" : someDone ? "PARTIAL" : "CREATED";
            m.checkouts.add(co);
        }
        m.checkouts.sort(Comparator.comparingLong(c -> c.id));
    }

    private String txn(long orderId) {
        return "PAY-" + UUID.nameUUIDFromBytes(("seed-large-" + orderId).getBytes(StandardCharsets.UTF_8));
    }

    /** Staff who may act on an order of the store (any assignment); the revoked staff only on old orders of store 3. */
    private List<User> staffFor(long storeId, int createdAgo) {
        List<User> l = new ArrayList<>();
        Map<Long, User> byId = m.users.stream().collect(Collectors.toMap(u -> u.id, u -> u));
        for (Assignment a : m.assignments) {
            if (a.storeId != storeId || !byId.containsKey(a.userId)) continue;
            if (a.status.equals("INACTIVE") && createdAgo < 90 * DAY) continue;
            l.add(byId.get(a.userId));
        }
        long[] old = OLD_STORE_STAFF.get(storeId);
        if (old != null) for (long u : old) { User x = new User(); x.id = u; l.add(x); }
        if (l.isEmpty()) throw new IllegalStateException("no staff for store " + storeId);
        l.sort(Comparator.comparingLong(u -> u.id));
        return l;
    }

    // ---- inventory ----
    private static final String[] ADJUST_NOTES = {
            "Kiểm kê định kỳ: nhập thêm hàng về", "Kiểm kê: phát hiện chênh lệch thực tế", "Nhập hàng bổ sung từ kho tổng",
            "Điều chỉnh do hàng trưng bày hư hỏng", "Điều chỉnh do hàng hết hạn sử dụng", "Điều chuyển hàng giữa các chi nhánh", "Sửa sai sót ghi nhận tồn kho"};

    private void buildInventory() {
        Map<Long, StoreProduct> spMap = m.spById();
        Map<Long, List<Movement>> bySp = new LinkedHashMap<>();
        List<Order> orders = m.orders();
        for (Order o : orders) {
            for (Item it : o.items) {
                Movement mv = new Movement();
                mv.storeProductId = it.storeProductId; mv.type = "ORDER"; mv.change = -it.qty; mv.orderId = o.id; mv.ago = o.createdAgo; mv.seq = (int) nextMovementSeq++;
                mv.note = "Checkout #" + o.checkoutId + " - Order #" + o.id;
                bySp.computeIfAbsent(mv.storeProductId, k -> new ArrayList<>()).add(mv);
                if (o.status.equals("CANCELLED")) {
                    Hist c = o.history.get(o.history.size() - 1);
                    Movement cm = new Movement();
                    cm.storeProductId = it.storeProductId; cm.type = "CANCEL_ORDER"; cm.change = it.qty; cm.orderId = o.id; cm.ago = c.ago; cm.seq = (int) nextMovementSeq++;
                    cm.note = "FAILED".equals(o.payStatus) ? "Hoàn tồn do thanh toán ONLINE thất bại - Order #" + o.id
                            : "Hoàn tồn do khách hàng hủy đơn - Order #" + o.id;
                    bySp.get(it.storeProductId).add(cm);
                }
            }
        }
        // stock adjustments by staff of the right store (>= 30)
        List<StoreProduct> adjustable = m.storeProducts.stream().filter(s -> s.status.equals("ACTIVE") && !s.targetZero).collect(Collectors.toList());
        for (int i = 0; i < 48; i++) {
            StoreProduct sp = adjustable.get((i * 13 + 5) % adjustable.size());
            List<User> staff = staffFor(sp.storeId, 0);
            Movement mv = new Movement();
            mv.storeProductId = sp.id; mv.type = "STOCK_ADJUST"; mv.ago = rint(3 * DAY, 100 * DAY); mv.seq = (int) nextMovementSeq++;
            boolean add = rnd.nextInt(100) < 70;
            mv.change = add ? rint(5, 40) : -rint(1, 3);
            mv.staffId = pick(staff).id; mv.note = ADJUST_NOTES[i % ADJUST_NOTES.length];
            bySp.computeIfAbsent(sp.id, k -> new ArrayList<>()).add(mv);
        }
        for (StoreProduct sp : m.storeProducts) {
            int target;
            int r = rnd.nextInt(100);
            if (sp.targetZero) target = 0;
            else if (r < 17) target = rint(1, 5);      // ~15% of all stock rows are low
            else target = rint(6, 200);
            List<Movement> ms = bySp.getOrDefault(sp.id, new ArrayList<>());
            ms.sort(Comparator.comparingInt((Movement x) -> -x.ago).thenComparingInt(x -> x.seq));
            int net = ms.stream().mapToInt(x -> x.change).sum();
            int initial = target - net;
            int run = initial, min = initial;
            for (Movement x : ms) { run += x.change; min = Math.min(min, run); }
            if (min < 0) { initial -= min; }
            run = initial;
            for (Movement x : ms) { x.before = run; run += x.change; x.after = run; }
            int fin = run;
            if (sp.targetZero && fin > 0 && !ms.isEmpty()) {
                int last = ms.get(ms.size() - 1).ago;
                Movement z = new Movement();
                z.storeProductId = sp.id; z.type = "STOCK_ADJUST"; z.change = -fin; z.before = fin; z.after = 0;
                z.ago = Math.max(1, last - 60); z.seq = (int) nextMovementSeq++;
                z.staffId = pick(staffFor(sp.storeId, 0)).id; z.note = "Kiểm kê: ghi nhận hết hàng, xuất hủy phần còn lại";
                ms.add(z); run = 0; fin = 0;
            }
            if (sp.targetZero && fin > 0 && ms.isEmpty()) { fin = 0; initial = 0; }
            sp.initial = initial; sp.quantity = fin;
            sp.updatedAgo = ms.isEmpty() ? rint(DAY, 30 * DAY) : ms.get(ms.size() - 1).ago;
            m.movements.addAll(ms);
        }
        m.movements.sort(Comparator.comparingInt((Movement x) -> -x.ago).thenComparingInt(x -> x.seq));
    }

    // ---- carts ----
    private void buildCarts() {
        Map<Long, List<StoreProduct>> avail = m.storeProducts.stream()
                .filter(s -> s.status.equals("ACTIVE") && s.quantity >= 6
                        && "ACTIVE".equals(m.productById().get(s.productId).status)
                        && "ACTIVE".equals(m.allStoresById.get(s.storeId).status))
                .collect(Collectors.groupingBy(s -> s.storeId, LinkedHashMap::new, Collectors.toList()));
        List<Long> storeIds = new ArrayList<>(avail.keySet());
        long cartId = 1000;
        for (int i = 0; i < 10; i++) {
            Cart c = new Cart(); c.id = cartId++; c.userId = 1100 + 12 + i; c.createdAgo = rint(DAY, 20 * DAY); c.updatedAgo = Math.max(1, c.createdAgo - rint(1, 600));
            int nStores = rint(2, 3);
            List<Long> pool = new ArrayList<>(storeIds); Collections.shuffle(pool, rnd);
            Set<Long> seen = new HashSet<>();
            for (int k = 0; k < nStores; k++) {
                List<StoreProduct> l = avail.get(pool.get(k));
                int nItems = rint(1, 2);
                for (int j = 0; j < nItems; j++) {
                    StoreProduct sp = pick(l);
                    if (seen.add(sp.id)) c.items.add(new long[]{sp.id, rint(1, 3)});
                }
            }
            m.carts.add(c);
        }
    }

    // ---- reviews ----
    static final String[][] COMMENTS = {
            {"Sản phẩm kém, không đúng mô tả.", "Dùng thấy không hợp, bị kích ứng nhẹ.", "Không như mong đợi, mùi hơi nồng."},
            {"Tạm được, chưa thấy khác biệt nhiều.", "Giá hơi cao so với chất lượng.", "Bình thường, giao hàng nhanh."},
            {"Dùng ổn, sẽ thử thêm một thời gian.", "Chất lượng đúng giá tiền, đóng gói cẩn thận.", "Khá ổn nhưng mình mong mùi dịu hơn."},
            {"Dùng rất thích, da mịn hơn sau 2 tuần.", "Đóng gói kỹ, nhân viên cửa hàng nhiệt tình.", "Màu đẹp, lên môi tự nhiên, sẽ mua lại.", "Thấm nhanh, không bết dính, rất hài lòng."},
            {"Quá tuyệt vời, đã mua lần thứ ba!", "Sản phẩm chính hãng, dùng rất thích, giao nhanh.", "Rất đáng tiền, cả nhà đều dùng.", "Mùi thơm dễ chịu, hiệu quả rõ rệt."}};
    static final String[] REPLIES = {"Cảm ơn bạn đã tin tưởng OneShop, chúc bạn luôn xinh đẹp!", "OneShop xin ghi nhận góp ý và sẽ cải thiện dịch vụ.",
            "Rất vui vì bạn hài lòng với sản phẩm, hẹn gặp lại bạn tại cửa hàng.", "Cảm ơn bạn đã đánh giá. Nếu cần hỗ trợ, bạn liên hệ chi nhánh nhé."};

    private void buildReviews() {
        Map<Long, StoreProduct> spMap = m.spById();
        Map<String, Review> byKey = new LinkedHashMap<>();
        List<Order> done = m.orders().stream().filter(o -> o.status.equals("COMPLETED") && o.updatedAgo > 180).collect(Collectors.toList());
        Collections.shuffle(done, rnd);
        long rid = 1000;
        for (Order o : done) {
            for (Item it : o.items) {
                if (byKey.size() >= 64) break;
                long pid = spMap.get(it.storeProductId).productId;
                String key = o.userId + ":" + pid;
                if (byKey.containsKey(key)) continue;
                if (rnd.nextInt(100) >= 42) continue;
                Review r = new Review();
                r.id = rid++; r.userId = o.userId; r.productId = pid; r.orderId = o.id;
                int p = rnd.nextInt(100);
                r.rating = p < 42 ? 5 : p < 78 ? 4 : p < 90 ? 3 : p < 95 ? 2 : 1;
                String[] pool = COMMENTS[r.rating - 1];
                r.comment = pool[rnd.nextInt(pool.length)];
                r.createdAgo = Math.max(1, o.updatedAgo - rint(60, 4 * DAY));
                byKey.put(key, r);
            }
        }
        List<Review> list = new ArrayList<>(byKey.values());
        for (int i = 0; i < list.size(); i++) {
            Review r = list.get(i);
            if (i % 9 == 4) r.status = "HIDDEN";
            if (i % 6 == 1) {
                Order o = m.orders().stream().filter(x -> x.id == r.orderId).findFirst().get();
                List<User> staff = staffFor(o.storeId, 0);
                r.reply = REPLIES[i % REPLIES.length];
                r.repliedBy = (i % 12 == 1) ? Long.valueOf(1090) : Long.valueOf(pick(staff).id);
                r.repliedAgo = Math.max(1, r.createdAgo - rint(30, 2 * DAY));
            }
        }
        m.reviews.addAll(list);
        if (m.reviews.size() < 50) throw new IllegalStateException("only " + m.reviews.size() + " reviews");
    }

    // ---- image plan ----
    private void buildImagePlan() {
        // categories
        for (Category c : m.categories) { m.imageSource.put("category:" + c.slug, "stock:category-" + c.slug); m.imagePublicId.put("category:" + c.slug, PUBLIC_ID_ROOT + "/categories/" + c.slug); }
        String[][] oldCats = {{"cham-soc-da", "static:categories/category-skincare.webp"}, {"trang-diem", "static:categories/category-makeup.webp"}, {"cham-soc-toc", "static:categories/category-haircare.webp"}};
        for (String[] c : oldCats) { m.imageSource.put("category:" + c[0], c[1]); m.imagePublicId.put("category:" + c[0], PUBLIC_ID_ROOT + "/categories/" + c[0]); }
        // brands
        for (Brand b : m.brands) { m.imageSource.put("brand:" + b.slug, "logo:" + b.slug); m.imagePublicId.put("brand:" + b.slug, PUBLIC_ID_ROOT + "/brands/" + b.slug); }
        for (String b : new String[]{"bbia", "cocoon", "innisfree"}) { m.imageSource.put("brand:" + b, "logo:" + b); m.imagePublicId.put("brand:" + b, PUBLIC_ID_ROOT + "/brands/" + b); }
        // products (new + 04's seven)
        String[][] oldProducts = {{"BBIA-CHEEK-06", "blush"}, {"BBIA-CHEEK-08", "blush"}, {"COCOON-SERUM-30", "serum"}, {"INNI-TONER-200", "toner"},
                {"COCOON-SHAM-310", "shampoo"}, {"INNI-CLEANS-120", "cleanser"}, {"COCOON-LIP-05", "lipbalm"}};
        List<String[]> all = new ArrayList<>(Arrays.asList(oldProducts));
        for (Product p : m.products) all.add(new String[]{p.sku, p.imgType});
        int idx = 0;
        for (String[] p : all) {
            int count = 1 + (idx++ % 3);
            List<String> keys = new ArrayList<>();
            for (int n = 1; n <= count; n++) {
                String key = "product:" + p[0] + ":" + n;
                String src = "stock:" + p[1] + "-" + (((n - 1) % 2) + 1);
                m.imageSource.put(key, src);
                m.imagePublicId.put(key, PUBLIC_ID_ROOT + "/products/" + p[0].toLowerCase(Locale.ROOT) + "-" + n);
                keys.add(key);
            }
            m.productImageKeys.put(p[0], keys);
        }
    }

    // ---------------------------------------------------------------- validation

    /** Returns every violation of consistency rules 1-9 and of the minimum volumes. Empty list = consistent. */
    public static List<String> validate(Model m) {
        List<String> v = new ArrayList<>();
        Map<Long, StoreProduct> sp = m.spById();
        Map<Long, Product> prod = m.productById();
        Map<Long, User> usr = m.users.stream().collect(Collectors.toMap(u -> u.id, u -> u));
        List<Order> orders = m.orders();
        Map<Long, Order> ordById = orders.stream().collect(Collectors.toMap(o -> o.id, o -> o));
        Set<String> assignedPairs = m.assignments.stream().map(a -> a.userId + ":" + a.storeId).collect(Collectors.toSet());
        for (var e : OLD_STORE_STAFF.entrySet()) for (long u : e.getValue()) assignedPairs.add(u + ":" + e.getKey());
        Set<Long> adminIds = Set.of(1L, 1090L);

        // rule 1: stock chains
        Map<Long, List<Movement>> bySp = m.movements.stream().collect(Collectors.groupingBy(x -> x.storeProductId));
        for (StoreProduct s : m.storeProducts) {
            if (s.quantity < 0) v.add("R1 negative final quantity sp#" + s.id);
            List<Movement> l = new ArrayList<>(bySp.getOrDefault(s.id, List.of()));
            l.sort(Comparator.comparingInt((Movement x) -> -x.ago).thenComparingInt(x -> x.seq));
            Integer prevAfter = null;
            int sum = 0;
            for (Movement x : l) {
                if (x.before < 0 || x.after < 0) v.add("R1 negative stock in chain sp#" + s.id);
                if (x.after != x.before + x.change) v.add("R1 math sp#" + s.id);
                if (prevAfter != null && prevAfter != x.before) v.add("R1 chain break sp#" + s.id);
                if (x.type.equals("ORDER") && (x.change >= 0 || x.orderId == 0)) v.add("R1 ORDER sign sp#" + s.id);
                if (x.type.equals("CANCEL_ORDER") && (x.change <= 0 || x.orderId == 0)) v.add("R1 CANCEL sign sp#" + s.id);
                if (x.type.equals("STOCK_ADJUST") && (x.staffId == null || x.change == 0)) v.add("R1 ADJUST staff sp#" + s.id);
                if (x.type.equals("STOCK_ADJUST") && x.staffId != null && !assignedPairs.contains(x.staffId + ":" + s.storeId))
                    v.add("R1 ADJUST by staff of another store sp#" + s.id);
                if (x.note == null || x.note.isBlank()) v.add("R1 movement without note sp#" + s.id);
                prevAfter = x.after; sum += x.change;
            }
            if (!l.isEmpty()) {
                if (prevAfter != s.quantity) v.add("R1 final quantity mismatch sp#" + s.id + " chain=" + prevAfter + " sp=" + s.quantity);
                if (l.get(0).before + sum != s.quantity) v.add("R1 initial+sum mismatch sp#" + s.id);
            }
        }
        // rule 2/3: totals, store consistency
        for (Checkout c : m.checkouts) {
            int sum = 0;
            for (Order o : c.orders) {
                int t = 0;
                for (Item it : o.items) {
                    if (it.subtotal() != it.unitPrice * it.qty) v.add("R2 subtotal order#" + o.id);
                    t += it.subtotal();
                    StoreProduct s = sp.get(it.storeProductId);
                    if (s == null) { v.add("R3 unknown store product in order#" + o.id); continue; }
                    if (s.storeId != o.storeId) v.add("R3 item from another store in order#" + o.id);
                }
                if (t != o.total) v.add("R2 order total mismatch order#" + o.id);
                if (o.items.isEmpty()) v.add("R2 order without items #" + o.id);
                sum += o.total;
                Store st = m.allStoresById.get(o.storeId);
                if (st == null) v.add("R3 unknown store order#" + o.id);
                else if (o.fulfillment.equals(DEL) ? !st.delivery : !st.pickup) v.add("R3 store does not support fulfillment order#" + o.id);
            }
            if (sum != c.total) v.add("R2 checkout total mismatch checkout#" + c.id);
        }
        // rule 4: history chains
        Map<String, Set<String>> graphDel = Map.of("PENDING_PAYMENT", Set.of("CONFIRMED", "CANCELLED"), "CONFIRMED", Set.of("PREPARING", "CANCELLED"),
                "PREPARING", Set.of("PACKED"), "PACKED", Set.of("SHIPPING"), "SHIPPING", Set.of("COMPLETED"));
        Map<String, Set<String>> graphPick = Map.of("PENDING_PAYMENT", Set.of("CONFIRMED", "CANCELLED"), "CONFIRMED", Set.of("PREPARING", "CANCELLED"),
                "PREPARING", Set.of("READY_FOR_PICKUP"), "READY_FOR_PICKUP", Set.of("COMPLETED"));
        for (Order o : orders) {
            Map<String, Set<String>> g = o.fulfillment.equals(DEL) ? graphDel : graphPick;
            String cur = null; int lastAgo = Integer.MAX_VALUE;
            for (Hist h : o.history) {
                if (!Objects.equals(h.oldStatus, cur)) v.add("R4 chain break order#" + o.id);
                if (cur == null) {
                    boolean ok = o.method.equals("ONLINE") ? h.newStatus.equals("PENDING_PAYMENT") : h.newStatus.equals("CONFIRMED");
                    if (!ok) v.add("R4 invalid first status order#" + o.id);
                } else if (!g.getOrDefault(cur, Set.of()).contains(h.newStatus)) v.add("R4 invalid transition " + cur + "->" + h.newStatus + " order#" + o.id);
                if (cur != null && cur.equals("CONFIRMED") && h.newStatus.equals("CANCELLED") && o.method.equals("ONLINE")) v.add("R4 online cancel from CONFIRMED order#" + o.id);
                if (h.ago >= lastAgo) v.add("R4 time not increasing order#" + o.id);
                if (h.ago > o.createdAgo) v.add("R4 history before creation order#" + o.id);
                lastAgo = h.ago;
                if (h.by != null) {
                    boolean customerCancel = h.newStatus.equals("CANCELLED") && h.by == o.userId;
                    boolean staff = assignedPairs.contains(h.by + ":" + o.storeId);
                    if (!customerCancel && !staff) v.add("R4 invalid actor " + h.by + " order#" + o.id);
                    if (!customerCancel && usr.containsKey(h.by) && usr.get(h.by).roleId != 2) v.add("R4 actor not staff order#" + o.id);
                }
                cur = h.newStatus;
            }
            if (!Objects.equals(cur, o.status)) v.add("R4 final status != history order#" + o.id);
        }
        // rule 5: payments
        for (Order o : orders) {
            Payment p = o.payment;
            if (p == null) { v.add("R5 no payment order#" + o.id); continue; }
            if (p.amount != o.total) v.add("R5 payment amount order#" + o.id);
            if (!p.method.equals(o.method)) v.add("R5 payment method order#" + o.id);
            if (o.method.equals("PAY_AT_STORE") && !o.fulfillment.equals(PICK)) v.add("R5 PAY_AT_STORE on delivery order#" + o.id);
            if (o.method.equals("COD") && !o.fulfillment.equals(DEL)) v.add("R5 COD on pickup order#" + o.id);
            boolean confirmed = o.history.stream().anyMatch(h -> h.newStatus.equals("CONFIRMED"));
            if (o.method.equals("ONLINE")) {
                if (p.status.equals("SUCCESS") != (confirmed && !o.status.equals("CANCELLED"))) v.add("R5 online success/confirmed order#" + o.id);
                if (p.status.equals("FAILED") && !o.status.equals("CANCELLED")) v.add("R5 online failed but not cancelled order#" + o.id);
                if (o.status.equals("PENDING_PAYMENT") && !p.status.equals("PENDING")) v.add("R5 pending order payment order#" + o.id);
            } else {
                if (o.status.equals("COMPLETED") != (p.status.equals("SUCCESS"))) v.add("R5 offline paid iff completed order#" + o.id);
            }
            if (p.status.equals("SUCCESS") != o.payStatus.equals("PAID")) v.add("R5 payStatus/payment order#" + o.id);
            if (o.payStatus.equals("FAILED") && !(o.method.equals("ONLINE") && o.status.equals("CANCELLED"))) v.add("R5 FAILED payStatus order#" + o.id);
            if (p.status.equals("SUCCESS") && p.paidAgo < 0) v.add("R5 success without paid_at order#" + o.id);
            if (p.paidAgo > p.createdAgo) v.add("R5 paid before created order#" + o.id);
        }
        // rule 6: pickup codes
        Set<String> codes = new HashSet<>();
        for (Order o : orders) {
            boolean shouldHave = o.fulfillment.equals(PICK) && o.history.stream().anyMatch(h -> h.newStatus.equals("READY_FOR_PICKUP"));
            if (shouldHave != (o.pickupCode != null)) v.add("R6 pickup code presence order#" + o.id);
            if (o.pickupCode != null && !codes.add(o.storeId + ":" + o.pickupCode)) v.add("R6 duplicate pickup code order#" + o.id);
            if (o.pickupCode != null && !o.pickupCode.matches("[A-HJ-NP-Z2-9]{8}")) v.add("R6 pickup code format order#" + o.id);
            if (o.fulfillment.equals(DEL) && (o.pickupCode != null || o.readyAgo >= 0 || o.pickedAgo >= 0)) v.add("R6 pickup data on delivery order#" + o.id);
            if (o.readyAgo >= 0 != shouldHave) v.add("R6 ready_at order#" + o.id);
            if ((o.pickedAgo >= 0) != (o.fulfillment.equals(PICK) && o.status.equals("COMPLETED"))) v.add("R6 picked_up_at order#" + o.id);
            if (o.pickedAgo >= 0 && o.pickedAgo > o.readyAgo) v.add("R6 picked before ready order#" + o.id);
        }
        // rule 7: cancellations restore exact stock
        for (Order o : orders) {
            Map<Long, Integer> ordered = new HashMap<>(), restored = new HashMap<>();
            for (Movement x : m.movements) if (x.orderId == o.id) {
                if (x.type.equals("ORDER")) ordered.merge(x.storeProductId, -x.change, Integer::sum);
                if (x.type.equals("CANCEL_ORDER")) restored.merge(x.storeProductId, x.change, Integer::sum);
            }
            Map<Long, Integer> expected = new HashMap<>();
            o.items.forEach(i -> expected.merge(i.storeProductId, i.qty, Integer::sum));
            if (!expected.equals(ordered)) v.add("R7 ORDER movements != items order#" + o.id);
            if (o.status.equals("CANCELLED") ? !expected.equals(restored) : !restored.isEmpty()) v.add("R7 CANCEL movements order#" + o.id);
            if (o.status.equals("CANCELLED") && !o.history.get(o.history.size() - 1).newStatus.equals("CANCELLED")) v.add("R7 cancel history order#" + o.id);
        }
        // rule 8: reviews
        Set<String> pairs = new HashSet<>();
        for (Review r : m.reviews) {
            Order o = ordById.get(r.orderId);
            if (r.rating < 1 || r.rating > 5) v.add("R8 rating review#" + r.id);
            if (!pairs.add(r.userId + ":" + r.productId)) v.add("R8 duplicate review#" + r.id);
            if (o == null || o.userId != r.userId || !o.status.equals("COMPLETED")) { v.add("R8 order not completed/owned review#" + r.id); continue; }
            if (o.items.stream().noneMatch(i -> sp.get(i.storeProductId).productId == r.productId)) v.add("R8 order lacks product review#" + r.id);
            if (r.createdAgo > o.updatedAgo) v.add("R8 review before completion review#" + r.id);
            if (r.reply != null) {
                if (r.reply.isBlank() || r.repliedBy == null || r.repliedAgo > r.createdAgo) v.add("R8 reply review#" + r.id);
                if (r.repliedBy != null && !adminIds.contains(r.repliedBy) && !assignedPairs.contains(r.repliedBy + ":" + o.storeId)) v.add("R8 reply by foreign staff review#" + r.id);
            }
        }
        // rule 9: no future times, updated >= created
        for (Order o : orders) {
            if (o.createdAgo < 0 || o.updatedAgo < 0) v.add("R9 future order#" + o.id);
            if (o.updatedAgo > o.createdAgo) v.add("R9 updated before created order#" + o.id);
            if (o.createdAgo > 120 * DAY + 600) v.add("R9 order older than window order#" + o.id);
        }
        for (Movement x : m.movements) if (x.ago < 0) v.add("R9 future movement");
        for (Review r : m.reviews) if (r.createdAgo < 0 || r.repliedAgo < 0) v.add("R9 future review#" + r.id);

        // ---- minimum volumes (Part 1) ----
        int brandsTotal = m.brands.size() + 3, catsTotal = m.categories.size() + 3, prodTotal = m.products.size() + 7;
        if (m.brands.size() < 17) v.add("COUNT brands new " + m.brands.size());
        if (brandsTotal < 20) v.add("COUNT brands total " + brandsTotal);
        if (catsTotal != 8) v.add("COUNT categories total " + catsTotal);
        if (m.products.size() < 80 || prodTotal < 87) v.add("COUNT products " + m.products.size());
        Map<Long, Long> perCat = m.products.stream().collect(Collectors.groupingBy(p -> p.categoryId, Collectors.counting()));
        for (long cid : new long[]{1, 2, 3, 1000, 1001, 1002, 1003, 1004}) if (perCat.getOrDefault(cid, 0L) < 6) v.add("COUNT category " + cid + " has < 6 products");
        Map<Long, Long> perBrand = m.products.stream().collect(Collectors.groupingBy(p -> p.brandId, Collectors.counting()));
        for (Brand b : m.brands) { long c = perBrand.getOrDefault(b.id, 0L); if (c < 2 || c > 8) v.add("COUNT brand " + b.name + " has " + c + " products"); }
        if (m.products.stream().filter(p -> !p.status.equals("ACTIVE")).count() < 4) v.add("COUNT inactive/hidden products < 4");
        Set<Long> soldProducts = orders.stream().flatMap(o -> o.items.stream()).map(i -> sp.get(i.storeProductId).productId).collect(Collectors.toSet());
        if (m.products.stream().filter(p -> !p.status.equals("ACTIVE") && soldProducts.contains(p.id)).count() < 2) v.add("COUNT inactive products with history < 2");
        if (m.products.stream().map(p -> p.sku).distinct().count() != m.products.size()) v.add("COUNT duplicate SKU");
        if (m.stores.size() != 4) v.add("COUNT new stores");
        if (m.allStoresById.values().stream().filter(s -> s.status.equals("INACTIVE")).count() < 2) v.add("COUNT inactive stores < 2");
        if (m.allStoresById.values().stream().map(s -> s.delivery + "/" + s.pickup).distinct().count() < 3) v.add("COUNT store flag mix");
        if (m.storeProducts.size() < 350) v.add("COUNT store products " + m.storeProducts.size());
        Map<Long, Long> spPerProduct = m.storeProducts.stream().collect(Collectors.groupingBy(s -> s.productId, Collectors.counting()));
        for (Product p : m.products) if ("ACTIVE".equals(p.status) && p.storesWanted > 1) {
            long c = spPerProduct.getOrDefault(p.id, 0L); if (c < 3 || c > 7) v.add("COUNT product " + p.sku + " in " + c + " stores");
        }
        if (spPerProduct.values().stream().filter(c -> c == 1).count() < 5) v.add("COUNT single-store products < 5");
        long oosEverywhere = m.products.stream().filter(p -> m.storeProducts.stream().filter(s -> s.productId == p.id).allMatch(s -> s.quantity == 0)
                && m.storeProducts.stream().anyMatch(s -> s.productId == p.id)).count();
        if (oosEverywhere < 3) v.add("COUNT out-of-stock-everywhere products " + oosEverywhere);
        if (m.storeProducts.stream().filter(s -> s.status.equals("INACTIVE")).count() < 3) v.add("COUNT inactive store products");
        long zeros = m.storeProducts.stream().filter(s -> s.quantity == 0).count();
        if (zeros < m.storeProducts.size() * 0.08) v.add("COUNT zero-stock share too low");
        long low = m.storeProducts.stream().filter(s -> s.quantity >= 1 && s.quantity <= 5).count();
        if (low < m.storeProducts.size() * 0.10) v.add("COUNT low-stock share too low " + low);
        for (StoreProduct s : m.storeProducts) { // price spread 0-8% between stores
            double base = prod.get(s.productId).basePrice;
            if (s.price < base * 0.995 || s.price > base * 1.0805 + 1000) v.add("COUNT price spread sp#" + s.id);
        }
        long customers = m.users.stream().filter(u -> u.roleId == 1).count();
        long staff = m.users.stream().filter(u -> u.roleId == 2).count();
        if (customers < 30) v.add("COUNT customers " + customers);
        if (staff < 12) v.add("COUNT staff " + staff);
        if (m.users.stream().noneMatch(u -> u.roleId == 3)) v.add("COUNT admin");
        if (m.users.stream().noneMatch(u -> u.status.equals("INACTIVE") && u.roleId == 1)) v.add("COUNT locked customer");
        if (m.addresses.size() < 40) v.add("COUNT addresses " + m.addresses.size());
        if (m.addresses.stream().collect(Collectors.groupingBy(a -> a.userId, Collectors.counting())).values().stream().noneMatch(c -> c > 1)) v.add("COUNT multi-address user");
        Set<Long> activeStores = m.allStoresById.values().stream().filter(s -> s.status.equals("ACTIVE")).map(s -> s.id).collect(Collectors.toSet());
        for (long sid : activeStores) {
            boolean has = m.assignments.stream().anyMatch(a -> a.storeId == sid && a.status.equals("ACTIVE")) || OLD_STORE_STAFF.containsKey(sid);
            if (!has) v.add("COUNT active store without staff " + sid);
        }
        if (m.assignments.stream().collect(Collectors.groupingBy(a -> a.userId, Collectors.counting())).values().stream().filter(c -> c >= 2).count() < 2) v.add("COUNT staff with 2 stores < 2");
        if (m.assignments.stream().noneMatch(a -> a.status.equals("INACTIVE"))) v.add("COUNT revoked assignment");
        Set<Long> assignedUsers = m.assignments.stream().map(a -> a.userId).collect(Collectors.toSet());
        if (m.users.stream().noneMatch(u -> u.roleId == 2 && !assignedUsers.contains(u.id))) v.add("COUNT unassigned staff");
        long multiStoreCarts = m.carts.stream().filter(c -> c.items.stream().map(i -> sp.get(i[0]).storeId).distinct().count() >= 2).count();
        if (multiStoreCarts < 8) v.add("COUNT multi-store carts " + multiStoreCarts);
        if (m.checkouts.size() + 3 < 90) v.add("COUNT checkouts " + (m.checkouts.size() + 3));
        if (orders.size() + 5 < 150) v.add("COUNT orders " + (orders.size() + 5));
        long multi = m.checkouts.stream().filter(c -> c.orders.size() >= 2).count();
        if (multi < 25) v.add("COUNT multi-order sessions " + multi);
        for (Checkout c : m.checkouts) if (c.orders.size() >= 2) {
            long stores = c.orders.stream().map(o -> o.storeId).distinct().count();
            long combos = c.orders.stream().map(o -> o.fulfillment + "/" + o.method).distinct().count();
            if (stores != c.orders.size() || combos != c.orders.size()) v.add("COUNT session " + c.id + " stores/combos not distinct");
        }
        int minAgo = orders.stream().mapToInt(o -> o.createdAgo).min().orElse(0), maxAgo = orders.stream().mapToInt(o -> o.createdAgo).max().orElse(0);
        if (maxAgo < 110 * DAY) v.add("COUNT orders span " + maxAgo / DAY + " days < 110");
        Map<String, Long> byStatus = orders.stream().collect(Collectors.groupingBy(o -> o.status, Collectors.counting()));
        long totalOrders = orders.size() + 5;
        long completed = byStatus.getOrDefault("COMPLETED", 0L) + 1;
        if (Math.abs(completed * 100.0 / totalOrders - 55) > 6) v.add("COUNT COMPLETED share " + completed * 100.0 / totalOrders);
        if (Math.abs(byStatus.getOrDefault("CANCELLED", 0L) * 100.0 / totalOrders - 12) > 3) v.add("COUNT CANCELLED share");
        if (Math.abs(byStatus.getOrDefault("PENDING_PAYMENT", 0L) * 100.0 / totalOrders - 5) > 2) v.add("COUNT PENDING share");
        for (String st : List.of("CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "READY_FOR_PICKUP")) {
            long n = byStatus.getOrDefault(st, 0L);
            long stores = orders.stream().filter(o -> o.status.equals(st)).map(o -> o.storeId).distinct().count();
            if (n < 8) v.add("COUNT status " + st + " has " + n);
            if (stores < 3) v.add("COUNT status " + st + " in " + stores + " stores");
        }
        if (orders.stream().noneMatch(o -> o.method.equals("ONLINE") && o.payStatus.equals("FAILED") && o.status.equals("CANCELLED"))) v.add("COUNT online failed cancelled");
        Map<Long, Long> perStore = orders.stream().collect(Collectors.groupingBy(o -> o.storeId, Collectors.counting()));
        if (perStore.values().stream().noneMatch(c -> c >= 58)) v.add("COUNT no store with >= 60 orders (max " + perStore.values().stream().max(Long::compare).orElse(0L) + ")");
        if (orders.stream().filter(o -> o.fulfillment.equals(DEL)).count() == 0 || orders.stream().filter(o -> o.fulfillment.equals(PICK)).count() == 0) v.add("COUNT fulfillment mix");
        long closedHistory = orders.stream().filter(o -> m.allStoresById.get(o.storeId).status.equals("INACTIVE")).count();
        if (closedHistory < 3) v.add("COUNT inactive store history " + closedHistory);
        long repriced = orders.stream().flatMap(o -> o.items.stream().map(i -> new int[]{i.unitPrice, sp.get(i.storeProductId).price})).filter(a -> a[0] != a[1]).count();
        if (repriced < 5) v.add("COUNT repriced order items " + repriced);
        if (m.reviews.size() < 50) v.add("COUNT reviews " + m.reviews.size());
        if (m.reviews.stream().filter(r -> r.status.equals("HIDDEN")).count() < 6) v.add("COUNT hidden reviews");
        if (m.reviews.stream().filter(r -> r.reply != null).count() < 8) v.add("COUNT replied reviews");
        long good = m.reviews.stream().filter(r -> r.rating >= 4).count();
        if (good * 100 / Math.max(1, m.reviews.size()) < 60) v.add("COUNT rating skew");
        long adj = m.movements.stream().filter(x -> x.type.equals("STOCK_ADJUST")).count();
        if (adj < 30) v.add("COUNT STOCK_ADJUST " + adj);
        if (m.movements.stream().noneMatch(x -> x.type.equals("CANCEL_ORDER"))) v.add("COUNT CANCEL_ORDER");
        if (m.products.stream().noneMatch(p -> p.name.length() > 200)) v.add("COUNT long name product");
        if (m.products.stream().noneMatch(p -> p.name.contains("&")) || m.products.stream().noneMatch(p -> p.name.contains("'"))) v.add("COUNT special char names");
        if (m.products.stream().noneMatch(p -> p.desc != null && p.desc.contains("<script>"))) v.add("COUNT html description");
        for (Product p : m.products) if (p.name.length() > 255 || p.sku.length() > 50) v.add("COUNT length " + p.sku);
        if (minAgo < 0) v.add("R9 negative age");
        return v;
    }

    // ---------------------------------------------------------------- SQL output

    static String q(String s) { return s == null ? "NULL" : "N'" + s.replace("'", "''") + "'"; }
    static String t(int ago) { return "DATEADD(MINUTE, -" + Math.max(0, ago) + ", @now)"; }
    static String tn(int ago) { return ago < 0 ? "NULL" : t(ago); }

    private static String header(String file, String what) {
        return "/* =============================================================================\n"
                + "   OneShop - large development seed - " + file + "\n"
                + "   " + what + "\n"
                + "   GENERATED by src/test/java/com/oneshop/seed/LargeSeedGenerator.java - do not edit by hand.\n"
                + "   Dev data only (all IDs >= 1000; 04_seed_data.sql rows are kept). See docs/SEED_LARGE.md.\n"
                + "   ========================================================================== */\n"
                + "SET NOCOUNT ON;\nSET QUOTED_IDENTIFIER ON;\nSET XACT_ABORT ON;\nGO\n"
                + "DECLARE @now DATETIME2(0) = SYSDATETIME();\n";
    }

    /** Image manifest: key -> {public_id, secure_url}. May be empty (images not uploaded yet). */
    public static Map<String, String[]> readManifest(Path file) throws IOException {
        Map<String, String[]> map = new HashMap<>();
        if (!Files.exists(file)) return map;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            if (c.length >= 3 && !c[0].isBlank()) map.put(c[0], new String[]{c[1], c[2]});
        }
        return map;
    }

    private static void insert(StringBuilder sb, String table, String cols, List<String> rows, boolean identity) {
        if (rows.isEmpty()) return;
        if (identity) sb.append("SET IDENTITY_INSERT dbo.").append(table).append(" ON;\n");
        for (int i = 0; i < rows.size(); i += 50) {
            sb.append("INSERT INTO dbo.").append(table).append(" (").append(cols).append(") VALUES\n");
            sb.append(String.join(",\n", rows.subList(i, Math.min(rows.size(), i + 50)))).append(";\n");
        }
        if (identity) sb.append("SET IDENTITY_INSERT dbo.").append(table).append(" OFF;\n");
    }

    private static String reseed(String table, String idCol) {
        return "DECLARE @m_" + table + " BIGINT = (SELECT ISNULL(MAX(" + idCol + "), 0) FROM dbo." + table + ");\n"
                + "DBCC CHECKIDENT ('dbo." + table + "', RESEED, @m_" + table + ") WITH NO_INFOMSGS;\n";
    }

    /** Writes 01..04 SQL, image plan and statistics. Returns the generated file names. */
    public static List<String> write(Model m, Path dir, Map<String, String[]> manifest) throws IOException {
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("images"));
        Map<Long, StoreProduct> spMap = m.spById();
        List<String> files = new ArrayList<>();

        // ---- 01 reference data
        StringBuilder a = new StringBuilder(header("01_reference_data.sql", "Cleanup of previous large seed, brands, categories, stores, users, assignments, addresses"));
        a.append("""

                BEGIN TRANSACTION;
                /* Remove the previous large seed (id >= 1000) so the script can be re-run; 04_seed_data.sql rows (id < 1000) stay. */
                DELETE FROM dbo.reviews WHERE review_id >= 1000 OR user_id >= 1000 OR product_id >= 1000;
                DELETE FROM dbo.order_status_history WHERE order_id >= 1000;
                DELETE FROM dbo.payments WHERE order_id >= 1000;
                DELETE FROM dbo.order_items WHERE order_id >= 1000;
                DELETE FROM dbo.inventory_movements WHERE reference_order_id >= 1000 OR store_product_id >= 1000 OR staff_id >= 1000;
                DELETE FROM dbo.orders WHERE order_id >= 1000;
                DELETE FROM dbo.checkout_sessions WHERE checkout_id >= 1000 OR user_id >= 1000;
                DELETE FROM dbo.cart_items WHERE cart_id >= 1000 OR store_product_id >= 1000;
                DELETE FROM dbo.carts WHERE cart_id >= 1000 OR user_id >= 1000;
                DELETE FROM dbo.store_products WHERE store_product_id >= 1000 OR product_id >= 1000 OR store_id >= 1000;
                DELETE FROM dbo.product_images WHERE image_id >= 1000 OR product_id >= 1000;
                DELETE FROM dbo.products WHERE product_id >= 1000;
                DELETE FROM dbo.brands WHERE brand_id >= 1000;
                DELETE FROM dbo.categories WHERE category_id >= 1000;
                DELETE FROM dbo.staff_store_assignments WHERE assignment_id >= 1000 OR user_id >= 1000 OR store_id >= 1000;
                DELETE FROM dbo.stores WHERE store_id >= 1000;
                DELETE FROM dbo.customer_addresses WHERE address_id >= 1000 OR user_id >= 1000;
                DELETE FROM dbo.users WHERE user_id >= 1000;
                """);
        // brands
        List<String> rows = new ArrayList<>();
        for (Brand b : m.brands) {
            String[] img = manifest.get("brand:" + b.slug);
            rows.add("(" + b.id + ", " + q(b.name) + ", " + q(b.desc) + ", " + (img == null ? "NULL" : q(img[1])) + ", " + (img == null ? "NULL" : q(img[0])) + ", " + q(b.status) + ")");
        }
        a.append("\n/* brands */\n");
        insert(a, "brands", "brand_id, name, description, logo_url, logo_public_id, status", rows, true);
        // logos of 04's brands
        for (Map.Entry<Long, String> e : Map.of(1L, "bbia", 2L, "cocoon", 3L, "innisfree").entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String[] img = manifest.get("brand:" + e.getValue());
            if (img != null) a.append("UPDATE dbo.brands SET logo_url = ").append(q(img[1])).append(", logo_public_id = ").append(q(img[0])).append(" WHERE brand_id = ").append(e.getKey()).append(";\n");
        }
        // categories
        rows = new ArrayList<>();
        for (Category c : m.categories) {
            String[] img = manifest.get("category:" + c.slug);
            rows.add("(" + c.id + ", " + q(c.name) + ", " + q(c.desc) + ", " + (img == null ? "NULL" : q(img[1])) + ", " + (img == null ? "NULL" : q(img[0])) + ", " + q(c.status) + ")");
        }
        a.append("\n/* categories */\n");
        insert(a, "categories", "category_id, name, description, image_url, image_public_id, status", rows, true);
        String[][] oldCats = {{"1", "cham-soc-da"}, {"2", "trang-diem"}, {"3", "cham-soc-toc"}};
        for (String[] c : oldCats) {
            String[] img = manifest.get("category:" + c[1]);
            if (img != null) a.append("UPDATE dbo.categories SET image_url = ").append(q(img[1])).append(", image_public_id = ").append(q(img[0])).append(" WHERE category_id = ").append(c[0]).append(";\n");
        }
        // stores
        rows = new ArrayList<>();
        for (Store s : m.stores) {
            rows.add("(" + s.id + ", " + q(s.code) + ", " + q(s.name) + ", " + q(s.address) + ", " + q(s.province) + ", " + q(s.area) + ", " + q(s.phone) + ", " + q(s.hours)
                    + ", " + (s.delivery ? 1 : 0) + ", " + (s.pickup ? 1 : 0) + ", " + q(s.status) + ")");
        }
        a.append("\n/* stores */\n");
        insert(a, "stores", "store_id, code, name, address, province_city, area, phone, opening_hours, delivery_enabled, pickup_enabled, status", rows, true);
        // users
        rows = new ArrayList<>();
        for (User u : m.users) {
            rows.add("(" + u.id + ", " + u.roleId + ", " + q(u.email) + ", " + q(u.userName) + ", " + q(PASSWORD_HASH) + ", " + q(u.fullName) + ", " + q(u.phone) + ", " + q(u.status)
                    + ", " + t(u.createdAgo) + ", " + t(u.createdAgo) + ")");
        }
        a.append("\n/* users (password hash = hash of the 04 accounts, i.e. OneShop@123) */\n");
        insert(a, "users", "user_id, role_id, email, user_name, password_hash, full_name, phone, status, created_at, updated_at", rows, true);
        rows = new ArrayList<>();
        long asid = 1000;
        for (Assignment s : m.assignments) rows.add("(" + (asid++) + ", " + s.userId + ", " + s.storeId + ", " + q(s.status) + ", " + t(s.assignedAgo) + ")");
        a.append("\n/* staff_store_assignments */\n");
        insert(a, "staff_store_assignments", "assignment_id, user_id, store_id, status, assigned_at", rows, true);
        rows = new ArrayList<>();
        for (Address d : m.addresses) rows.add("(" + d.id + ", " + d.userId + ", " + q(d.receiver) + ", " + q(d.phone) + ", " + q(d.line) + ", " + (d.isDefault ? 1 : 0) + ", " + t(d.createdAgo) + ")");
        a.append("\n/* customer_addresses */\n");
        insert(a, "customer_addresses", "address_id, user_id, receiver_name, receiver_phone, address_line, is_default, created_at", rows, true);
        a.append("\nCOMMIT TRANSACTION;\nGO\nPRINT 'Large seed 01_reference_data loaded.';\nGO\n");
        write(dir.resolve("01_reference_data.sql"), a); files.add("01_reference_data.sql");

        // ---- 02 catalog
        StringBuilder c = new StringBuilder(header("02_catalog.sql", "Products, product images, store products (final quantities of the movement chains)"));
        c.append("\nBEGIN TRANSACTION;\n");
        rows = new ArrayList<>();
        for (Product p : m.products) {
            rows.add("(" + p.id + ", " + p.categoryId + ", " + p.brandId + ", " + q(p.sku) + ", " + q(p.name) + ", " + q(p.desc) + ", " + q(p.status) + ", " + t(150 * DAY) + ", " + t(150 * DAY) + ")");
        }
        c.append("/* products */\n");
        insert(c, "products", "product_id, category_id, brand_id, sku, name, description, status, created_at, updated_at", rows, true);
        // product images: new products and 04's seven
        rows = new ArrayList<>();
        long imgId = 1000;
        Map<String, Long> oldProductIds = new LinkedHashMap<>();
        String[] oldSkus = {"BBIA-CHEEK-06", "BBIA-CHEEK-08", "COCOON-SERUM-30", "INNI-TONER-200", "COCOON-SHAM-310", "INNI-CLEANS-120", "COCOON-LIP-05"};
        for (int i = 0; i < oldSkus.length; i++) oldProductIds.put(oldSkus[i], (long) (i + 1));
        List<Object[]> imageOwners = new ArrayList<>();
        for (String sku : oldSkus) imageOwners.add(new Object[]{sku, oldProductIds.get(sku)});
        for (Product p : m.products) imageOwners.add(new Object[]{p.sku, p.id});
        for (Object[] o : imageOwners) {
            List<String> keys = m.productImageKeys.get((String) o[0]);
            for (int n = 0; n < keys.size(); n++) {
                String[] img = manifest.get(keys.get(n));
                if (img == null) continue;
                rows.add("(" + (imgId++) + ", " + o[1] + ", " + q(img[1]) + ", " + q(img[0]) + ", " + (n == 0 ? 1 : 0) + ", " + n + ", " + t(150 * DAY) + ")");
            }
        }
        c.append("\n/* product_images */\n");
        if (rows.isEmpty()) c.append("-- no image manifest entries: product images skipped (run the image pipeline, see docs/SEED_LARGE.md)\n");
        insert(c, "product_images", "image_id, product_id, image_url, public_id, is_primary, sort_order, created_at", rows, true);
        rows = new ArrayList<>();
        for (StoreProduct s : m.storeProducts) rows.add("(" + s.id + ", " + s.storeId + ", " + s.productId + ", " + s.price + ", " + s.quantity + ", " + q(s.status) + ", " + t(s.updatedAgo) + ")");
        c.append("\n/* store_products */\n");
        insert(c, "store_products", "store_product_id, store_id, product_id, price, quantity, status, updated_at", rows, true);
        c.append("\nCOMMIT TRANSACTION;\nGO\nPRINT 'Large seed 02_catalog loaded.';\nGO\n");
        write(dir.resolve("02_catalog.sql"), c); files.add("02_catalog.sql");

        // ---- 03 transactions
        StringBuilder t3 = new StringBuilder(header("03_transactions.sql", "Carts, checkout sessions, orders, items, payments, status history, inventory movements"));
        t3.append("\nBEGIN TRANSACTION;\n");
        rows = new ArrayList<>();
        for (Cart ct : m.carts) rows.add("(" + ct.id + ", " + ct.userId + ", " + t(ct.createdAgo) + ", " + t(ct.updatedAgo) + ")");
        t3.append("/* carts */\n");
        insert(t3, "carts", "cart_id, user_id, created_at, updated_at", rows, true);
        rows = new ArrayList<>();
        long ciid = 1000;
        for (Cart ct : m.carts) for (long[] it : ct.items) rows.add("(" + (ciid++) + ", " + ct.id + ", " + it[0] + ", " + it[1] + ", " + t(ct.createdAgo) + ", " + t(ct.updatedAgo) + ")");
        t3.append("\n/* cart_items */\n");
        insert(t3, "cart_items", "cart_item_id, cart_id, store_product_id, quantity, created_at, updated_at", rows, true);
        rows = new ArrayList<>();
        for (Checkout co : m.checkouts) rows.add("(" + co.id + ", " + co.userId + ", " + co.total + ", " + q(co.status) + ", " + t(co.createdAgo) + ")");
        t3.append("\n/* checkout_sessions */\n");
        insert(t3, "checkout_sessions", "checkout_id, user_id, total_amount, status, created_at", rows, true);
        List<Order> orders = m.orders();
        rows = new ArrayList<>();
        for (Order o : orders) {
            rows.add("(" + o.id + ", " + o.checkoutId + ", " + o.userId + ", " + o.storeId + ", " + q(o.fulfillment) + ", " + q(o.method) + ", " + q(o.payStatus) + ", " + q(o.status)
                    + ", " + q(o.receiver) + ", " + q(o.phone) + ", " + q(o.address) + ", " + q(o.pickupCode) + ", " + tn(o.readyAgo) + ", " + tn(o.pickedAgo) + ", " + o.total
                    + ", " + t(o.createdAgo) + ", " + t(o.updatedAgo) + ")");
        }
        t3.append("\n/* orders */\n");
        insert(t3, "orders", "order_id, checkout_id, user_id, store_id, fulfillment_type, payment_method, payment_status, order_status, receiver_name, receiver_phone, shipping_address, pickup_code, ready_at, picked_up_at, total_amount, created_at, updated_at", rows, true);
        rows = new ArrayList<>();
        long oiid = 1000;
        for (Order o : orders) for (Item it : o.items) rows.add("(" + (oiid++) + ", " + o.id + ", " + it.storeProductId + ", " + q(it.name) + ", " + it.unitPrice + ", " + it.qty + ", " + it.subtotal() + ")");
        t3.append("\n/* order_items (unit_price/name are snapshots; some differ from the current price: TC-14) */\n");
        insert(t3, "order_items", "order_item_id, order_id, store_product_id, product_name, unit_price, quantity, subtotal", rows, true);
        rows = new ArrayList<>();
        long pid = 1000;
        for (Order o : orders) {
            Payment p = o.payment;
            rows.add("(" + (pid++) + ", " + o.id + ", " + q(p.method) + ", " + p.amount + ", " + q(p.status) + ", " + q(p.txn) + ", " + tn(p.paidAgo) + ", " + t(p.createdAgo) + ")");
        }
        t3.append("\n/* payments */\n");
        insert(t3, "payments", "payment_id, order_id, method, amount, status, transaction_code, paid_at, created_at", rows, true);
        rows = new ArrayList<>();
        long hid = 1000;
        for (Order o : orders) for (Hist h : o.history) {
            rows.add("(" + (hid++) + ", " + o.id + ", " + q(h.oldStatus) + ", " + q(h.newStatus) + ", " + (h.by == null ? "NULL" : h.by) + ", " + q(h.note) + ", " + t(h.ago) + ")");
        }
        t3.append("\n/* order_status_history */\n");
        insert(t3, "order_status_history", "history_id, order_id, old_status, new_status, changed_by_user_id, note, changed_at", rows, true);
        rows = new ArrayList<>();
        long mid = 1000;
        for (Movement x : m.movements) {
            rows.add("(" + (mid++) + ", " + x.storeProductId + ", " + q(x.type) + ", " + x.change + ", " + x.before + ", " + x.after + ", " + (x.orderId == 0 ? "NULL" : x.orderId)
                    + ", " + (x.staffId == null ? "NULL" : x.staffId) + ", " + q(x.note) + ", " + t(x.ago) + ")");
        }
        t3.append("\n/* inventory_movements */\n");
        insert(t3, "inventory_movements", "movement_id, store_product_id, type, quantity_change, quantity_before, quantity_after, reference_order_id, staff_id, note, created_at", rows, true);
        t3.append("\nCOMMIT TRANSACTION;\nGO\nPRINT 'Large seed 03_transactions loaded.';\nGO\n");
        write(dir.resolve("03_transactions.sql"), t3); files.add("03_transactions.sql");

        // ---- 04 reviews + identity reseed
        StringBuilder r = new StringBuilder(header("04_reviews.sql", "Reviews (only customers with a COMPLETED order containing the product), then identity reseed"));
        r.append("\nBEGIN TRANSACTION;\n");
        rows = new ArrayList<>();
        for (Review v : m.reviews) {
            rows.add("(" + v.id + ", " + v.userId + ", " + v.productId + ", " + v.rating + ", " + q(v.comment) + ", " + q(v.status) + ", " + t(v.createdAgo) + ", " + v.orderId
                    + ", " + q(v.reply) + ", " + (v.repliedBy == null ? "NULL" : v.repliedBy) + ", " + (v.reply == null ? "NULL" : t(v.repliedAgo)) + ")");
        }
        insert(r, "reviews", "review_id, user_id, product_id, rating, comment, status, created_at, order_id, reply, replied_by, replied_at", rows, true);
        r.append("\nCOMMIT TRANSACTION;\nGO\n/* identity reseed: new rows created by the application never collide with seed ids */\n");
        for (String[] tb : new String[][]{{"users", "user_id"}, {"customer_addresses", "address_id"}, {"stores", "store_id"}, {"staff_store_assignments", "assignment_id"},
                {"categories", "category_id"}, {"brands", "brand_id"}, {"products", "product_id"}, {"product_images", "image_id"}, {"store_products", "store_product_id"},
                {"carts", "cart_id"}, {"cart_items", "cart_item_id"}, {"checkout_sessions", "checkout_id"}, {"orders", "order_id"}, {"order_items", "order_item_id"},
                {"payments", "payment_id"}, {"inventory_movements", "movement_id"}, {"order_status_history", "history_id"}, {"reviews", "review_id"}}) {
            r.append(reseed(tb[0], tb[1]));
        }
        r.append("GO\nPRINT 'Large seed 04_reviews loaded.';\nGO\n");
        write(dir.resolve("04_reviews.sql"), r); files.add("04_reviews.sql");

        // ---- image plan
        StringBuilder plan = new StringBuilder("key,source_key,public_id\n");
        for (var e : m.imageSource.entrySet()) plan.append(e.getKey()).append(',').append(e.getValue()).append(',').append(m.imagePublicId.get(e.getKey())).append('\n');
        Files.writeString(dir.resolve("images/image_plan.csv"), plan.toString(), StandardCharsets.UTF_8);
        files.add("images/image_plan.csv");
        Files.writeString(dir.resolve("SEED_STATS.md"), stats(m), StandardCharsets.UTF_8);
        files.add("SEED_STATS.md");
        return files;
    }

    private static void write(Path p, StringBuilder sb) throws IOException {
        // UTF-8 without BOM, LF line endings: same as the repo's other SQL files (sqlcmd -f 65001).
        Files.write(p, sb.toString().replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
    }

    public static String stats(Model m) {
        List<Order> orders = m.orders();
        StringBuilder s = new StringBuilder("# Large seed - generated statistics\n\nGenerated by `LargeSeedGenerator` (seed " + SEED + "); large seed only, rows of 04_seed_data.sql are not counted unless stated.\n\n");
        s.append("| Item | Count |\n|---|---|\n");
        s.append("| Brands (new / total) | ").append(m.brands.size()).append(" / ").append(m.brands.size() + 3).append(" |\n");
        s.append("| Categories (new / total) | ").append(m.categories.size()).append(" / ").append(m.categories.size() + 3).append(" |\n");
        s.append("| Products (new / total) | ").append(m.products.size()).append(" / ").append(m.products.size() + 7).append(" |\n");
        s.append("| Products not ACTIVE | ").append(m.products.stream().filter(p -> !p.status.equals("ACTIVE")).count()).append(" |\n");
        s.append("| Stores (new / total) | ").append(m.stores.size()).append(" / ").append(m.stores.size() + 5).append(" |\n");
        s.append("| Store products | ").append(m.storeProducts.size()).append(" |\n");
        s.append("| Customers | ").append(m.users.stream().filter(u -> u.roleId == 1).count()).append(" |\n");
        s.append("| Staff | ").append(m.users.stream().filter(u -> u.roleId == 2).count()).append(" |\n");
        s.append("| Addresses | ").append(m.addresses.size()).append(" |\n");
        s.append("| Carts | ").append(m.carts.size()).append(" |\n");
        s.append("| Checkout sessions (large / incl. 04) | ").append(m.checkouts.size()).append(" / ").append(m.checkouts.size() + 3).append(" |\n");
        s.append("| Orders (large / incl. 04) | ").append(orders.size()).append(" / ").append(orders.size() + 5).append(" |\n");
        s.append("| Reviews | ").append(m.reviews.size()).append(" |\n");
        s.append("| Inventory movements | ").append(m.movements.size()).append(" |\n");
        s.append("| Image keys (products / brands / categories) | ")
                .append(m.imageSource.keySet().stream().filter(k -> k.startsWith("product:")).count()).append(" / ")
                .append(m.imageSource.keySet().stream().filter(k -> k.startsWith("brand:")).count()).append(" / ")
                .append(m.imageSource.keySet().stream().filter(k -> k.startsWith("category:")).count()).append(" |\n\n");
        s.append("## Orders by status (large seed)\n\n| Status | Orders | Stores |\n|---|---|---|\n");
        for (String st : List.of("COMPLETED", "CANCELLED", "PENDING_PAYMENT", "CONFIRMED", "PREPARING", "PACKED", "SHIPPING", "READY_FOR_PICKUP")) {
            s.append("| ").append(st).append(" | ").append(orders.stream().filter(o -> o.status.equals(st)).count()).append(" | ")
                    .append(orders.stream().filter(o -> o.status.equals(st)).map(o -> o.storeId).distinct().count()).append(" |\n");
        }
        s.append("\n## Orders by store (large seed)\n\n| Store | Orders |\n|---|---|\n");
        orders.stream().collect(Collectors.groupingBy(o -> o.storeId, TreeMap::new, Collectors.counting())).forEach((k, v) -> s.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        return s.toString();
    }

    public static final String BEGIN_MARK = "/* ===== BEGIN LARGE SEED (generated by LargeSeedGenerator - do not edit by hand) ===== */";
    public static final String END_MARK = "/* ===== END LARGE SEED ===== */";

    /**
     * Writes the large seed INTO the single seed file ({@code database/04_seed_data.sql}): the generated block sits between
     * BEGIN_MARK and END_MARK after the base data and is replaced on every run (idempotent).
     */
    public static void mergeInto(Model m, Path seedFile, Path imagesDir, Map<String, String[]> manifest) throws IOException {
        Path tmp = Files.createTempDirectory("seedmerge");
        write(m, tmp, manifest);
        StringBuilder block = new StringBuilder(BEGIN_MARK).append('\n');
        for (String f : List.of("01_reference_data.sql", "02_catalog.sql", "03_transactions.sql", "04_reviews.sql")) {
            StringBuilder kept = new StringBuilder();
            for (String line : Files.readString(tmp.resolve(f)).split("\n", -1)) {
                // per-file cleanup (re-run support) is not needed: the full seed file starts from an empty database
                if (line.startsWith("DELETE FROM") || line.startsWith("/* Remove the previous large seed")) continue;
                kept.append(line).append('\n');
            }
            block.append(kept.toString().stripTrailing()).append('\n');
        }
        block.append(END_MARK).append('\n');
        String base = Files.readString(seedFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int a = base.indexOf(BEGIN_MARK);
        if (a >= 0) {
            int b = base.indexOf(END_MARK, a);
            if (b < 0) throw new IOException("END marker missing in " + seedFile);
            String tail = base.substring(b + END_MARK.length()).stripLeading();
            base = base.substring(0, a).stripTrailing() + "\n\n" + block + (tail.isEmpty() ? "" : "\n" + tail);
        } else {
            base = base.stripTrailing() + "\n\n" + block;
        }
        Files.writeString(seedFile, base, StandardCharsets.UTF_8);
        Files.createDirectories(imagesDir);
        Files.copy(tmp.resolve("images/image_plan.csv"), imagesDir.resolve("image_plan.csv"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(tmp.resolve("SEED_STATS.md"), imagesDir.getParent().resolve("SEED_STATS.md"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------------------------------------------------------------- CLI

    public static void main(String[] args) throws IOException {
        Path seedFile = Path.of("database/04_seed_data.sql");
        Path images = Path.of("database/seed_large/images");
        boolean allowMissing = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed-file")) seedFile = Path.of(args[++i]);
            else if (args[i].equals("--images")) images = Path.of(args[++i]);
            else if (args[i].equals("--allow-missing-images")) allowMissing = true;
        }
        Model m = generate();
        List<String> violations = validate(m);
        if (!violations.isEmpty()) {
            System.err.println("Seed model is inconsistent (" + violations.size() + " violations):");
            violations.stream().limit(40).forEach(x -> System.err.println("  - " + x));
            System.exit(2);
        }
        Map<String, String[]> manifest = readManifest(images.resolve("image_manifest.csv"));
        List<String> missing = m.imageSource.keySet().stream().filter(k -> !manifest.containsKey(k)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            System.err.println("WARNING: " + missing.size() + " of " + m.imageSource.size() + " image keys are not in image_manifest.csv.");
            if (!allowMissing) {
                System.err.println("Upload the images first (SeedImages.java --upload), or pass --allow-missing-images.");
                System.exit(3);
            }
        }
        mergeInto(m, seedFile, images, manifest);
        System.out.println("Large seed merged into " + seedFile);
    }
}
