package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.dto.request.CreateExitSlipRequest;
import com.example.tricol.tricolspringbootrestapi.dto.request.ExitSlipItemRequest;
import com.example.tricol.tricolspringbootrestapi.dto.response.ExitSlipResponse;
import com.example.tricol.tricolspringbootrestapi.enums.ExitSlipStatus;
import com.example.tricol.tricolspringbootrestapi.exception.InsufficientStockException;
import com.example.tricol.tricolspringbootrestapi.exception.ResourceNotFoundException;
import com.example.tricol.tricolspringbootrestapi.mapper.ExitSlipMapper;
import com.example.tricol.tricolspringbootrestapi.model.*;
import com.example.tricol.tricolspringbootrestapi.repository.ExitSlipRepository;
import com.example.tricol.tricolspringbootrestapi.repository.ProductRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockMovementRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockSlotRepository;
import com.example.tricol.tricolspringbootrestapi.service.ExitSlipService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExitSlipServiceImpl implements ExitSlipService {
    
    private final ExitSlipRepository exitSlipRepository;
    private final ProductRepository productRepository;
    private final StockSlotRepository stockSlotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ExitSlipMapper exitSlipMapper;
    
    @Transactional
    public ExitSlipResponse createExitSlip(CreateExitSlipRequest request) {
        ExitSlip exitSlip = new ExitSlip();
        exitSlip.setSlipNumber(generateSlipNumber());
        exitSlip.setExitDate(request.getExitDate());
        exitSlip.setDestinationWorkshop(request.getDestinationWorkshop());
        exitSlip.setReason(request.getReason());
        exitSlip.setComment(request.getComment());
        exitSlip.setStatus(ExitSlipStatus.DRAFT);
        exitSlip.setCreatedBy("SYSTEM"); 
        
        // Add items
        for (ExitSlipItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));
            
            ExitSlipItem item = new ExitSlipItem();
            item.setExitSlip(exitSlip);
            item.setProduct(product);
            item.setRequestedQuantity(itemRequest.getQuantity());
            item.setNote(itemRequest.getNote());
            
            exitSlip.getItems().add(item);
        }
        
        ExitSlip saved = exitSlipRepository.save(exitSlip);
        return exitSlipMapper.toResponse(saved);
    }

    @Transactional
    public ExitSlipResponse validateExitSlip(Long id) {
        ExitSlip exitSlip = exitSlipRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Exit slip not found: " + id));
        
        if (exitSlip.getStatus() != ExitSlipStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT exit slips can be validated");
        }
        
        // Consume stock in FIFO for each item
        for (ExitSlipItem item : exitSlip.getItems()) {
            Product product = item.getProduct();
            double quantityNeeded = item.getRequestedQuantity().doubleValue();
            
            List<StockSlot> availableSlots = stockSlotRepository
                .findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(product, 0.0);
            
            if (availableSlots.isEmpty()) {
                throw new InsufficientStockException("Insufficient stock for product: " + product.getName());
            }
            
            double totalAvailable = availableSlots.stream()
                .mapToDouble(slot -> slot.getAvailableQuantity() != null ? slot.getAvailableQuantity() : 0.0)
                .sum();
            
            if (totalAvailable < quantityNeeded) {
                throw new InsufficientStockException(
                    String.format("Insufficient stock for product: %s. Required: %.2f, Available: %.2f",
                        product.getName(), quantityNeeded, totalAvailable)
                );
            }
            
            double remainingQuantity = quantityNeeded;
            
            // Consume from oldest slots first (FIFO)
            for (StockSlot slot : availableSlots) {
                if (remainingQuantity <= 0) {
                    break;
                }
                
                double availableInSlot = slot.getAvailableQuantity() != null ? slot.getAvailableQuantity() : 0.0;
                double toConsume = Math.min(remainingQuantity, availableInSlot);
                
                // Create stock movement OUT
                saveStockMovementOut(slot, product, toConsume);
                
                // Update slot available quantity
                slot.setAvailableQuantity(availableInSlot - toConsume);
                stockSlotRepository.save(slot);
                
                remainingQuantity -= toConsume;
            }
            
            item.setActualQuantity(item.getRequestedQuantity());
            
            // Update product current stock
            double newStock = product.getCurrentStock() - quantityNeeded;
            product.setCurrentStock(newStock);
            productRepository.save(product);
        }
        
        exitSlip.setStatus(ExitSlipStatus.VALIDATED);
        exitSlip.setValidatedAt(LocalDateTime.now());
        exitSlip.setValidatedBy("SYSTEM");
        
        ExitSlip validated = exitSlipRepository.save(exitSlip);
        return exitSlipMapper.toResponse(validated);
    }
    
    private void saveStockMovementOut(StockSlot stockSlot, Product product, double quantity) {
        StockMovement stockMovement = new StockMovement();
        stockMovement.setType(StockMovement.Type.out);
        stockMovement.setQuantity(-quantity);
        stockMovement.setProduct(product);
        stockMovement.setStockSlot(stockSlot);
        
        stockMovementRepository.save(stockMovement);
    }
    
    @Transactional
    public ExitSlipResponse cancelExitSlip(Long id) {
        ExitSlip exitSlip = exitSlipRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Exit slip not found: " + id));
        
        if (exitSlip.getStatus() == ExitSlipStatus.CANCELLED) {
            throw new RuntimeException("Exit slip is already cancelled");
        }
        
        if (exitSlip.getStatus() == ExitSlipStatus.VALIDATED) {
            throw new RuntimeException("Cannot cancel a validated exit slip (stock already consumed)");
        }
        
        exitSlip.setStatus(ExitSlipStatus.CANCELLED);
        exitSlip.setCancelledAt(LocalDateTime.now());
        exitSlip.setCancelledBy("SYSTEM"); 
        
        ExitSlip cancelled = exitSlipRepository.save(exitSlip);
        return exitSlipMapper.toResponse(cancelled);
    }
    
    public ExitSlipResponse getExitSlip(Long id) {
        ExitSlip exitSlip = exitSlipRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Exit slip not found: " + id));
        return exitSlipMapper.toResponse(exitSlip);
    }
    
    public List<ExitSlipResponse> getAllExitSlips() {
        return exitSlipMapper.toResponseList(exitSlipRepository.findAll());
    }

    public List<ExitSlipResponse> getExitSlipsByStatus(ExitSlipStatus status) {
        return exitSlipMapper.toResponseList(exitSlipRepository.findByStatus(status));
    }
    
    public List<ExitSlipResponse> getExitSlipsByWorkshop(String workshop) {
        return exitSlipMapper.toResponseList(exitSlipRepository.findByDestinationWorkshop(workshop));
    }

    private String generateSlipNumber() {
        String prefix = "BS";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = exitSlipRepository.count() + 1;
        return String.format("%s-%s-%04d", prefix, date, count);
    }

    public double calculateStockValue(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        List<StockSlot> stockSlots = stockSlotRepository
            .findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(product, 0.0);

        return stockSlots.stream()
            .mapToDouble(slot -> {
                double availableQty = slot.getAvailableQuantity() != null ? slot.getAvailableQuantity() : 0.0;
                double price = slot.getUnitPrice() != null ? slot.getUnitPrice() : 0.0;
                return availableQty * price;
            })
            .sum();
    }
}
