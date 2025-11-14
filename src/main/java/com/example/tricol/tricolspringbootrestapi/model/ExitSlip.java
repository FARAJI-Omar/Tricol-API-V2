package com.example.tricol.tricolspringbootrestapi.model;

import com.example.tricol.tricolspringbootrestapi.enums.ExitReason;
import com.example.tricol.tricolspringbootrestapi.enums.ExitSlipStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exit_slips")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExitSlip {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String slipNumber;
    
    @Column(nullable = false)
    private LocalDateTime exitDate;
    
    @Column(nullable = false)
    private String destinationWorkshop;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExitReason reason;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExitSlipStatus status;
    
    @Column(length = 500)
    private String comment;
    
    @OneToMany(mappedBy = "exitSlip", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<ExitSlipItem> items = new ArrayList<>();
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime validatedAt;
    
    private LocalDateTime cancelledAt;
    
    private String createdBy;
    
    private String validatedBy;
    
    private String cancelledBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ExitSlipStatus.DRAFT;
        }
    }
}
