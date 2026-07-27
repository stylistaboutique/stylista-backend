package com.stylista.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        ORDER_RECEIVED, PAYMENT_RECEIVED, MEASUREMENT_TAKEN,
        STITCHING_IN_PROGRESS, READY_FOR_PICKUP, DELIVERED
    }
    public enum ProductType {
        BLOUSE, LEHENGA, BRIDAL_WEAR, GOWN, SUIT, ALTERATION, OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "tailor_id")
    private Long tailorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType = ProductType.OTHER;

    @Column(name = "product_description", columnDefinition = "TEXT")
    private String productDescription;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "expected_price")
    private Integer expectedPrice;

    @Column(name = "advance_paid")
    private Integer advancePaid = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ORDER_RECEIVED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ===== #3 soft delete: hidden from customers, kept for admin =====
    @Column(nullable = false)
    private boolean deleted = false;

    // ===== #4 how much existing balance was applied to THIS order =====
    @Column(name = "applied_cashback_balance", nullable = false)
    private Integer appliedCashbackBalance = 0;

    // ===== cashback-on-delivery: intended reward held until DELIVERED =====
    @Column(name = "pending_cashback_percent")
    private Integer pendingCashbackPercent;

    @Column(name = "pending_cashback_amount")
    private Integer pendingCashbackAmount;

    @Column(name = "pending_expiry_days")
    private Integer pendingExpiryDays;

    @Column(name = "cashback_granted", nullable = false)
    private boolean cashbackGranted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    @Transient
    public int getRemaining() {
        int price = expectedPrice != null ? expectedPrice : 0;
        int advance = advancePaid != null ? advancePaid : 0;
        int applied = appliedCashbackBalance != null ? appliedCashbackBalance : 0;
        return Math.max(0, price - advance - applied);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getTailorId() { return tailorId; }
    public void setTailorId(Long tailorId) { this.tailorId = tailorId; }
    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }
    public String getProductDescription() { return productDescription; }
    public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Integer getExpectedPrice() { return expectedPrice; }
    public void setExpectedPrice(Integer expectedPrice) { this.expectedPrice = expectedPrice; }
    public Integer getAdvancePaid() { return advancePaid; }
    public void setAdvancePaid(Integer advancePaid) { this.advancePaid = advancePaid; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public Integer getAppliedCashbackBalance() { return appliedCashbackBalance; }
    public void setAppliedCashbackBalance(Integer v) { this.appliedCashbackBalance = v; }
    public Integer getPendingCashbackPercent() { return pendingCashbackPercent; }
    public void setPendingCashbackPercent(Integer v) { this.pendingCashbackPercent = v; }
    public Integer getPendingCashbackAmount() { return pendingCashbackAmount; }
    public void setPendingCashbackAmount(Integer v) { this.pendingCashbackAmount = v; }
    public Integer getPendingExpiryDays() { return pendingExpiryDays; }
    public void setPendingExpiryDays(Integer v) { this.pendingExpiryDays = v; }
    public boolean isCashbackGranted() { return cashbackGranted; }
    public void setCashbackGranted(boolean v) { this.cashbackGranted = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
