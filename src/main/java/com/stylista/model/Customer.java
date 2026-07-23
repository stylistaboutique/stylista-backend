package com.stylista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String mobile;

    // Measurements stored as a JSON string — no migration ever needed.
    // Example: {"chest":"36","waist":"30","hip":"38","shoulder":"14",
    //            "sleeve":"24","length":"42","blouse":"16","notes":""}
    @Column(name = "measurements", columnDefinition = "TEXT")
    private String measurements;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ===== Getters & Setters =====
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
