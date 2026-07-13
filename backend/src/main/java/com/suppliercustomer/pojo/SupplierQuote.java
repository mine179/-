package com.suppliercustomer.pojo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SupplierQuote {
    private Long id;
    private String orderNo;
    private Long customerItemId;
    private Long masterProductId;
    private String supplierUsername;
    private String customerUsername;
    private String code;
    private String pricingGroup;
    private String specModel;
    private BigDecimal purchasePrice;
    private BigDecimal salePrice;
    private LocalDate priceValidUntil;
    private String status;
    private String pricingStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCustomerItemId() { return customerItemId; }
    public void setCustomerItemId(Long customerItemId) { this.customerItemId = customerItemId; }
    public Long getMasterProductId() { return masterProductId; }
    public void setMasterProductId(Long masterProductId) { this.masterProductId = masterProductId; }
    public String getSupplierUsername() { return supplierUsername; }
    public void setSupplierUsername(String supplierUsername) { this.supplierUsername = supplierUsername; }
    public String getCustomerUsername() { return customerUsername; }
    public void setCustomerUsername(String customerUsername) { this.customerUsername = customerUsername; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPricingGroup() { return pricingGroup; }
    public void setPricingGroup(String pricingGroup) { this.pricingGroup = pricingGroup; }
    public String getSpecModel() { return specModel; }
    public void setSpecModel(String specModel) { this.specModel = specModel; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public LocalDate getPriceValidUntil() { return priceValidUntil; }
    public void setPriceValidUntil(LocalDate priceValidUntil) { this.priceValidUntil = priceValidUntil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPricingStatus() { return pricingStatus; }
    public void setPricingStatus(String pricingStatus) { this.pricingStatus = pricingStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
