USE supplier_customer;

DELIMITER //

CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ', column_definition_value);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE drop_column_if_exists(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` DROP COLUMN `', column_name_value, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL add_column_if_missing('master_products', 'price_valid_until', 'DATE');
CALL add_column_if_missing('master_products', 'linked_master_product_id', 'BIGINT');
CALL add_column_if_missing('master_products', 'customer_username', 'VARCHAR(80)');
CALL add_column_if_missing('master_products', 'order_no', 'VARCHAR(60)');

CALL add_column_if_missing('internal_products', 'price_valid_until', 'DATE');
CALL add_column_if_missing('internal_products', 'master_product_id', 'BIGINT');
CALL add_column_if_missing('internal_products', 'manual_price_1', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'manual_price_2', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'manual_price_3', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'manual_price_4', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'manual_price_5', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'order_price_1', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'order_price_2', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'order_price_3', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'order_price_4', 'VARCHAR(160)');
CALL add_column_if_missing('internal_products', 'order_price_5', 'VARCHAR(160)');

CALL add_column_if_missing('supplier_submissions', 'price_valid_until', 'DATE');
CALL add_column_if_missing('supplier_submissions', 'master_product_id', 'BIGINT');

CALL add_column_if_missing('customer_order_items', 'price_valid_until', 'DATE');
CALL add_column_if_missing('customer_order_items', 'pricing_group', 'VARCHAR(80)');
CALL add_column_if_missing('customer_order_items', 'order_remark', 'VARCHAR(500)');
CALL add_column_if_missing('customer_order_items', 'material_link_status', 'VARCHAR(30) DEFAULT ''UNLINKED''');
CALL add_column_if_missing('customer_order_items', 'status', 'VARCHAR(30) DEFAULT ''ACTIVE''');

CALL add_column_if_missing('customer_products', 'price_valid_until', 'DATE');
CALL add_column_if_missing('customer_products', 'material_link_status', 'VARCHAR(30) DEFAULT ''UNLINKED''');

CALL add_column_if_missing('unmatched_customer_items', 'price_valid_until', 'DATE');
CALL add_column_if_missing('unmatched_customer_items', 'master_product_id', 'BIGINT');

CALL add_column_if_missing('supplier_quotes', 'pricing_group', 'VARCHAR(80)');
CALL add_column_if_missing('supplier_quotes', 'price_valid_until', 'DATE');
CALL add_column_if_missing('supplier_quotes', 'pricing_status', 'VARCHAR(30) DEFAULT ''WAIT_USE_PRICE''');

CALL drop_column_if_exists('supplier_quotes', 'order_no');
CALL drop_column_if_exists('supplier_quotes', 'customer_username');

UPDATE customer_products
SET status = 'APPROVED',
    matched = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE';

UPDATE customer_products
SET material_link_status = 'LINKED'
WHERE code IS NOT NULL
  AND code != ''
  AND (material_link_status IS NULL OR material_link_status = '' OR material_link_status = 'UNLINKED');

DROP PROCEDURE add_column_if_missing;
DROP PROCEDURE drop_column_if_exists;
