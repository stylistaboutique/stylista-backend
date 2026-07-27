package com.stylista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashback_assignments")
public class CashbackAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "cashback_percent", nullable = false)
    private Integer cashbackPercent = 20;

    @Column(name = "cashback_amount", nullable = false)
    private Integer cashbackAmount;

    // #4 how much of this cashback is still spendable (FIFO partial use)
    @Column(name = "remaining_amount")
    private Integer remainingAmount;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_redeemed", nullable = false)
    private boolean redeemed = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Transient
    public boolean isExpired() {
        return !redeemed && LocalDateTime.now().isAfter(expiresAt);
    }

    // Active = not redeemed, not expired, and still has balance left
    @Transient
    public boolean isActive() {
        return !redeemed && !isExpired()
                && (remainingAmount == null || remainingAmount > 0);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getCashbackPercent() { return cashbackPercent; }
    public void setCashbackPercent(Integer cashbackPercent) { this.cashbackPercent = cashbackPercent; }
    public Integer getCashbackAmount() { return cashbackAmount; }
    public void setCashbackAmount(Integer cashbackAmount) { this.cashbackAmount = cashbackAmount; }
    public Integer getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(Integer remainingAmount) { this.remainingAmount = remainingAmount; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRedeemed() { return redeemed; }
    public void setRedeemed(boolean redeemed) { this.redeemed = redeemed; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
