package com.example.tricol.tricolspringbootrestapi.service;

import com.example.tricol.tricolspringbootrestapi.dto.response.StockMovementResponse;
import com.example.tricol.tricolspringbootrestapi.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface StockMovementService {
    Page<StockMovementResponse> searchMovements(
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Long productId, 
            String reference, 
            StockMovement.Type type,
            String lotNumber,
            Pageable pageable);
}
