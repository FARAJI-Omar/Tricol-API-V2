package com.example.tricol.tricolspringbootrestapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double unitPrice;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String measureUnit;

    @Column(nullable = false)
    private Double reorderPoint;

    @Column(nullable = false)
    private Double currentStock;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "product")
    private List<OrderItem> commandItems = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private List<StockSlot> stockSlot;

    @OneToMany(mappedBy = "product")
    private List<StockMovement> stockMovements;

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", reference='" + reference + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", unitPrice=" + unitPrice +
                ", category='" + category + '\'' +
                ", measureUnit='" + measureUnit + '\'' +
                ", reorderPoint=" + reorderPoint +
                ", currentStock=" + currentStock +
                ", createdAt=" + createdAt +
                ", commandItems=" + commandItems +
                ", stockSlot=" + stockSlot +
                ", stockMovements=" + stockMovements +
                '}';
    }
}
