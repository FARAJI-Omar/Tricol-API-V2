package com.example.tricol.tricolspringbootrestapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "exit_slip_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExitSlipItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_slip_id", nullable = false)
    @ToString.Exclude
    private ExitSlip exitSlip;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(nullable = false)
    private BigDecimal requestedQuantity;
    
    @Column
    private BigDecimal actualQuantity;
    
    @Column(length = 500)
    private String note;
}
