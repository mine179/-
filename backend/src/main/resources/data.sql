MERGE INTO users (username, password, salt, role, permissions, enabled)
KEY(username)
VALUES ('admin', '123456', '', 'ADMIN', 'ALL', TRUE);

MERGE INTO users (username, password, salt, role, permissions, enabled)
KEY(username)
VALUES ('supplier01', '123456', '', 'SUPPLIER', 'SUBMIT_PRODUCT,QUOTE', TRUE);

MERGE INTO users (username, password, salt, role, permissions, enabled)
KEY(username)
VALUES ('customer01', '123456', '', 'CUSTOMER', 'UPLOAD_ORDER', TRUE);
