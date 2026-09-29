/* =============================================================================
   OneShop - Phase 2 - File 02: 19 bang + PK / FK / UNIQUE / CHECK
   Nguon: Roadmap V2 muc 7.1 - 7.4.
   Script co the chay lai: xoa cac bang (theo thu tu FK) roi tao lai.
   Khong xoa cung du lieu co lich su o runtime (BR-17) - viec DROP o day chi de dung lai schema.
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

/* ---------- Drop (con truoc, cha sau) ---------- */
DROP TABLE IF EXISTS dbo.reviews;
DROP TABLE IF EXISTS dbo.order_status_history;
DROP TABLE IF EXISTS dbo.payments;
DROP TABLE IF EXISTS dbo.order_items;
DROP TABLE IF EXISTS dbo.inventory_movements;
DROP TABLE IF EXISTS dbo.orders;
DROP TABLE IF EXISTS dbo.checkout_sessions;
DROP TABLE IF EXISTS dbo.cart_items;
DROP TABLE IF EXISTS dbo.carts;
DROP TABLE IF EXISTS dbo.store_products;
DROP TABLE IF EXISTS dbo.product_images;
DROP TABLE IF EXISTS dbo.products;
DROP TABLE IF EXISTS dbo.brands;
DROP TABLE IF EXISTS dbo.categories;
DROP TABLE IF EXISTS dbo.staff_store_assignments;
DROP TABLE IF EXISTS dbo.stores;
DROP TABLE IF EXISTS dbo.customer_addresses;
DROP TABLE IF EXISTS dbo.users;
DROP TABLE IF EXISTS dbo.roles;
GO

/* ============================================================================
   1. roles  - CUSTOMER / STAFF / ADMIN
   ========================================================================== */
CREATE TABLE dbo.roles (
    role_id     BIGINT IDENTITY(1,1) NOT NULL,
    name        NVARCHAR(30)         NOT NULL,
    description NVARCHAR(200)        NULL,
    CONSTRAINT PK_roles PRIMARY KEY (role_id),
    CONSTRAINT UQ_roles_name UNIQUE (name),
    CONSTRAINT CK_roles_name CHECK (name IN (N'CUSTOMER', N'STAFF', N'ADMIN'))
);
GO

/* ============================================================================
   2. users
   ========================================================================== */
CREATE TABLE dbo.users (
    user_id       BIGINT IDENTITY(1,1) NOT NULL,
    role_id       BIGINT               NOT NULL,
    email         NVARCHAR(255)        NOT NULL,
    password_hash NVARCHAR(255)        NOT NULL,
    full_name     NVARCHAR(150)        NOT NULL,
    phone         NVARCHAR(20)         NULL,
    status        NVARCHAR(20)         NOT NULL CONSTRAINT DF_users_status DEFAULT N'ACTIVE',
    created_at    DATETIME2(0)         NOT NULL CONSTRAINT DF_users_created_at DEFAULT SYSDATETIME(),
    updated_at    DATETIME2(0)         NOT NULL CONSTRAINT DF_users_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_users PRIMARY KEY (user_id),
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT FK_users_role FOREIGN KEY (role_id) REFERENCES dbo.roles (role_id),
    CONSTRAINT CK_users_status CHECK (status IN (N'ACTIVE', N'INACTIVE'))
);
GO

/* ============================================================================
   3. customer_addresses
   ========================================================================== */
CREATE TABLE dbo.customer_addresses (
    address_id     BIGINT IDENTITY(1,1) NOT NULL,
    user_id        BIGINT               NOT NULL,
    receiver_name  NVARCHAR(150)        NOT NULL,
    receiver_phone NVARCHAR(20)         NOT NULL,
    address_line   NVARCHAR(500)        NOT NULL,
    is_default     BIT                  NOT NULL CONSTRAINT DF_customer_addresses_default DEFAULT 0,
    created_at     DATETIME2(0)         NOT NULL CONSTRAINT DF_customer_addresses_created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_customer_addresses PRIMARY KEY (address_id),
    CONSTRAINT FK_customer_addresses_user FOREIGN KEY (user_id) REFERENCES dbo.users (user_id)
);
GO

/* ============================================================================
   4. stores  - chi nhanh (Store Finder metadata, khong toa do/map)
   ========================================================================== */
