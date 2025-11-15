package com.example.tricol.tricolspringbootrestapi.controller;

import com.example.tricol.tricolspringbootrestapi.dto.response.StockMovementResponse;
import com.example.tricol.tricolspringbootrestapi.model.StockMovement;
import com.example.tricol.tricolspringbootrestapi.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/stock/movements")
@RequiredArgsConstructor
public class StockMovementController {
    
    private final StockMovementService stockMovementService;
    
    @GetMapping
    public ResponseEntity<Page<StockMovementResponse>> searchMovements(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate startDate,
            
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate endDate,
            
            @RequestParam(required = false) 
            Long productId,
            
            @RequestParam(required = false) 
            String reference,
            
            @RequestParam(required = false) 
            StockMovement.Type type,
            
            @RequestParam(required = false) 
            String lotNumber,
            
            @RequestParam(defaultValue = "0")
            int page,
            
            @RequestParam(defaultValue = "10")
            int size) {
        
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;
        
        Page<StockMovementResponse> movements = stockMovementService.searchMovements(
                startDateTime, endDateTime, productId, reference, type, lotNumber, 
                PageRequest.of(page, size));
        
        if (movements.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(movements);
    }
}
