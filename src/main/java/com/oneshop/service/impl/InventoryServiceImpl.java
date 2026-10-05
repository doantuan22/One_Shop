package com.oneshop.service.impl;

import com.oneshop.entity.InventoryMovement;
import com.oneshop.entity.InventoryMovementType;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private static final String DEFAULT_ADJUST_NOTE = "Điều chỉnh tồn kho";

    private final StoreProductRepository storeProductRepository;
    private final InventoryMovementRepository movementRepository;
    private final UserRepository userRepository;

    public InventoryServiceImpl(StoreProductRepository storeProductRepository,
                                InventoryMovementRepository movementRepository, UserRepository userRepository) {
        this.storeProductRepository = storeProductRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
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
}
