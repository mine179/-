package com.suppliercustomer.mapper;

import com.suppliercustomer.pojo.CustomerOrder;
import com.suppliercustomer.pojo.Product;
import com.suppliercustomer.pojo.SupplierQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {
    List<Map<String, Object>> listTable(@Param("tableName") String tableName);

    void updateTableRow(@Param("tableName") String tableName, @Param("id") Long id, @Param("data") Map<String, Object> data);

    void deleteTableRow(@Param("tableName") String tableName, @Param("id") Long id);

    void insertTableRow(@Param("tableName") String tableName, @Param("data") Map<String, Object> data);

    void insertMaster(Product product);

    void insertInternalProduct(Product product);

    void updateInternalMasterId(@Param("id") Long id, @Param("masterProductId") Long masterProductId);

    void insertSupplierSubmission(Product product);

    void insertCustomerOrder(CustomerOrder order);

    void insertCustomerOrderItem(Product product);

    void insertCustomerProduct(Product product);

    void insertUnmatchedCustomerItem(Product product);

    void insertSupplierQuote(SupplierQuote quote);

    CustomerOrder findCustomerOrder(String orderNo);

    CustomerOrder findCustomerOrderByCustomer(@Param("orderNo") String orderNo,
                                              @Param("customerUsername") String customerUsername);

    Product findSupplierSubmission(Long id);

    Product findUnmatchedCustomerItem(Long id);

    Product findCustomerOrderItem(Long id);

    Product findCustomerProductByCustomer(@Param("id") Long id, @Param("customerUsername") String customerUsername);

    Product findCustomerProduct(Long id);

    Product findInternalProduct(Long id);

    Product findInternalProductByCode(String code);

    Product findSupplierSubmissionBySpecAndSupplier(@Param("specModel") String specModel,
                                                    @Param("supplierUsername") String supplierUsername);

    Product findCustomerProductByCode(@Param("customerUsername") String customerUsername, @Param("code") String code);

    Product findCustomerProductByModels(@Param("customerUsername") String customerUsername,
                                        @Param("specModel") String specModel,
                                        @Param("commonModel") String commonModel);

    Product findMasterByCode(String code);

    List<Product> findMastersByCode(String code);

    List<Product> findSupplierSubmissionsByCode(String code);

    List<Product> listMatchedOrderItems(String orderNo);

    List<Product> listAdminOrderItems(String orderNo);

    List<Product> listSupplierSubmissions(String supplierUsername);

    List<SupplierQuote> listSupplierQuotes(String supplierUsername);

    List<Map<String, Object>> listSupplierQuoteOrders(String supplierUsername);

    List<SupplierQuote> listSupplierQuoteItems(@Param("supplierUsername") String supplierUsername,
                                               @Param("orderNo") String orderNo);

    List<Map<String, Object>> listSupplierQuoteItemDetails(@Param("supplierUsername") String supplierUsername,
                                                           @Param("orderNo") String orderNo);

    List<Map<String, Object>> listAdminSupplierQuoteItemDetails(@Param("supplierUsername") String supplierUsername,
                                                                @Param("orderNo") String orderNo);

    List<Map<String, Object>> listPricingAuditRows();

    List<CustomerOrder> listCustomerOrders(String customerUsername);

    List<Product> listCustomerOrderItems(@Param("orderNo") String orderNo, @Param("customerUsername") String customerUsername);

    List<Product> listCustomerProducts(String customerUsername);

    List<Product> listInternalProducts();

    void cancelCustomerOrder(String orderNo);

    void cancelCustomerOrderItemsByOrderNo(String orderNo);

    void cancelCustomerOrderItem(Long id);

    void cancelCustomerOrderItemByCustomer(@Param("id") Long id, @Param("customerUsername") String customerUsername);

    void updateCustomerOrderItemPrices(@Param("id") Long id,
                                       @Param("purchasePrice") java.math.BigDecimal purchasePrice,
                                       @Param("salePrice") java.math.BigDecimal salePrice);

    void markCustomerOrderQuoted(String orderNo);

    void markCustomerOrderCompletedIfReady(String orderNo);

    void markQuotePricingUsed(@Param("id") Long id);

    void markOtherQuotePricingNotUsed(@Param("id") Long id,
                                      @Param("orderNo") String orderNo,
                                      @Param("code") String code);

    void updateCustomerProductByCustomer(Product product);

    void approveSupplierSubmission(Product product);

    void approveUnmatchedCustomerItem(Product product);

    void approveCustomerProduct(Product product);

    void linkCustomerOrderItemsByModels(Product product);

    void supplierUpdateQuote(SupplierQuote quote);

    int supplierUpdateQuoteByOrderAndCode(SupplierQuote quote);

    void adminUpdateQuote(SupplierQuote quote);

    void updateSupplierSubmissionPrice(@Param("supplierUsername") String supplierUsername,
                                       @Param("code") String code,
                                       @Param("purchasePrice") java.math.BigDecimal purchasePrice);

    SupplierQuote findSupplierQuote(Long id);

    void useQuoteOnCustomerItem(@Param("customerItemId") Long customerItemId,
                                @Param("purchasePrice") java.math.BigDecimal purchasePrice,
                                @Param("salePrice") java.math.BigDecimal salePrice);

    void pushInternalOrderPrice(@Param("code") String code, @Param("entry") String entry);

    void updateInternalPricesByCode(@Param("code") String code,
                                    @Param("purchasePrice") java.math.BigDecimal purchasePrice,
                                    @Param("salePrice") java.math.BigDecimal salePrice);

    void updateMasterById(Product product);

    void updateMasterFromSupplier(Product product);

    void updateMasterFromCustomer(Product product);

    void updateInternalLinkedMaster(Product product);
}
