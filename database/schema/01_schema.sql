/* =============================================================================
   OneShop V3 - canonical fresh-install schema

   Usage (sqlcmd):
     sqlcmd ... -v DatabaseName=oneshop_v3_audit_test -i database/schema/01_schema.sql

   The script only creates a database which does not already exist. It never drops,
   truncates or alters an existing database; use the controlled upgrade script in
   migrations/ for an existing installation.
   ========================================================================== */
SET NOCOUNT ON;
GO
IF DB_ID(N'$(DatabaseName)') IS NOT NULL
    THROW 51001, 'Fresh-install schema refuses an existing database.', 1;
DECLARE @create nvarchar(max) = N'CREATE DATABASE ' + QUOTENAME(N'$(DatabaseName)')
    + N' COLLATE Vietnamese_100_CI_AI;';
EXEC sys.sp_executesql @create;
GO
USE [$(DatabaseName)];
GO
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* Operational retry journal; no image bytes and no automatic external cleanup worker. */
CREATE TABLE dbo.image_cleanup_tasks (
    task_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_image_cleanup_tasks PRIMARY KEY,
    public_id NVARCHAR(255) NOT NULL,
    attempts INT NOT NULL CONSTRAINT DF_image_cleanup_attempts DEFAULT 0,
    last_error NVARCHAR(500) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_image_cleanup_created DEFAULT SYSDATETIME(),
    CONSTRAINT CK_image_cleanup_attempts CHECK (attempts >= 0)
);
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
    user_name     NVARCHAR(50)         NULL,
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
   4. stores  - Chi nhanh phan phoi & Fulfillment
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
   5. staff_store_assignments  - Phan cong nhan vien -> chi nhanh
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

/* A Staff works at exactly one Store: at most one ACTIVE assignment per user (INACTIVE = suspended, any number). */
CREATE UNIQUE INDEX UX_ssa_one_active_store_per_user ON dbo.staff_store_assignments (user_id) WHERE status = N'ACTIVE';
GO

/* ============================================================================
   6. categories
   ========================================================================== */
CREATE TABLE dbo.categories (
    category_id     BIGINT IDENTITY(1,1) NOT NULL,
    name            NVARCHAR(150)        NOT NULL,
    description     NVARCHAR(500)        NULL,
    image_url       NVARCHAR(500)        NULL,
    image_public_id NVARCHAR(255)        NULL,
    status          NVARCHAR(20)         NOT NULL CONSTRAINT DF_categories_status DEFAULT N'ACTIVE',
    CONSTRAINT PK_categories PRIMARY KEY (category_id),
    CONSTRAINT UQ_categories_name UNIQUE (name),
    CONSTRAINT CK_categories_status CHECK (status IN (N'ACTIVE', N'HIDDEN'))
);
GO

/* ============================================================================
   7. brands
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
   8. products  - SKU ban duoc, gia ban online thong nhat toan chuoi (V3-DEC-02)
   ========================================================================== */
CREATE TABLE dbo.products (
    product_id     BIGINT IDENTITY(1,1) NOT NULL,
    category_id    BIGINT               NOT NULL,
    brand_id       BIGINT               NOT NULL,
    sku            NVARCHAR(50)         NOT NULL,
    name           NVARCHAR(255)        NOT NULL,
    description    NVARCHAR(MAX)        NULL,
    price          DECIMAL(18,2)        NOT NULL CONSTRAINT DF_products_price DEFAULT 0,
    original_price DECIMAL(18,2)        NULL,
    status         NVARCHAR(20)         NOT NULL CONSTRAINT DF_products_status DEFAULT N'ACTIVE',
    created_at     DATETIME2(0)         NOT NULL CONSTRAINT DF_products_created_at DEFAULT SYSDATETIME(),
    updated_at     DATETIME2(0)         NOT NULL CONSTRAINT DF_products_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_products PRIMARY KEY (product_id),
    CONSTRAINT UQ_products_sku UNIQUE (sku),
    CONSTRAINT FK_products_category FOREIGN KEY (category_id) REFERENCES dbo.categories (category_id),
    CONSTRAINT FK_products_brand    FOREIGN KEY (brand_id)    REFERENCES dbo.brands (brand_id),
    CONSTRAINT CK_products_status CHECK (status IN (N'ACTIVE', N'INACTIVE', N'HIDDEN')),
    CONSTRAINT CK_products_price CHECK (price >= 0),
    CONSTRAINT CK_products_original_price CHECK (original_price IS NULL OR original_price >= price)
);
GO

/* ============================================================================
   9. product_images
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
CREATE UNIQUE INDEX UX_product_images_one_primary ON dbo.product_images(product_id) WHERE is_primary=1;
GO

/* ============================================================================
   10. store_products  - Ton kho vat ly tai tung chi nhanh (V3-DEC-03)
   ========================================================================== */
CREATE TABLE dbo.store_products (
    store_product_id  BIGINT IDENTITY(1,1) NOT NULL,
    store_id          BIGINT               NOT NULL,
    product_id        BIGINT               NOT NULL,
    price             DECIMAL(18,2)        NULL CONSTRAINT DF_store_products_price DEFAULT 0, -- V2 legacy compatibility
    quantity          INT                  NOT NULL CONSTRAINT DF_store_products_quantity DEFAULT 0,
    reserved_quantity INT                  NOT NULL CONSTRAINT DF_store_products_reserved DEFAULT 0,
    status            NVARCHAR(20)         NOT NULL CONSTRAINT DF_store_products_status DEFAULT N'ACTIVE',
    updated_at        DATETIME2(0)         NOT NULL CONSTRAINT DF_store_products_updated DEFAULT SYSDATETIME(),
    CONSTRAINT PK_store_products PRIMARY KEY (store_product_id),
    CONSTRAINT UQ_store_products_store_product UNIQUE (store_id, product_id),
    CONSTRAINT FK_store_products_store   FOREIGN KEY (store_id)   REFERENCES dbo.stores (store_id),
    CONSTRAINT FK_store_products_product FOREIGN KEY (product_id) REFERENCES dbo.products (product_id),
    CONSTRAINT CK_store_products_quantity CHECK (quantity >= 0),
    CONSTRAINT CK_store_products_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity),
    CONSTRAINT CK_store_products_status   CHECK (status IN (N'ACTIVE', N'INACTIVE'))
);
GO

/* ============================================================================
   11. carts  - Gio hang moi User (1 User -- 1 Cart)
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
   12. cart_items  - Gio hang thong nhat tham chieu product_id (V3-DEC-04)
   ========================================================================== */
