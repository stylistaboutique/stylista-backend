package com.stylista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers",
       uniqueConstraints = @UniqueConstraint(name = "uq_customers_name_mobile",
                                             columnNames = {"name", "mobile"}))
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // No longer globally unique - the SAME mobile can have multiple names
    // (e.g. mother & daughter). Uniqueness is on (name, mobile).
    @Column(nullable = false)
    private String mobile;

    @Column(name = "measurements", columnDefinition = "TEXT")
    private String measurements;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getMeasurements() { return measurements; }
    public void setMeasurements(String measurements) { this.measurements = measurements; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
