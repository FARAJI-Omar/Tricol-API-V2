package com.example.tricol.tricolspringbootrestapi.service;

import com.example.tricol.tricolspringbootrestapi.dto.response.ExitSlipResponse;
import com.example.tricol.tricolspringbootrestapi.enums.ExitReason;
import com.example.tricol.tricolspringbootrestapi.enums.ExitSlipStatus;
import com.example.tricol.tricolspringbootrestapi.exception.InsufficientStockException;
import com.example.tricol.tricolspringbootrestapi.mapper.ExitSlipMapper;
import com.example.tricol.tricolspringbootrestapi.mapper.OrderMapper;
import com.example.tricol.tricolspringbootrestapi.mapper.OrderItemMapper;
import com.example.tricol.tricolspringbootrestapi.model.*;
import com.example.tricol.tricolspringbootrestapi.repository.ExitSlipRepository;
import com.example.tricol.tricolspringbootrestapi.repository.OrderRepository;
import com.example.tricol.tricolspringbootrestapi.repository.ProductRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockMovementRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockSlotRepository;
import com.example.tricol.tricolspringbootrestapi.repository.SupplierRepository;
import com.example.tricol.tricolspringbootrestapi.service.impl.ExitSlipServiceImpl;
import com.example.tricol.tricolspringbootrestapi.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExitSlipServiceTest {
    /*
        Tâche 1.1.A: Mécanisme FIFO

        testWithdraw_Scenario1_PartialSingleLot()

        testWithdraw_Scenario2_MultipleLots()

        testWithdraw_Scenario3_InsufficientStock()

        testWithdraw_Scenario4_ExactExhaustion()

        Tâche 1.1.B: Création Automatique de Lot

        testProcessReception_createsLotAndMovement()

        Tâche 1.1.C: Calcul de Valorisation

        testCalculateStockValue_withMultiplePrices()
        *********************
        Tâche 1.2 : Tests des Transitions de Statut
        Tester les workflows de validation :

        Vérifier que la validation d'un bon de sortie (passage de BROUILLON à VALIDÉ) déclenche automatiquement :

        La création des mouvements de stock correspondants

        La mise à jour des quantités restantes dans les lots

        L'enregistrement des informations de validation (utilisateur, date)
    */

    @Mock
    private ExitSlipRepository exitSlipRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockSlotRepository stockSlotRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ExitSlipMapper exitSlipMapper;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private ExitSlipServiceImpl exitSlipService;

    @InjectMocks
    private OrderServiceImpl orderService;


    // Tâche 1.1.A: Mécanisme FIFO
    @Test
    void testWithdraw_Scenario1_PartialSingleLot() {
        // Arrange: Create a test product and single stock slot with 100 units
        Product testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setReference("TEST-001");
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setUnitPrice(100.0);
        testProduct.setCategory("Test Category");
        testProduct.setMeasureUnit("pcs");
        testProduct.setReorderPoint(10.0);
        testProduct.setCurrentStock(0.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(testProduct);
        slot.setQuantity(100.0);
        slot.setAvailableQuantity(100.0);
        slot.setUnitPrice(15.50);
        slot.setEntryDate(LocalDateTime.now().minusDays(1));

        // Update product stock
        testProduct.setCurrentStock(100.0);

        // Create exit slip
        ExitSlip exitSlip = createMockExitSlip(1L, ExitSlipStatus.DRAFT);
        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(testProduct);
        item.setRequestedQuantity(BigDecimal.valueOf(40.0));
        item.setExitSlip(exitSlip);
        exitSlip.setItems(List.of(item));

        // Mock repository behaviors
        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(exitSlip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(testProduct, 0.0))
                .thenReturn(List.of(slot));
        when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exitSlipMapper.toResponse(any(ExitSlip.class))).thenAnswer(invocation -> {
            ExitSlip es = invocation.getArgument(0);
            return createMockExitSlipResponse(es);
        });

        // Act: Validate the exit slip
        ExitSlipResponse validatedSlip = exitSlipService.validateExitSlip(1L);

        // Assert: Check status is validated
        assertEquals(ExitSlipStatus.VALIDATED, validatedSlip.getStatus());

        // Assert: Verify slot available quantity is reduced to 60
        ArgumentCaptor<StockSlot> slotCaptor = ArgumentCaptor.forClass(StockSlot.class);

        verify(stockSlotRepository, atLeastOnce()).save(slotCaptor.capture());
        StockSlot savedSlot = slotCaptor.getValue();
        assertEquals(60.0, savedSlot.getAvailableQuantity(), 0.001);

        // Assert: Verify product current stock is reduced to 60
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertEquals(60.0, savedProduct.getCurrentStock(), 0.001);

        // Verify stock movement was created
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
        System.out.println("test finished");
    }

    @Test
    void testWithdraw_Scenario2_MultipleLots() {
        // Arrange: Create a test product and three stock slots (FIFO order)
        Product testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setReference("TEST-001");
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setUnitPrice(100.0);
        testProduct.setCategory("Test Category");
        testProduct.setMeasureUnit("pcs");
        testProduct.setReorderPoint(10.0);
        testProduct.setCurrentStock(0.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(1L);
        slot1.setProduct(testProduct);
        slot1.setQuantity(30.0);
        slot1.setAvailableQuantity(30.0);
        slot1.setUnitPrice(100.0);
        slot1.setEntryDate(LocalDateTime.now().minusDays(3)); // Oldest
        //System.out.println(slot1.getAvailableQuantity());

        StockSlot slot2 = new StockSlot();
        slot2.setId(2L);
        slot2.setProduct(testProduct);
        slot2.setQuantity(50.0);
        slot2.setAvailableQuantity(50.0);
        slot2.setUnitPrice(105.0);
        slot2.setEntryDate(LocalDateTime.now().minusDays(2)); // Middle
        //System.out.println(slot2.getAvailableQuantity());

        StockSlot slot3 = new StockSlot();
        slot3.setId(3L);
        slot3.setProduct(testProduct);
        slot3.setQuantity(20.0);
        slot3.setAvailableQuantity(20.0);
        slot3.setUnitPrice(110.0);
        slot3.setEntryDate(LocalDateTime.now().minusDays(1)); // Newest
        //System.out.println(slot3.getAvailableQuantity());

        // Update product stock
        testProduct.setCurrentStock(100.0);

        // Create exit slip
        ExitSlip exitSlip = createMockExitSlip(2L, ExitSlipStatus.DRAFT);
        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(testProduct);
        item.setRequestedQuantity(BigDecimal.valueOf(60.0));
        item.setExitSlip(exitSlip);
        exitSlip.setItems(List.of(item));

        // Mock repository behaviors
        when(exitSlipRepository.findById(2L)).thenReturn(Optional.of(exitSlip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(testProduct, 0.0))
                .thenReturn(List.of(slot1, slot2, slot3));
        when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exitSlipMapper.toResponse(any(ExitSlip.class))).thenAnswer(invocation -> {
            ExitSlip es = invocation.getArgument(0);
            return createMockExitSlipResponse(es);
        });

        // Act: Validate the exit slip
        ExitSlipResponse validatedSlip = exitSlipService.validateExitSlip(2L);

        // Assert: Check status is validated
        assertEquals(ExitSlipStatus.VALIDATED, validatedSlip.getStatus());

        // Assert: Verify FIFO consumption - slot1 fully consumed (30), slot2 partially (30 out of 50)
        assertEquals(0.0, slot1.getAvailableQuantity(), 0.001, "First slot should be fully consumed");
        assertEquals(20.0, slot2.getAvailableQuantity(), 0.001, "Second slot should have 20 remaining");
        assertEquals(20.0, slot3.getAvailableQuantity(), 0.001, "Third slot should be untouched");
        //System.out.println(slot1.getAvailableQuantity());
        //System.out.println(slot2.getAvailableQuantity());
        //System.out.println(slot3.getAvailableQuantity());

        // Verify product current stock is reduced to 40
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertEquals(40.0, savedProduct.getCurrentStock(), 0.001);

        // Verify stock movements were created (2 movements: from slot1 and slot2)
        verify(stockMovementRepository, times(2)).save(any(StockMovement.class));
    }

    @Test
    void testWithdraw_Scenario3_InsufficientStock() {
        // Arrange: Create a test product and single stock slot with only 50 units
        Product testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setReference("TEST-001");
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setUnitPrice(100.0);
        testProduct.setCategory("Test Category");
        testProduct.setMeasureUnit("pcs");
        testProduct.setReorderPoint(10.0);
        testProduct.setCurrentStock(0.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(testProduct);
        slot.setQuantity(50.0);
        slot.setAvailableQuantity(50.0);
        slot.setUnitPrice(100.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(1));
        System.out.println("1- " + slot.getAvailableQuantity());

        // Update product stock
        testProduct.setCurrentStock(50.0);
        System.out.println("2- " + testProduct.getCurrentStock());

        // Create exit slip requesting 100 units (more than available)
        ExitSlip exitSlip = createMockExitSlip(3L, ExitSlipStatus.DRAFT);
        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(testProduct);
        item.setRequestedQuantity(BigDecimal.valueOf(100.0));
        item.setExitSlip(exitSlip);
        exitSlip.setItems(List.of(item));

        // Mock repository behaviors
        when(exitSlipRepository.findById(3L)).thenReturn(Optional.of(exitSlip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(testProduct, 0.0))
                .thenReturn(List.of(slot));

        // Act & Assert: Validation should throw an exception
        InsufficientStockException exception = assertThrows(InsufficientStockException.class,
                () -> exitSlipService.validateExitSlip(3L));

        // Assert: Exception message should mention insufficient stock
        assertTrue(exception.getMessage().contains("Insufficient stock"));
        try {
            exitSlipService.validateExitSlip(3L);
        }catch (InsufficientStockException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("3- " + slot.getAvailableQuantity());

        // Assert: Verify no stock was saved (transaction should rollback)
        verify(stockSlotRepository, never()).save(any(StockSlot.class));
        verify(productRepository, never()).save(any(Product.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));

        // Slot and product quantities remain unchanged (verified by not calling save)
        assertEquals(50.0, slot.getAvailableQuantity(), 0.001);
        assertEquals(50.0, testProduct.getCurrentStock(), 0.001);
        System.out.println("4- " + slot.getAvailableQuantity());
        System.out.println("5- " + testProduct.getCurrentStock());
    }

    @Test
    void testWithdraw_Scenario4_ExactExhaustion() {
        // Arrange: Create a test product and two stock slots totaling exactly 100 units
        Product testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setReference("TEST-001");
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setUnitPrice(100.0);
        testProduct.setCategory("Test Category");
        testProduct.setMeasureUnit("pcs");
        testProduct.setReorderPoint(10.0);
        testProduct.setCurrentStock(0.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(1L);
        slot1.setProduct(testProduct);
        slot1.setQuantity(70.0);
        slot1.setAvailableQuantity(70.0);
        slot1.setUnitPrice(100.0);
        slot1.setEntryDate(LocalDateTime.now().minusDays(2)); // Older

        StockSlot slot2 = new StockSlot();
        slot2.setId(2L);
        slot2.setProduct(testProduct);
        slot2.setQuantity(30.0);
        slot2.setAvailableQuantity(30.0);
        slot2.setUnitPrice(105.0);
        slot2.setEntryDate(LocalDateTime.now().minusDays(1)); // Newer

        // Update product stock
        testProduct.setCurrentStock(slot1.getQuantity()+slot2.getQuantity());
        System.out.println(testProduct.getCurrentStock());

        // Create exit slip requesting exactly 100 units
        ExitSlip exitSlip = createMockExitSlip(4L, ExitSlipStatus.DRAFT);
        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(testProduct);
        item.setRequestedQuantity(BigDecimal.valueOf(100.0));
        item.setExitSlip(exitSlip);
        exitSlip.setItems(List.of(item));

        // Mock repository behaviors
        when(exitSlipRepository.findById(4L)).thenReturn(Optional.of(exitSlip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(testProduct, 0.0))
                .thenReturn(List.of(slot1, slot2));
        when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exitSlipMapper.toResponse(any(ExitSlip.class))).thenAnswer(invocation -> {
            ExitSlip es = invocation.getArgument(0);
            return createMockExitSlipResponse(es);
        });

        // Act: Validate the exit slip
        ExitSlipResponse validatedSlip = exitSlipService.validateExitSlip(4L);

        // Assert: Check status is validated
        assertEquals(ExitSlipStatus.VALIDATED, validatedSlip.getStatus());

        // Assert: Both slots should be fully exhausted
        assertEquals(0.0, slot1.getAvailableQuantity(), 0.001, "First slot should be fully exhausted");
        assertEquals(0.0, slot2.getAvailableQuantity(), 0.001, "Second slot should be fully exhausted");

        // Verify product current stock is reduced to 0
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertEquals(0.0, savedProduct.getCurrentStock(), 0.001);

        // Verify stock movements were created (2 movements: from both slots)
        verify(stockMovementRepository, times(2)).save(any(StockMovement.class));
        System.out.println(testProduct.getCurrentStock());
    }

    // Helper methods to create mock objects
    private ExitSlip createMockExitSlip(Long id, ExitSlipStatus status) {
        ExitSlip exitSlip = new ExitSlip();
        exitSlip.setId(id);
        exitSlip.setSlipNumber("BS-TEST-" + String.format("%04d", id));
        exitSlip.setExitDate(LocalDateTime.now());
        exitSlip.setDestinationWorkshop("Test Workshop");
        exitSlip.setReason(ExitReason.PRODUCTION);
        exitSlip.setStatus(status);
        exitSlip.setCreatedBy("SYSTEM");
        exitSlip.setItems(new ArrayList<>());
        return exitSlip;
    }

    private ExitSlipResponse createMockExitSlipResponse(ExitSlip exitSlip) {
        ExitSlipResponse response = new ExitSlipResponse();
        response.setId(exitSlip.getId());
        response.setSlipNumber(exitSlip.getSlipNumber());
        response.setExitDate(exitSlip.getExitDate());
        response.setDestinationWorkshop(exitSlip.getDestinationWorkshop());
        response.setReason(exitSlip.getReason());
        response.setStatus(exitSlip.getStatus());
        response.setComment(exitSlip.getComment());
        response.setCreatedBy(exitSlip.getCreatedBy());
        response.setValidatedBy(exitSlip.getValidatedBy());
        response.setValidatedAt(exitSlip.getValidatedAt());
        return response;
    }

    // Tâche 1.1.B: Création Automatique de Lot
    @Test
    void testProcessReception_createsLotAndMovement() {
        // Arrange: Create an order with items
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSocialReason("Test Supplier");

        Product product1 = new Product();
        product1.setId(1L);
        product1.setReference("PROD-001");
        product1.setName("Product 1");
        product1.setUnitPrice(50.0);
        product1.setCurrentStock(0.0);
        product1.setCategory("Category A");
        product1.setMeasureUnit("pcs");
        product1.setReorderPoint(10.0);

        Product product2 = new Product();
        product2.setId(2L);
        product2.setReference("PROD-002");
        product2.setName("Product 2");
        product2.setUnitPrice(75.0);
        product2.setCurrentStock(10.0);
        product2.setCategory("Category B");
        product2.setMeasureUnit("pcs");
        product2.setReorderPoint(5.0);

        Order order = new Order();
        order.setId(1L);
        order.setSupplier(supplier);
        order.setStatus(Order.OrderStatus.pending);
        order.setTotalAmount(500.0);

        OrderItem item1 = new OrderItem();
        item1.setId(1L);
        item1.setOrder(order);
        item1.setProduct(product1);
        item1.setQuantity(50.0);
        item1.setUnitPrice(50.0);
        item1.setTotal(2500.0);

        OrderItem item2 = new OrderItem();
        item2.setId(2L);
        item2.setOrder(order);
        item2.setProduct(product2);
        item2.setQuantity(20.0);
        item2.setUnitPrice(75.0);
        item2.setTotal(1500.0);

        order.setItems(List.of(item1, item2));

        // Mock repository behaviors
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockSlotRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<StockSlot> slots = invocation.getArgument(0);
            // Simulate @PrePersist (dateEntry) behavior and assign IDs
            for (int i = 0; i < slots.size(); i++) {
                StockSlot slot = slots.get(i);
                slot.setId((long) (i + 1));
                // Trigger @PrePersist (to set entry date manually since mocks don't trigger it
                if (slot.getEntryDate() == null) {
                    slot.setEntryDate(LocalDateTime.now());
                }
            }
            return slots;
        });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(1L);
            return movement;
        });

        // Act: Receive the order (process reception)
        orderService.receiveOrder(1L);

        System.out.println(order.getStatus());
        System.out.println(order.getItems());

        // Assert: Verify order status changed to delivered
        assertEquals(Order.OrderStatus.delivered, order.getStatus());

        // Assert: Verify stock slots were saved
        verify(stockSlotRepository, times(1)).saveAll(anyList());

        // Assert: Verify the slots that were created and attached to the order
        List<StockSlot> createdSlots = order.getStockSlot();
        assertNotNull(createdSlots, "Stock slots should be attached to order");
        assertEquals(2, createdSlots.size(), "Two stock slots should be created (one per order item)");

        // Verify first slot properties
        StockSlot createdSlot1 = createdSlots.get(0);
        assertEquals(product1, createdSlot1.getProduct(), "Slot 1 should reference product 1");
        assertEquals(50.0, createdSlot1.getQuantity(), 0.001, "Slot 1 quantity should match order item");
        assertEquals(50.0, createdSlot1.getAvailableQuantity(), 0.001, "Slot 1 all quantity should be available");
        assertEquals(50.0, createdSlot1.getUnitPrice(), 0.001, "Slot 1 price should match order item");
        assertEquals(order, createdSlot1.getOrder(), "Slot 1 should reference the order");
        assertNotNull(createdSlot1.getEntryDate(), "Slot 1 should have an entry date");

        // Verify second slot properties
        StockSlot createdSlot2 = createdSlots.get(1);
        assertEquals(product2, createdSlot2.getProduct(), "Slot 2 should reference product 2");
        assertEquals(20.0, createdSlot2.getQuantity(), 0.001, "Slot 2 quantity should match order item");
        assertEquals(20.0, createdSlot2.getAvailableQuantity(), 0.001, "Slot 2 all quantity should be available");
        assertEquals(75.0, createdSlot2.getUnitPrice(), 0.001, "Slot 2 price should match order item");
        assertEquals(order, createdSlot2.getOrder(), "Slot 2 should reference the order");
        assertNotNull(createdSlot2.getEntryDate(), "Slot 2 should have an entry date");

        // Assert: Verify product stocks were updated (2 products)
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(2)).save(productCaptor.capture());

        List<Product> savedProducts = productCaptor.getAllValues();

        savedProducts.forEach(System.out::println);

        // Product 1 stock should increase from 0 to 50
        assertEquals(50.0, savedProducts.get(0).getCurrentStock(), 0.001);
        // Product 2 stock should increase from 10 to 30
        assertEquals(30.0, savedProducts.get(1).getCurrentStock(), 0.001);

        // Assert: Verify stock movements were created (2 movements of type IN)
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
        List<StockMovement> savedMovements = movementCaptor.getAllValues();

        // Verify first movement
        StockMovement movement1 = savedMovements.get(0);
        assertEquals(StockMovement.Type.in, movement1.getType());
        assertEquals(50.0, movement1.getQuantity(), 0.001);
        assertEquals(product1, movement1.getProduct());
        assertNotNull(movement1.getStockSlot());
        assertEquals(order, movement1.getOrder());

        System.out.println(movement1.getType());

        // Verify second movement
        StockMovement movement2 = savedMovements.get(1);
        assertEquals(StockMovement.Type.in, movement2.getType());
        assertEquals(20.0, movement2.getQuantity(), 0.001);
        assertEquals(product2, movement2.getProduct());
        assertNotNull(movement2.getStockSlot());
        assertEquals(order, movement2.getOrder());

        System.out.println(movement2.getType());

    }

    // Tâche 1.1.C: Calcul de Valorisation
    @Test
    void testCalculateStockValue_withMultiplePrices() {
        // Arrange: Create a product with multiple stock slots at different prices
        Product testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setReference("TEST-001");
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setUnitPrice(100.0);
        testProduct.setCategory("Test Category");
        testProduct.setMeasureUnit("pcs");
        testProduct.setReorderPoint(10.0);
        testProduct.setCurrentStock(100.0);

        // Create 3 stock slots with different quantities and prices
        StockSlot slot1 = new StockSlot();
        slot1.setId(1L);
        slot1.setProduct(testProduct);
        slot1.setQuantity(30.0);
        slot1.setAvailableQuantity(30.0);
        slot1.setUnitPrice(100.0);
        slot1.setEntryDate(LocalDateTime.now().minusDays(3));

        StockSlot slot2 = new StockSlot();
        slot2.setId(2L);
        slot2.setProduct(testProduct);
        slot2.setQuantity(50.0);
        slot2.setAvailableQuantity(50.0);
        slot2.setUnitPrice(105.0);
        slot2.setEntryDate(LocalDateTime.now().minusDays(2));

        StockSlot slot3 = new StockSlot();
        slot3.setId(3L);
        slot3.setProduct(testProduct);
        slot3.setQuantity(20.0);
        slot3.setAvailableQuantity(20.0);
        slot3.setUnitPrice(110.0);
        slot3.setEntryDate(LocalDateTime.now().minusDays(1));

        // Expected total value: 3,000 + 5,250 + 2,200 = 10,450
        double expectedValue = (30.0 * 100.0) + (50.0 * 105.0) + (20.0 * 110.0);
        System.out.println("Expected Stock Value: " + expectedValue);

        // Mock repository behaviors
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(testProduct, 0.0))
                .thenReturn(List.of(slot1, slot2, slot3));

        // Act: Calculate stock value
        double actualValue = exitSlipService.calculateStockValue(1L);
        System.out.println("Calculated Stock Value: " + actualValue);

        // Assert: Verify the total value is correctly calculated
        assertEquals(expectedValue, actualValue, 0.01, "Stock value should be sum of (quantity × price) for all slots");
        assertEquals(10450.0, actualValue, 0.01, "Stock value should be 10,450");

        // Verify repository was called
        verify(productRepository, times(1)).findById(1L);
        verify(stockSlotRepository, times(1))
                .findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(testProduct, 0.0);
    }

    // Tâche 1.2: Tests des Transitions de Statut
    @Test
    void testValidateExitSlip_StatusTransitionWorkflow() {
        // Arrange: Create product
        Product product = new Product();
        product.setId(1L);
        product.setReference("PROD-001");
        product.setName("Test Product");
        product.setCurrentStock(0.0);
        product.setCategory("Category1");
        product.setMeasureUnit("pcs");
        product.setReorderPoint(10.0);
        product.setUnitPrice(50.0);

        // Create stock slot (simulates order reception)
        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(product);
        slot.setQuantity(100.0);
        slot.setAvailableQuantity(100.0);
        slot.setUnitPrice(50.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(1));
        
        // Update product stock after slot creation (simulates what happens in receiveOrder)
        product.setCurrentStock(100.0);

        // Create DRAFT exit slip
        ExitSlip exitSlip = createMockExitSlip(1L, ExitSlipStatus.DRAFT);
        ExitSlipItem item = new ExitSlipItem();
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(30.0));
        item.setExitSlip(exitSlip);
        exitSlip.setItems(List.of(item));

        // Mock repositories
        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(exitSlip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(product, 0.0))
                .thenReturn(List.of(slot));
        when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exitSlipMapper.toResponse(any(ExitSlip.class))).thenAnswer(inv -> createMockExitSlipResponse(inv.getArgument(0)));

        // Act: Validate exit slip
        ExitSlipResponse response = exitSlipService.validateExitSlip(1L);

        // Assert 1: Status transition DRAFT -> VALIDATED
        assertEquals(ExitSlipStatus.VALIDATED, response.getStatus(), "Status should be VALIDATED");

        // Assert 2: Validation metadata recorded
        assertNotNull(exitSlip.getValidatedAt(), "Validation timestamp should be set");
        assertNotNull(exitSlip.getValidatedBy(), "Validation user should be set");
        assertEquals("SYSTEM", exitSlip.getValidatedBy(), "Validated by should be SYSTEM");

        // Assert 3: Stock movements created
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertEquals(StockMovement.Type.out, movement.getType(), "Movement type should be OUT");
        assertEquals(-30.0, movement.getQuantity(), 0.001, "Movement quantity should be negative");
        assertEquals(product, movement.getProduct(), "Movement should reference product");
        assertEquals(slot, movement.getStockSlot(), "Movement should reference stock slot");

        // Assert 4: Stock slot quantities updated
        assertEquals(70.0, slot.getAvailableQuantity(), 0.001, "Slot available quantity should be reduced");
        verify(stockSlotRepository, times(1)).save(slot);

        // Assert 5: Product stock updated
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(1)).save(productCaptor.capture());
        assertEquals(70.0, productCaptor.getValue().getCurrentStock(), 0.001, "Product stock should be reduced");
    }
}