CREATE TABLE dbo.cart_items (
    cart_item_id     BIGINT IDENTITY(1,1) NOT NULL,
    cart_id          BIGINT               NOT NULL,
    product_id       BIGINT               NOT NULL,
    store_product_id BIGINT               NULL,      -- V2 legacy compatibility
    quantity         INT                  NOT NULL,
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_cart_items_created_at DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_cart_items_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_cart_items PRIMARY KEY (cart_item_id),
    CONSTRAINT UQ_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT FK_cart_items_cart          FOREIGN KEY (cart_id)          REFERENCES dbo.carts (cart_id),
    CONSTRAINT FK_cart_items_product       FOREIGN KEY (product_id)       REFERENCES dbo.products (product_id),
    CONSTRAINT FK_cart_items_store_product FOREIGN KEY (store_product_id) REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT CK_cart_items_quantity CHECK (quantity > 0)
);
GO

/* ============================================================================
   13. checkout_sessions  - Phien checkout
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
   14. orders  - Don hang trung tam OneShop (store_id nullable trong V3)
   ========================================================================== */
CREATE TABLE dbo.orders (
    order_id         BIGINT IDENTITY(1,1) NOT NULL,
    checkout_id      BIGINT               NOT NULL,
    user_id          BIGINT               NOT NULL,
    store_id         BIGINT               NULL,      -- V2 legacy / StorePickup store / null voi online multi-store
    fulfillment_type NVARCHAR(20)         NOT NULL,
    payment_method   NVARCHAR(20)         NOT NULL,
    payment_status   NVARCHAR(20)         NOT NULL CONSTRAINT DF_orders_payment_status DEFAULT N'UNPAID',
    order_status     NVARCHAR(30)         NOT NULL,
    receiver_name    NVARCHAR(150)        NOT NULL,
    receiver_phone   NVARCHAR(20)         NOT NULL,
    shipping_address NVARCHAR(500)        NULL,      -- DELIVERY
    pickup_code      NVARCHAR(20)         NULL,      -- STORE_PICKUP
    ready_at         DATETIME2(0)         NULL,      -- STORE_PICKUP
    picked_up_at     DATETIME2(0)         NULL,      -- STORE_PICKUP
    total_amount     DECIMAL(18,2)        NOT NULL,
    shipping_fee     DECIMAL(18,2)        NOT NULL CONSTRAINT DF_orders_shipping_fee DEFAULT 0,
    discount_amount  DECIMAL(18,2)        NOT NULL CONSTRAINT DF_orders_discount_amount DEFAULT 0,  -- promotion: taken off the goods, never the shipping fee
    promotion_code   NVARCHAR(50)         NULL,      -- snapshot of the code used; total_amount = goods - discount_amount + shipping_fee
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_orders_created_at DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_orders_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_orders PRIMARY KEY (order_id),
    CONSTRAINT FK_orders_checkout FOREIGN KEY (checkout_id) REFERENCES dbo.checkout_sessions (checkout_id),
    CONSTRAINT FK_orders_user     FOREIGN KEY (user_id)     REFERENCES dbo.users (user_id),
    CONSTRAINT FK_orders_store    FOREIGN KEY (store_id)    REFERENCES dbo.stores (store_id),
    CONSTRAINT CK_orders_fulfillment_type CHECK (fulfillment_type IN (N'DELIVERY', N'STORE_PICKUP')),
    CONSTRAINT CK_orders_payment_method   CHECK (payment_method IN (N'ONLINE', N'COD', N'PAY_AT_STORE')),
    CONSTRAINT CK_orders_payment_status   CHECK (payment_status IN (N'UNPAID', N'PAID', N'FAILED', N'REFUNDED')),
    CONSTRAINT CK_orders_order_status CHECK (order_status IN (
        N'PENDING_PAYMENT', N'CONFIRMED', N'PROCESSING', N'SHIPPING',
        N'PARTIALLY_COMPLETED', N'COMPLETED', N'CANCELLED', N'NEEDS_ATTENTION')),
    CONSTRAINT CK_orders_total        CHECK (total_amount >= 0),
    CONSTRAINT CK_orders_shipping_fee CHECK (shipping_fee >= 0),
    CONSTRAINT CK_orders_discount CHECK (discount_amount >= 0),
    CONSTRAINT CK_orders_delivery_address CHECK (fulfillment_type <> N'DELIVERY' OR shipping_address IS NOT NULL)
);
GO

/* ============================================================================
   15. order_fulfillments  - Kien hang dieu phoi theo chi nhanh (V3 NEW / P0)
   ========================================================================== */
CREATE TABLE dbo.order_fulfillments (
    fulfillment_id   BIGINT IDENTITY(1,1) NOT NULL,
    order_id         BIGINT               NOT NULL,
    store_id         BIGINT               NOT NULL,
    fulfillment_type NVARCHAR(20)         NOT NULL CONSTRAINT DF_of_type DEFAULT N'DELIVERY',
    status           NVARCHAR(30)         NOT NULL CONSTRAINT DF_of_status DEFAULT N'CONFIRMED',
    tracking_code    NVARCHAR(50)         NULL,
    pickup_code      NVARCHAR(20)         NULL,
    assigned_at      DATETIME2(0)         NOT NULL CONSTRAINT DF_of_assigned_at DEFAULT SYSDATETIME(),
    prepared_at      DATETIME2(0)         NULL,
    packed_at        DATETIME2(0)         NULL,
    ready_at         DATETIME2(0)         NULL,
    picked_up_at     DATETIME2(0)         NULL,
    shipped_at       DATETIME2(0)         NULL,
    completed_at     DATETIME2(0)         NULL,
    cancelled_at     DATETIME2(0)         NULL,
    cancel_reason    NVARCHAR(500)        NULL,
    note             NVARCHAR(500)        NULL,
    created_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_of_created_at DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(0)         NOT NULL CONSTRAINT DF_of_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_order_fulfillments PRIMARY KEY (fulfillment_id),
    CONSTRAINT UQ_order_fulfillments_order_store UNIQUE (order_id, store_id),
    CONSTRAINT FK_order_fulfillments_order FOREIGN KEY (order_id) REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_order_fulfillments_store FOREIGN KEY (store_id) REFERENCES dbo.stores (store_id),
    CONSTRAINT CK_order_fulfillments_type CHECK (fulfillment_type IN (N'DELIVERY', N'STORE_PICKUP')),
    CONSTRAINT CK_order_fulfillments_status CHECK (status IN (
        N'CONFIRMED', N'PREPARING', N'PACKED', N'SHIPPING',
        N'READY_FOR_PICKUP', N'COMPLETED', N'CANCELLED', N'FAILED'))
);
GO

/* ============================================================================
   16. order_fulfillment_status_history  - Audit transition cua tung kien hang (V3 NEW)
   ========================================================================== */
