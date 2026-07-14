package com.suppliercustomer.service;

import com.suppliercustomer.pojo.Product;
import com.suppliercustomer.pojo.SupplierQuote;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ProductService {
    List<Map<String, Object>> listTable(String name);

    void updateTableRow(String name, Long id, Map<String, Object> row);

    void deleteTableRow(String name, Long id);

    List<Product> listAdminOrderItems(String orderNo);

    void cancelOrder(String orderNo);

    void cancelOrderItem(Long id);

    void updateOrderItemPrices(Long id, Product product);

    void addMaster(Product product);

    void addInternalProduct(Product product);

    void approveSupplier(Long id, Product product);

    void approveUnmatched(Long id, Product product);

    void approveCustomerProduct(Long id, Product product);

    String generateQuotes(String orderNo);

    String generateQuotesForItems(List<Long> itemIds);

    void adminUpdateQuote(Long id, SupplierQuote quote);

    void adminUpdateQuoteBatch(List<SupplierQuote> quotes);

    void useSupplierQuote(Long id);

    String sendPricingAuditQuoteTasks(List<String> codes);

    void usePricingAuditPrice(Map<String, Object> price);

    ResponseEntity<byte[]> adminQuoteDownload(List<Long> quoteIds) throws IOException;

    Map<String, Object> importAdminQuotePrices(MultipartFile file) throws IOException;

    List<Map<String, Object>> listSupplierSubmissions(String supplierUsername);

    void addSupplierSubmission(String supplierUsername, Product product);

    void adminAddSupplierSubmission(Product product);

    List<SupplierQuote> listSupplierQuotes(String supplierUsername);

    List<Map<String, Object>> listSupplierQuoteOrders(String supplierUsername);

    List<SupplierQuote> listSupplierQuoteItems(String supplierUsername, String orderNo);

    List<Map<String, Object>> listSupplierQuoteItemDetails(String supplierUsername, String orderNo);

    List<Map<String, Object>> listAdminSupplierQuoteItemDetails(String supplierUsername, String orderNo);

    void supplierUpdateQuote(String supplierUsername, Long id, SupplierQuote quote);

    void supplierUpdateQuoteBatch(String supplierUsername, List<SupplierQuote> quotes);

    void supplierProductQuoteBatch(String supplierUsername, List<SupplierQuote> quotes);

    void adminSupplierProductQuoteBatch(List<SupplierQuote> quotes);

    ResponseEntity<byte[]> supplierQuoteDownload(String supplierUsername, List<String> orderNos) throws IOException;

    Map<String, Object> importSupplierQuotePrices(String supplierUsername, MultipartFile file) throws IOException;

    List listCustomerOrders(String customerUsername);

    List<Product> listCustomerOrderItems(String orderNo, String customerUsername);

    List<Product> listCustomerProducts(String customerUsername);

    List<Product> listInternalProducts();

    Map<String, Object> addCustomerOrder(String customerUsername, Product product);

    Map<String, Object> addCustomerOrderFromInternal(String customerUsername, List<Long> internalProductIds);

    void updateCustomerProduct(String customerUsername, Long id, Product product);

    void cancelCustomerOrder(String customerUsername, String orderNo);

    void cancelCustomerOrderItem(String customerUsername, Long id);

    ResponseEntity<byte[]> template() throws IOException;

    Map<String, Object> upload(String customerUsername, MultipartFile file) throws IOException;

    ResponseEntity<byte[]> tableTemplate(String name) throws IOException;

    Map<String, Object> importTable(String name, MultipartFile file) throws IOException;

    ResponseEntity<byte[]> supplierTemplate() throws IOException;

    Map<String, Object> uploadSupplierSubmissions(String supplierUsername, MultipartFile file) throws IOException;
}
