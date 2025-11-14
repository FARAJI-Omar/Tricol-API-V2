package com.example.tricol.tricolspringbootrestapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "stock_slots")
public class StockSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String lotNumber;

    private LocalDateTime entryDate;
    @PrePersist
    protected void onCreate(){entryDate = LocalDateTime.now();}

    private Double quantity;
    private Double availableQuantity;
    private Double unitPrice;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @OneToMany(mappedBy = "stockSlot")
    private List<StockMovement> stockMovements;
}