CREATE TABLE dbo.order_fulfillment_status_history (
    history_id         BIGINT IDENTITY(1,1) NOT NULL,
    fulfillment_id     BIGINT               NOT NULL,
    old_status         NVARCHAR(30)         NULL,
    new_status         NVARCHAR(30)         NOT NULL,
    changed_by_user_id BIGINT               NULL,
    note               NVARCHAR(500)        NULL,
    changed_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_ofsh_changed_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_order_fulfillment_status_history PRIMARY KEY (history_id),
    CONSTRAINT FK_ofsh_fulfillment FOREIGN KEY (fulfillment_id)     REFERENCES dbo.order_fulfillments (fulfillment_id),
    CONSTRAINT FK_ofsh_user        FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_ofsh_old CHECK (old_status IS NULL OR old_status IN (
        N'CONFIRMED', N'PREPARING', N'PACKED', N'SHIPPING',
        N'READY_FOR_PICKUP', N'COMPLETED', N'CANCELLED', N'FAILED')),
    CONSTRAINT CK_ofsh_new CHECK (new_status IN (
        N'CONFIRMED', N'PREPARING', N'PACKED', N'SHIPPING',
        N'READY_FOR_PICKUP', N'COMPLETED', N'CANCELLED', N'FAILED'))
);
GO

/* ============================================================================
   17. order_items  - Chi tiet dong hang gan fulfillment_id va store_product_id (V3)
   ========================================================================== */
CREATE TABLE dbo.order_items (
    order_item_id    BIGINT IDENTITY(1,1) NOT NULL,
    order_id         BIGINT               NOT NULL,
    fulfillment_id   BIGINT               NOT NULL,
    store_product_id BIGINT               NOT NULL,
    product_name     NVARCHAR(255)        NOT NULL,
    unit_price       DECIMAL(18,2)        NOT NULL,
    quantity         INT                  NOT NULL,
    subtotal         DECIMAL(18,2)        NOT NULL,
    discount_amount  DECIMAL(18,2)        NOT NULL CONSTRAINT DF_order_items_discount DEFAULT 0,  -- this line's share of the Order discount; what it is worth = subtotal - discount_amount
    CONSTRAINT PK_order_items PRIMARY KEY (order_item_id),
    CONSTRAINT FK_order_items_order         FOREIGN KEY (order_id)         REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_order_items_fulfillment   FOREIGN KEY (fulfillment_id)   REFERENCES dbo.order_fulfillments (fulfillment_id),
    CONSTRAINT FK_order_items_store_product FOREIGN KEY (store_product_id) REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT CK_order_items_quantity   CHECK (quantity > 0),
    CONSTRAINT CK_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT CK_order_items_subtotal   CHECK (subtotal = unit_price * quantity),
    CONSTRAINT CK_order_items_discount   CHECK (discount_amount >= 0 AND discount_amount <= subtotal)
);
GO

/* ============================================================================
   18. payments  - Thanh toan gan Order + ho tro simulated refund (V3)
   ========================================================================== */
CREATE TABLE dbo.payments (
    payment_id          BIGINT IDENTITY(1,1) NOT NULL,
    order_id            BIGINT               NOT NULL,
    method              NVARCHAR(20)         NOT NULL,
    amount              DECIMAL(18,2)        NOT NULL,
    status              NVARCHAR(20)         NOT NULL CONSTRAINT DF_payments_status DEFAULT N'PENDING',
    transaction_code    NVARCHAR(100)        NULL,
    paid_at             DATETIME2(0)         NULL,
    refund_status       NVARCHAR(30)         NOT NULL CONSTRAINT DF_payments_refund_status DEFAULT N'NONE',
    refund_amount       DECIMAL(18,2)        NULL,
    refund_requested_at DATETIME2(0)         NULL,
    refunded_at         DATETIME2(0)         NULL,
    refund_reason       NVARCHAR(500)        NULL,
    created_at          DATETIME2(0)         NOT NULL CONSTRAINT DF_payments_created_at DEFAULT SYSDATETIME(),
    /* payment gateway (V009): NULL for COD / PAY_AT_STORE; MOCK = simulated; PAYOS = real payOS hosted checkout */
    provider             NVARCHAR(20)  NULL,
    provider_order_code  BIGINT        NULL,   -- payOS orderCode (unique, <= 2^53-1), made by the backend
    provider_link_id     NVARCHAR(64)  NULL,   -- payOS paymentLinkId
    checkout_url         NVARCHAR(500) NULL,
    provider_status      NVARCHAR(20)  NULL,   -- last payOS evidence: PENDING/PROCESSING/PAID/UNDERPAID/CANCELLED/EXPIRED/FAILED
    provider_amount_paid BIGINT        NULL,   -- VND actually received per payOS
    provider_reference   NVARCHAR(100) NULL,   -- bank transaction reference
    provider_paid_at     DATETIME2(0)  NULL,   -- when the BANK recorded the payment (not when the webhook arrived)
    expires_at           DATETIME2(0)  NULL,   -- payment link expiry (= the Order payment deadline)
    last_synced_at       DATETIME2(0)  NULL,
    CONSTRAINT PK_payments PRIMARY KEY (payment_id),
    CONSTRAINT FK_payments_order FOREIGN KEY (order_id) REFERENCES dbo.orders (order_id),
    CONSTRAINT CK_payments_method CHECK (method IN (N'ONLINE', N'COD', N'PAY_AT_STORE')),
    CONSTRAINT CK_payments_status CHECK (status IN (N'PENDING', N'SUCCESS', N'FAILED')),
    CONSTRAINT CK_payments_amount CHECK (amount >= 0),
    CONSTRAINT CK_payments_paid_at CHECK (status <> N'SUCCESS' OR paid_at IS NOT NULL),
    CONSTRAINT CK_payments_refund_status CHECK (refund_status IN (
        N'NONE', N'PENDING_SIMULATION', N'SIMULATED_REFUNDED', N'SIMULATION_FAILED', N'MANUAL_REQUESTED', N'REFUNDED')),
    CONSTRAINT CK_payments_provider CHECK (provider IS NULL OR provider IN (N'MOCK', N'PAYOS')),
    CONSTRAINT CK_payments_payos_order_code CHECK (provider IS NULL OR provider <> N'PAYOS'
        OR (provider_order_code IS NOT NULL AND provider_order_code BETWEEN 1 AND 9007199254740991)),
    CONSTRAINT CK_payments_provider_status CHECK (provider_status IS NULL OR provider_status IN (
        N'PENDING', N'PROCESSING', N'PAID', N'UNDERPAID', N'CANCELLED', N'EXPIRED', N'FAILED'))
);
GO
CREATE UNIQUE INDEX UX_payments_provider_order_code ON dbo.payments (provider_order_code) WHERE provider_order_code IS NOT NULL;
CREATE UNIQUE INDEX UX_payments_provider_link ON dbo.payments (provider_link_id) WHERE provider_link_id IS NOT NULL;
CREATE UNIQUE INDEX UX_payments_one_pending_payos ON dbo.payments (order_id) WHERE provider = N'PAYOS' AND status = N'PENDING';
GO

