package com.suppliercustomer.controller;

import com.suppliercustomer.pojo.LoginRequest;
import com.suppliercustomer.pojo.LoginUser;
import com.suppliercustomer.pojo.PasswordRequest;
import com.suppliercustomer.pojo.Product;
import com.suppliercustomer.pojo.Result;
import com.suppliercustomer.pojo.SupplierQuote;
import com.suppliercustomer.service.ProductService;
import com.suppliercustomer.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private UserService userService;
    @Autowired
    private ProductService productService;

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest loginRequest) {
        return Result.success(userService.login(loginRequest));
    }

    @GetMapping("/me")
    public Result me(@RequestHeader("Authorization") String authorization) {
        return Result.success(userService.currentUser(authorization));
    }

    @PutMapping("/me/password")
    public Result changePassword(@RequestHeader("Authorization") String authorization,
                                 @RequestBody PasswordRequest passwordRequest) {
        userService.changePassword(authorization, passwordRequest);
        return Result.success();
    }

    @GetMapping("/admin/users")
    public Result users(@RequestHeader("Authorization") String authorization) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(userService.list());
    }

    @PostMapping("/admin/users")
    public Result addUser(@RequestHeader("Authorization") String authorization,
                          @RequestBody LoginUser user) {
        userService.requireRole(authorization, "ADMIN");
        userService.add(user);
        return Result.success();
    }

    @PutMapping("/admin/users/{id}")
    public Result updateUser(@RequestHeader("Authorization") String authorization,
                             @PathVariable Long id,
                             @RequestBody LoginUser user) {
        userService.requireRole(authorization, "ADMIN");
        user.setId(id);
        userService.update(user);
        return Result.success();
    }

    @DeleteMapping("/admin/users/{id}")
    public Result deleteUser(@RequestHeader("Authorization") String authorization,
                             @PathVariable Long id) {
        userService.requireRole(authorization, "ADMIN");
        userService.delete(id);
        return Result.success();
    }

    @GetMapping("/admin/table/{name}")
    public Result table(@RequestHeader("Authorization") String authorization,
                        @PathVariable String name) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(productService.listTable(name));
    }

    @PutMapping("/admin/table/{name}/{id}")
    public Result updateTableRow(@RequestHeader("Authorization") String authorization,
                                 @PathVariable String name,
                                 @PathVariable Long id,
                                 @RequestBody Map<String, Object> row) {
        userService.requireRole(authorization, "ADMIN");
        productService.updateTableRow(name, id, row);
        return Result.success();
    }

    @DeleteMapping("/admin/table/{name}/{id}")
    public Result deleteTableRow(@RequestHeader("Authorization") String authorization,
                                 @PathVariable String name,
                                 @PathVariable Long id) {
        userService.requireRole(authorization, "ADMIN");
        productService.deleteTableRow(name, id);
        return Result.success();
    }

    @GetMapping("/admin/orders/{orderNo}/items")
    public Result adminOrderItems(@RequestHeader("Authorization") String authorization,
                                  @PathVariable String orderNo) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(productService.listAdminOrderItems(orderNo));
    }

    @PutMapping("/admin/orders/{orderNo}/cancel")
    public Result cancelOrder(@RequestHeader("Authorization") String authorization,
                              @PathVariable String orderNo) {
        userService.requireRole(authorization, "ADMIN");
        productService.cancelOrder(orderNo);
        return Result.success();
    }

    @PutMapping("/admin/order-items/{id}/cancel")
    public Result cancelOrderItem(@RequestHeader("Authorization") String authorization,
                                  @PathVariable Long id) {
        userService.requireRole(authorization, "ADMIN");
        productService.cancelOrderItem(id);
        return Result.success();
    }

    @PutMapping("/admin/order-items/{id}/prices")
    public Result updateOrderItemPrices(@RequestHeader("Authorization") String authorization,
                                        @PathVariable Long id,
                                        @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.updateOrderItemPrices(id, product);
        return Result.success();
    }

    @GetMapping("/admin/table/{name}/template")
    public ResponseEntity<byte[]> adminTableTemplate(@RequestHeader("Authorization") String authorization,
                                                     @PathVariable String name) throws IOException {
        userService.requireRole(authorization, "ADMIN");
        return productService.tableTemplate(name);
    }

    @PostMapping("/admin/table/{name}/import")
    public Result importTable(@RequestHeader("Authorization") String authorization,
                              @PathVariable String name,
                              @RequestParam("file") MultipartFile file) throws IOException {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(productService.importTable(name, file));
    }

    @PostMapping("/admin/master-products")
    public Result addMaster(@RequestHeader("Authorization") String authorization,
                            @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.addMaster(product);
        return Result.success();
    }

    @PostMapping("/admin/internal-products")
    public Result addInternalProduct(@RequestHeader("Authorization") String authorization,
                                     @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.addInternalProduct(product);
        return Result.success();
    }

    @PostMapping("/admin/supplier-submissions/{id}/approve")
    public Result approveSupplier(@RequestHeader("Authorization") String authorization,
                                  @PathVariable Long id,
                                  @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.approveSupplier(id, product);
        return Result.success();
    }

    @PostMapping("/admin/unmatched-items/{id}/approve")
    public Result approveUnmatched(@RequestHeader("Authorization") String authorization,
                                   @PathVariable Long id,
                                   @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.approveUnmatched(id, product);
        return Result.success();
    }

    @PostMapping("/admin/customer-products/{id}/approve")
    public Result approveCustomerProduct(@RequestHeader("Authorization") String authorization,
                                         @PathVariable Long id,
                                         @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.approveCustomerProduct(id, product);
        return Result.success();
    }

    @PostMapping("/admin/orders/{orderNo}/generate-quotes")
    public Result generateQuotes(@RequestHeader("Authorization") String authorization,
                                 @PathVariable String orderNo) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(message(productService.generateQuotes(orderNo)));
    }

    @PostMapping("/admin/order-items/generate-quotes")
    public Result generateQuotesForItems(@RequestHeader("Authorization") String authorization,
                                         @RequestBody List<Long> itemIds) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(message(productService.generateQuotesForItems(itemIds)));
    }

    @PostMapping("/admin/pricing-audit/send-quotes")
    public Result sendPricingAuditQuoteTasks(@RequestHeader("Authorization") String authorization,
                                             @RequestBody List<String> codes) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(message(productService.sendPricingAuditQuoteTasks(codes)));
    }

    @PostMapping("/admin/pricing-audit/use-price")
    public Result usePricingAuditPrice(@RequestHeader("Authorization") String authorization,
                                       @RequestBody Map<String, Object> price) {
        userService.requireRole(authorization, "ADMIN");
        productService.usePricingAuditPrice(price);
        return Result.success();
    }

    @PutMapping("/admin/quotes/{id}")
    public Result adminUpdateQuote(@RequestHeader("Authorization") String authorization,
                                   @PathVariable Long id,
                                   @RequestBody SupplierQuote quote) {
        userService.requireRole(authorization, "ADMIN");
        productService.adminUpdateQuote(id, quote);
        return Result.success();
    }

    @PutMapping("/admin/quotes/batch")
    public Result adminUpdateQuoteBatch(@RequestHeader("Authorization") String authorization,
                                        @RequestBody List<SupplierQuote> quotes) {
        userService.requireRole(authorization, "ADMIN");
        productService.adminUpdateQuoteBatch(quotes);
        return Result.success();
    }

    @PutMapping("/admin/supplier-products/quotes/batch")
    public Result adminSupplierProductQuoteBatch(@RequestHeader("Authorization") String authorization,
                                                 @RequestBody List<SupplierQuote> quotes) {
        userService.requireRole(authorization, "ADMIN");
        productService.adminSupplierProductQuoteBatch(quotes);
        return Result.success();
    }

    @PostMapping("/admin/supplier-products")
    public Result adminAddSupplierProduct(@RequestHeader("Authorization") String authorization,
                                          @RequestBody Product product) {
        userService.requireRole(authorization, "ADMIN");
        productService.adminAddSupplierSubmission(product);
        return Result.success();
    }

    @PostMapping("/admin/quotes/{id}/use")
    public Result useSupplierQuote(@RequestHeader("Authorization") String authorization,
                                   @PathVariable Long id) {
        userService.requireRole(authorization, "ADMIN");
        productService.useSupplierQuote(id);
        return Result.success();
    }

    @GetMapping("/admin/quote-orders/{orderNo}/items")
    public Result adminSupplierQuoteItems(@RequestHeader("Authorization") String authorization,
                                          @PathVariable String orderNo,
                                          @RequestParam("supplierUsername") String supplierUsername) {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(productService.listAdminSupplierQuoteItemDetails(supplierUsername, orderNo));
    }

    @PostMapping("/admin/quote-orders/download")
    public ResponseEntity<byte[]> adminQuoteDownload(@RequestHeader("Authorization") String authorization,
                                                     @RequestBody List<Long> quoteIds) throws IOException {
        userService.requireRole(authorization, "ADMIN");
        return productService.adminQuoteDownload(quoteIds);
    }

    @PostMapping("/admin/quote-orders/import")
    public Result importAdminQuotePrices(@RequestHeader("Authorization") String authorization,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        userService.requireRole(authorization, "ADMIN");
        return Result.success(productService.importAdminQuotePrices(file));
    }

    @GetMapping("/supplier/submissions")
    public Result supplierSubmissions(@RequestHeader("Authorization") String authorization) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.listSupplierSubmissions(user.getUsername()));
    }

    @PostMapping("/supplier/submissions")
    public Result addSupplierSubmission(@RequestHeader("Authorization") String authorization,
                                        @RequestBody Product product) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        productService.addSupplierSubmission(user.getUsername(), product);
        return Result.success();
    }

    @GetMapping("/supplier/template")
    public ResponseEntity<byte[]> supplierTemplate(@RequestHeader("Authorization") String authorization) throws IOException {
        userService.requireRole(authorization, "SUPPLIER");
        return productService.supplierTemplate();
    }

    @PostMapping("/supplier/upload")
    public Result supplierUpload(@RequestHeader("Authorization") String authorization,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.uploadSupplierSubmissions(user.getUsername(), file));
    }

    @GetMapping("/supplier/quotes")
    public Result supplierQuotes(@RequestHeader("Authorization") String authorization) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.listSupplierQuotes(user.getUsername()));
    }

    @GetMapping("/supplier/quote-orders")
    public Result supplierQuoteOrders(@RequestHeader("Authorization") String authorization) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.listSupplierQuoteOrders(user.getUsername()));
    }

    @GetMapping("/supplier/quote-orders/{orderNo}/items")
    public Result supplierQuoteItems(@RequestHeader("Authorization") String authorization,
                                     @PathVariable String orderNo) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.listSupplierQuoteItems(user.getUsername(), orderNo));
    }

    @GetMapping("/supplier/quote-orders/{orderNo}/detail-items")
    public Result supplierQuoteDetailItems(@RequestHeader("Authorization") String authorization,
                                           @PathVariable String orderNo) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.listSupplierQuoteItemDetails(user.getUsername(), orderNo));
    }

    @PutMapping("/supplier/quotes/{id}")
    public Result supplierUpdateQuote(@RequestHeader("Authorization") String authorization,
                                      @PathVariable Long id,
                                      @RequestBody SupplierQuote quote) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        productService.supplierUpdateQuote(user.getUsername(), id, quote);
        return Result.success();
    }

    @PutMapping("/supplier/quotes/batch")
    public Result supplierUpdateQuoteBatch(@RequestHeader("Authorization") String authorization,
                                           @RequestBody List<SupplierQuote> quotes) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        productService.supplierUpdateQuoteBatch(user.getUsername(), quotes);
        return Result.success();
    }

    @PutMapping("/supplier/products/quotes/batch")
    public Result supplierProductQuoteBatch(@RequestHeader("Authorization") String authorization,
                                            @RequestBody List<SupplierQuote> quotes) {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        productService.supplierProductQuoteBatch(user.getUsername(), quotes);
        return Result.success();
    }

    @PostMapping("/supplier/quote-orders/download")
    public ResponseEntity<byte[]> supplierQuoteDownload(@RequestHeader("Authorization") String authorization,
                                                        @RequestBody List<String> orderNos) throws IOException {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return productService.supplierQuoteDownload(user.getUsername(), orderNos);
    }

    @PostMapping("/supplier/quote-orders/import")
    public Result supplierQuoteImport(@RequestHeader("Authorization") String authorization,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        LoginUser user = userService.requireRole(authorization, "SUPPLIER");
        return Result.success(productService.importSupplierQuotePrices(user.getUsername(), file));
    }

    @GetMapping("/customer/orders")
    public Result customerOrders(@RequestHeader("Authorization") String authorization) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.listCustomerOrders(user.getUsername()));
    }

    @GetMapping("/customer/orders/{orderNo}/items")
    public Result customerOrderItems(@RequestHeader("Authorization") String authorization,
                                     @PathVariable String orderNo) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.listCustomerOrderItems(orderNo, user.getUsername()));
    }

    @GetMapping("/customer/products")
    public Result customerProducts(@RequestHeader("Authorization") String authorization) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.listCustomerProducts(user.getUsername()));
    }

    @GetMapping("/customer/internal-products")
    public Result customerInternalProducts(@RequestHeader("Authorization") String authorization) {
        userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.listInternalProducts());
    }

    @PostMapping("/customer/orders")
    public Result addCustomerOrder(@RequestHeader("Authorization") String authorization,
                                   @RequestBody Product product) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.addCustomerOrder(user.getUsername(), product));
    }

    @PostMapping("/customer/orders/from-internal")
    public Result addCustomerOrderFromInternal(@RequestHeader("Authorization") String authorization,
                                               @RequestBody List<Long> internalProductIds) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.addCustomerOrderFromInternal(user.getUsername(), internalProductIds));
    }

    @PutMapping("/customer/orders/{orderNo}/cancel")
    public Result cancelCustomerOrder(@RequestHeader("Authorization") String authorization,
                                      @PathVariable String orderNo) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        productService.cancelCustomerOrder(user.getUsername(), orderNo);
        return Result.success();
    }

    @PutMapping("/customer/products/{id}")
    public Result updateCustomerProduct(@RequestHeader("Authorization") String authorization,
                                        @PathVariable Long id,
                                        @RequestBody Product product) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        productService.updateCustomerProduct(user.getUsername(), id, product);
        return Result.success();
    }

    @PutMapping("/customer/order-items/{id}/cancel")
    public Result cancelCustomerOrderItem(@RequestHeader("Authorization") String authorization,
                                          @PathVariable Long id) {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        productService.cancelCustomerOrderItem(user.getUsername(), id);
        return Result.success();
    }

    @GetMapping("/customer/template")
    public ResponseEntity<byte[]> template(@RequestHeader("Authorization") String authorization) throws IOException {
        userService.requireRole(authorization, "CUSTOMER");
        return productService.template();
    }

    @PostMapping("/customer/upload")
    public Result upload(@RequestHeader("Authorization") String authorization,
                         @RequestParam("file") MultipartFile file) throws IOException {
        LoginUser user = userService.requireRole(authorization, "CUSTOMER");
        return Result.success(productService.upload(user.getUsername(), file));
    }

    private Map<String, Object> message(String message) {
        return java.util.Collections.singletonMap("message", message);
    }
}
