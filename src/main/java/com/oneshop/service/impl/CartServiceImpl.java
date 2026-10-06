package com.oneshop.service.impl;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartItemStatus;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CartStoreGroupResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.Product;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.ProductService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CartServiceImpl implements CartService {

    private static final String NOT_ON_SALE = "Sản phẩm này hiện không còn bán tại chi nhánh đó.";
    private static final String OUT_OF_STOCK = "Sản phẩm này đã hết hàng tại chi nhánh đó.";
    private static final String NOT_ENOUGH = "Chi nhánh không đủ hàng cho số lượng bạn chọn. Vui lòng giảm số lượng.";
    private static final String NOT_ENOUGH_WITH_CART =
            "Chi nhánh không đủ hàng để thêm số lượng này vào số đã có trong giỏ. Vui lòng giảm số lượng.";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final StoreProductRepository storeProductRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final TransactionTemplate transaction;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                           StoreProductRepository storeProductRepository, UserRepository userRepository,
                           ProductService productService, PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.storeProductRepository = storeProductRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ read

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(String customerEmail) {
        return cartRepository.findByUserEmail(customerEmail)
                .map(cart -> toResponse(cartItemRepository.findByCartIdOrderByIdAsc(cart.getId())))
                .orElseGet(CartResponse::empty);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getSelection(String customerEmail, Collection<Long> cartItemIds) {
        Set<Long> wanted = new LinkedHashSet<>(cartItemIds == null ? List.of() : cartItemIds);
        wanted.remove(null);
        if (wanted.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất một sản phẩm để đặt hàng.");
        }
        List<CartItem> own = cartRepository.findByUserEmail(customerEmail)
                .map(cart -> cartItemRepository.findByCartIdOrderByIdAsc(cart.getId()))
                .orElseGet(List::of);
        List<CartItem> chosen = own.stream().filter(item -> wanted.contains(item.getId())).toList();
        if (chosen.size() != wanted.size()) {
            // an id that is not a line of this customer's cart: never looked up anywhere else
            throw new BadRequestException("Có sản phẩm đã chọn không còn trong giỏ hàng của bạn. Vui lòng chọn lại.");
        }
        CartResponse selection = toResponse(chosen);
        if (selection.groups().stream().flatMap(group -> group.items().stream()).anyMatch(item -> !item.selectable())) {
            throw new BadRequestException(
                    "Có sản phẩm đã chọn hiện không thể mua (hết hàng, không đủ hàng hoặc ngừng bán). Vui lòng kiểm tra lại giỏ hàng.");
        }
        return selection;
    }

    // ------------------------------------------------------------------ write

    @Override
    public void addItem(String customerEmail, AddCartItemRequest request) {
        try {
            transaction.executeWithoutResult(status -> doAddItem(customerEmail, request));
        } catch (DataIntegrityViolationException ex) {
            // UQ_carts_user or UQ_cart_items_cart_store_product: a parallel request of the same customer created the
            // cart or the line first. It is committed now, so this second attempt finds it and adds to it.
            transaction.executeWithoutResult(status -> doAddItem(customerEmail, request));
        }
    }

    private void doAddItem(String customerEmail, AddCartItemRequest request) {
        int quantity = requirePositive(request.quantity());
        StoreProduct storeProduct = request.storeProductId() == null
                ? null
                : storeProductRepository.findSellableById(request.storeProductId()).orElse(null);
        if (storeProduct == null) {
            throw new BadRequestException("storeProductId", NOT_ON_SALE);
        }

        User user = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + customerEmail));
        Cart cart = cartRepository.findByUserIdForUpdate(user.getId()).orElseGet(() -> createCart(user));
        CartItem item = cartItemRepository.findByCartIdAndStoreProductId(cart.getId(), storeProduct.getId()).orElse(null);
        long resulting = (item == null ? 0L : item.getQuantity()) + quantity;

        // a check against the stock of this moment, not a reservation: checkout checks again under a lock
        if (storeProduct.getQuantity() <= 0) {
            throw new BadRequestException("quantity", OUT_OF_STOCK);
        }
        if (resulting > storeProduct.getQuantity()) {
            throw new BadRequestException("quantity", item == null ? NOT_ENOUGH : NOT_ENOUGH_WITH_CART);
        }

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setStoreProduct(storeProduct);
        }
        item.setQuantity((int) resulting);
        cartItemRepository.saveAndFlush(item);
    }

    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.saveAndFlush(cart);
    }

    @Override
    @Transactional
    public void updateItemQuantity(String customerEmail, Long cartItemId, int quantity) {
        requirePositive(quantity);
        CartItem item = ownItem(customerEmail, cartItemId);
        StoreProduct storeProduct = item.getStoreProduct();

        if (storeProductRepository.findSellableIdsIn(List.of(storeProduct.getId())).isEmpty()) {
            throw new BadRequestException("quantity", NOT_ON_SALE + " Bạn có thể xóa sản phẩm khỏi giỏ.");
        }
        if (storeProduct.getQuantity() <= 0) {
            throw new BadRequestException("quantity", OUT_OF_STOCK);
        }
        if (quantity > storeProduct.getQuantity()) {
            throw new BadRequestException("quantity", NOT_ENOUGH);
        }
        item.setQuantity(quantity);
    }

    @Override
    @Transactional
    public void removeItem(String customerEmail, Long cartItemId) {
        cartItemRepository.delete(ownItem(customerEmail, cartItemId));
    }

    /** The line, only if it is in the cart of this customer; the cart row is locked for the rest of the transaction. */
    private CartItem ownItem(String customerEmail, Long cartItemId) {
        return userRepository.findByEmail(customerEmail)
                .flatMap(user -> cartRepository.findByUserIdForUpdate(user.getId()))
                .flatMap(cart -> cartItemRepository.findByIdAndCartId(cartItemId, cart.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm này trong giỏ hàng của bạn."));
    }

    private static int requirePositive(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BadRequestException("quantity", "Số lượng tối thiểu là 1.");
        }
        return quantity;
    }

    // ------------------------------------------------------------------ view model

    /** Groups lines by the Store of their StoreProduct and judges each one against the current data. */
    private CartResponse toResponse(List<CartItem> items) {
        if (items.isEmpty()) {
            return CartResponse.empty();
        }
        Set<Long> onSale = new HashSet<>(storeProductRepository.findSellableIdsIn(
                items.stream().map(item -> item.getStoreProduct().getId()).toList()));
        Map<Long, String> images = productService.getPrimaryImageUrls(
                items.stream().map(item -> item.getStoreProduct().getProduct().getId()).distinct().toList());

        Map<Store, List<CartItemResponse>> byStore = new LinkedHashMap<>();
        items.stream()
                .sorted(Comparator.comparing((CartItem item) -> item.getStoreProduct().getStore().getName())
                        .thenComparing(CartItem::getId))
                .forEach(item -> byStore.computeIfAbsent(item.getStoreProduct().getStore(), store -> new ArrayList<>())
                        .add(toItem(item, onSale, images)));

        List<CartStoreGroupResponse> groups = byStore.entrySet().stream()
                .map(entry -> new CartStoreGroupResponse(entry.getKey().getId(), entry.getKey().getName(),
                        entry.getKey().getAddress(), entry.getKey().getStatus() == ActiveStatus.ACTIVE,
                        entry.getKey().isDeliveryEnabled(), entry.getKey().isPickupEnabled(),
                        entry.getValue(), purchasableSum(entry.getValue())))
                .toList();
        BigDecimal total = groups.stream().map(CartStoreGroupResponse::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(groups, items.size(), total);
    }

    private static CartItemResponse toItem(CartItem item, Set<Long> onSale, Map<Long, String> images) {
        StoreProduct storeProduct = item.getStoreProduct();
        Product product = storeProduct.getProduct();
        BigDecimal subtotal = storeProduct.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(item.getId(), storeProduct.getId(), product.getId(), product.getSku(),
                product.getName(), images.get(product.getId()), storeProduct.getPrice(), item.getQuantity(), subtotal,
                statusOf(item, onSale));
    }

    private static CartItemStatus statusOf(CartItem item, Set<Long> onSale) {
        StoreProduct storeProduct = item.getStoreProduct();
        if (!onSale.contains(storeProduct.getId())) {
            return CartItemStatus.UNAVAILABLE;
        }
        if (storeProduct.getQuantity() <= 0) {
            return CartItemStatus.OUT_OF_STOCK;
        }
        return storeProduct.getQuantity() < item.getQuantity()
                ? CartItemStatus.INSUFFICIENT_STOCK
                : CartItemStatus.AVAILABLE;
    }

    private static BigDecimal purchasableSum(List<CartItemResponse> items) {
        return items.stream().filter(CartItemResponse::selectable).map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
