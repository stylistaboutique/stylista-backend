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

    // Also link to the order that generated this cashback (optional)
    @Column(name = "order_id")
    private Long orderId;

    // Percentage (default 20)
    @Column(name = "cashback_percent", nullable = false)
    private Integer cashbackPercent = 20;

    // Rupee amount of cashback earned
    @Column(name = "cashback_amount", nullable = false)
    private Integer cashbackAmount;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    // Default expiry = 60 days from assignedAt
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Admin manually marks this when customer uses the cashback
    @Column(name = "is_redeemed", nullable = false)
    private boolean redeemed = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ===== Computed helpers (not stored) =====

    @Transient
    public boolean isExpired() {
        return !redeemed && LocalDateTime.now().isAfter(expiresAt);
    }

    @Transient
    public boolean isActive() {
        return !redeemed && !isExpired();
    }

    // ===== Getters & Setters =====
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

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRedeemed() { return redeemed; }
    public void setRedeemed(boolean redeemed) { this.redeemed = redeemed; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