CREATE TABLE dbo.stores (
    store_id         BIGINT IDENTITY(1,1) NOT NULL,
    code             NVARCHAR(30)         NOT NULL,
    name             NVARCHAR(150)        NOT NULL,
    address          NVARCHAR(500)        NOT NULL,
    province_city    NVARCHAR(100)        NOT NULL,
    area             NVARCHAR(100)        NOT NULL,
    phone            NVARCHAR(20)         NULL,
    opening_hours    NVARCHAR(200)        NULL,
    delivery_enabled BIT                  NOT NULL CONSTRAINT DF_stores_delivery DEFAULT 1,
    pickup_enabled   BIT                  NOT NULL CONSTRAINT DF_stores_pickup DEFAULT 1,
    status           NVARCHAR(20)         NOT NULL CONSTRAINT DF_stores_status DEFAULT N'ACTIVE',
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_stores_created_at DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_stores_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_stores PRIMARY KEY (store_id),
    CONSTRAINT UQ_stores_code UNIQUE (code),
    CONSTRAINT CK_stores_status CHECK (status IN (N'ACTIVE', N'INACTIVE'))
);
GO

/* ============================================================================
   5. staff_store_assignments  - phan cong Staff -> Store (BR-14)
   ========================================================================== */
CREATE TABLE dbo.staff_store_assignments (
    assignment_id BIGINT IDENTITY(1,1) NOT NULL,
    user_id       BIGINT               NOT NULL,
    store_id      BIGINT               NOT NULL,
    status        NVARCHAR(20)         NOT NULL CONSTRAINT DF_ssa_status DEFAULT N'ACTIVE',
    assigned_at   DATETIME2(0)         NOT NULL CONSTRAINT DF_ssa_assigned_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_staff_store_assignments PRIMARY KEY (assignment_id),
    CONSTRAINT UQ_ssa_user_store UNIQUE (user_id, store_id),
    CONSTRAINT FK_ssa_user  FOREIGN KEY (user_id)  REFERENCES dbo.users (user_id),
    CONSTRAINT FK_ssa_store FOREIGN KEY (store_id) REFERENCES dbo.stores (store_id),
    CONSTRAINT CK_ssa_status CHECK (status IN (N'ACTIVE', N'INACTIVE'))
);
GO

/* ============================================================================
   6. categories
   ========================================================================== */
CREATE TABLE dbo.categories (
    category_id BIGINT IDENTITY(1,1) NOT NULL,
    name        NVARCHAR(150)        NOT NULL,
    description NVARCHAR(500)        NULL,
    status      NVARCHAR(20)         NOT NULL CONSTRAINT DF_categories_status DEFAULT N'ACTIVE',
    CONSTRAINT PK_categories PRIMARY KEY (category_id),
    CONSTRAINT UQ_categories_name UNIQUE (name),
    CONSTRAINT CK_categories_status CHECK (status IN (N'ACTIVE', N'HIDDEN'))
);
GO

/* ============================================================================
   7. brands  - anh thuong hieu luu tren Cloudinary: chi URL + public_id
   ========================================================================== */
CREATE TABLE dbo.brands (
    brand_id       BIGINT IDENTITY(1,1) NOT NULL,
    name           NVARCHAR(150)        NOT NULL,
    description    NVARCHAR(500)        NULL,
    logo_url       NVARCHAR(500)        NULL,
    logo_public_id NVARCHAR(255)        NULL,
    status         NVARCHAR(20)         NOT NULL CONSTRAINT DF_brands_status DEFAULT N'ACTIVE',
    CONSTRAINT PK_brands PRIMARY KEY (brand_id),
    CONSTRAINT UQ_brands_name UNIQUE (name),
    CONSTRAINT CK_brands_status CHECK (status IN (N'ACTIVE', N'HIDDEN'))
);
GO

/* ============================================================================
   8. products  - MOT SKU ban duoc cu the, du lieu chung toan chuoi (BR-01)
   ========================================================================== */
