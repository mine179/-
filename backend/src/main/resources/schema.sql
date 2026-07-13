CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    salt VARCHAR(80),
    role VARCHAR(20) NOT NULL,
    permissions VARCHAR(500) DEFAULT '',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS master_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series VARCHAR(120),
    brand VARCHAR(120),
    code VARCHAR(120),
    new_code VARCHAR(120),
    color VARCHAR(120),
    category VARCHAR(120),
    craft_material VARCHAR(160),
    spec_model VARCHAR(200),
    common_model VARCHAR(200),
    size_value VARCHAR(120),
    resolution VARCHAR(120),
    model_remark VARCHAR(500),
    sale_price DECIMAL(14,2),
    purchase_price DECIMAL(14,2),
    price_valid_until DATE,
    update_date DATE,
    supplier_username VARCHAR(80),
    customer_username VARCHAR(80),
    order_no VARCHAR(60),
    linked_master_product_id BIGINT,
    source_type VARCHAR(40),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE master_products ADD COLUMN IF NOT EXISTS customer_username VARCHAR(80);
ALTER TABLE master_products ADD COLUMN IF NOT EXISTS order_no VARCHAR(60);
ALTER TABLE master_products ADD COLUMN IF NOT EXISTS linked_master_product_id BIGINT;
ALTER TABLE master_products ADD COLUMN IF NOT EXISTS price_valid_until DATE;

CREATE TABLE IF NOT EXISTS internal_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series VARCHAR(120),
    brand VARCHAR(120),
    code VARCHAR(120),
    new_code VARCHAR(120),
    color VARCHAR(120),
    category VARCHAR(120),
    craft_material VARCHAR(160),
    spec_model VARCHAR(200),
    common_model VARCHAR(200),
    size_value VARCHAR(120),
    resolution VARCHAR(120),
    model_remark VARCHAR(500),
    sale_price DECIMAL(14,2),
    purchase_price DECIMAL(14,2),
    manual_price_1 VARCHAR(160),
    manual_price_2 VARCHAR(160),
    manual_price_3 VARCHAR(160),
    manual_price_4 VARCHAR(160),
    manual_price_5 VARCHAR(160),
    order_price_1 VARCHAR(160),
    order_price_2 VARCHAR(160),
    order_price_3 VARCHAR(160),
    order_price_4 VARCHAR(160),
    order_price_5 VARCHAR(160),
    update_date DATE,
    master_product_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS master_product_id BIGINT;
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS manual_price_1 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS manual_price_2 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS manual_price_3 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS manual_price_4 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS manual_price_5 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS order_price_1 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS order_price_2 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS order_price_3 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS order_price_4 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS order_price_5 VARCHAR(160);
ALTER TABLE internal_products ADD COLUMN IF NOT EXISTS price_valid_until DATE;

CREATE TABLE IF NOT EXISTS supplier_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series VARCHAR(120),
    brand VARCHAR(120),
    code VARCHAR(120),
    new_code VARCHAR(120),
    color VARCHAR(120),
    category VARCHAR(120),
    craft_material VARCHAR(160),
    spec_model VARCHAR(200),
    common_model VARCHAR(200),
    size_value VARCHAR(120),
    resolution VARCHAR(120),
    model_remark VARCHAR(500),
    sale_price DECIMAL(14,2),
    purchase_price DECIMAL(14,2),
    update_date DATE,
    supplier_username VARCHAR(80) NOT NULL,
    master_product_id BIGINT,
    status VARCHAR(30) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE supplier_submissions ADD COLUMN IF NOT EXISTS master_product_id BIGINT;
ALTER TABLE supplier_submissions ADD COLUMN IF NOT EXISTS price_valid_until DATE;