/* payment_provider_events: durable de-duplication of payOS webhooks (unique provider + event_key) */
CREATE TABLE dbo.payment_provider_events (
    event_id        BIGINT IDENTITY(1,1) NOT NULL,
    provider        NVARCHAR(20)  NOT NULL,
    event_key       NVARCHAR(128) NOT NULL,
    payment_id      BIGINT        NULL,
    order_code      BIGINT        NULL,
    payment_link_id NVARCHAR(64)  NULL,
    reference       NVARCHAR(100) NULL,
    amount          BIGINT        NULL,
    event_code      NVARCHAR(10)  NULL,
    transaction_at  DATETIME2(0)  NULL,
    outcome         NVARCHAR(30)  NOT NULL,
    received_at     DATETIME2(0)  NOT NULL CONSTRAINT DF_payment_provider_events_received_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_payment_provider_events PRIMARY KEY (event_id),
    CONSTRAINT UQ_payment_provider_events_key UNIQUE (provider, event_key),
    CONSTRAINT FK_payment_provider_events_payment FOREIGN KEY (payment_id) REFERENCES dbo.payments (payment_id),
    CONSTRAINT CK_payment_provider_events_outcome CHECK (outcome IN (
        N'APPLIED', N'TRIGGERED_SYNC', N'UNKNOWN_ORDER', N'REJECTED_MISMATCH'))
);
CREATE INDEX IX_payment_provider_events_payment ON dbo.payment_provider_events (payment_id);
GO

/* ============================================================================
   18a. order_returns / order_return_images / order_return_items  - Tra hang sau khi da nhan hang
   Customer returns the WHOLE completed Order within 3 days of the latest Fulfillment completion, with a reason and
   photos. Only an Administrator approves or rejects. After approval the simulated refund of the goods (never the
   shipping fee) is recorded at once, and each returned line waits for Staff of its own Store to choose
   RESTOCKED (stock += quantity, RETURN_ORDER movement) or DISPOSED (real-world handling, stock untouched).
   The Order and Fulfillment statuses stay COMPLETED: a return is a layer above them.
   ========================================================================== */
