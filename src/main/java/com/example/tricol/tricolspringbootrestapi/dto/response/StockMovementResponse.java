package com.example.tricol.tricolspringbootrestapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private Long id;
    private String type;
    private LocalDateTime date;
    private Double quantity;
    private Long productId;
    private String productName;
    private Long orderId;
    private Long stockSlotId;
    private String lotNumber;
}
