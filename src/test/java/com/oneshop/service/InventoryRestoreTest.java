package com.oneshop.service;

import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.*;
import com.oneshop.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryRestoreTest {
    private final StoreProductRepository stocks = mock(StoreProductRepository.class);
    private final InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final InventoryService inventory = new InventoryServiceImpl(stocks, movements, mock(UserRepository.class), items);
    private Order order;
    private StoreProduct first;
    private StoreProduct second;

    @BeforeEach
    void setup() {
        Store store = new Store();
        ReflectionTestUtils.setField(store, "id", 1L);
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setStore(store);
        first = stock(2L, store);
        second = stock(4L, store);
        when(items.findByOrderId(100L)).thenReturn(List.of(item(12L, second), item(11L, first)));
        when(stocks.findAllByIdForUpdate(List.of(2L, 4L))).thenReturn(List.of(first, second));
    }

    private StoreProduct stock(long id, Store store) {
        StoreProduct stock = new StoreProduct();
        ReflectionTestUtils.setField(stock, "id", id);
        stock.setStore(store);
        stock.setQuantity(8);
        return stock;
    }
    private OrderItem item(long id, StoreProduct stock) {
        OrderItem item = new OrderItem();
        ReflectionTestUtils.setField(item, "id", id);
        item.setStoreProduct(stock);
        item.setQuantity(2);
        return item;
    }

    @Test
    void restoreLocksAscendingIdsBeforeWritingAndAuditsPositiveChanges() {
        inventory.restoreForCancelledOrder(order);
        var sequence = inOrder(stocks, movements);
        sequence.verify(stocks).findAllByIdForUpdate(List.of(2L, 4L));
        sequence.verify(movements, times(2)).save(any());
        assertThat(first.getQuantity()).isEqualTo(10);
        assertThat(second.getQuantity()).isEqualTo(10);
    }

    @Test
    void missingStockRefusesBeforeAnyWrite() {
        when(stocks.findAllByIdForUpdate(List.of(2L, 4L))).thenReturn(List.of(first));
        assertThatThrownBy(() -> inventory.restoreForCancelledOrder(order)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(movements);
        assertThat(first.getQuantity()).isEqualTo(8);
    }

    @Test
    void noItemsIsRefused() {
        when(items.findByOrderId(100L)).thenReturn(List.of());
        assertThatThrownBy(() -> inventory.restoreForCancelledOrder(order)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(stocks, movements);
    }

    @Test
    void stockIntegerOverflowIsRefused() {
        first.setQuantity(Integer.MAX_VALUE);
        assertThatThrownBy(() -> inventory.restoreForCancelledOrder(order)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(movements);
    }
}
