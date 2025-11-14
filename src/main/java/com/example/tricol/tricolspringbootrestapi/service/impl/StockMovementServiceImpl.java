package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.dto.response.StockMovementResponse;
import com.example.tricol.tricolspringbootrestapi.mapper.StockMovementMapper;
import com.example.tricol.tricolspringbootrestapi.model.StockMovement;
import com.example.tricol.tricolspringbootrestapi.repository.StockMovementRepository;
import com.example.tricol.tricolspringbootrestapi.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockMovementServiceImpl implements StockMovementService {
    
    private final StockMovementRepository stockMovementRepository;
    private final StockMovementMapper stockMovementMapper;
    
    @Override
    public List<StockMovementResponse> searchMovements(
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Long productId, 
            String reference, 
            StockMovement.Type type,
            String lotNumber) {
        
        Specification<StockMovement> spec = null;
        
        if (startDate != null && endDate != null) {
            spec = (root, query, cb) -> cb.between(root.get("date"), startDate, endDate);
        } else if (startDate != null) {
            spec = addSpec(spec, (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), startDate));
        } else if (endDate != null) {
            spec = addSpec(spec, (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), endDate));
        }
        
        if (productId != null) {
            spec = addSpec(spec, (root, query, cb) -> cb.equal(root.get("product").get("id"), productId));
        }
        
        if (reference != null) {
            spec = addSpec(spec, (root, query, cb) -> cb.equal(root.get("product").get("reference"), reference));
        }
        
        if (type != null) {
            spec = addSpec(spec, (root, query, cb) -> cb.equal(root.get("type"), type));
        }
        
        if (lotNumber != null) {
            spec = addSpec(spec, (root, query, cb) -> cb.equal(root.get("stockSlot").get("lotNumber"), lotNumber));
        }
        
        List<StockMovement> movements = spec != null ? stockMovementRepository.findAll(spec) : stockMovementRepository.findAll();
        return stockMovementMapper.toResponseList(movements);
    }
    
    private Specification<StockMovement> addSpec(Specification<StockMovement> spec, Specification<StockMovement> newSpec) {
        return spec == null ? newSpec : spec.and(newSpec);
    }
}
