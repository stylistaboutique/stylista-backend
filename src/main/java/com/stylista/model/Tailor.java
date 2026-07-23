package com.stylista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tailor / boutique partner who can take and handle orders.
 * New tailors can be added from the admin panel at any time.
 */
@Entity
@Table(name = "tailors")
public class Tailor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String mobile;

    @Column(name = "boutique_name")
    private String boutiqueName;

    private String city;

    // e.g. "Bridal, Blouse, Lehenga"
    private String specialization;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getBoutiqueName() { return boutiqueName; }
    public void setBoutiqueName(String boutiqueName) { this.boutiqueName = boutiqueName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
