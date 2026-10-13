-- Sải Bước shoe shop: initial schema.
-- Money is VND (numeric(12,0)); all timestamps are timestamptz (UTC).

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20),
    address       VARCHAR(255),
    province      VARCHAR(80),
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    sort_order  INT          NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(140) NOT NULL UNIQUE,
    name        VARCHAR(140) NOT NULL,
    brand       VARCHAR(60)  NOT NULL,
    category_id BIGINT       NOT NULL REFERENCES categories (id),
    gender      VARCHAR(10)  NOT NULL CHECK (gender IN ('MEN', 'WOMEN', 'UNISEX', 'KIDS')),
    color       VARCHAR(40)  NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    price       NUMERIC(12, 0) NOT NULL CHECK (price > 0),
    sale_price  NUMERIC(12, 0) CHECK (sale_price IS NULL OR sale_price > 0),
    image       VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    featured    BOOLEAN      NOT NULL DEFAULT FALSE,
    sold_count  INT          NOT NULL DEFAULT 0,
    rating      NUMERIC(2, 1) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_active_created ON products (active, created_at DESC);
CREATE INDEX ix_products_brand ON products (brand);

CREATE TABLE product_variants (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id),
    size       VARCHAR(4)  NOT NULL,
    sku        VARCHAR(40) NOT NULL UNIQUE,
    stock      INT         NOT NULL DEFAULT 0 CHECK (stock >= 0),
    version    INT         NOT NULL DEFAULT 0,
    UNIQUE (product_id, size)
);
CREATE INDEX ix_variants_product ON product_variants (product_id);

CREATE TABLE coupons (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL UNIQUE,
    description  VARCHAR(160) NOT NULL DEFAULT '',
    type         VARCHAR(10)  NOT NULL CHECK (type IN ('PERCENT', 'FIXED')),
    value        NUMERIC(12, 0) NOT NULL CHECK (value > 0),
    min_order    NUMERIC(12, 0) NOT NULL DEFAULT 0,
    max_discount NUMERIC(12, 0),
    starts_on    DATE         NOT NULL,
    ends_on      DATE         NOT NULL,
    usage_limit  INT,
    used_count   INT          NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CHECK (ends_on >= starts_on),
    CHECK (type <> 'PERCENT' OR value <= 100)
);

CREATE SEQUENCE order_code_seq START 100001;

CREATE TABLE orders (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL UNIQUE,
    user_id        BIGINT REFERENCES users (id),
    customer_name  VARCHAR(120) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    email          VARCHAR(160),
    address        VARCHAR(255) NOT NULL,
    province       VARCHAR(80)  NOT NULL,
    note           VARCHAR(500),
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'PAYMENT_FAILED', 'CONFIRMED', 'SHIPPING',
                                                           'DELIVERED', 'CANCELLED')),
    payment_method VARCHAR(10)  NOT NULL CHECK (payment_method IN ('COD', 'CARD')),
    payment_status VARCHAR(10)  NOT NULL CHECK (payment_status IN ('UNPAID', 'PAID', 'FAILED', 'REFUNDED')),
    payment_ref    VARCHAR(60),
    payment_error  VARCHAR(255),
    subtotal       NUMERIC(12, 0) NOT NULL,
    discount       NUMERIC(12, 0) NOT NULL DEFAULT 0,
    shipping_fee   NUMERIC(12, 0) NOT NULL DEFAULT 0,
    total          NUMERIC(12, 0) NOT NULL,
    coupon_code    VARCHAR(30),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_orders_created ON orders (created_at DESC);
CREATE INDEX ix_orders_status ON orders (status);
CREATE INDEX ix_orders_user ON orders (user_id);
CREATE INDEX ix_orders_phone ON orders (phone);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders (id),
    product_id   BIGINT       NOT NULL REFERENCES products (id),
    variant_id   BIGINT       NOT NULL REFERENCES product_variants (id),
    product_name VARCHAR(140) NOT NULL,
    size         VARCHAR(4)   NOT NULL,
    image        VARCHAR(200) NOT NULL,
    unit_price   NUMERIC(12, 0) NOT NULL,
    quantity     INT          NOT NULL CHECK (quantity > 0),
    line_total   NUMERIC(12, 0) NOT NULL
);
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE order_history (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    note        VARCHAR(500),
    changed_by  VARCHAR(160),
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_history_order ON order_history (order_id);
