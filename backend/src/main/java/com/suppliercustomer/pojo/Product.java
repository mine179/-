package com.suppliercustomer.pojo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Product {
    private Long id;
    private String serialNo;
    private String series;
    private String brand;
    private String code;
    private String newCode;
    private String color;
    private String category;
    private String craftMaterial;
    private String specModel;
    private String commonModel;
    private String sizeValue;
    private String resolution;
    private String modelRemark;
    private BigDecimal salePrice;
    private BigDecimal purchasePrice;
    private LocalDate priceValidUntil;
    private LocalDate updateDate;
    private String supplierUsername;
    private String customerUsername;
    private String sourceType;
    private String status;
    private String orderNo;
    private String pricingGroup;
    private String orderRemark;
    private String materialLinkStatus;
    private Boolean matched;
    private Long masterProductId;
    private Long linkedMasterProductId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNewCode() { return newCode; }
    public void setNewCode(String newCode) { this.newCode = newCode; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCraftMaterial() { return craftMaterial; }
    public void setCraftMaterial(String craftMaterial) { this.craftMaterial = craftMaterial; }
    public String getSpecModel() { return specModel; }
    public void setSpecModel(String specModel) { this.specModel = specModel; }
    public String getCommonModel() { return commonModel; }
    public void setCommonModel(String commonModel) { this.commonModel = commonModel; }
    public String getSizeValue() { return sizeValue; }
    public void setSizeValue(String sizeValue) { this.sizeValue = sizeValue; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getModelRemark() { return modelRemark; }
    public void setModelRemark(String modelRemark) { this.modelRemark = modelRemark; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public LocalDate getPriceValidUntil() { return priceValidUntil; }
    public void setPriceValidUntil(LocalDate priceValidUntil) { this.priceValidUntil = priceValidUntil; }
    public LocalDate getUpdateDate() { return updateDate; }
    public void setUpdateDate(LocalDate updateDate) { this.updateDate = updateDate; }
    public String getSupplierUsername() { return supplierUsername; }
    public void setSupplierUsername(String supplierUsername) { this.supplierUsername = supplierUsername; }
    public String getCustomerUsername() { return customerUsername; }
    public void setCustomerUsername(String customerUsername) { this.customerUsername = customerUsername; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPricingGroup() { return pricingGroup; }
    public void setPricingGroup(String pricingGroup) { this.pricingGroup = pricingGroup; }
    public String getOrderRemark() { return orderRemark; }
    public void setOrderRemark(String orderRemark) { this.orderRemark = orderRemark; }
    public String getMaterialLinkStatus() { return materialLinkStatus; }
    public void setMaterialLinkStatus(String materialLinkStatus) { this.materialLinkStatus = materialLinkStatus; }
    public Boolean getMatched() { return matched; }
    public void setMatched(Boolean matched) { this.matched = matched; }
    public Long getMasterProductId() { return masterProductId; }
    public void setMasterProductId(Long masterProductId) { this.masterProductId = masterProductId; }
    public Long getLinkedMasterProductId() { return linkedMasterProductId; }
    public void setLinkedMasterProductId(Long linkedMasterProductId) { this.linkedMasterProductId = linkedMasterProductId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
