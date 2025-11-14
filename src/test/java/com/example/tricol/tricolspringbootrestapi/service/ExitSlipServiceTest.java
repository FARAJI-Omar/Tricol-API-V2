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


}