CREATE TABLE dbo.products (
    product_id  BIGINT IDENTITY(1,1) NOT NULL,
    category_id BIGINT               NOT NULL,
    brand_id    BIGINT               NOT NULL,
    sku         NVARCHAR(50)         NOT NULL,
    name        NVARCHAR(255)        NOT NULL,
    description NVARCHAR(MAX)        NULL,
    status      NVARCHAR(20)         NOT NULL CONSTRAINT DF_products_status DEFAULT N'ACTIVE',
    created_at  DATETIME2(0)         NOT NULL CONSTRAINT DF_products_created_at DEFAULT SYSDATETIME(),
    updated_at  DATETIME2(0)         NOT NULL CONSTRAINT DF_products_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_products PRIMARY KEY (product_id),
    CONSTRAINT UQ_products_sku UNIQUE (sku),
    CONSTRAINT FK_products_category FOREIGN KEY (category_id) REFERENCES dbo.categories (category_id),
    CONSTRAINT FK_products_brand    FOREIGN KEY (brand_id)    REFERENCES dbo.brands (brand_id),
    CONSTRAINT CK_products_status CHECK (status IN (N'ACTIVE', N'INACTIVE', N'HIDDEN'))
);
GO

/* ============================================================================
   9. product_images  - Cloudinary: chi URL + public_id, khong luu binary
   ========================================================================== */
CREATE TABLE dbo.product_images (
    image_id   BIGINT IDENTITY(1,1) NOT NULL,
    product_id BIGINT               NOT NULL,
    image_url  NVARCHAR(500)        NOT NULL,
    public_id  NVARCHAR(255)        NOT NULL,
    is_primary BIT                  NOT NULL CONSTRAINT DF_product_images_primary DEFAULT 0,
    sort_order INT                  NOT NULL CONSTRAINT DF_product_images_sort DEFAULT 0,
    created_at DATETIME2(0)         NOT NULL CONSTRAINT DF_product_images_created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_product_images PRIMARY KEY (image_id),
    CONSTRAINT FK_product_images_product FOREIGN KEY (product_id) REFERENCES dbo.products (product_id)
);
GO

/* ============================================================================
   10. store_products  - NGUON SU THAT cua price / quantity / status theo Store (BR-02)
   ========================================================================== */
CREATE TABLE dbo.store_products (
    store_product_id BIGINT IDENTITY(1,1) NOT NULL,
    store_id         BIGINT               NOT NULL,
    product_id       BIGINT               NOT NULL,
    price            DECIMAL(18,2)        NOT NULL,
    quantity         INT                  NOT NULL CONSTRAINT DF_store_products_quantity DEFAULT 0,
    status           NVARCHAR(20)         NOT NULL CONSTRAINT DF_store_products_status DEFAULT N'ACTIVE',
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_store_products_updated DEFAULT SYSDATETIME(),
    CONSTRAINT PK_store_products PRIMARY KEY (store_product_id),
    CONSTRAINT UQ_store_products_store_product UNIQUE (store_id, product_id),
    CONSTRAINT FK_store_products_store   FOREIGN KEY (store_id)   REFERENCES dbo.stores (store_id),
    CONSTRAINT FK_store_products_product FOREIGN KEY (product_id) REFERENCES dbo.products (product_id),
    CONSTRAINT CK_store_products_quantity CHECK (quantity >= 0),
    CONSTRAINT CK_store_products_price    CHECK (price >= 0),
    CONSTRAINT CK_store_products_status   CHECK (status IN (N'ACTIVE', N'INACTIVE'))
);
GO

/* ============================================================================
   11. carts  - moi user 1 cart (USER 1--1 CART)
   ========================================================================== */
CREATE TABLE dbo.carts (
    cart_id    BIGINT IDENTITY(1,1) NOT NULL,
    user_id    BIGINT               NOT NULL,
    created_at DATETIME2(0)         NOT NULL CONSTRAINT DF_carts_created_at DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0)         NOT NULL CONSTRAINT DF_carts_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_carts PRIMARY KEY (cart_id),
    CONSTRAINT UQ_carts_user UNIQUE (user_id),
    CONSTRAINT FK_carts_user FOREIGN KEY (user_id) REFERENCES dbo.users (user_id)
);
GO

/* ============================================================================
   12. cart_items  - luon tham chieu StoreProduct (BR-05)
   ========================================================================== */
CREATE TABLE dbo.cart_items (
    cart_item_id     BIGINT IDENTITY(1,1) NOT NULL,
    cart_id          BIGINT               NOT NULL,
    store_product_id BIGINT               NOT NULL,
    quantity         INT                  NOT NULL,
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_cart_items_created_at DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_cart_items_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_cart_items PRIMARY KEY (cart_item_id),
    CONSTRAINT UQ_cart_items_cart_store_product UNIQUE (cart_id, store_product_id),
    CONSTRAINT FK_cart_items_cart          FOREIGN KEY (cart_id)          REFERENCES dbo.carts (cart_id),
    CONSTRAINT FK_cart_items_store_product FOREIGN KEY (store_product_id) REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT CK_cart_items_quantity CHECK (quantity > 0)
);
GO

