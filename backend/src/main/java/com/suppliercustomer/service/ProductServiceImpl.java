package com.suppliercustomer.service;

import com.suppliercustomer.exception.CustomException;
import com.suppliercustomer.mapper.ProductMapper;
import com.suppliercustomer.pojo.CustomerOrder;
import com.suppliercustomer.pojo.Product;
import com.suppliercustomer.pojo.SupplierQuote;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductServiceImpl implements ProductService {
    private static final String[] HEADERS = {
            "系列", "品牌", "编码", "新编码", "颜色", "类别", "工艺/材质", "规格型号", "通用型号",
            "尺寸", "分辨率", "型号备注", "销售价格", "供应价"
    };
    private static final List<FieldDef> PRODUCT_FIELDS = Arrays.asList(
            new FieldDef("系列", "series"),
            new FieldDef("品牌", "brand"),
            new FieldDef("编码", "code"),
            new FieldDef("新编码", "new_code"),
            new FieldDef("颜色", "color"),
            new FieldDef("类别", "category"),
            new FieldDef("工艺/材质", "craft_material"),
            new FieldDef("规格型号", "spec_model"),
            new FieldDef("通用型号", "common_model"),
            new FieldDef("尺寸", "size_value"),
            new FieldDef("分辨率", "resolution"),
            new FieldDef("型号备注", "model_remark"),
            new FieldDef("销售价格", "sale_price", "decimal"),
            new FieldDef("供应价", "purchase_price", "decimal")
    );
    private static final List<FieldDef> SUPPLIER_PRODUCT_FIELDS = withoutFields(PRODUCT_FIELDS, "code", "new_code", "sale_price");
    private static final List<FieldDef> CUSTOMER_PRODUCT_FIELDS = withoutFields(PRODUCT_FIELDS, "code", "new_code", "purchase_price");
    private static final List<FieldDef> SUPPLIER_QUOTE_PRICE_FIELDS = Arrays.asList(
            new FieldDef("订单编号", "order_no"),
            new FieldDef("物料编码", "code"),
            new FieldDef("规格型号", "spec_model"),
            new FieldDef("供应价", "purchase_price", "decimal")
    );
    private static final List<FieldDef> ADMIN_QUOTE_PRICE_FIELDS = Arrays.asList(
            new FieldDef("订单编号", "order_no"),
            new FieldDef("供应商账号", "supplier_username"),
            new FieldDef("物料编码", "code"),
            new FieldDef("规格型号", "spec_model"),
            new FieldDef("供应价", "purchase_price", "decimal")
    );
    private static final Map<String, List<FieldDef>> TEMPLATE_FIELDS = createTemplateFields();

    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<Map<String, Object>> listTable(String name) {
        if ("pricingAudit".equals(name)) {
            return productMapper.listPricingAuditRows();
        }
        return productMapper.listTable(tableName(name));
    }

    @Override
    public void updateTableRow(String name, Long id, Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        Set<String> allowed = editableColumns(name);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String column = toColumnName(entry.getKey());
            if (allowed.contains(column)) {
                data.put(column, entry.getValue());
            }
        }
        if (data.isEmpty()) {
            throw new CustomException("没有可修改的字段");
        }
        productMapper.updateTableRow(tableName(name), id, data);
        syncLinkedMaster(name, id);
    }

    @Override
    public void deleteTableRow(String name, Long id) {
        productMapper.deleteTableRow(tableName(name), id);
    }

    @Override
    public List<Product> listAdminOrderItems(String orderNo) {
        return productMapper.listAdminOrderItems(orderNo);
    }

    @Override
    public void cancelOrder(String orderNo) {
        CustomerOrder order = productMapper.findCustomerOrder(orderNo);
        ensureOrderCancellable(order);
        productMapper.cancelCustomerOrder(orderNo);
        productMapper.cancelCustomerOrderItemsByOrderNo(orderNo);
    }

    @Override
    public void cancelOrderItem(Long id) {
        Product item = productMapper.findCustomerOrderItem(id);
        productMapper.cancelCustomerOrderItem(id);
        if (item != null && !empty(item.getOrderNo())) {
            productMapper.markCustomerOrderCompletedIfReady(item.getOrderNo());
        }
    }

    @Override
    public void updateOrderItemPrices(Long id, Product product) {
        Product item = productMapper.findCustomerOrderItem(id);
        if (item == null) {
            throw new CustomException("订单产品不存在");
        }
        productMapper.updateCustomerOrderItemPrices(id, product.getPurchasePrice(), product.getSalePrice());
        if (product.getPurchasePrice() != null && !empty(item.getCode())) {
            productMapper.pushInternalOrderPrice(item.getCode(), priceTrendEntry(product.getPurchasePrice(), "内部备货"));
        }
        if (!empty(item.getOrderNo())) {
            productMapper.markCustomerOrderCompletedIfReady(item.getOrderNo());
        }
    }

    @Override
    public void addMaster(Product product) {
        product.setSourceType(empty(product.getSourceType()) ? "MANUAL" : product.getSourceType());
        productMapper.insertMaster(product);
    }

    @Override
    public void addInternalProduct(Product product) {
        saveInternalProduct(product);
    }

    @Override
    public void approveSupplier(Long id, Product product) {
        Product dbProduct = productMapper.findSupplierSubmission(id);
        if (dbProduct == null) {
            throw new CustomException("供应商提交记录不存在");
        }
        dbProduct.setCode(product.getCode());
        dbProduct.setNewCode(product.getNewCode());
        dbProduct.setId(id);
        dbProduct.setMasterProductId(null);
        productMapper.approveSupplierSubmission(dbProduct);
    }

    @Override
    public void approveUnmatched(Long id, Product product) {
        Product dbProduct = productMapper.findUnmatchedCustomerItem(id);
        if (dbProduct == null) {
            throw new CustomException("客户未匹配记录不存在");
        }
        dbProduct.setCode(product.getCode());
        dbProduct.setNewCode(product.getNewCode());
        dbProduct.setSupplierUsername(product.getSupplierUsername());
        dbProduct.setId(id);
        dbProduct.setMasterProductId(null);
        productMapper.approveUnmatchedCustomerItem(dbProduct);
    }

    @Override
    public void approveCustomerProduct(Long id, Product product) {
        Product dbProduct = productMapper.findCustomerProduct(id);
        if (dbProduct == null) {
            throw new CustomException("客户产品不存在");
        }
        dbProduct.setCode(product.getCode());
        dbProduct.setNewCode(product.getNewCode());
        dbProduct.setId(id);
        dbProduct.setMasterProductId(null);
        productMapper.approveCustomerProduct(dbProduct);
        productMapper.linkCustomerOrderItemsByModels(dbProduct);
    }

    @Override
    public String generateQuotes(String orderNo) {
        CustomerOrder order = productMapper.findCustomerOrder(orderNo);
        ensureOrderCanGenerateQuote(order);
        List<Product> items = productMapper.listMatchedOrderItems(orderNo);
        int count = 0;
        for (Product item : items) {
            List<Product> suppliers = productMapper.findSupplierSubmissionsByCode(item.getCode());
            for (Product supplier : suppliers) {
                SupplierQuote quote = new SupplierQuote();
                quote.setOrderNo(orderNo);
                quote.setCustomerItemId(item.getId());
                quote.setMasterProductId(supplier.getId());
                quote.setSupplierUsername(supplier.getSupplierUsername());
                quote.setCustomerUsername(item.getCustomerUsername());
                quote.setCode(item.getCode());
                quote.setSpecModel(item.getSpecModel());
                quote.setPurchasePrice(supplier.getPurchasePrice());
                quote.setSalePrice(item.getSalePrice());
                quote.setStatus("WAIT_SUPPLIER_PRICE");
                productMapper.insertSupplierQuote(quote);
                count++;
            }
        }
        if (count > 0) {
            productMapper.markCustomerOrderQuoted(orderNo);
        }
        return "已生成 " + count + " 条供应商报价任务";
    }

    @Override
    public void adminUpdateQuote(Long id, SupplierQuote quote) {
        quote.setId(id);
        if (quote.getPurchasePrice() != null && empty(quote.getStatus())) {
            quote.setStatus("SUPPLIER_PRICED");
        }
        productMapper.adminUpdateQuote(quote);
    }

    @Override
    public void adminUpdateQuoteBatch(List<SupplierQuote> quotes) {
        if (quotes == null) {
            return;
        }
        for (SupplierQuote quote : quotes) {
            if (quote.getId() == null) {
                continue;
            }
            if (quote.getPurchasePrice() != null && empty(quote.getStatus())) {
                quote.setStatus("SUPPLIER_PRICED");
            }
            productMapper.adminUpdateQuote(quote);
        }
    }

    @Override
    public void useSupplierQuote(Long id) {
        SupplierQuote quote = productMapper.findSupplierQuote(id);
        if (quote == null) {
            throw new CustomException("报价记录不存在");
        }
        if (quote.getPurchasePrice() == null) {
            throw new CustomException("请先填写供应价，再采用价格");
        }
        if (quote.getSalePrice() == null) {
            throw new CustomException("请先填写销售价，再采用价格");
        }
        if (quote.getCustomerItemId() == null) {
            throw new CustomException("报价缺少客户产品明细，不能写回订单");
        }
        if (empty(quote.getCode())) {
            throw new CustomException("报价缺少物料编码，不能写入价格趋势表");
        }
        productMapper.useQuoteOnCustomerItem(quote.getCustomerItemId(), quote.getPurchasePrice(), quote.getSalePrice());
        productMapper.updateInternalSalePriceByCode(quote.getCode(), quote.getSalePrice());
        productMapper.pushInternalOrderPrice(quote.getCode(), priceTrendEntry(quote.getPurchasePrice(), quote.getSupplierUsername()));
        productMapper.markQuotePricingUsed(id);
        productMapper.markOtherQuotePricingNotUsed(id, quote.getOrderNo(), quote.getCode());
        productMapper.markCustomerOrderCompletedIfReady(quote.getOrderNo());
    }

    @Override
    public ResponseEntity<byte[]> adminQuoteDownload(List<Long> quoteIds) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Sheet sheet = workbook.createSheet("报价填价");
        Row header = sheet.createRow(0);
        for (int i = 0; i < ADMIN_QUOTE_PRICE_FIELDS.size(); i++) {
            header.createCell(i).setCellValue(ADMIN_QUOTE_PRICE_FIELDS.get(i).label);
            sheet.setColumnWidth(i, 4200);
        }

        Set<String> selectedGroups = selectedQuoteGroups(quoteIds);
        int rowIndex = 1;
        for (Map<String, Object> item : listTable("quotes")) {
            String groupKey = quoteGroupKey(item);
            if (!selectedGroups.contains(groupKey)) {
                continue;
            }
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(stringValue(item.get("order_no")));
            row.createCell(1).setCellValue(stringValue(item.get("supplier_username")));
            row.createCell(2).setCellValue(stringValue(item.get("code")));
            row.createCell(3).setCellValue(stringValue(item.get("spec_model")));
            Object price = item.get("purchase_price");
            if (price instanceof Number) {
                row.createCell(4).setCellValue(((Number) price).doubleValue());
            } else {
                row.createCell(4).setCellValue(stringValue(price));
            }
        }

        workbook.write(outputStream);
        workbook.close();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin-quote-orders.xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(outputStream.toByteArray());
    }

    @Override
    public Map<String, Object> importAdminQuotePrices(MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, ADMIN_QUOTE_PRICE_FIELDS);
        int total = 0;
        int updated = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, ADMIN_QUOTE_PRICE_FIELDS.size())) {
                continue;
            }
            String orderNo = cell(row.getCell(0));
            String supplierUsername = cell(row.getCell(1));
            String code = cell(row.getCell(2));
            String price = cell(row.getCell(4));
            total++;
            if (empty(orderNo) || empty(supplierUsername) || empty(code) || empty(price)) {
                continue;
            }
            try {
                SupplierQuote quote = new SupplierQuote();
                quote.setOrderNo(orderNo);
                quote.setSupplierUsername(supplierUsername);
                quote.setCode(code);
                quote.setPurchasePrice(decimal(price));
                updated += productMapper.supplierUpdateQuoteByOrderAndCode(quote);
            } catch (NumberFormatException e) {
                // 非数字价格忽略，不新增也不中断。
            }
        }
        workbook.close();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        result.put("updated", updated);
        return result;
    }

    @Override
    public List<Product> listSupplierSubmissions(String supplierUsername) {
        return productMapper.listSupplierSubmissions(supplierUsername);
    }

    @Override
    public void addSupplierSubmission(String supplierUsername, Product product) {
        clearProtectedCodes(product);
        product.setSupplierUsername(supplierUsername);
        product.setStatus("PENDING");
        productMapper.insertSupplierSubmission(product);
    }

    @Override
    public List<SupplierQuote> listSupplierQuotes(String supplierUsername) {
        return productMapper.listSupplierQuotes(supplierUsername);
    }

    @Override
    public List<Map<String, Object>> listSupplierQuoteOrders(String supplierUsername) {
        return productMapper.listSupplierQuoteOrders(supplierUsername);
    }

    @Override
    public List<SupplierQuote> listSupplierQuoteItems(String supplierUsername, String orderNo) {
        return productMapper.listSupplierQuoteItems(supplierUsername, orderNo);
    }

    @Override
    public List<Map<String, Object>> listSupplierQuoteItemDetails(String supplierUsername, String orderNo) {
        return productMapper.listSupplierQuoteItemDetails(supplierUsername, orderNo);
    }

    @Override
    public List<Map<String, Object>> listAdminSupplierQuoteItemDetails(String supplierUsername, String orderNo) {
        return productMapper.listAdminSupplierQuoteItemDetails(supplierUsername, orderNo);
    }

    @Override
    public void supplierUpdateQuote(String supplierUsername, Long id, SupplierQuote quote) {
        quote.setId(id);
        quote.setSupplierUsername(supplierUsername);
        productMapper.supplierUpdateQuote(quote);
    }

    @Override
    public void supplierUpdateQuoteBatch(String supplierUsername, List<SupplierQuote> quotes) {
        if (quotes == null) {
            return;
        }
        for (SupplierQuote quote : quotes) {
            if (quote.getId() == null) {
                continue;
            }
            quote.setSupplierUsername(supplierUsername);
            productMapper.supplierUpdateQuote(quote);
        }
    }

    @Override
    public ResponseEntity<byte[]> supplierQuoteDownload(String supplierUsername, List<String> orderNos) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Sheet sheet = workbook.createSheet("报价填价");
        Row header = sheet.createRow(0);
        for (int i = 0; i < SUPPLIER_QUOTE_PRICE_FIELDS.size(); i++) {
            header.createCell(i).setCellValue(SUPPLIER_QUOTE_PRICE_FIELDS.get(i).label);
            sheet.setColumnWidth(i, 4200);
        }
        int rowIndex = 1;
        List<String> selected = orderNos == null ? Collections.<String>emptyList() : orderNos;
        for (String orderNo : selected) {
            List<SupplierQuote> items = productMapper.listSupplierQuoteItems(supplierUsername, orderNo);
            for (SupplierQuote item : items) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.getOrderNo());
                row.createCell(1).setCellValue(item.getCode());
                row.createCell(2).setCellValue(item.getSpecModel());
                if (item.getPurchasePrice() != null) {
                    row.createCell(3).setCellValue(item.getPurchasePrice().doubleValue());
                } else {
                    row.createCell(3).setCellValue("");
                }
            }
        }
        workbook.write(outputStream);
        workbook.close();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"supplier-quote-orders.xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(outputStream.toByteArray());
    }

    @Override
    public Map<String, Object> importSupplierQuotePrices(String supplierUsername, MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, SUPPLIER_QUOTE_PRICE_FIELDS);
        int total = 0;
        int updated = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, SUPPLIER_QUOTE_PRICE_FIELDS.size())) {
                continue;
            }
            String orderNo = cell(row.getCell(0));
            String code = cell(row.getCell(1));
            String price = cell(row.getCell(3));
            total++;
            if (empty(orderNo) || empty(code) || empty(price)) {
                continue;
            }
            try {
                SupplierQuote quote = new SupplierQuote();
                quote.setSupplierUsername(supplierUsername);
                quote.setOrderNo(orderNo);
                quote.setCode(code);
                quote.setPurchasePrice(decimal(price));
                updated += productMapper.supplierUpdateQuoteByOrderAndCode(quote);
            } catch (NumberFormatException e) {
                // 非数字价格忽略，不新增也不中断。
            }
        }
        workbook.close();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        result.put("updated", updated);
        return result;
    }

    @Override
    public List listCustomerOrders(String customerUsername) {
        return productMapper.listCustomerOrders(customerUsername);
    }

    @Override
    public List<Product> listCustomerOrderItems(String orderNo, String customerUsername) {
        return productMapper.listCustomerOrderItems(orderNo, customerUsername);
    }

    @Override
    public List<Product> listCustomerProducts(String customerUsername) {
        return productMapper.listCustomerProducts(customerUsername);
    }

    @Override
    public List<Product> listInternalProducts() {
        return productMapper.listInternalProducts();
    }

    @Override
    public Map<String, Object> addCustomerOrder(String customerUsername, Product product) {
        String orderNo = nextOrderNo();
        CustomerOrder order = new CustomerOrder();
        order.setOrderNo(orderNo);
        order.setCustomerUsername(customerUsername);
        order.setStatus("SUBMITTED");
        productMapper.insertCustomerOrder(order);
        saveCustomerOrderItem(orderNo, customerUsername, product);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "新增订单完成");
        result.put("orderNo", orderNo);
        result.put("total", 1);
        return result;
    }

    @Override
    public Map<String, Object> addCustomerOrderFromInternal(String customerUsername, List<Long> internalProductIds) {
        if (internalProductIds == null || internalProductIds.isEmpty()) {
            throw new CustomException("请先勾选主表产品");
        }
        String orderNo = nextOrderNo();
        CustomerOrder order = new CustomerOrder();
        order.setOrderNo(orderNo);
        order.setCustomerUsername(customerUsername);
        order.setStatus("SUBMITTED");
        productMapper.insertCustomerOrder(order);

        int total = 0;
        for (Long id : internalProductIds) {
            Product internal = productMapper.findInternalProduct(id);
            if (internal == null) {
                continue;
            }
            Product item = copyProduct(internal);
            item.setOrderNo(orderNo);
            item.setCustomerUsername(customerUsername);
            item.setStatus("ACTIVE");
            item.setMatched(!empty(internal.getCode()));
            item.setMasterProductId(internal.getId());
            productMapper.insertCustomerOrderItem(item);
            ensureCustomerProductFromInternal(customerUsername, internal);
            total++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "下单完成");
        result.put("orderNo", orderNo);
        result.put("total", total);
        return result;
    }

    @Override
    public void updateCustomerProduct(String customerUsername, Long id, Product product) {
        Product dbProduct = productMapper.findCustomerProductByCustomer(id, customerUsername);
        if (dbProduct == null) {
            throw new CustomException("客户产品不存在");
        }
        Product renamed = copyProduct(dbProduct);
        renamed.setSeries(product.getSeries());
        renamed.setBrand(product.getBrand());
        renamed.setColor(product.getColor());
        renamed.setCategory(product.getCategory());
        renamed.setCraftMaterial(product.getCraftMaterial());
        renamed.setSpecModel(product.getSpecModel());
        renamed.setCommonModel(product.getCommonModel());
        renamed.setSizeValue(product.getSizeValue());
        renamed.setResolution(product.getResolution());
        renamed.setModelRemark(product.getModelRemark());
        renamed.setSalePrice(product.getSalePrice());
        renamed.setCustomerUsername(customerUsername);
        renamed.setOrderNo(dbProduct.getOrderNo());

        product.setId(id);
        product.setCustomerUsername(customerUsername);
        product.setCode(dbProduct.getCode());
        product.setNewCode(dbProduct.getNewCode());
        product.setMasterProductId(dbProduct.getMasterProductId());
        productMapper.updateCustomerProductByCustomer(product);
    }

    @Override
    public void cancelCustomerOrder(String customerUsername, String orderNo) {
        CustomerOrder order = productMapper.findCustomerOrderByCustomer(orderNo, customerUsername);
        ensureOrderCancellable(order);
        productMapper.cancelCustomerOrder(orderNo);
        productMapper.cancelCustomerOrderItemsByOrderNo(orderNo);
    }

    @Override
    public void cancelCustomerOrderItem(String customerUsername, Long id) {
        productMapper.cancelCustomerOrderItemByCustomer(id, customerUsername);
    }

    @Override
    public ResponseEntity<byte[]> template() throws IOException {
        return buildTemplate("customer-template.xlsx", CUSTOMER_PRODUCT_FIELDS);
    }

    @Override
    public Map<String, Object> upload(String customerUsername, MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, CUSTOMER_PRODUCT_FIELDS);

        String orderNo = nextOrderNo();
        CustomerOrder order = new CustomerOrder();
        order.setOrderNo(orderNo);
        order.setCustomerUsername(customerUsername);
        order.setStatus("SUBMITTED");
        productMapper.insertCustomerOrder(order);

        int total = 0;
        int unmatched = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, CUSTOMER_PRODUCT_FIELDS.size())) {
                continue;
            }
            Product product = readRow(row, CUSTOMER_PRODUCT_FIELDS);
            Product master = saveCustomerOrderItem(orderNo, customerUsername, product);
            if (master == null) {
                product.setStatus("WAIT_CODE");
                productMapper.insertUnmatchedCustomerItem(product);
                unmatched++;
            }
            total++;
        }
        workbook.close();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "上传完成");
        result.put("orderNo", orderNo);
        result.put("total", total);
        result.put("unmatched", unmatched);
        return result;
    }

    private void ensureOrderCancellable(CustomerOrder order) {
        if (order == null) {
            throw new CustomException("订单不存在");
        }
        String status = order.getStatus();
        if ("QUOTE_GENERATED".equals(status)) {
            throw new CustomException("已生成报价任务的订单不能作废");
        }
        if ("CANCELLED".equals(status)) {
            throw new CustomException("订单已作废");
        }
    }

    private void ensureOrderCanGenerateQuote(CustomerOrder order) {
        if (order == null) {
            throw new CustomException("订单不存在");
        }
        String status = order.getStatus();
        if ("CANCELLED".equals(status)) {
            throw new CustomException("已作废的订单不能生成报价任务");
        }
        if ("QUOTE_GENERATED".equals(status)) {
            throw new CustomException("订单已生成报价任务");
        }
    }

    private Product readRow(Row row, List<FieldDef> fields) {
        Product product = new Product();
        for (int i = 0; i < fields.size(); i++) {
            setProductValue(product, fields.get(i).column, cell(row.getCell(i)));
        }
        return product;
    }

    private void setProductValue(Product product, String column, String value) {
        if ("series".equals(column)) product.setSeries(value);
        if ("brand".equals(column)) product.setBrand(value);
        if ("code".equals(column)) product.setCode(value);
        if ("new_code".equals(column)) product.setNewCode(value);
        if ("color".equals(column)) product.setColor(value);
        if ("category".equals(column)) product.setCategory(value);
        if ("craft_material".equals(column)) product.setCraftMaterial(value);
        if ("spec_model".equals(column)) product.setSpecModel(value);
        if ("common_model".equals(column)) product.setCommonModel(value);
        if ("size_value".equals(column)) product.setSizeValue(value);
        if ("resolution".equals(column)) product.setResolution(value);
        if ("model_remark".equals(column)) product.setModelRemark(value);
        if ("sale_price".equals(column)) product.setSalePrice(decimal(value));
        if ("purchase_price".equals(column)) product.setPurchasePrice(decimal(value));
    }

    private boolean linkedStatus(Product product) {
        return product != null && "APPROVED".equals(product.getStatus());
    }

    private void syncLinkedMaster(String name, Long id) {
        // 总表已经从业务流程中移除，链接只负责写入物料编码。
    }

    private String cell(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        String value = formatter.formatCellValue(cell);
        return value == null ? "" : value.trim();
    }

    private BigDecimal decimal(String value) {
        return empty(value) ? null : new BigDecimal(value);
    }

    @Override
    public ResponseEntity<byte[]> tableTemplate(String name) throws IOException {
        return buildTemplate(name + "-template.xlsx", templateFields(name));
    }

    @Override
    public Map<String, Object> importTable(String name, MultipartFile file) throws IOException {
        if ("orders".equals(name)) {
            return importAdminCustomerOrders(file);
        }
        if ("internal".equals(name)) {
            return importInternalProducts(file);
        }
        if ("supplier".equals(name)) {
            return importAdminSupplierRows(file);
        }
        List<FieldDef> fields = templateFields(name);
        String dbTable = tableName(name);
        int total = importRows(file, fields, dbTable, Collections.<String, Object>emptyMap());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        return result;
    }

    private Map<String, Object> importAdminSupplierRows(MultipartFile file) throws IOException {
        List<FieldDef> fields = templateFields("supplier");
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, fields);
        int total = 0;
        int updated = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, fields.size())) {
                continue;
            }
            Map<String, Object> data = readTableRow(row, fields);
            String supplierUsername = String.valueOf(data.get("supplier_username") == null ? "" : data.get("supplier_username"));
            String specModel = String.valueOf(data.get("spec_model") == null ? "" : data.get("spec_model"));
            if (empty(supplierUsername)) {
                workbook.close();
                throw new CustomException("第 " + (i + 1) + " 行缺少供应商账号");
            }
            Product existing = productMapper.findSupplierSubmissionBySpecAndSupplier(specModel, supplierUsername);
            if (existing == null) {
                data.put("status", "PENDING");
                productMapper.insertTableRow("supplier_submissions", data);
            } else {
                productMapper.updateTableRow("supplier_submissions", existing.getId(), data);
                syncLinkedMaster("supplier", existing.getId());
                updated++;
            }
            total++;
        }
        workbook.close();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        result.put("updated", updated);
        return result;
    }

    private Map<String, Object> importInternalProducts(MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, PRODUCT_FIELDS);
        int total = 0;
        int updated = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, PRODUCT_FIELDS.size())) {
                continue;
            }
            Product product = readRow(row, PRODUCT_FIELDS);
            Product existing = empty(product.getCode()) ? null : productMapper.findInternalProductByCode(product.getCode());
            if (existing == null) {
                saveInternalProduct(product);
            } else {
                product.setId(existing.getId());
                productMapper.updateTableRow("internal_products", existing.getId(), internalProductData(product));
                updated++;
            }
            total++;
        }
        workbook.close();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        result.put("updated", updated);
        return result;
    }

    private Map<String, Object> importAdminCustomerOrders(MultipartFile file) throws IOException {
        List<FieldDef> fields = templateFields("orders");
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, fields);

        Map<String, String> orderNoByCustomer = new LinkedHashMap<>();
        int total = 0;
        int unmatched = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, fields.size())) {
                continue;
            }
            Product product = readRow(row, CUSTOMER_PRODUCT_FIELDS);
            String customerUsername = cell(row.getCell(CUSTOMER_PRODUCT_FIELDS.size()));
            if (empty(customerUsername)) {
                workbook.close();
                throw new CustomException("第 " + (i + 1) + " 行缺少客户账号");
            }
            String orderNo = orderNoByCustomer.get(customerUsername);
            if (orderNo == null) {
                orderNo = nextOrderNo() + String.format("%03d", orderNoByCustomer.size() + 1);
                CustomerOrder order = new CustomerOrder();
                order.setOrderNo(orderNo);
                order.setCustomerUsername(customerUsername);
                order.setStatus("SUBMITTED");
                productMapper.insertCustomerOrder(order);
                orderNoByCustomer.put(customerUsername, orderNo);
            }
            Product master = saveCustomerOrderItem(orderNo, customerUsername, product);
            if (master == null) {
                product.setStatus("WAIT_CODE");
                productMapper.insertUnmatchedCustomerItem(product);
                unmatched++;
            }
            total++;
        }
        workbook.close();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        result.put("unmatched", unmatched);
        result.put("orders", orderNoByCustomer.size());
        return result;
    }

    private Product saveCustomerOrderItem(String orderNo, String customerUsername, Product product) {
        clearProtectedCodes(product);
        product.setOrderNo(orderNo);
        product.setCustomerUsername(customerUsername);
        product.setStatus("WAIT_CODE");
        Product master = null;
        product.setMatched(false);
        product.setMasterProductId(null);
        productMapper.insertCustomerOrderItem(product);
        ensureCustomerProductFromSelf(customerUsername, product);
        return master;
    }

    private void ensureCustomerProductFromInternal(String customerUsername, Product internal) {
        if (empty(internal.getCode())) {
            return;
        }
        Product existing = productMapper.findCustomerProductByCode(customerUsername, internal.getCode());
        if (existing != null) {
            return;
        }
        Product libraryProduct = copyProduct(internal);
        libraryProduct.setOrderNo("");
        libraryProduct.setCustomerUsername(customerUsername);
        libraryProduct.setStatus(empty(internal.getCode()) ? "WAIT_CODE" : "APPROVED");
        libraryProduct.setMatched(!empty(internal.getCode()));
        libraryProduct.setMasterProductId(internal.getId());
        productMapper.insertCustomerProduct(libraryProduct);
    }

    private void ensureCustomerProductFromSelf(String customerUsername, Product product) {
        String specModel = product.getSpecModel() == null ? "" : product.getSpecModel();
        String commonModel = product.getCommonModel() == null ? "" : product.getCommonModel();
        Product existing = productMapper.findCustomerProductByModels(customerUsername, specModel, commonModel);
        if (existing != null) {
            return;
        }
        Product libraryProduct = copyProduct(product);
        libraryProduct.setOrderNo("");
        libraryProduct.setCustomerUsername(customerUsername);
        libraryProduct.setCode("");
        libraryProduct.setNewCode("");
        libraryProduct.setMatched(false);
        libraryProduct.setMasterProductId(null);
        libraryProduct.setStatus("WAIT_CODE");
        productMapper.insertCustomerProduct(libraryProduct);
    }

    private String nextOrderNo() {
        return "SO" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
    }

    private void saveInternalProduct(Product product) {
        productMapper.insertInternalProduct(product);
    }

    private Product copyProduct(Product source) {
        Product target = new Product();
        target.setSeries(source.getSeries());
        target.setBrand(source.getBrand());
        target.setCode(source.getCode());
        target.setNewCode(source.getNewCode());
        target.setColor(source.getColor());
        target.setCategory(source.getCategory());
        target.setCraftMaterial(source.getCraftMaterial());
        target.setSpecModel(source.getSpecModel());
        target.setCommonModel(source.getCommonModel());
        target.setSizeValue(source.getSizeValue());
        target.setResolution(source.getResolution());
        target.setModelRemark(source.getModelRemark());
        target.setSalePrice(source.getSalePrice());
        target.setPurchasePrice(source.getPurchasePrice());
        target.setUpdateDate(source.getUpdateDate());
        target.setSupplierUsername(source.getSupplierUsername());
        target.setCustomerUsername(source.getCustomerUsername());
        target.setOrderNo(source.getOrderNo());
        target.setMatched(source.getMatched());
        target.setMasterProductId(source.getMasterProductId());
        target.setLinkedMasterProductId(source.getLinkedMasterProductId());
        target.setStatus(source.getStatus());
        return target;
    }

    @Override
    public ResponseEntity<byte[]> supplierTemplate() throws IOException {
        return buildTemplate("supplier-products-template.xlsx", SUPPLIER_PRODUCT_FIELDS);
    }

    @Override
    public Map<String, Object> uploadSupplierSubmissions(String supplierUsername, MultipartFile file) throws IOException {
        int total = importSupplierRows(file, supplierUsername);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "导入完成");
        result.put("total", total);
        return result;
    }

    private int importSupplierRows(MultipartFile file, String supplierUsername) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, SUPPLIER_PRODUCT_FIELDS);
        int total = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, SUPPLIER_PRODUCT_FIELDS.size())) {
                continue;
            }
            Product product = readRow(row, SUPPLIER_PRODUCT_FIELDS);
            product.setSupplierUsername(supplierUsername);
            product.setStatus("PENDING");
            Product existing = productMapper.findSupplierSubmissionBySpecAndSupplier(
                    product.getSpecModel() == null ? "" : product.getSpecModel(),
                    supplierUsername
            );
            if (existing == null) {
                productMapper.insertSupplierSubmission(product);
            } else {
                product.setId(existing.getId());
                product.setMasterProductId(existing.getMasterProductId());
                product.setStatus(empty(existing.getStatus()) ? "PENDING" : existing.getStatus());
                productMapper.updateTableRow("supplier_submissions", existing.getId(), supplierProductData(product));
                syncLinkedMaster("supplier", existing.getId());
            }
            total++;
        }
        workbook.close();
        return total;
    }

    private ResponseEntity<byte[]> buildTemplate(String filename, List<FieldDef> fields) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Sheet sheet = workbook.createSheet("模板");
        Row row = sheet.createRow(0);
        for (int i = 0; i < fields.size(); i++) {
            row.createCell(i).setCellValue(fields.get(i).label);
            sheet.setColumnWidth(i, 4200);
        }
        workbook.write(outputStream);
        workbook.close();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(outputStream.toByteArray());
    }

    private int importRows(MultipartFile file, List<FieldDef> fields, String tableName, Map<String, Object> fixedValues) throws IOException {
        int total = 0;
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, fields);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || blankRow(row, fields.size())) {
                continue;
            }
            try {
                Map<String, Object> data = readTableRow(row, fields);
                data.putAll(fixedValues);
                if (!data.isEmpty()) {
                    productMapper.insertTableRow(tableName, data);
                    total++;
                }
            } catch (DataAccessException e) {
                throw new CustomException("第 " + (i + 1) + " 行导入失败，请检查是否有重复编号或必填字段为空");
            } catch (NumberFormatException e) {
                throw new CustomException("第 " + (i + 1) + " 行导入失败，价格字段只能填写数字");
            }
        }
        workbook.close();
        return total;
    }

    private void validateHeader(Sheet sheet, List<FieldDef> fields) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new CustomException("Excel 第一行必须是模板表头");
        }
        for (int i = 0; i < fields.size(); i++) {
            String actual = cell(header.getCell(i));
            String expected = fields.get(i).label;
            if (!expected.equals(actual)) {
                throw new CustomException("Excel 表头不匹配，请下载当前表模板后填写。第 " + (i + 1) + " 列应为：" + expected);
            }
        }
    }

    private Map<String, Object> readTableRow(Row row, List<FieldDef> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            FieldDef field = fields.get(i);
            String value = cell(row.getCell(i));
            if (empty(value)) {
                continue;
            }
            if ("decimal".equals(field.type)) {
                data.put(field.column, decimal(value));
            } else if ("boolean".equals(field.type)) {
                data.put(field.column, booleanValue(value));
            } else {
                data.put(field.column, value);
            }
        }
        return data;
    }

    private Map<String, Object> productBaseData(Product product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series", product.getSeries());
        data.put("brand", product.getBrand());
        data.put("code", product.getCode());
        data.put("new_code", product.getNewCode());
        data.put("color", product.getColor());
        data.put("category", product.getCategory());
        data.put("craft_material", product.getCraftMaterial());
        data.put("spec_model", product.getSpecModel());
        data.put("common_model", product.getCommonModel());
        data.put("size_value", product.getSizeValue());
        data.put("resolution", product.getResolution());
        data.put("model_remark", product.getModelRemark());
        data.put("sale_price", product.getSalePrice());
        data.put("purchase_price", product.getPurchasePrice());
        return data;
    }

    private Map<String, Object> internalProductData(Product product) {
        Map<String, Object> data = productBaseData(product);
        data.put("master_product_id", product.getMasterProductId());
        return data;
    }

    private Map<String, Object> supplierProductData(Product product) {
        Map<String, Object> data = productBaseData(product);
        data.put("supplier_username", product.getSupplierUsername());
        data.put("status", product.getStatus());
        data.put("master_product_id", product.getMasterProductId());
        return data;
    }

    private boolean blankRow(Row row, int size) {
        for (int i = 0; i < size; i++) {
            if (!empty(cell(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private Boolean booleanValue(String value) {
        return "是".equals(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private Set<String> selectedQuoteGroups(List<Long> quoteIds) {
        Set<Long> selectedIds = new HashSet<>();
        if (quoteIds != null) {
            for (Long id : quoteIds) {
                if (id != null) {
                    selectedIds.add(id);
                }
            }
        }
        Set<String> groups = new HashSet<>();
        for (Map<String, Object> item : listTable("quotes")) {
            Long id = longValue(item.get("id"));
            if (id != null && selectedIds.contains(id)) {
                groups.add(quoteGroupKey(item));
            }
        }
        return groups;
    }

    private String quoteGroupKey(Map<String, Object> item) {
        return stringValue(item.get("order_no")) + "|" + stringValue(item.get("supplier_username"));
    }

    private String priceTrendEntry(BigDecimal price, String supplier) {
        String amount = price.stripTrailingZeros().toPlainString();
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        return "￥" + amount + "/" + supplier + "/" + date;
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null || empty(String.valueOf(value))) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<FieldDef> templateFields(String name) {
        List<FieldDef> fields = TEMPLATE_FIELDS.get(name);
        if (fields == null) {
            throw new CustomException("未知表名");
        }
        return fields;
    }

    private boolean empty(String value) {
        return value == null || "".equals(value.trim());
    }

    private String tableName(String name) {
        if ("master".equals(name)) {
            return "master_products";
        }
        if ("supplier".equals(name)) {
            return "supplier_submissions";
        }
        if ("internal".equals(name) || "priceTrend".equals(name)) {
            return "internal_products";
        }
        if ("orders".equals(name)) {
            return "customer_orders";
        }
        if ("orderItems".equals(name)) {
            return "customer_order_items";
        }
        if ("customerProducts".equals(name)) {
            return "customer_products";
        }
        if ("unmatched".equals(name)) {
            return "unmatched_customer_items";
        }
        if ("quotes".equals(name) || "pricingAudit".equals(name)) {
            return "supplier_quotes";
        }
        throw new CustomException("未知表名");
    }

    private Set<String> editableColumns(String name) {
        Set<String> product = new HashSet<>(Arrays.asList(
                "series", "brand", "code", "new_code", "color", "category", "craft_material",
                "spec_model", "common_model", "size_value", "resolution", "model_remark", "sale_price",
                "purchase_price", "supplier_username", "customer_username", "source_type",
                "status", "order_no", "matched", "master_product_id"
        ));
        if ("master".equals(name) || "internal".equals(name)) {
            return new HashSet<>(Arrays.asList(
                    "series", "brand", "code", "new_code", "color", "category", "craft_material",
                    "spec_model", "common_model", "size_value", "resolution", "model_remark", "sale_price",
                    "purchase_price", "supplier_username", "customer_username", "master_product_id"
            ));
        }
        if ("priceTrend".equals(name)) {
            return new HashSet<>(Arrays.asList(
                    "manual_price_1", "manual_price_2", "manual_price_3", "manual_price_4", "manual_price_5"
            ));
        }
        if ("orders".equals(name)) {
            return new HashSet<>(Arrays.asList("order_no", "customer_username", "status"));
        }
        if ("customerProducts".equals(name)) {
            return new HashSet<>(Arrays.asList(
                    "series", "brand", "color", "category", "craft_material", "spec_model",
                    "common_model", "size_value", "resolution", "model_remark", "sale_price",
                    "customer_username", "status", "matched", "master_product_id"
            ));
        }
        if ("quotes".equals(name)) {
            return new HashSet<>(Arrays.asList(
                    "order_no", "customer_item_id", "master_product_id", "supplier_username",
                    "customer_username", "code", "spec_model", "purchase_price", "sale_price", "status"
            ));
        }
        return product;
    }

    private String toColumnName(String key) {
        if (key == null || "id".equals(key)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                builder.append('_').append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static Map<String, List<FieldDef>> createTemplateFields() {
        Map<String, List<FieldDef>> map = new LinkedHashMap<>();
        map.put("master", concat(PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("供应商账号", "supplier_username"),
                new FieldDef("客户账号", "customer_username")
        )));
        map.put("internal", PRODUCT_FIELDS);
        map.put("supplier", concat(SUPPLIER_PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("供应商账号", "supplier_username")
        )));
        map.put("orderItems", concat(CUSTOMER_PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("订单编号", "order_no"),
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("是否匹配", "matched", "boolean")
        )));
        map.put("customerProducts", concat(CUSTOMER_PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("是否匹配", "matched", "boolean"),
                new FieldDef("状态", "status")
        )));
        map.put("unmatched", concat(CUSTOMER_PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("订单编号", "order_no"),
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("状态", "status")
        )));
        map.put("orders", concat(CUSTOMER_PRODUCT_FIELDS, Collections.singletonList(
                new FieldDef("客户账号", "customer_username")
        )));
        map.put("quotes", Arrays.asList(
                new FieldDef("订单编号", "order_no"),
                new FieldDef("客户明细ID", "customer_item_id"),
                new FieldDef("供应商账号", "supplier_username"),
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("编码", "code"),
                new FieldDef("规格型号", "spec_model"),
                new FieldDef("供应价", "purchase_price", "decimal"),
                new FieldDef("销售价", "sale_price", "decimal"),
                new FieldDef("状态", "status")
        ));
        return map;
    }

    private static List<FieldDef> concat(List<FieldDef> first, List<FieldDef> second) {
        List<FieldDef> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static List<FieldDef> withoutFields(List<FieldDef> fields, String... columns) {
        Set<String> excluded = new HashSet<>(Arrays.asList(columns));
        List<FieldDef> result = new ArrayList<>();
        for (FieldDef field : fields) {
            if (!excluded.contains(field.column)) {
                result.add(field);
            }
        }
        return result;
    }

    private void clearProtectedCodes(Product product) {
        product.setCode("");
        product.setNewCode("");
    }

    private static class FieldDef {
        private final String label;
        private final String column;
        private final String type;

        private FieldDef(String label, String column) {
            this(label, column, "string");
        }

        private FieldDef(String label, String column, String type) {
            this.label = label;
            this.column = column;
            this.type = type;
        }
    }
}
