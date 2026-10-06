package com.oneshop.service.impl;

import com.oneshop.entity.InventoryMovement;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private static final String DEFAULT_ADJUST_NOTE = "Điều chỉnh tồn kho";

    private final StoreProductRepository storeProductRepository;
    private final InventoryMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;

    public InventoryServiceImpl(StoreProductRepository storeProductRepository,
                                InventoryMovementRepository movementRepository, UserRepository userRepository,
                                OrderItemRepository orderItemRepository) {
        this.storeProductRepository = storeProductRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public void adjustStock(Long storeProductId, int newQuantity, String userEmail, String note) {
        if (newQuantity < 0) {
            throw new BadRequestException("quantity", "Tồn kho không được âm");
        }
        StoreProduct storeProduct = storeProductRepository.findByIdForUpdate(storeProductId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm tại chi nhánh #" + storeProductId));
        int before = storeProduct.getQuantity();
        if (before == newQuantity) {
            return;
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userEmail));

        storeProduct.setQuantity(newQuantity);

        InventoryMovement movement = new InventoryMovement();
        movement.setStoreProduct(storeProduct);
        movement.setType(InventoryMovementType.STOCK_ADJUST);
        movement.setQuantityBefore(before);
        movement.setQuantityChange(newQuantity - before);
        movement.setQuantityAfter(newQuantity);
        movement.setStaff(user);
        movement.setNote(StringUtils.hasText(note) ? note.trim() : DEFAULT_ADJUST_NOTE);
        movementRepository.save(movement);
    }

    @Override
    public void deductForOrder(StoreProduct lockedStoreProduct, int quantity, Order order) {
        int before = lockedStoreProduct.getQuantity();
        if (quantity <= 0) {
            throw new BadRequestException("quantity", "Số lượng đặt hàng không hợp lệ.");
        }
        if (quantity > before) {
            // last line of defence in the application; CK_store_products_quantity is the one in the database
            throw new BadRequestException("quantity", "Chi nhánh không đủ hàng cho số lượng đã chọn.");
        }
        int after = before - quantity;
        lockedStoreProduct.setQuantity(after);

        InventoryMovement movement = new InventoryMovement();
        movement.setStoreProduct(lockedStoreProduct);
        movement.setType(InventoryMovementType.ORDER);
        movement.setQuantityBefore(before);
        movement.setQuantityChange(-quantity);
        movement.setQuantityAfter(after);
        movement.setReferenceOrder(order);
        movement.setNote("Checkout #" + order.getCheckoutSession().getId() + " - Order #" + order.getId());
        movementRepository.save(movement);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForCancelledOrder(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .sorted(Comparator.comparing((OrderItem item) -> item.getStoreProduct().getId())
                        .thenComparing(OrderItem::getId)).toList();
        if (items.isEmpty()) {
            throw new BadRequestException("Đơn hàng không có sản phẩm để hoàn tồn.");
        }
        List<Long> ids = items.stream().map(item -> item.getStoreProduct().getId()).distinct().sorted().toList();
        Map<Long, StoreProduct> locked = storeProductRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(StoreProduct::getId, Function.identity()));
        if (locked.size() != ids.size()) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm tại chi nhánh cần hoàn tồn.");
        }
        for (OrderItem item : items) {
            StoreProduct stock = locked.get(item.getStoreProduct().getId());
            if (item.getQuantity() <= 0 || !stock.getStore().getId().equals(order.getStore().getId())) {
                throw new BadRequestException("Sản phẩm hoàn tồn không hợp lệ với chi nhánh của đơn hàng.");
            }
            int before = stock.getQuantity();
            int after;
            try {
                after = Math.addExact(before, item.getQuantity());
            } catch (ArithmeticException ex) {
                throw new BadRequestException("Tồn kho sau hoàn vượt giới hạn cho phép.");
            }
            stock.setQuantity(after);
            InventoryMovement movement = new InventoryMovement();
            movement.setStoreProduct(stock);
            movement.setType(InventoryMovementType.CANCEL_ORDER);
            movement.setQuantityBefore(before);
            movement.setQuantityChange(item.getQuantity());
            movement.setQuantityAfter(after);
            movement.setReferenceOrder(order);
            movement.setStaff(null);
            movement.setNote("Hoàn tồn do thanh toán ONLINE thất bại - Order #" + order.getId());
            movementRepository.save(movement);
        }
    }
}
