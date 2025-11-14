package com.example.tricol.tricolspringbootrestapi.service;

import com.example.tricol.tricolspringbootrestapi.dto.response.StockMovementResponse;
import com.example.tricol.tricolspringbootrestapi.model.StockMovement;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementService {
    List<StockMovementResponse> searchMovements(
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Long productId, 
            String reference, 
            StockMovement.Type type,
            String lotNumber);
}