CREATE TABLE dbo.order_returns (
    return_id           BIGINT IDENTITY(1,1) NOT NULL,
    order_id            BIGINT               NOT NULL,
    user_id             BIGINT               NOT NULL,
    reason              NVARCHAR(500)        NOT NULL,
    status              NVARCHAR(20)         NOT NULL CONSTRAINT DF_order_returns_status DEFAULT N'REQUESTED',
    received_at         DATETIME2(0)         NOT NULL,      -- latest Fulfillment completed_at: the 3-day window starts here
    refund_amount       DECIMAL(18,2)        NOT NULL,      -- goods value of the returned lines; the shipping fee is kept
    requested_at        DATETIME2(0)         NOT NULL CONSTRAINT DF_order_returns_requested_at DEFAULT SYSDATETIME(),
    reviewed_by_user_id BIGINT               NULL,
    reviewed_at         DATETIME2(0)         NULL,
    review_note         NVARCHAR(500)        NULL,
    completed_at        DATETIME2(0)         NULL,
    CONSTRAINT PK_order_returns PRIMARY KEY (return_id),
    CONSTRAINT UQ_order_returns_order UNIQUE (order_id),
    CONSTRAINT FK_order_returns_order    FOREIGN KEY (order_id)            REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_order_returns_user     FOREIGN KEY (user_id)             REFERENCES dbo.users (user_id),
    CONSTRAINT FK_order_returns_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_order_returns_status CHECK (status IN (N'REQUESTED', N'APPROVED', N'REJECTED', N'COMPLETED')),
    CONSTRAINT CK_order_returns_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0),
    CONSTRAINT CK_order_returns_amount CHECK (refund_amount > 0),
    CONSTRAINT CK_order_returns_review CHECK (
        (status = N'REQUESTED' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL AND completed_at IS NULL)
        OR (status = N'REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND review_note IS NOT NULL AND LEN(LTRIM(RTRIM(review_note))) > 0 AND completed_at IS NULL)
        OR (status = N'APPROVED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND completed_at IS NULL)
        OR (status = N'COMPLETED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND completed_at IS NOT NULL))
);
GO

CREATE TABLE dbo.order_return_images (
    image_id   BIGINT IDENTITY(1,1) NOT NULL,
    return_id  BIGINT               NOT NULL,
    image_url  NVARCHAR(500)        NOT NULL,
    public_id  NVARCHAR(255)        NOT NULL,
    sort_order INT                  NOT NULL CONSTRAINT DF_order_return_images_sort DEFAULT 0,
    created_at DATETIME2(0)         NOT NULL CONSTRAINT DF_order_return_images_created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_order_return_images PRIMARY KEY (image_id),
    CONSTRAINT FK_order_return_images_return FOREIGN KEY (return_id) REFERENCES dbo.order_returns (return_id)
);
GO

CREATE TABLE dbo.order_return_items (
    return_item_id     BIGINT IDENTITY(1,1) NOT NULL,
    return_id          BIGINT               NOT NULL,
    order_item_id      BIGINT               NOT NULL,
    fulfillment_id     BIGINT               NOT NULL,
    store_id           BIGINT               NOT NULL,      -- the Store that delivered the line: the only Staff scope that may handle it
    store_product_id   BIGINT               NOT NULL,
    product_name       NVARCHAR(255)        NOT NULL,
    quantity           INT                  NOT NULL,
    unit_price         DECIMAL(18,2)        NOT NULL,
    subtotal           DECIMAL(18,2)        NOT NULL,
    disposition        NVARCHAR(20)         NOT NULL CONSTRAINT DF_order_return_items_disposition DEFAULT N'PENDING',
    handled_by_user_id BIGINT               NULL,
    handled_at         DATETIME2(0)         NULL,
    handle_note        NVARCHAR(500)        NULL,
    CONSTRAINT PK_order_return_items PRIMARY KEY (return_item_id),
    CONSTRAINT UQ_order_return_items_line UNIQUE (return_id, order_item_id),
    CONSTRAINT FK_order_return_items_return        FOREIGN KEY (return_id)          REFERENCES dbo.order_returns (return_id),
    CONSTRAINT FK_order_return_items_order_item    FOREIGN KEY (order_item_id)      REFERENCES dbo.order_items (order_item_id),
    CONSTRAINT FK_order_return_items_fulfillment   FOREIGN KEY (fulfillment_id)     REFERENCES dbo.order_fulfillments (fulfillment_id),
    CONSTRAINT FK_order_return_items_store         FOREIGN KEY (store_id)           REFERENCES dbo.stores (store_id),
    CONSTRAINT FK_order_return_items_store_product FOREIGN KEY (store_product_id)   REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT FK_order_return_items_handler       FOREIGN KEY (handled_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_order_return_items_quantity CHECK (quantity > 0),
    CONSTRAINT CK_order_return_items_subtotal CHECK (subtotal = unit_price * quantity),
    CONSTRAINT CK_order_return_items_disposition CHECK (disposition IN (N'PENDING', N'RESTOCKED', N'DISPOSED')),
    CONSTRAINT CK_order_return_items_handled CHECK (
        (disposition = N'PENDING' AND handled_by_user_id IS NULL AND handled_at IS NULL)
        OR (disposition <> N'PENDING' AND handled_by_user_id IS NOT NULL AND handled_at IS NOT NULL))
);
GO
CREATE INDEX IX_order_returns_status_requested ON dbo.order_returns (status, requested_at);
CREATE INDEX IX_order_return_images_return ON dbo.order_return_images (return_id, sort_order);
CREATE INDEX IX_order_return_items_store_disposition ON dbo.order_return_items (store_id, disposition);
GO

/* ============================================================================
   18b. payment_refunds  - Lich su hoan tien MO PHONG theo Order / Fulfillment (OPEN-10.1)
   Ledger of simulated refunds. payments.refund_* keeps the whole-Order refund summary; this table is the history
   (goods of one Fulfillment, the shipping fee, a whole-Order refund, or the goods of a returned Order) with who, why, how much and when.
   ========================================================================== */
CREATE TABLE dbo.payment_refunds (
    refund_id            BIGINT IDENTITY(1,1) NOT NULL,
    payment_id           BIGINT               NOT NULL,
    order_id             BIGINT               NOT NULL,
    fulfillment_id       BIGINT               NULL,
    return_id            BIGINT               NULL,      -- RETURN: the order_returns row this refund pays for
    component            NVARCHAR(20)         NOT NULL,
    amount               DECIMAL(18,2)        NOT NULL,
    status               NVARCHAR(30)         NOT NULL,
    reason               NVARCHAR(500)        NOT NULL,
    requested_by_user_id BIGINT               NULL,
    requested_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_payment_refunds_requested_at DEFAULT SYSDATETIME(),
    refunded_at          DATETIME2(0)         NULL,
    evidence_reference   NVARCHAR(100)        NULL,      -- REAL refund: the bank transaction code of the money given back
    processed_by_user_id BIGINT               NULL,      -- REAL refund: who confirmed it
    processed_at         DATETIME2(0)         NULL,
    processed_note       NVARCHAR(500)        NULL,
    CONSTRAINT PK_payment_refunds PRIMARY KEY (refund_id),
    CONSTRAINT FK_payment_refunds_processed_by FOREIGN KEY (processed_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT FK_payment_refunds_payment     FOREIGN KEY (payment_id)           REFERENCES dbo.payments (payment_id),
    CONSTRAINT FK_payment_refunds_order       FOREIGN KEY (order_id)             REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_payment_refunds_fulfillment FOREIGN KEY (fulfillment_id)       REFERENCES dbo.order_fulfillments (fulfillment_id),
    CONSTRAINT FK_payment_refunds_user        FOREIGN KEY (requested_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT FK_payment_refunds_return      FOREIGN KEY (return_id)            REFERENCES dbo.order_returns (return_id),
    CONSTRAINT CK_payment_refunds_component CHECK (component IN (N'GOODS', N'SHIPPING', N'WHOLE_ORDER', N'RETURN')),
    CONSTRAINT CK_payment_refunds_status CHECK (status IN (N'PENDING_SIMULATION', N'SIMULATED_REFUNDED', N'SIMULATION_FAILED', N'MANUAL_REQUESTED', N'REFUNDED')),
    CONSTRAINT CK_payment_refunds_amount CHECK (amount > 0),
    CONSTRAINT CK_payment_refunds_target CHECK (
        (component = N'GOODS' AND fulfillment_id IS NOT NULL AND return_id IS NULL)
        OR (component = N'RETURN' AND fulfillment_id IS NULL AND return_id IS NOT NULL)
        OR (component IN (N'SHIPPING', N'WHOLE_ORDER') AND fulfillment_id IS NULL AND return_id IS NULL)),
    CONSTRAINT CK_payment_refunds_refunded_at CHECK (status NOT IN (N'SIMULATED_REFUNDED', N'REFUNDED') OR refunded_at IS NOT NULL),
    CONSTRAINT CK_payment_refunds_real_evidence CHECK (status <> N'REFUNDED'
        OR (evidence_reference IS NOT NULL AND processed_by_user_id IS NOT NULL AND processed_at IS NOT NULL))
);
GO
/* One goods refund per Fulfillment, one shipping refund per Order, one whole-Order refund per Payment, one return refund per Return: a repeated or
   concurrent request can never refund the same thing twice, whatever the application does. */
CREATE UNIQUE INDEX UX_payment_refunds_goods    ON dbo.payment_refunds (fulfillment_id) WHERE component = N'GOODS';
CREATE UNIQUE INDEX UX_payment_refunds_shipping ON dbo.payment_refunds (order_id)       WHERE component = N'SHIPPING';
CREATE UNIQUE INDEX UX_payment_refunds_whole    ON dbo.payment_refunds (payment_id)     WHERE component = N'WHOLE_ORDER';
CREATE UNIQUE INDEX UX_payment_refunds_return   ON dbo.payment_refunds (return_id)      WHERE component = N'RETURN';
CREATE UNIQUE INDEX UX_payment_refunds_evidence ON dbo.payment_refunds (evidence_reference) WHERE evidence_reference IS NOT NULL;
CREATE INDEX IX_payment_refunds_order ON dbo.payment_refunds (order_id, requested_at);
CREATE INDEX IX_payment_refunds_payment ON dbo.payment_refunds (payment_id);
GO

/* ============================================================================
   18c. promotions / promotion_usages  - Ma khuyen mai
   An Administrator manages the codes. At checkout a customer picks ONE code for the whole order: it takes a discount
   off the GOODS (never the shipping fee) when the order reaches min_order_amount, the code is ACTIVE, inside its dates,
   has uses left (used_count < usage_limit) and the customer has not used it per_user_limit times. One use is spent
   per checkout (a COD order split over several Stores still costs ONE use; its discount is shared between the Orders).
   A use is given back (RELEASED) only when every Order of that checkout was cancelled.
   ========================================================================== */
CREATE TABLE dbo.promotions (
    promotion_id        BIGINT IDENTITY(1,1) NOT NULL,
    code                NVARCHAR(50)         NOT NULL,
    name                NVARCHAR(150)        NOT NULL,
    description         NVARCHAR(500)        NULL,
    discount_type       NVARCHAR(20)         NOT NULL,
    discount_value      DECIMAL(18,2)        NOT NULL,   -- PERCENT: 1..100; FIXED: VND
    max_discount_amount DECIMAL(18,2)        NULL,       -- PERCENT only: cap of the discount
    min_order_amount    DECIMAL(18,2)        NOT NULL CONSTRAINT DF_promotions_min_order DEFAULT 0,  -- goods value needed
    usage_limit         INT                  NOT NULL,   -- total uses
    used_count          INT                  NOT NULL CONSTRAINT DF_promotions_used_count DEFAULT 0,
    per_user_limit      INT                  NOT NULL CONSTRAINT DF_promotions_per_user DEFAULT 1,
    start_at            DATETIME2(0)         NULL,
    end_at              DATETIME2(0)         NULL,
    status              NVARCHAR(20)         NOT NULL CONSTRAINT DF_promotions_status DEFAULT N'ACTIVE',
    created_at          DATETIME2(0)         NOT NULL CONSTRAINT DF_promotions_created_at DEFAULT SYSDATETIME(),
    updated_at          DATETIME2(0)         NOT NULL CONSTRAINT DF_promotions_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_promotions PRIMARY KEY (promotion_id),
    CONSTRAINT UQ_promotions_code UNIQUE (code),
    CONSTRAINT CK_promotions_type CHECK (discount_type IN (N'PERCENT', N'FIXED')),
    CONSTRAINT CK_promotions_status CHECK (status IN (N'ACTIVE', N'INACTIVE')),
    CONSTRAINT CK_promotions_value CHECK (discount_value > 0 AND (discount_type <> N'PERCENT' OR discount_value <= 100)),
    CONSTRAINT CK_promotions_cap CHECK (max_discount_amount IS NULL OR (discount_type = N'PERCENT' AND max_discount_amount > 0)),
    CONSTRAINT CK_promotions_min_order CHECK (min_order_amount >= 0),
    CONSTRAINT CK_promotions_limits CHECK (usage_limit >= 1 AND per_user_limit >= 1 AND used_count >= 0 AND used_count <= usage_limit),
    CONSTRAINT CK_promotions_dates CHECK (start_at IS NULL OR end_at IS NULL OR end_at > start_at)
);
GO

CREATE TABLE dbo.promotion_usages (
    usage_id        BIGINT IDENTITY(1,1) NOT NULL,
    promotion_id    BIGINT               NOT NULL,
    user_id         BIGINT               NOT NULL,
    checkout_id     BIGINT               NOT NULL,
    discount_amount DECIMAL(18,2)        NOT NULL,
    status          NVARCHAR(20)         NOT NULL CONSTRAINT DF_promotion_usages_status DEFAULT N'USED',
    used_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_promotion_usages_used_at DEFAULT SYSDATETIME(),
    released_at     DATETIME2(0)         NULL,
    CONSTRAINT PK_promotion_usages PRIMARY KEY (usage_id),
    CONSTRAINT UQ_promotion_usages_checkout UNIQUE (checkout_id),
    CONSTRAINT FK_promotion_usages_promotion FOREIGN KEY (promotion_id) REFERENCES dbo.promotions (promotion_id),
    CONSTRAINT FK_promotion_usages_user      FOREIGN KEY (user_id)      REFERENCES dbo.users (user_id),
    CONSTRAINT FK_promotion_usages_checkout  FOREIGN KEY (checkout_id)  REFERENCES dbo.checkout_sessions (checkout_id),
    CONSTRAINT CK_promotion_usages_status CHECK (status IN (N'USED', N'RELEASED')),
    CONSTRAINT CK_promotion_usages_amount CHECK (discount_amount >= 0),
    CONSTRAINT CK_promotion_usages_released CHECK ((status = N'USED' AND released_at IS NULL) OR (status = N'RELEASED' AND released_at IS NOT NULL))
);
GO
CREATE INDEX IX_promotion_usages_user ON dbo.promotion_usages (promotion_id, user_id, status);
GO

/* ============================================================================
   19. inventory_movements  - Audit bien dong ton kho theo StoreProduct
   ========================================================================== */
CREATE TABLE dbo.inventory_movements (
    movement_id        BIGINT IDENTITY(1,1) NOT NULL,
    store_product_id   BIGINT               NOT NULL,
    type               NVARCHAR(20)         NOT NULL,
    quantity_change    INT                  NOT NULL,
    quantity_before    INT                  NOT NULL,
    quantity_after     INT                  NOT NULL,
    reference_order_id BIGINT               NULL,
    staff_id           BIGINT               NULL,
    note               NVARCHAR(500)        NULL,
    created_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_inventory_movements_created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_inventory_movements PRIMARY KEY (movement_id),
    CONSTRAINT FK_inventory_movements_store_product FOREIGN KEY (store_product_id)   REFERENCES dbo.store_products (store_product_id),
    CONSTRAINT FK_inventory_movements_order         FOREIGN KEY (reference_order_id) REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_inventory_movements_staff         FOREIGN KEY (staff_id)           REFERENCES dbo.users (user_id),
    CONSTRAINT CK_inventory_movements_type   CHECK (type IN (N'ORDER', N'CANCEL_ORDER', N'STOCK_ADJUST', N'RETURN_ORDER')),
    CONSTRAINT CK_inventory_movements_before CHECK (quantity_before >= 0),
    CONSTRAINT CK_inventory_movements_after  CHECK (quantity_after >= 0),
    CONSTRAINT CK_inventory_movements_math   CHECK (quantity_after = quantity_before + quantity_change),
    CONSTRAINT CK_inventory_movements_by_type CHECK (
        (type = N'ORDER'        AND quantity_change < 0 AND reference_order_id IS NOT NULL) OR
        (type = N'CANCEL_ORDER' AND quantity_change > 0 AND reference_order_id IS NOT NULL) OR
        (type = N'STOCK_ADJUST' AND quantity_change <> 0 AND staff_id IS NOT NULL) OR
        (type = N'RETURN_ORDER' AND quantity_change > 0 AND reference_order_id IS NOT NULL AND staff_id IS NOT NULL))
);
GO

/* ============================================================================
   20. order_status_history  - Audit transition trang thai tong the cua Order
   ========================================================================== */
CREATE TABLE dbo.order_status_history (
    history_id         BIGINT IDENTITY(1,1) NOT NULL,
    order_id           BIGINT               NOT NULL,
    old_status         NVARCHAR(30)         NULL,
    new_status         NVARCHAR(30)         NOT NULL,
    changed_by_user_id BIGINT               NULL,
    note               NVARCHAR(500)        NULL,
    changed_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_order_status_history_changed DEFAULT SYSDATETIME(),
    CONSTRAINT PK_order_status_history PRIMARY KEY (history_id),
    CONSTRAINT FK_order_status_history_order FOREIGN KEY (order_id)           REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_order_status_history_user  FOREIGN KEY (changed_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_order_status_history_old CHECK (old_status IS NULL OR old_status IN (
        N'PENDING_PAYMENT', N'CONFIRMED', N'PROCESSING', N'SHIPPING',
        N'PARTIALLY_COMPLETED', N'COMPLETED', N'CANCELLED', N'NEEDS_ATTENTION')),
    CONSTRAINT CK_order_status_history_new CHECK (new_status IN (
        N'PENDING_PAYMENT', N'CONFIRMED', N'PROCESSING', N'SHIPPING',
        N'PARTIALLY_COMPLETED', N'COMPLETED', N'CANCELLED', N'NEEDS_ATTENTION'))
);
GO

/* ============================================================================
   21. reviews  - Danh gia theo order_item_id da mua (V3)
   ========================================================================== */
CREATE TABLE dbo.reviews (
    review_id     BIGINT IDENTITY(1,1) NOT NULL,
    user_id       BIGINT               NOT NULL,
    product_id    BIGINT               NOT NULL,
    order_item_id BIGINT               NULL,      -- V3: gan voi order_item_id da mua thuc te
    rating        INT                  NOT NULL,
    comment       NVARCHAR(1000)       NULL,
    status        NVARCHAR(20)         NOT NULL CONSTRAINT DF_reviews_status DEFAULT N'ACTIVE',
    created_at    DATETIME2(0)         NOT NULL CONSTRAINT DF_reviews_created_at DEFAULT SYSDATETIME(),
    order_id      BIGINT               NULL,      -- legacy
    reply         NVARCHAR(1000)       NULL,      -- legacy
    replied_by    BIGINT               NULL,      -- legacy
    replied_at    DATETIME2(0)         NULL,      -- legacy
    CONSTRAINT PK_reviews PRIMARY KEY (review_id),
    CONSTRAINT FK_reviews_user       FOREIGN KEY (user_id)       REFERENCES dbo.users (user_id),
    CONSTRAINT FK_reviews_product    FOREIGN KEY (product_id)    REFERENCES dbo.products (product_id),
    CONSTRAINT FK_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES dbo.order_items (order_item_id),
    CONSTRAINT FK_reviews_order      FOREIGN KEY (order_id)      REFERENCES dbo.orders (order_id),
    CONSTRAINT FK_reviews_replied_by FOREIGN KEY (replied_by)    REFERENCES dbo.users (user_id),
    CONSTRAINT CK_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT CK_reviews_status CHECK (status IN (N'ACTIVE', N'HIDDEN')),
    CONSTRAINT CK_reviews_reply CHECK (
        (reply IS NULL AND replied_by IS NULL AND replied_at IS NULL)
        OR
        (reply IS NOT NULL AND LEN(LTRIM(RTRIM(reply))) > 0
         AND replied_by IS NOT NULL AND replied_at IS NOT NULL))
);
GO

/* DEC-26 / BE-06 is part of the fresh V3 schema: item reviews are unique per
   purchased OrderItem, while legacy reviews without an item retain user/SKU uniqueness. */
CREATE UNIQUE INDEX UX_reviews_order_item ON dbo.reviews(order_item_id) WHERE order_item_id IS NOT NULL;
CREATE UNIQUE INDEX UX_reviews_legacy_user_product ON dbo.reviews(user_id, product_id) WHERE order_item_id IS NULL;
GO

/* ============================================================================
   22. review_replies  - Phan hoi danh gia tu nhan vien chi nhanh (V3 NEW)
   ========================================================================== */
CREATE TABLE dbo.review_replies (
    reply_id      BIGINT IDENTITY(1,1) NOT NULL,
    review_id     BIGINT               NOT NULL,
    staff_user_id BIGINT               NOT NULL,
    content       NVARCHAR(1000)       NOT NULL,
    created_at    DATETIME2(0)         NOT NULL CONSTRAINT DF_review_replies_created DEFAULT SYSDATETIME(),
    updated_at    DATETIME2(0)         NOT NULL CONSTRAINT DF_review_replies_updated DEFAULT SYSDATETIME(),
    CONSTRAINT PK_review_replies PRIMARY KEY (reply_id),
    CONSTRAINT UQ_review_replies_review UNIQUE (review_id),
    CONSTRAINT FK_review_replies_review FOREIGN KEY (review_id)     REFERENCES dbo.reviews (review_id),
    CONSTRAINT FK_review_replies_staff  FOREIGN KEY (staff_user_id) REFERENCES dbo.users (user_id)
);
GO

/* Query indexes used by catalog, allocation, staff queue and history views. */
CREATE INDEX IX_store_products_store_status_qty ON dbo.store_products (store_id, status, quantity);
CREATE INDEX IX_store_products_product_status ON dbo.store_products (product_id, status);
CREATE INDEX IX_order_fulfillments_store_status ON dbo.order_fulfillments (store_id, status, created_at);
CREATE INDEX IX_order_fulfillments_order_status ON dbo.order_fulfillments (order_id, status);
CREATE INDEX IX_orders_user_created ON dbo.orders (user_id, created_at);
CREATE INDEX IX_orders_store_status_created ON dbo.orders (store_id, order_status, created_at) WHERE store_id IS NOT NULL;
CREATE INDEX IX_order_items_fulfillment ON dbo.order_items (fulfillment_id);
CREATE INDEX IX_order_items_store_product ON dbo.order_items (store_product_id);
CREATE INDEX IX_inventory_movements_sp_created ON dbo.inventory_movements (store_product_id, created_at);
CREATE INDEX IX_order_status_history_order_changed ON dbo.order_status_history (order_id, changed_at);
CREATE INDEX IX_ofsh_fulfillment_changed ON dbo.order_fulfillment_status_history (fulfillment_id, changed_at);
CREATE UNIQUE INDEX UX_users_user_name ON dbo.users (user_name) WHERE user_name IS NOT NULL;
CREATE INDEX IX_reviews_order ON dbo.reviews (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IX_reviews_order_item ON dbo.reviews (order_item_id) WHERE order_item_id IS NOT NULL;
CREATE INDEX IX_cart_items_product ON dbo.cart_items (product_id);
GO

/* ============================================================================
   Forgot-password one-time codes. A code is bound to ONE account (user_id + the e-mail it was sent to);
   only a hash of the code is stored.
   ========================================================================== */
CREATE TABLE dbo.password_reset_otps (
    otp_id      BIGINT IDENTITY(1,1) NOT NULL,
    user_id     BIGINT        NOT NULL,
    email       NVARCHAR(255) NOT NULL,
    otp_hash    NVARCHAR(100) NOT NULL,
    expires_at  DATETIME2(0)  NOT NULL,
    attempts    INT           NOT NULL CONSTRAINT DF_password_reset_otps_attempts DEFAULT 0,
    consumed_at DATETIME2(0)  NULL,
    created_at  DATETIME2(0)  NOT NULL CONSTRAINT DF_password_reset_otps_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_password_reset_otps PRIMARY KEY (otp_id),
    CONSTRAINT FK_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_password_reset_otps_attempts CHECK (attempts >= 0)
);
CREATE INDEX IX_password_reset_otps_email ON dbo.password_reset_otps (email, created_at DESC);
GO

/* Structural verification (read only). Any violation THROWs, so a broken install is never silent. */
DECLARE @tables INT = (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0);
IF @tables <> 31 THROW 50001, 'Expected exactly 31 tables (29 domain tables, the image cleanup journal and the password reset codes).', 1;
DECLARE @expected TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @expected VALUES
 (N'roles'),(N'users'),(N'customer_addresses'),(N'stores'),(N'staff_store_assignments'),
 (N'categories'),(N'brands'),(N'products'),(N'product_images'),(N'store_products'),
 (N'carts'),(N'cart_items'),(N'checkout_sessions'),(N'orders'),(N'order_fulfillments'),
 (N'order_fulfillment_status_history'),(N'order_items'),(N'payments'),(N'inventory_movements'),
  (N'order_status_history'),(N'reviews'),(N'review_replies'),(N'payment_refunds'),(N'image_cleanup_tasks'),
 (N'order_returns'),(N'order_return_images'),(N'order_return_items'),(N'promotions'),(N'promotion_usages'),
 (N'password_reset_otps'),(N'payment_provider_events');
IF EXISTS (SELECT 1 FROM @expected e WHERE NOT EXISTS (SELECT 1 FROM sys.tables t WHERE t.name = e.name))
   OR EXISTS (SELECT 1 FROM sys.tables t WHERE t.is_ms_shipped = 0 AND NOT EXISTS (SELECT 1 FROM @expected e WHERE e.name = t.name))
    THROW 50011, 'Table set differs from the OneShop V3 specification list.', 1;
IF EXISTS (SELECT 1 FROM sys.tables t WHERE NOT EXISTS (SELECT 1 FROM sys.key_constraints k WHERE k.parent_object_id = t.object_id AND k.type = 'PK'))
    THROW 50012, 'A table has no primary key.', 1;
DECLARE @required TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @required VALUES
 (N'UQ_users_email'),(N'UQ_products_sku'),(N'UQ_store_products_store_product'),(N'UQ_cart_items_cart_product'),
 (N'UQ_order_fulfillments_order_store'),(N'UQ_review_replies_review'),
 (N'CK_products_price'),(N'CK_store_products_quantity'),(N'CK_cart_items_quantity'),
 (N'CK_order_items_quantity'),(N'CK_order_items_subtotal'),(N'CK_inventory_movements_math'),
  (N'CK_reviews_rating'),(N'UX_users_user_name'),(N'UX_product_images_one_primary'),
 (N'IX_store_products_store_status_qty'),(N'IX_store_products_product_status'),
 (N'IX_order_fulfillments_store_status'),(N'IX_order_fulfillments_order_status'),
 (N'IX_order_items_fulfillment'),(N'IX_order_items_store_product'),
 (N'IX_orders_user_created'),(N'IX_inventory_movements_sp_created'),
 (N'IX_order_status_history_order_changed'),(N'IX_ofsh_fulfillment_changed'),
 (N'UQ_order_returns_order'),(N'UQ_order_return_items_line'),(N'IX_order_return_items_store_disposition'),
 (N'UQ_promotions_code'),(N'UQ_promotion_usages_checkout'),(N'CK_promotions_limits'),(N'CK_order_items_discount');
IF EXISTS (SELECT 1 FROM @required r WHERE NOT EXISTS (SELECT 1 FROM sys.objects o WHERE o.name = r.name)
                                         AND NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = r.name))
    THROW 50013, 'A required UNIQUE/CHECK/INDEX object is missing.', 1;
IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.reviews') AND name IN (N'UX_reviews_order_item', N'UX_reviews_legacy_user_product')
     AND is_unique = 1 AND has_filter = 1 AND is_disabled = 0) <> 2
    THROW 50019, 'DEC-26 Review filtered unique indexes are missing.', 1;
IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name IN (N'UX_payment_refunds_goods', N'UX_payment_refunds_shipping', N'UX_payment_refunds_whole', N'UX_payment_refunds_return')
     AND is_unique = 1 AND has_filter = 1 AND is_disabled = 0) <> 4
    THROW 50023, 'OPEN-10.1 payment_refunds filtered unique indexes are missing.', 1;
IF (SELECT COUNT(*) FROM sys.indexes WHERE is_unique = 1 AND has_filter = 1 AND is_disabled = 0 AND (
      (object_id = OBJECT_ID(N'dbo.payments') AND name IN (N'UX_payments_provider_order_code', N'UX_payments_provider_link', N'UX_payments_one_pending_payos'))
      OR (object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'UX_payment_refunds_evidence'))) <> 4
    THROW 50024, 'payOS filtered unique indexes are missing.', 1;
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_reviews_user_product' AND parent_object_id = OBJECT_ID(N'dbo.reviews'))
    THROW 50022, 'The obsolete UQ_reviews_user_product constraint must not exist.', 1;
IF COL_LENGTH('dbo.products', 'price') IS NULL OR COL_LENGTH('dbo.cart_items', 'product_id') IS NULL OR COL_LENGTH('dbo.order_items', 'fulfillment_id') IS NULL
   OR COL_LENGTH('dbo.reviews', 'order_item_id') IS NULL OR COL_LENGTH('dbo.payments', 'refund_status') IS NULL
    THROW 50014, 'A V3 column is missing.', 1;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1) OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1)
    THROW 50090, 'Untrusted constraint found.', 1;
GO

PRINT 'OneShop V3 fresh-install schema created and verified: 31 tables including the image cleanup journal, the password reset codes, the post-delivery return flow and promotions, primary image uniqueness, DEC-26 and OPEN-10.1.';
GO
