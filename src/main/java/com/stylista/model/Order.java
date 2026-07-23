package com.stylista.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        ORDER_RECEIVED,
        PAYMENT_RECEIVED,
        MEASUREMENT_TAKEN,
        STITCHING_IN_PROGRESS,
        READY_FOR_PICKUP,
        DELIVERED
    }

    public enum ProductType {
        BLOUSE,
        LEHENGA,
        BRIDAL_WEAR,
        GOWN,
        SUIT,
        ALTERATION,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // Optional: which tailor is handling this order
    @Column(name = "tailor_id")
    private Long tailorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType = ProductType.OTHER;

    // Free-text product description (e.g. "Red silk bridal blouse with zari work")
    @Column(name = "product_description", columnDefinition = "TEXT")
    private String productDescription;

    // Due date for prioritization — orders sorted by this ascending
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "expected_price")
    private Integer expectedPrice;

    @Column(name = "advance_paid")
    private Integer advancePaid = 0;

    // remaining = expectedPrice - advancePaid (computed on the fly, not stored)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ORDER_RECEIVED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ===== Computed helper =====
    @Transient
    public int getRemaining() {
        int price = expectedPrice != null ? expectedPrice : 0;
        int advance = advancePaid != null ? advancePaid : 0;
        return Math.max(0, price - advance);
    }

    // ===== Getters & Setters =====
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
