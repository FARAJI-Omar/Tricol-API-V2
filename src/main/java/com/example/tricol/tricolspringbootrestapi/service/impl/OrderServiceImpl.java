package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.dto.request.CreateOrderItemRequest;
import com.example.tricol.tricolspringbootrestapi.dto.request.CreateOrderRequest;
import com.example.tricol.tricolspringbootrestapi.dto.request.UpdateOrderStatus;
import com.example.tricol.tricolspringbootrestapi.dto.response.OrderResponse;
import com.example.tricol.tricolspringbootrestapi.dto.response.ReceiveOrderResponse;
import com.example.tricol.tricolspringbootrestapi.exception.BadRequestException;
import com.example.tricol.tricolspringbootrestapi.exception.InvalidOperationException;
import com.example.tricol.tricolspringbootrestapi.exception.ResourceNotFoundException;
import com.example.tricol.tricolspringbootrestapi.mapper.OrderItemMapper;
import com.example.tricol.tricolspringbootrestapi.mapper.OrderMapper;
import com.example.tricol.tricolspringbootrestapi.model.*;
import com.example.tricol.tricolspringbootrestapi.repository.*;
import com.example.tricol.tricolspringbootrestapi.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final StockSlotRepository stockSlotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Validate order has items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order must contain at least one item");
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId()).
                orElseThrow(() -> new ResourceNotFoundException("Supplier with id " + request.getSupplierId() + " not found"));

        Order order = new Order();
        order.setSupplier(supplier);
        order.setStatus(Order.OrderStatus.pending);

        List<OrderItem> items = new ArrayList<>();
        double totalAmount = 0;

        for (CreateOrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product with id " + itemReq.getProductId() + " not found"));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getUnitPrice());
            item.setTotal(product.getUnitPrice() * itemReq.getQuantity());

            totalAmount += item.getTotal();

            items.add(item);
        }
        order.setItems(items);
        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);

        return orderMapper.toDto(saved);
    }

    public OrderResponse getOrderById(Long id){
        return orderRepository.findById(id)
                .map(order -> orderMapper.toDto(order))
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + id + " not found"));
    }

    public List<OrderResponse> getAllOrders(){
        return orderMapper.toDTOList(orderRepository.findAll());
    }

    //update order
    public OrderResponse updateOrder(Long id, UpdateOrderStatus request) {
        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + id + " not found"));

        orderMapper.updateOrderFromDTO(request, existingOrder);
        return orderMapper.toDto(orderRepository.save(existingOrder));
    }
    // filter order
    public List<OrderResponse> filterOrdersByStatus(Order.OrderStatus status) {
        return orderMapper.toDTOList(orderRepository.findByStatus(status));
    }
    public List<OrderResponse> filterOrdersBySupplier(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId));
        return orderMapper.toDTOList(orderRepository.findBySupplier(supplier));
    }
    public List<OrderResponse> filterOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return orderMapper.toDTOList(orderRepository.findByOrderDateBetween(startDate, endDate));
    }

    // receive an order
    @Transactional
    public ReceiveOrderResponse receiveOrder(Long orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getStatus() == Order.OrderStatus.delivered) {
            throw new InvalidOperationException("Order has already been received");
        }
        order.setStatus(Order.OrderStatus.delivered);

        // create one stockslot per orderitem (per product)
        List<StockSlot> stockSlots = new ArrayList<>();

        for (OrderItem orderItem : order.getItems()) {
            StockSlot stockSlot = new StockSlot();
            stockSlot.setLotNumber(generateLotNumber());
            stockSlot.setOrder(order);
            stockSlot.setProduct(orderItem.getProduct());
            stockSlot.setQuantity(orderItem.getQuantity());
            stockSlot.setAvailableQuantity(orderItem.getQuantity());
            stockSlot.setUnitPrice(orderItem.getUnitPrice());

            stockSlots.add(stockSlot);

            // update product current stock
            Product product = orderItem.getProduct();
            Double currentStock = product.getCurrentStock();
            Double newStock = currentStock + orderItem.getQuantity();
            product.setCurrentStock(newStock);

            productRepository.save(product);
        }

        // save all stock slots
        stockSlotRepository.saveAll(stockSlots);
        order.setStockSlot(stockSlots);

        // save stock movements for each stock slot
        for (StockSlot stockSlot : stockSlots) {
            saveStockMovementIn(stockSlot);
        }

        // save updated order
        Order savedOrder = orderRepository.save(order);

        return orderMapper.toReceiveOrderResponse(savedOrder);
    }

    private String generateLotNumber() {
        long count = stockSlotRepository.count() + 1;
        int year = java.time.Year.now().getValue();
        return String.format("LOT-%d-%03d", year, count);
    }

    private void saveStockMovementIn(StockSlot stockSlot){
        StockMovement stockMovement = new StockMovement();
        stockMovement.setType(StockMovement.Type.in);
        stockMovement.setQuantity(stockSlot.getQuantity());
        stockMovement.setProduct(stockSlot.getProduct());
        stockMovement.setStockSlot(stockSlot);
        stockMovement.setOrder(stockSlot.getOrder());

        stockMovementRepository.save(stockMovement);
    }

}
