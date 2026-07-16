CREATE DATABASE IF NOT EXISTS supplier_customer
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE supplier_customer;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    salt VARCHAR(80),
    role VARCHAR(20) NOT NULL,
    permissions VARCHAR(500) DEFAULT '',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_master_code (code),
    INDEX idx_master_source_code (source_type, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    price_valid_until DATE,
    update_date DATE,
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
    master_product_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_internal_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    price_valid_until DATE,
    update_date DATE,
    supplier_username VARCHAR(80) NOT NULL,
    master_product_id BIGINT,
    status VARCHAR(30) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier_code (supplier_username, code),
    INDEX idx_supplier_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(60) NOT NULL UNIQUE,
    customer_username VARCHAR(80),
    status VARCHAR(30) DEFAULT 'SUBMITTED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_orders_customer (customer_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(60),
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
    customer_username VARCHAR(80) NOT NULL,
    matched BOOLEAN DEFAULT FALSE,
    master_product_id BIGINT,
    pricing_group VARCHAR(80),
    order_remark VARCHAR(500),
    material_link_status VARCHAR(30) DEFAULT 'UNLINKED',
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_items_order_no (order_no),
    INDEX idx_order_items_customer (customer_username),
    INDEX idx_order_items_code (code),
    INDEX idx_order_items_pricing_group (pricing_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    price_valid_until DATE,
    update_date DATE,
    customer_username VARCHAR(80) NOT NULL,
    matched BOOLEAN DEFAULT FALSE,
    master_product_id BIGINT,
    material_link_status VARCHAR(30) DEFAULT 'UNLINKED',
    status VARCHAR(30) DEFAULT 'WAIT_CODE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_products_customer (customer_username),
    INDEX idx_customer_products_code (customer_username, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    price_valid_until DATE,
    update_date DATE,
    customer_username VARCHAR(80) NOT NULL,
    master_product_id BIGINT,
    status VARCHAR(30) DEFAULT 'WAIT_CODE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_unmatched_order_no (order_no),
    INDEX idx_unmatched_customer (customer_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS supplier_quotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_item_id BIGINT,
    master_product_id BIGINT,
    supplier_username VARCHAR(80) NOT NULL,
    code VARCHAR(120),
    pricing_group VARCHAR(80),
    spec_model VARCHAR(200),
    purchase_price DECIMAL(14,2),
    sale_price DECIMAL(14,2),
    price_valid_until DATE,
    status VARCHAR(30) DEFAULT 'WAIT_SUPPLIER_PRICE',
    pricing_status VARCHAR(30) DEFAULT 'WAIT_USE_PRICE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_quotes_supplier (supplier_username),
    INDEX idx_quotes_code_group (code, pricing_group),
    INDEX idx_quotes_customer_item (customer_item_id),
    INDEX idx_quotes_status (status, pricing_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO users(username,password,salt,role,permissions,enabled)
VALUES ('admin','123456','','ADMIN','ALL',TRUE)
ON DUPLICATE KEY UPDATE username=username;

INSERT INTO users(username,password,salt,role,permissions,enabled)
VALUES ('supplier01','123456','','SUPPLIER','SUBMIT_PRODUCT,QUOTE',TRUE)
ON DUPLICATE KEY UPDATE username=username;

INSERT INTO users(username,password,salt,role,permissions,enabled)
VALUES ('customer01','123456','','CUSTOMER','UPLOAD_ORDER',TRUE)
ON DUPLICATE KEY UPDATE username=username;
