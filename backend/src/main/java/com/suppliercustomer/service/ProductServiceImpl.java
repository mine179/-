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
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {
    private static final String VALID_PRICE_SUPPLIER = "\u6709\u6548\u671f\u5185\u4ef7\u683c";
    private static final String[] HEADERS = {
            "系列", "品牌", "物料编码", "新编码", "颜色", "类别", "工艺/材质", "规格型号", "通用型号",
            "尺寸", "分辨率", "型号备注", "销售价格", "供应价"
    };
    private static final List<FieldDef> PRODUCT_FIELDS = Arrays.asList(
            new FieldDef("系列", "series"),
            new FieldDef("品牌", "brand"),
            new FieldDef("物料编码", "code"),
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
            new FieldDef("供应价", "purchase_price", "decimal"),
            new FieldDef("\u4ef7\u683c\u6709\u6548\u671f\u9650", "price_valid_until", "date")
    );
    private static final List<FieldDef> SUPPLIER_PRODUCT_FIELDS = withoutFields(PRODUCT_FIELDS, "sale_price");
    private static final List<FieldDef> CUSTOMER_PRODUCT_FIELDS = withoutFields(PRODUCT_FIELDS, "purchase_price");
    private static final List<FieldDef> CUSTOMER_ORDER_FIELDS = concat(CUSTOMER_PRODUCT_FIELDS, Collections.singletonList(
            new FieldDef("备注", "order_remark")
    ));
    private static final List<FieldDef> SUPPLIER_QUOTE_PRICE_FIELDS = Arrays.asList(
            new FieldDef("????", "code"),
            new FieldDef("????", "spec_model"),
            new FieldDef("???", "purchase_price", "decimal"),
            new FieldDef("\u4ef7\u683c\u6709\u6548\u671f\u9650", "price_valid_until", "date")
    );
    private static final List<FieldDef> ADMIN_QUOTE_PRICE_FIELDS = Arrays.asList(
            new FieldDef("?????", "supplier_username"),
            new FieldDef("????", "code"),
            new FieldDef("????", "spec_model"),
            new FieldDef("???", "purchase_price", "decimal"),
            new FieldDef("\u4ef7\u683c\u6709\u6548\u671f\u9650", "price_valid_until", "date")
    );
    private static final Map<String, List<FieldDef>> TEMPLATE_FIELDS = createTemplateFields();

    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<Map<String, Object>> listTable(String name) {
        if ("pricingAudit".equals(name)) {
            return buildPricingAuditRows();
        }
        if ("orders".equals(name)) {
            return productMapper.listAdminOrderRows();
        }
        if ("supplier".equals(name)) {
            return productMapper.listAdminSupplierProductRows();
        }
        if ("quotes".equals(name)) {
            return productMapper.listAdminSupplierQuoteRows();
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
        if ("supplier".equals(name)) {
            Product product = productMapper.findSupplierSubmission(id);
            syncSupplierManualPriceToInternal(product);
        }
        if ("internal".equals(name)) {
            Product product = productMapper.findInternalProduct(id);
            syncSupplierProductsFromInternal(product);
        }
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
        List<Long> itemIds = new ArrayList<>();
        for (Product item : items) {
            if (item.getId() != null) {
                itemIds.add(item.getId());
            }
        }
        if (itemIds.size() >= 0) {
            return generateQuotesForItems(itemIds);
        }
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
        return "已生成" + count + " 条供应商报价任务";
    }

    @Override
    public String generateQuotesForItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new CustomException("NO_SELECTED_ITEMS");
        }
        Map<String, List<Product>> groups = new LinkedHashMap<>();
        for (Long itemId : itemIds) {
            if (itemId == null) {
                continue;
            }
            Product item = productMapper.findCustomerOrderItem(itemId);
            if (item == null || empty(item.getCode()) || "CANCELLED".equals(item.getStatus())) {
                continue;
            }
            if (!groups.containsKey(item.getCode())) {
                groups.put(item.getCode(), new ArrayList<Product>());
            }
            groups.get(item.getCode()).add(item);
        }
        int count = 0;
        Set<String> touchedOrders = new HashSet<>();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (Map.Entry<String, List<Product>> entry : groups.entrySet()) {
            String code = entry.getKey();
            List<Product> items = entry.getValue();
            if (items.isEmpty()) {
                continue;
            }
            String pricingGroup = "PG" + UUID.randomUUID().toString().replace("-", "");
            Product firstItem = items.get(0);
            for (Product item : items) {
                productMapper.assignCustomerOrderItemPricingGroup(item.getId(), pricingGroup);
                if (!empty(item.getOrderNo())) {
                    touchedOrders.add(item.getOrderNo());
                }
            }
            Product internal = productMapper.findInternalProductByCode(code);
            if (hasValidInternalPrice(internal, today)) {
                SupplierQuote quote = quoteFromItem(firstItem, internal);
                quote.setPricingGroup(pricingGroup);
                quote.setSupplierUsername(VALID_PRICE_SUPPLIER);
                quote.setMasterProductId(internal.getId());
                quote.setPurchasePrice(internal.getPurchasePrice());
                quote.setSalePrice(internal.getSalePrice());
                quote.setPriceValidUntil(internal.getPriceValidUntil());
                quote.setStatus("SUPPLIER_PRICED");
                productMapper.insertSupplierQuote(quote);
                markItemsQuoted(items);
                count++;
                continue;
            }
            List<Product> suppliers = productMapper.findAllSupplierSubmissionsByCode(code);
            for (Product supplier : suppliers) {
                SupplierQuote quote = quoteFromItem(firstItem, supplier);
                quote.setPricingGroup(pricingGroup);
                quote.setMasterProductId(supplier.getId());
                quote.setSupplierUsername(supplier.getSupplierUsername());
                quote.setPurchasePrice(supplier.getPurchasePrice());
                quote.setSalePrice(firstItem.getSalePrice());
                quote.setStatus("WAIT_SUPPLIER_PRICE");
                productMapper.insertSupplierQuote(quote);
                count++;
            }
            if (!suppliers.isEmpty()) {
                markItemsQuoted(items);
            }
        }
        for (String orderNo : touchedOrders) {
            productMapper.markCustomerOrderQuoted(orderNo);
        }
        return "OK " + count;
    }

    private SupplierQuote quoteFromItem(Product item, Product sourceProduct) {
        SupplierQuote quote = new SupplierQuote();
        quote.setOrderNo(item.getOrderNo());
        quote.setCustomerItemId(item.getId());
        quote.setCustomerUsername(item.getCustomerUsername());
        quote.setCode(item.getCode());
        quote.setSpecModel(item.getSpecModel());
        if (sourceProduct != null) {
            quote.setMasterProductId(sourceProduct.getId());
        }
        return quote;
    }

    private boolean hasValidInternalPrice(Product internal, LocalDate today) {
        return internal != null
                && internal.getPurchasePrice() != null
                && internal.getSalePrice() != null
                && internal.getPriceValidUntil() != null
                && !internal.getPriceValidUntil().isBefore(today);
    }

    private void markItemsQuoted(List<Product> items) {
        for (Product item : items) {
            productMapper.markCustomerOrderItemQuoted(item.getId());
        }
    }

    @Override
    public void adminUpdateQuote(Long id, SupplierQuote quote) {
        SupplierQuote existing = productMapper.findSupplierQuote(id);
        quote.setId(id);
        if (quote.getPurchasePrice() != null && empty(quote.getStatus())) {
            quote.setStatus("SUPPLIER_PRICED");
        }
        productMapper.adminUpdateQuote(quote);
        syncSupplierProductPrice(existing, quote.getPurchasePrice());
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
            SupplierQuote existing = productMapper.findSupplierQuote(quote.getId());
            productMapper.adminUpdateQuote(quote);
            syncSupplierProductPrice(existing, quote.getPurchasePrice());
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
        if (empty(quote.getPricingGroup())) {
            productMapper.useQuoteOnCustomerItem(quote.getCustomerItemId(), quote.getPurchasePrice(), quote.getSalePrice());
        } else {
            productMapper.markCustomerOrderItemsCompletedByGroup(quote.getPricingGroup(), quote.getCode(), quote.getPurchasePrice(), quote.getSalePrice());
        }
        productMapper.updateInternalPricesByCode(quote.getCode(), quote.getPurchasePrice(), quote.getSalePrice(), quote.getPriceValidUntil());
        productMapper.pushInternalOrderPrice(quote.getCode(), priceTrendEntry(quote.getPurchasePrice(), quote.getSupplierUsername()));
        productMapper.markQuotePricingUsed(id);
        productMapper.markOtherQuotePricingNotUsed(id, quote.getOrderNo(), quote.getCode(), quote.getPricingGroup());
        productMapper.markCustomerOrderCompletedIfReady(quote.getOrderNo());
    }

    @Override
    public String sendPricingAuditQuoteTasks(List<String> codes) {
        return sendPricingAuditQuoteTasks(codes, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    @Override
    public String sendPricingAuditQuoteTasks(Map<String, Object> request) {
        return sendPricingAuditQuoteTasks(
                stringList(request.get("codes")),
                stringList(request.get("supplierUsernames")),
                stringList(request.get("supplierTargets")),
                booleanValue(request.get("inquiryOnly"))
        );
    }

    private String sendPricingAuditQuoteTasks(List<String> codes, List<String> selectedSuppliers, List<String> selectedTargets) {
        return sendPricingAuditQuoteTasks(codes, selectedSuppliers, selectedTargets, false);
    }

    private String sendPricingAuditQuoteTasks(List<String> codes, List<String> selectedSuppliers, List<String> selectedTargets, boolean inquiryOnly) {
        if (codes == null || codes.isEmpty()) {
            throw new CustomException("NO_SELECTED_ITEMS");
        }
        int count = 0;
        Set<String> sent = new HashSet<>();
        Set<String> supplierSet = selectedSuppliers == null ? Collections.<String>emptySet() : new HashSet<>(selectedSuppliers);
        Set<String> targetSet = selectedTargets == null ? Collections.<String>emptySet() : new HashSet<>(selectedTargets);
        for (String code : codes) {
            if (empty(code) || sent.contains(code)) {
                continue;
            }
            sent.add(code);
            String pricingGroup = (inquiryOnly ? "INQUIRY-" : "AUDIT-") + code + "-" + UUID.randomUUID().toString().substring(0, 8);
            if (!inquiryOnly && productMapper.countActiveCustomerOrderItemsByCode(code) <= 0) {
                continue;
            }
            List<Product> suppliers = productMapper.findAllSupplierSubmissionsByCode(code);
            for (Product supplier : suppliers) {
                String targetKey = code + "|" + supplier.getSupplierUsername();
                if (!targetSet.isEmpty() && !targetSet.contains(targetKey)) {
                    continue;
                }
                if (targetSet.isEmpty() && !supplierSet.isEmpty() && !supplierSet.contains(supplier.getSupplierUsername())) {
                    continue;
                }
                SupplierQuote quote = new SupplierQuote();
                quote.setCode(code);
                quote.setPricingGroup(pricingGroup);
                quote.setMasterProductId(supplier.getId());
                quote.setSupplierUsername(supplier.getSupplierUsername());
                quote.setPurchasePrice(supplier.getPurchasePrice());
                quote.setPriceValidUntil(supplier.getPriceValidUntil());
                quote.setStatus("WAIT_SUPPLIER_PRICE");
                productMapper.insertSupplierQuote(quote);
                count++;
            }
        }
        return "OK " + count;
    }

    @Override
    public List<Map<String, Object>> listPricingAuditSuppliers(String code) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (empty(code)) {
            return result;
        }
        for (Product supplier : productMapper.findAllSupplierSubmissionsByCode(code)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("supplier_username", supplier.getSupplierUsername());
            item.put("code", supplier.getCode());
            item.put("purchase_price", supplier.getPurchasePrice());
            item.put("price_valid_until", supplier.getPriceValidUntil());
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listPricingAuditOrders(String code) {
        if (empty(code)) {
            return Collections.emptyList();
        }
        return productMapper.listActiveOrdersByCode(code);
    }

    @Override
    public void usePricingAuditPrice(Map<String, Object> price) {
        String code = stringValue(price.get("code"));
        if (empty(code)) {
            throw new CustomException("缺少物料编码");
        }
        BigDecimal purchasePrice = decimal(stringValue(price.get("purchasePrice")));
        BigDecimal salePrice = decimal(stringValue(price.get("salePrice")));
        LocalDate priceValidUntil = dateValue(stringValue(price.get("priceValidUntil")));
        boolean inquiryOnly = booleanValue(price.get("inquiryOnly"));
        if (purchasePrice == null) {
            throw new CustomException("请先选择供应价");
        }
        if (!inquiryOnly && salePrice == null) {
            throw new CustomException("请填写销售价");
        }
        if (priceValidUntil == null) {
            throw new CustomException("请填写价格有效期限");
        }
        if (inquiryOnly) {
            productMapper.updateInternalInquiryPriceByCode(code, purchasePrice, priceValidUntil);
            productMapper.markInquiryQuotesUsedByCode(code);
            productMapper.pushInternalOrderPrice(code, priceTrendEntry(purchasePrice, stringValue(price.get("supplierUsername"))));
            return;
        }
        List<String> selectedOrderNos = stringList(price.get("orderNos"));
        List<String> activeOrderNos = selectedOrderNos.isEmpty() ? productMapper.listActiveOrderNosByCode(code) : selectedOrderNos;
        if (activeOrderNos.isEmpty()) {
            throw new CustomException("请先选择要采用价格的订单");
        }
        if (selectedOrderNos.isEmpty()) {
            productMapper.markCustomerOrderItemsCompletedByCode(code, purchasePrice, salePrice);
        } else {
            productMapper.markCustomerOrderItemsCompletedByCodeAndOrders(code, selectedOrderNos, purchasePrice, salePrice);
        }
        productMapper.updateInternalPricesByCode(code, purchasePrice, salePrice, priceValidUntil);
        productMapper.pushInternalOrderPrice(code, priceTrendEntry(purchasePrice, stringValue(price.get("supplierUsername"))));
        for (String orderNo : activeOrderNos) {
            productMapper.markCustomerOrderCompletedIfReady(orderNo);
        }
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
            row.createCell(0).setCellValue(stringValue(item.get("supplier_username")));
            row.createCell(1).setCellValue(stringValue(item.get("code")));
            row.createCell(2).setCellValue(stringValue(item.get("spec_model")));
            Object price = item.get("purchase_price");
            if (price instanceof Number) {
                row.createCell(3).setCellValue(((Number) price).doubleValue());
            } else {
                row.createCell(3).setCellValue(stringValue(price));
            }
            row.createCell(4).setCellValue(stringValue(item.get("price_valid_until")));
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
            String supplierUsername = cell(row.getCell(0));
            String code = cell(row.getCell(1));
            String price = cell(row.getCell(3));
            String priceValidUntil = cell(row.getCell(4));
            total++;
            if (empty(supplierUsername) || empty(code) || empty(price)) {
                continue;
            }
            try {
                SupplierQuote quote = new SupplierQuote();
                quote.setSupplierUsername(supplierUsername);
                quote.setCode(code);
                quote.setPurchasePrice(decimal(price));
                quote.setPriceValidUntil(dateValue(priceValidUntil));
                int changed = productMapper.supplierUpdateQuoteByOrderAndCode(quote);
                updated += changed;
                syncSupplierProductPrice(quote);
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
    public List<Map<String, Object>> listSupplierSubmissions(String supplierUsername) {
        return productMapper.listSupplierProductRows(supplierUsername);
    }

    @Override
    public void addSupplierSubmission(String supplierUsername, Product product) {
        clearProtectedCodes(product);
        product.setSupplierUsername(supplierUsername);
        applySupplierCodeStatus(product);
        productMapper.insertSupplierSubmission(product);
        syncSupplierManualPriceToInternal(product);
    }

    @Override
    public void adminAddSupplierSubmission(Product product) {
        if (empty(product.getSupplierUsername())) {
            throw new CustomException("SUPPLIER_REQUIRED");
        }
        applySupplierCodeStatus(product);
        productMapper.insertSupplierSubmission(product);
        syncSupplierManualPriceToInternal(product);
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
        SupplierQuote existing = productMapper.findSupplierQuote(id);
        quote.setId(id);
        quote.setSupplierUsername(supplierUsername);
        productMapper.supplierUpdateQuote(quote);
        syncSupplierProductPrice(mergeQuoteForSync(existing, quote));
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
            SupplierQuote existing = productMapper.findSupplierQuote(quote.getId());
            quote.setSupplierUsername(supplierUsername);
            productMapper.supplierUpdateQuote(quote);
            syncSupplierProductPrice(mergeQuoteForSync(existing, quote));
        }
    }

    @Override
    public void supplierProductQuoteBatch(String supplierUsername, List<SupplierQuote> quotes) {
        if (quotes == null) {
            return;
        }
        for (SupplierQuote quote : quotes) {
            if (quote == null || empty(quote.getCode()) || quote.getPurchasePrice() == null) {
                continue;
            }
            quote.setSupplierUsername(supplierUsername);
            productMapper.supplierUpdateQuoteByOrderAndCode(quote);
            syncSupplierProductPrice(quote);
        }
    }

    @Override
    public void adminSupplierProductQuoteBatch(List<SupplierQuote> quotes) {
        if (quotes == null) {
            return;
        }
        for (SupplierQuote quote : quotes) {
            if (quote == null || empty(quote.getSupplierUsername()) || empty(quote.getCode()) || quote.getPurchasePrice() == null) {
                continue;
            }
            productMapper.supplierUpdateQuoteByOrderAndCode(quote);
            syncSupplierProductPrice(quote);
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
                row.createCell(0).setCellValue(item.getCode());
                row.createCell(1).setCellValue(item.getSpecModel());
                if (item.getPurchasePrice() != null) {
                    row.createCell(2).setCellValue(item.getPurchasePrice().doubleValue());
                } else {
                    row.createCell(2).setCellValue("");
                }
                row.createCell(3).setCellValue(item.getPriceValidUntil() == null ? "" : item.getPriceValidUntil().toString());
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
            String code = cell(row.getCell(0));
            String price = cell(row.getCell(2));
            String priceValidUntil = cell(row.getCell(3));
            total++;
            if (empty(code) || empty(price)) {
                continue;
            }
            try {
                SupplierQuote quote = new SupplierQuote();
                quote.setSupplierUsername(supplierUsername);
                quote.setCode(code);
                quote.setPurchasePrice(decimal(price));
                quote.setPriceValidUntil(dateValue(priceValidUntil));
                int changed = productMapper.supplierUpdateQuoteByOrderAndCode(quote);
                updated += changed;
                syncSupplierProductPrice(quote);
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
    public Map<String, Object> addCustomerOrderFromInternal(String customerUsername, List<Long> internalProductIds, String orderRemark) {
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
            item.setStatus("SUBMITTED_ORDER");
            item.setMatched(!empty(internal.getCode()));
            item.setMasterProductId(internal.getId());
            item.setMaterialLinkStatus(empty(internal.getCode()) ? "UNLINKED" : "LINKED");
            item.setOrderRemark(orderRemark);
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
    public void updateCustomerOrderItemRemark(Long id, String orderRemark, String customerUsername) {
        Product item = productMapper.findCustomerOrderItem(id);
        ensureCustomerOrderItemAccess(item, customerUsername);
        productMapper.updateCustomerOrderItemRemark(id, orderRemark);
    }

    @Override
    public void linkCustomerOrderItem(Long id, Product product, String customerUsername) {
        Product item = productMapper.findCustomerOrderItem(id);
        ensureCustomerOrderItemAccess(item, customerUsername);
        String code = product == null ? "" : product.getCode();
        if (empty(code)) {
            throw new CustomException("请填写物料编码");
        }
        Product internal = productMapper.findInternalProductByCode(code);
        if (internal == null) {
            throw new CustomException("主表没有该物料编码，暂时无法链接");
        }
        Product linked = copyProduct(item);
        linked.setId(id);
        linked.setCode(internal.getCode());
        linked.setNewCode(empty(product.getNewCode()) ? internal.getNewCode() : product.getNewCode());
        linked.setMasterProductId(internal.getId());
        linked.setCustomerUsername(item.getCustomerUsername());
        linked.setSpecModel(item.getSpecModel());
        linked.setCommonModel(item.getCommonModel());
        productMapper.linkCustomerOrderItem(linked);
        productMapper.updateCustomerProductCodeByModels(linked);
    }

    private void ensureCustomerOrderItemAccess(Product item, String customerUsername) {
        if (item == null) {
            throw new CustomException("订单产品不存在");
        }
        if (!empty(customerUsername) && !customerUsername.equals(item.getCustomerUsername())) {
            throw new CustomException("不能修改其他客户的订单产品");
        }
    }

    @Override
    public ResponseEntity<byte[]> template() throws IOException {
        return buildTemplate("customer-template.xlsx", CUSTOMER_ORDER_FIELDS);
    }

    @Override
    public Map<String, Object> upload(String customerUsername, MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        validateHeader(sheet, CUSTOMER_ORDER_FIELDS);

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
            if (row == null || blankRow(row, CUSTOMER_ORDER_FIELDS.size())) {
                continue;
            }
            Product product = readRow(row, CUSTOMER_ORDER_FIELDS);
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
        if ("price_valid_until".equals(column)) product.setPriceValidUntil(dateValue(value));
        if ("order_remark".equals(column)) product.setOrderRemark(value);
        if ("material_link_status".equals(column)) product.setMaterialLinkStatus(value);
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

    private LocalDate dateValue(String value) {
        if (empty(value)) {
            return null;
        }
        String text = value.trim().split("\\s+")[0]
                .replace('/', '-')
                .replace("年", "-")
                .replace("月", "-")
                .replace("日", "");
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("M-d-yyyy"),
                DateTimeFormatter.ofPattern("M-d-yy")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (RuntimeException ignored) {
                // Try the next common Excel date format.
            }
        }
        throw new CustomException("日期格式不正确：" + value + "，请使用 2026-07-25 或 2026/07/25");
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
                throw new CustomException("第" + (i + 1) + " 行缺少供应商账号");
            }
            Product existing = productMapper.findSupplierSubmissionBySpecAndSupplier(specModel, supplierUsername);
            Product imported = mapToProduct(data);
            applySupplierCodeStatus(imported);
            data.put("status", imported.getStatus());
            data.put("master_product_id", imported.getMasterProductId());
            if (existing == null) {
                productMapper.insertTableRow("supplier_submissions", data);
            } else {
                productMapper.updateTableRow("supplier_submissions", existing.getId(), data);
                syncLinkedMaster("supplier", existing.getId());
                updated++;
            }
            syncSupplierManualPriceToInternal(mapToSupplierProduct(data));
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
                syncSupplierProductsFromInternal(product);
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
            Product product = readRow(row, CUSTOMER_ORDER_FIELDS);
            String customerUsername = cell(row.getCell(CUSTOMER_ORDER_FIELDS.size()));
            if (empty(customerUsername)) {
                throw new CustomException("第" + (i + 1) + " 行缺少客户账号");
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
        product.setOrderNo(orderNo);
        product.setCustomerUsername(customerUsername);
        product.setStatus("SUBMITTED_ORDER");
        Product master = empty(product.getCode()) ? null : productMapper.findInternalProductByCode(product.getCode());
        if (master != null) {
            product.setCode(master.getCode());
            product.setNewCode(empty(product.getNewCode()) ? master.getNewCode() : product.getNewCode());
            product.setMatched(true);
            product.setMasterProductId(master.getId());
            product.setMaterialLinkStatus("LINKED");
        } else {
            product.setMatched(false);
            product.setMasterProductId(null);
            product.setMaterialLinkStatus("UNLINKED");
        }
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
        libraryProduct.setMaterialLinkStatus("LINKED");
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
        libraryProduct.setMatched(!empty(product.getCode()));
        libraryProduct.setMasterProductId(product.getMasterProductId());
        libraryProduct.setStatus(empty(product.getCode()) ? "WAIT_CODE" : "APPROVED");
        libraryProduct.setMaterialLinkStatus(empty(product.getCode()) ? "UNLINKED" : "LINKED");
        productMapper.insertCustomerProduct(libraryProduct);
    }

    private String nextOrderNo() {
        return "SO" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
    }

    private void saveInternalProduct(Product product) {
        productMapper.insertInternalProduct(product);
        syncSupplierProductsFromInternal(product);
    }

    private void syncSupplierProductsFromInternal(Product internal) {
        if (internal == null || empty(internal.getCode())) {
            return;
        }
        Product savedInternal = internal.getId() == null
                ? productMapper.findInternalProductByCode(internal.getCode())
                : internal;
        if (savedInternal == null || savedInternal.getId() == null) {
            savedInternal = productMapper.findInternalProductByCode(internal.getCode());
        }
        if (savedInternal == null || savedInternal.getId() == null) {
            return;
        }
        List<Product> suppliers = productMapper.findAllSupplierSubmissionsByCode(savedInternal.getCode());
        for (Product supplier : suppliers) {
            if (supplier == null || supplier.getId() == null) {
                continue;
            }
            productMapper.linkSupplierSubmissionToInternal(
                    supplier.getId(),
                    empty(savedInternal.getNewCode()) ? supplier.getNewCode() : savedInternal.getNewCode(),
                    savedInternal.getId()
            );
            syncSupplierManualPriceToInternal(supplier);
        }
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
        target.setPriceValidUntil(source.getPriceValidUntil());
        target.setUpdateDate(source.getUpdateDate());
        target.setSupplierUsername(source.getSupplierUsername());
        target.setCustomerUsername(source.getCustomerUsername());
        target.setOrderNo(source.getOrderNo());
        target.setMatched(source.getMatched());
        target.setMasterProductId(source.getMasterProductId());
        target.setLinkedMasterProductId(source.getLinkedMasterProductId());
        target.setStatus(source.getStatus());
        target.setPricingGroup(source.getPricingGroup());
        target.setOrderRemark(source.getOrderRemark());
        target.setMaterialLinkStatus(source.getMaterialLinkStatus());
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
            applySupplierCodeStatus(product);
            Product existing = productMapper.findSupplierSubmissionBySpecAndSupplier(
                    product.getSpecModel() == null ? "" : product.getSpecModel(),
                    supplierUsername
            );
            if (existing == null) {
                productMapper.insertSupplierSubmission(product);
            } else {
                product.setId(existing.getId());
                product.setMasterProductId(existing.getMasterProductId());
                applySupplierCodeStatus(product);
                productMapper.updateTableRow("supplier_submissions", existing.getId(), supplierProductData(product));
                syncLinkedMaster("supplier", existing.getId());
            }
            syncSupplierManualPriceToInternal(product);
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
                throw new CustomException("第" + (i + 1) + " 行导入失败，请检查是否有重复编号或必填字段为空");
            } catch (NumberFormatException e) {
                throw new CustomException("第" + (i + 1) + " 行导入失败，价格字段只能填写数字");
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
            } else if ("date".equals(field.type)) {
                data.put(field.column, dateValue(value));
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
        data.put("price_valid_until", product.getPriceValidUntil());
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

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && booleanValue(String.valueOf(value));
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
        String pricingGroup = stringValue(item.get("pricing_group"));
        String group = pricingGroup;
        return group + "|" + stringValue(item.get("supplier_username")) + "|" + stringValue(item.get("code"));
    }

    private String priceTrendEntry(BigDecimal price, String supplier) {
        String amount = price.stripTrailingZeros().toPlainString();
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        return "￥" + amount + "/" + supplier + "/" + date;
    }

    private void syncSupplierProductPrice(SupplierQuote quote, BigDecimal purchasePrice) {
        if (quote == null || purchasePrice == null || empty(quote.getSupplierUsername()) || empty(quote.getCode())) {
            return;
        }
        Product existing = productMapper.findSupplierSubmissionBySupplierAndCode(quote.getSupplierUsername(), quote.getCode());
        if (existing != null) {
            productMapper.updateSupplierSubmissionPrice(quote.getSupplierUsername(), quote.getCode(), purchasePrice, quote.getPriceValidUntil());
            Product pricedProduct = copyProduct(existing);
            pricedProduct.setPurchasePrice(purchasePrice);
            pricedProduct.setPriceValidUntil(quote.getPriceValidUntil());
            syncSupplierManualPriceToInternal(pricedProduct);
            return;
        }
        Product internal = productMapper.findInternalProductByCode(quote.getCode());
        if (internal == null) {
            return;
        }
        Product supplierProduct = copyProduct(internal);
        supplierProduct.setSupplierUsername(quote.getSupplierUsername());
        supplierProduct.setPurchasePrice(purchasePrice);
        supplierProduct.setSalePrice(null);
        supplierProduct.setPriceValidUntil(quote.getPriceValidUntil());
        supplierProduct.setMasterProductId(internal.getId());
        supplierProduct.setStatus("APPROVED");
        productMapper.insertSupplierSubmission(supplierProduct);
        syncSupplierManualPriceToInternal(supplierProduct);
    }

    private void syncSupplierProductPrice(SupplierQuote quote) {
        if (quote == null) {
            return;
        }
        syncSupplierProductPrice(quote, quote.getPurchasePrice());
    }

    private SupplierQuote mergeQuoteForSync(SupplierQuote existing, SupplierQuote update) {
        if (existing == null || update == null) {
            return update == null ? existing : update;
        }
        SupplierQuote merged = new SupplierQuote();
        merged.setSupplierUsername(empty(update.getSupplierUsername()) ? existing.getSupplierUsername() : update.getSupplierUsername());
        merged.setCode(empty(update.getCode()) ? existing.getCode() : update.getCode());
        merged.setPurchasePrice(update.getPurchasePrice() == null ? existing.getPurchasePrice() : update.getPurchasePrice());
        merged.setPriceValidUntil(update.getPriceValidUntil() == null ? existing.getPriceValidUntil() : update.getPriceValidUntil());
        return merged;
    }

    private void syncSupplierManualPriceToInternal(Product product) {
        if (product == null
                || empty(product.getCode())
                || product.getPurchasePrice() == null
                || product.getPriceValidUntil() == null) {
            return;
        }
        productMapper.updateInternalSupplierPriceIfCheaper(
                product.getCode(),
                product.getPurchasePrice(),
                product.getPriceValidUntil()
        );
    }

    private List<Map<String, Object>> buildPricingAuditRows() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (Product internal : productMapper.listInternalProducts()) {
            if (internal == null || empty(internal.getCode())) {
                continue;
            }
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", internal.getId());
            target.put("series", internal.getSeries());
            target.put("brand", internal.getBrand());
            target.put("code", internal.getCode());
            target.put("new_code", internal.getNewCode());
            target.put("color", internal.getColor());
            target.put("category", internal.getCategory());
            target.put("craft_material", internal.getCraftMaterial());
            target.put("spec_model", internal.getSpecModel());
            target.put("common_model", internal.getCommonModel());
            target.put("size_value", internal.getSizeValue());
            target.put("resolution", internal.getResolution());
            target.put("model_remark", internal.getModelRemark());
            target.put("sale_price", internal.getSalePrice());
            target.put("purchase_price", internal.getPurchasePrice());
            target.put("price_valid_until", internal.getPriceValidUntil());
            boolean hasValidPrice = hasValidInternalPrice(internal, today);
            target.put("price_source_status", hasValidPrice ? "VALID_PRICE" : "NO_VALID_PRICE");
            boolean hasOrder = productMapper.countActiveCustomerOrderItemsByCode(internal.getCode()) > 0;
            boolean hasInquiry = !hasOrder && productMapper.countActiveInquiryQuotesByCode(internal.getCode()) > 0;
            target.put("current_order_status", hasOrder ? "HAS_ORDER" : hasInquiry ? "INQUIRY_ONLY" : "NO_ORDER");
            target.put("valid_price", hasValidPrice ? internal.getPurchasePrice() : "");
            target.put("pricing_status", "WAIT_USE_PRICE");

            List<Product> suppliers = productMapper.findAllSupplierSubmissionsByCode(internal.getCode());
            List<Product> pricedSuppliers = new ArrayList<>();
            for (Product supplier : suppliers) {
                if (supplier.getPurchasePrice() != null) {
                    pricedSuppliers.add(supplier);
                }
            }
            pricedSuppliers.sort((left, right) -> left.getPurchasePrice().compareTo(right.getPurchasePrice()));
            for (int i = 0; i < 5; i++) {
                int index = i + 1;
                if (i < pricedSuppliers.size()) {
                    Product supplier = pricedSuppliers.get(i);
                    target.put("ref_price_" + index, supplier.getPurchasePrice());
                    target.put("supplier_" + index, supplier.getSupplierUsername());
                    target.put("ref_valid_until_" + index, supplier.getPriceValidUntil());
                } else {
                    target.put("ref_price_" + index, "");
                    target.put("supplier_" + index, "");
                    target.put("ref_valid_until_" + index, "");
                }
            }
            result.add(target);
        }
        result.sort((left, right) -> {
            String leftStatus = stringValue(left.get("current_order_status"));
            String rightStatus = stringValue(right.get("current_order_status"));
            if (!leftStatus.equals(rightStatus)) {
                return Integer.valueOf(orderStatusRank(leftStatus)).compareTo(orderStatusRank(rightStatus));
            }
            Long leftId = longValue(left.get("id"));
            Long rightId = longValue(right.get("id"));
            return leftId.compareTo(rightId);
        });
        return result;
    }

    private int orderStatusRank(String status) {
        if ("HAS_ORDER".equals(status)) {
            return 0;
        }
        if ("INQUIRY_ONLY".equals(status)) {
            return 1;
        }
        return 2;
    }

    private String groupPricingStatus(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if ("USED_PRICE".equals(stringValue(row.get("pricing_status")))) {
                return "USED_PRICE";
            }
        }
        return "WAIT_USE_PRICE";
    }

    private boolean hasValidPriceRow(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (VALID_PRICE_SUPPLIER.equals(stringValue(row.get("supplier_username")))) {
                return true;
            }
        }
        return false;
    }

    private Object validPriceValue(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (VALID_PRICE_SUPPLIER.equals(stringValue(row.get("supplier_username")))) {
                return row.get("purchase_price");
            }
        }
        return "";
    }

    private Object validPriceUntilValue(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (row.get("price_valid_until") != null) {
                return row.get("price_valid_until");
            }
        }
        return "";
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        if (value == null || empty(String.valueOf(value))) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
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

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (Iterable<?>) value) {
            String text = stringValue(item);
            if (!empty(text)) {
                result.add(text);
            }
        }
        return result;
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
                "purchase_price", "price_valid_until", "supplier_username", "customer_username", "source_type",
                "status", "order_no", "matched", "master_product_id"
        ));
        if ("master".equals(name) || "internal".equals(name)) {
            return new HashSet<>(Arrays.asList(
                    "series", "brand", "code", "new_code", "color", "category", "craft_material",
                    "spec_model", "common_model", "size_value", "resolution", "model_remark", "sale_price",
                    "purchase_price", "price_valid_until", "supplier_username", "customer_username", "master_product_id"
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
                    "customer_item_id", "master_product_id", "supplier_username",
                    "code", "spec_model", "purchase_price", "sale_price", "status", "price_valid_until", "pricing_group"
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
        map.put("orderItems", concat(CUSTOMER_ORDER_FIELDS, Arrays.asList(
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("是否匹配", "matched", "boolean")
        )));
        map.put("customerProducts", concat(CUSTOMER_PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("是否匹配", "matched", "boolean"),
                new FieldDef("状态", "status")
        )));
        map.put("unmatched", concat(CUSTOMER_PRODUCT_FIELDS, Arrays.asList(
                new FieldDef("客户账号", "customer_username"),
                new FieldDef("状态", "status")
        )));
        map.put("orders", concat(CUSTOMER_ORDER_FIELDS, Collections.singletonList(
                new FieldDef("客户账号", "customer_username")
        )));
        map.put("quotes", Arrays.asList(
                new FieldDef("客户明细ID", "customer_item_id"),
                new FieldDef("供应商账号", "supplier_username"),
                new FieldDef("物料编码", "code"),
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
        // 供应商允许提交物料编码和新编码。
    }

    private void applySupplierCodeStatus(Product product) {
        if (empty(product.getCode())) {
            product.setMasterProductId(null);
            product.setStatus("PENDING");
            return;
        }
        Product internal = productMapper.findInternalProductByCode(product.getCode());
        if (internal == null) {
            product.setMasterProductId(null);
            product.setStatus("CODE_NOT_FOUND");
            return;
        }
        product.setMasterProductId(internal.getId());
        product.setStatus("APPROVED");
    }

    private Product mapToProduct(Map<String, Object> data) {
        Product product = new Product();
        product.setCode(stringValue(data.get("code")));
        product.setNewCode(stringValue(data.get("new_code")));
        product.setSpecModel(stringValue(data.get("spec_model")));
        product.setCommonModel(stringValue(data.get("common_model")));
        product.setOrderRemark(stringValue(data.get("order_remark")));
        product.setMaterialLinkStatus(stringValue(data.get("material_link_status")));
        return product;
    }

    private Product mapToSupplierProduct(Map<String, Object> data) {
        Product product = mapToProduct(data);
        product.setSupplierUsername(stringValue(data.get("supplier_username")));
        Object purchasePrice = data.get("purchase_price");
        if (purchasePrice instanceof BigDecimal) {
            product.setPurchasePrice((BigDecimal) purchasePrice);
        } else if (purchasePrice != null) {
            product.setPurchasePrice(decimal(String.valueOf(purchasePrice)));
        }
        Object priceValidUntil = data.get("price_valid_until");
        if (priceValidUntil instanceof LocalDate) {
            product.setPriceValidUntil((LocalDate) priceValidUntil);
        } else if (priceValidUntil != null) {
            product.setPriceValidUntil(dateValue(String.valueOf(priceValidUntil)));
        }
        return product;
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