/* ============================================================================
   13. checkout_sessions  - chi nhom cac Order cua cung mot lan checkout (BR-18)
       KHONG chua thong tin thanh toan.
   ========================================================================== */
CREATE TABLE dbo.checkout_sessions (
    checkout_id  BIGINT IDENTITY(1,1) NOT NULL,
    user_id      BIGINT               NOT NULL,
    total_amount DECIMAL(18,2)        NOT NULL,
    status       NVARCHAR(20)         NOT NULL CONSTRAINT DF_checkout_sessions_status DEFAULT N'CREATED',
    created_at   DATETIME2(0)         NOT NULL CONSTRAINT DF_checkout_sessions_created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_checkout_sessions PRIMARY KEY (checkout_id),
    CONSTRAINT FK_checkout_sessions_user FOREIGN KEY (user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_checkout_sessions_total  CHECK (total_amount >= 0),
    CONSTRAINT CK_checkout_sessions_status CHECK (status IN (N'CREATED', N'PARTIAL', N'COMPLETED', N'CANCELLED'))
);
GO

/* ============================================================================
   14. orders  - moi Order dung MOT Store (BR-07)
   ========================================================================== */
CREATE TABLE dbo.orders (
    order_id         BIGINT IDENTITY(1,1) NOT NULL,
    checkout_id      BIGINT               NOT NULL,
    user_id          BIGINT               NOT NULL,
    store_id         BIGINT               NOT NULL,
    fulfillment_type NVARCHAR(20)         NOT NULL,
    payment_method   NVARCHAR(20)         NOT NULL,
    payment_status   NVARCHAR(20)         NOT NULL CONSTRAINT DF_orders_payment_status DEFAULT N'UNPAID',
    order_status     NVARCHAR(30)         NOT NULL,
    receiver_name    NVARCHAR(150)        NOT NULL,
    receiver_phone   NVARCHAR(20)         NOT NULL,
    shipping_address NVARCHAR(500)        NULL,      -- chi DELIVERY
    pickup_code      NVARCHAR(20)         NULL,      -- chi STORE_PICKUP
    ready_at         DATETIME2(0)         NULL,      -- chi STORE_PICKUP
    picked_up_at     DATETIME2(0)         NULL,      -- chi STORE_PICKUP
    total_amount     DECIMAL(18,2)        NOT NULL,
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_orders_created_at DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_orders_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_orders PRIMARY KEY (order_id),
    CONSTRAINT FK_orders_checkout FOREIGN KEY (checkout_id) REFERENCES dbo.checkout_sessions (checkout_id),
    CONSTRAINT FK_orders_user     FOREIGN KEY (user_id)     REFERENCES dbo.users (user_id),
    CONSTRAINT FK_orders_store    FOREIGN KEY (store_id)    REFERENCES dbo.stores (store_id),
    CONSTRAINT CK_orders_fulfillment_type CHECK (fulfillment_type IN (N'DELIVERY', N'STORE_PICKUP')),
    CONSTRAINT CK_orders_payment_method   CHECK (payment_method IN (N'ONLINE', N'COD', N'PAY_AT_STORE')),
    CONSTRAINT CK_orders_payment_status   CHECK (payment_status IN (N'UNPAID', N'PAID', N'FAILED')),
    CONSTRAINT CK_orders_order_status CHECK (order_status IN (
        N'PENDING_PAYMENT', N'CONFIRMED', N'PREPARING', N'PACKED', N'SHIPPING',
        N'READY_FOR_PICKUP', N'COMPLETED', N'CANCELLED')),
    CONSTRAINT CK_orders_total CHECK (total_amount >= 0),
    -- DELIVERY: COD hoac ONLINE; STORE_PICKUP: PAY_AT_STORE hoac ONLINE (Roadmap 6.4, Phase 9)
    CONSTRAINT CK_orders_fulfillment_payment CHECK (
        (fulfillment_type = N'DELIVERY'     AND payment_method IN (N'COD', N'ONLINE')) OR
        (fulfillment_type = N'STORE_PICKUP' AND payment_method IN (N'PAY_AT_STORE', N'ONLINE'))),
    CONSTRAINT CK_orders_delivery_address CHECK (fulfillment_type <> N'DELIVERY' OR shipping_address IS NOT NULL),
    -- pickup_* chi dung cho STORE_PICKUP (Roadmap 7.3)
    CONSTRAINT CK_orders_pickup_only CHECK (
        fulfillment_type = N'STORE_PICKUP'
        OR (pickup_code IS NULL AND ready_at IS NULL AND picked_up_at IS NULL)),
    CONSTRAINT CK_orders_ready_has_code CHECK (
        order_status <> N'READY_FOR_PICKUP' OR (pickup_code IS NOT NULL AND ready_at IS NOT NULL)),
    -- State flow theo fulfillment_type (Roadmap 6.5, 6.6)
    CONSTRAINT CK_orders_status_by_fulfillment CHECK (
        (fulfillment_type = N'DELIVERY'     AND order_status <> N'READY_FOR_PICKUP') OR
        (fulfillment_type = N'STORE_PICKUP' AND order_status NOT IN (N'PACKED', N'SHIPPING'))),
    -- PENDING_PAYMENT chi ton tai voi ONLINE (COD / PAY_AT_STORE bat dau tu CONFIRMED)
    CONSTRAINT CK_orders_pending_payment_online CHECK (
        order_status <> N'PENDING_PAYMENT' OR payment_method = N'ONLINE')
);
GO

/* ============================================================================
   15. order_items  - snapshot product_name/unit_price/quantity/subtotal; giu store_product_id de hoan ton dung StoreProduct (BR-12)
   ========================================================================== */
CREATE TABLE dbo.order_items (
    order_item_id    BIGINT IDENTITY(1,1) NOT NULL,
    order_id         BIGINT               NOT NULL,
    store_product_id BIGINT               NOT NULL,
    product_name     NVARCHAR(255)        NOT NULL,
    unit_price       DECIMAL(18,2)        NOT NULL,
    quantity         INT                  NOT NULL,
    subtotal         DECIMAL(18,2)        NOT NULL,
    CONSTRAINT PK_order_items PRIMARY KEY (order_item_id),
    CONSTRAINT FK_order_items_order         FOREIGN KEY (order_id)         REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_order_items_store_product FOREIGN KEY (store_product_id) REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT CK_order_items_quantity   CHECK (quantity > 0),
    CONSTRAINT CK_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT CK_order_items_subtotal   CHECK (subtotal = unit_price * quantity)
);
GO

/* ============================================================================
   16. payments  - gan truc tiep Order, KHONG gan CheckoutSession (BR-18)
   ========================================================================== */
CREATE TABLE dbo.payments (
    payment_id       BIGINT IDENTITY(1,1) NOT NULL,
    order_id         BIGINT               NOT NULL,
    method           NVARCHAR(20)         NOT NULL,
    amount           DECIMAL(18,2)        NOT NULL,
    status           NVARCHAR(20)         NOT NULL CONSTRAINT DF_payments_status DEFAULT N'PENDING',
    transaction_code NVARCHAR(100)        NULL,
    paid_at          DATETIME2(0)         NULL,
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_payments_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_payments PRIMARY KEY (payment_id),
    CONSTRAINT FK_payments_order FOREIGN KEY (order_id) REFERENCES dbo.orders (order_id),
    CONSTRAINT CK_payments_method CHECK (method IN (N'ONLINE', N'COD', N'PAY_AT_STORE')),
    CONSTRAINT CK_payments_status CHECK (status IN (N'PENDING', N'SUCCESS', N'FAILED')),
    CONSTRAINT CK_payments_amount CHECK (amount >= 0),
    CONSTRAINT CK_payments_paid_at CHECK (status <> N'SUCCESS' OR paid_at IS NOT NULL)
);
GO

/* ============================================================================
   17. inventory_movements  - audit ton kho theo StoreProduct (ORDER / CANCEL_ORDER / STOCK_ADJUST)
   ========================================================================== */
CREATE TABLE dbo.inventory_movements (
    movement_id       BIGINT IDENTITY(1,1) NOT NULL,
    store_product_id  BIGINT               NOT NULL,
    type              NVARCHAR(20)         NOT NULL,
    quantity_change   INT                  NOT NULL,
    quantity_before   INT                  NOT NULL,
    quantity_after    INT                  NOT NULL,
    reference_order_id BIGINT              NULL,
    staff_id          BIGINT               NULL,
    note              NVARCHAR(500)        NULL,
    created_at        DATETIME2(0)         NOT NULL CONSTRAINT DF_inventory_movements_created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_inventory_movements PRIMARY KEY (movement_id),
    CONSTRAINT FK_inventory_movements_store_product FOREIGN KEY (store_product_id)   REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT FK_inventory_movements_order         FOREIGN KEY (reference_order_id) REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_inventory_movements_staff         FOREIGN KEY (staff_id)           REFERENCES dbo.users (user_id),
    CONSTRAINT CK_inventory_movements_type   CHECK (type IN (N'ORDER', N'CANCEL_ORDER', N'STOCK_ADJUST')),
    CONSTRAINT CK_inventory_movements_before CHECK (quantity_before >= 0),
    CONSTRAINT CK_inventory_movements_after  CHECK (quantity_after >= 0),
    CONSTRAINT CK_inventory_movements_math   CHECK (quantity_after = quantity_before + quantity_change),
    -- ORDER tru kho (am), CANCEL_ORDER hoan kho (duong) va deu tham chieu Order; STOCK_ADJUST can staff_id
    CONSTRAINT CK_inventory_movements_by_type CHECK (
        (type = N'ORDER'        AND quantity_change < 0 AND reference_order_id IS NOT NULL) OR
        (type = N'CANCEL_ORDER' AND quantity_change > 0 AND reference_order_id IS NOT NULL) OR
        (type = N'STOCK_ADJUST' AND quantity_change <> 0 AND staff_id IS NOT NULL))
);
GO

/* ============================================================================
   18. order_status_history  - audit transition trang thai Order (BR-13)
   ========================================================================== */
CREATE TABLE dbo.order_status_history (
    history_id         BIGINT IDENTITY(1,1) NOT NULL,
    order_id           BIGINT               NOT NULL,
    old_status         NVARCHAR(30)         NULL,      -- NULL o ban ghi tao don dau tien
    new_status         NVARCHAR(30)         NOT NULL,
    changed_by_user_id BIGINT               NULL,      -- NULL neu he thong tu doi trang thai
    note               NVARCHAR(500)        NULL,
    changed_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_order_status_history_changed DEFAULT SYSDATETIME(),
    CONSTRAINT PK_order_status_history PRIMARY KEY (history_id),
    CONSTRAINT FK_order_status_history_order FOREIGN KEY (order_id)           REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_order_status_history_user  FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_order_status_history_old CHECK (old_status IS NULL OR old_status IN (
        N'PENDING_PAYMENT', N'CONFIRMED', N'PREPARING', N'PACKED', N'SHIPPING',
        N'READY_FOR_PICKUP', N'COMPLETED', N'CANCELLED')),
    CONSTRAINT CK_order_status_history_new CHECK (new_status IN (
        N'PENDING_PAYMENT', N'CONFIRMED', N'PREPARING', N'PACKED', N'SHIPPING',
        N'READY_FOR_PICKUP', N'COMPLETED', N'CANCELLED'))
);
GO

/* ============================================================================
   19. reviews  - danh gia (chi sau COMPLETED - kiem tra o ReviewService)
   ========================================================================== */
CREATE TABLE dbo.reviews (
    review_id  BIGINT IDENTITY(1,1) NOT NULL,
    user_id    BIGINT               NOT NULL,
    product_id BIGINT               NOT NULL,
    rating     INT                  NOT NULL,
    comment    NVARCHAR(1000)       NULL,
    status     NVARCHAR(20)         NOT NULL CONSTRAINT DF_reviews_status DEFAULT N'ACTIVE',
    created_at DATETIME2(0)         NOT NULL CONSTRAINT DF_reviews_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_reviews PRIMARY KEY (review_id),
    CONSTRAINT FK_reviews_user    FOREIGN KEY (user_id)    REFERENCES dbo.users (user_id),
    CONSTRAINT FK_reviews_product FOREIGN KEY (product_id) REFERENCES dbo.products (product_id),
    CONSTRAINT CK_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT CK_reviews_status CHECK (status IN (N'ACTIVE', N'HIDDEN'))
);
GO

PRINT '19 tables created.';
GO