CREATE TABLE IF NOT EXISTS customer_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(60) NOT NULL UNIQUE,
    customer_username VARCHAR(80) NOT NULL,
    status VARCHAR(30) DEFAULT 'SUBMITTED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(60) NOT NULL,
    series VARCHAR(120),
    brand VARCHAR(120),
    code VARCHAR(120),
    new_code VARCHAR(120),
    color VARCHAR(120),
    category VARCHAR(120),
    craft_material VARCHAR(160),
    spec_model VARCHAR(200),
    common_model VARCHAR(200),
    size_value VARCHAR(120),
    resolution VARCHAR(120),
    model_remark VARCHAR(500),
    sale_price DECIMAL(14,2),
    purchase_price DECIMAL(14,2),
    update_date DATE,
    customer_username VARCHAR(80) NOT NULL,
    matched BOOLEAN DEFAULT FALSE,
    master_product_id BIGINT,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE customer_order_items ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE';
ALTER TABLE customer_order_items ADD COLUMN IF NOT EXISTS pricing_group VARCHAR(80);
ALTER TABLE customer_order_items ADD COLUMN IF NOT EXISTS price_valid_until DATE;

CREATE TABLE IF NOT EXISTS customer_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series VARCHAR(120),
    brand VARCHAR(120),
    code VARCHAR(120),
    new_code VARCHAR(120),
    color VARCHAR(120),
    category VARCHAR(120),
    craft_material VARCHAR(160),
    spec_model VARCHAR(200),
    common_model VARCHAR(200),
    size_value VARCHAR(120),
    resolution VARCHAR(120),
    model_remark VARCHAR(500),
    sale_price DECIMAL(14,2),
    purchase_price DECIMAL(14,2),
    update_date DATE,
    customer_username VARCHAR(80) NOT NULL,
    matched BOOLEAN DEFAULT FALSE,
    master_product_id BIGINT,
    status VARCHAR(30) DEFAULT 'WAIT_CODE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
UPDATE customer_products
SET status='APPROVED',
    matched=true,
    updated_at=CURRENT_TIMESTAMP
WHERE status='ACTIVE';
ALTER TABLE customer_products ADD COLUMN IF NOT EXISTS price_valid_until DATE;

CREATE TABLE IF NOT EXISTS unmatched_customer_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(60) NOT NULL,
    series VARCHAR(120),
    brand VARCHAR(120),
    code VARCHAR(120),
    new_code VARCHAR(120),
    color VARCHAR(120),
    category VARCHAR(120),
    craft_material VARCHAR(160),
    spec_model VARCHAR(200),
    common_model VARCHAR(200),
    size_value VARCHAR(120),
    resolution VARCHAR(120),
    model_remark VARCHAR(500),
    sale_price DECIMAL(14,2),
    purchase_price DECIMAL(14,2),
    update_date DATE,
    customer_username VARCHAR(80) NOT NULL,
    master_product_id BIGINT,
    status VARCHAR(30) DEFAULT 'WAIT_CODE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE unmatched_customer_items ADD COLUMN IF NOT EXISTS master_product_id BIGINT;
ALTER TABLE unmatched_customer_items ADD COLUMN IF NOT EXISTS price_valid_until DATE;

CREATE TABLE IF NOT EXISTS supplier_quotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(60) NOT NULL,
    customer_item_id BIGINT,
    master_product_id BIGINT,
    supplier_username VARCHAR(80) NOT NULL,
    customer_username VARCHAR(80) NOT NULL,
    code VARCHAR(120),
    spec_model VARCHAR(200),
    purchase_price DECIMAL(14,2),
    sale_price DECIMAL(14,2),
    status VARCHAR(30) DEFAULT 'WAIT_SUPPLIER_PRICE',
    pricing_status VARCHAR(30) DEFAULT 'WAIT_USE_PRICE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE supplier_quotes ADD COLUMN IF NOT EXISTS pricing_status VARCHAR(30) DEFAULT 'WAIT_USE_PRICE';
ALTER TABLE supplier_quotes ADD COLUMN IF NOT EXISTS pricing_group VARCHAR(80);
ALTER TABLE supplier_quotes ADD COLUMN IF NOT EXISTS price_valid_until DATE;
