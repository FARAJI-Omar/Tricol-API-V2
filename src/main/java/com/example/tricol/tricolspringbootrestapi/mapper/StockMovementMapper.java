package com.example.tricol.tricolspringbootrestapi.mapper;

import com.example.tricol.tricolspringbootrestapi.dto.response.StockMovementResponse;
import com.example.tricol.tricolspringbootrestapi.model.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {
    
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "stockSlot.id", target = "stockSlotId")
    @Mapping(source = "stockSlot.lotNumber", target = "lotNumber")
    StockMovementResponse toResponse(StockMovement stockMovement);
    
    List<StockMovementResponse> toResponseList(List<StockMovement> stockMovements);
}
