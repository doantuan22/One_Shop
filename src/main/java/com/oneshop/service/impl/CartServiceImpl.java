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
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.ProductRepository;
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
import java.util.stream.Collectors;

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
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final TransactionTemplate transaction;

    @org.springframework.beans.factory.annotation.Autowired
    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                           StoreProductRepository storeProductRepository, ProductRepository productRepository,
                           UserRepository userRepository, ProductService productService,
                           PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.storeProductRepository = storeProductRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                           StoreProductRepository storeProductRepository, UserRepository userRepository,
                           ProductService productService, PlatformTransactionManager transactionManager) {
        this(cartRepository, cartItemRepository, storeProductRepository, null, userRepository, productService, transactionManager);
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
        if (selection.items().stream().anyMatch(item -> !item.selectable())) {
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
            // UQ_carts_user or UQ_cart_items_cart_product: a parallel request of the same customer created the
            // cart or the line first. It is committed now, so this second attempt finds it and adds to it.
            transaction.executeWithoutResult(status -> doAddItem(customerEmail, request));
        }
    }

    private void doAddItem(String customerEmail, AddCartItemRequest request) {
        int quantity = requirePositive(request.quantity());
        Product product = null;
        StoreProduct storeProduct = null;

        if (request.productId() != null) {
            product = productRepository.findVisibleById(request.productId()).orElse(null);
            if (product == null) {
                throw new BadRequestException("productId", NOT_ON_SALE);
            }
            if (request.storeProductId() != null) {
                storeProduct = storeProductRepository.findSellableById(request.storeProductId()).orElse(null);
                if (storeProduct == null) {
                    throw new BadRequestException("storeProductId", NOT_ON_SALE);
                }
            }
        } else if (request.storeProductId() != null) {
            storeProduct = storeProductRepository.findSellableById(request.storeProductId()).orElse(null);
            if (storeProduct == null) {
                throw new BadRequestException("storeProductId", NOT_ON_SALE);
            }
            product = storeProduct.getProduct();
        } else {
            throw new BadRequestException("Thiếu thông tin sản phẩm.");
        }

        if (product == null && storeProduct != null) {
            product = storeProduct.getProduct();
        }
        if (product == null) {
            throw new BadRequestException("Sản phẩm không hợp lệ hoặc đã ngừng bán.");
        }

        User user = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + customerEmail));
        Cart cart = cartRepository.findByUserIdForUpdate(user.getId()).orElseGet(() -> createCart(user));
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
        if (item == null && storeProduct != null) {
            item = cartItemRepository.findByCartIdAndStoreProductId(cart.getId(), storeProduct.getId()).orElse(null);
        }
        long resulting = (item == null ? 0L : item.getQuantity()) + quantity;

        int availableStock;
        if (storeProduct != null) {
            availableStock = storeProduct.getQuantity() - storeProduct.getReservedQuantity();
            if (availableStock <= 0) {
                throw new BadRequestException("quantity", OUT_OF_STOCK);
            }
            if (resulting > availableStock) {
                throw new BadRequestException("quantity", item == null ? NOT_ENOUGH : NOT_ENOUGH_WITH_CART);
            }
        } else {
            availableStock = getChainAvailableStock(product.getId());
            if (availableStock <= 0) {
                throw new BadRequestException("quantity", OUT_OF_STOCK);
            }
            if (resulting > availableStock) {
                throw new BadRequestException("quantity", item == null ? NOT_ENOUGH : NOT_ENOUGH_WITH_CART);
            }
        }

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            if (storeProduct != null) {
                item.setStoreProduct(storeProduct);
            } else {
                List<StoreProduct> sellable = storeProductRepository.findSellableByProductIds(List.of(product.getId()));
                if (!sellable.isEmpty()) {
                    item.setStoreProduct(sellable.get(0));
                }
            }
        } else {
            if (item.getStoreProduct() == null && storeProduct != null) {
                item.setStoreProduct(storeProduct);
            }
        }
        item.setQuantity((int) resulting);
        cartItemRepository.saveAndFlush(item);
    }

    private int getChainAvailableStock(Long productId) {
        return storeProductRepository.findSellableByProductIds(List.of(productId)).stream()
                .mapToInt(sp -> Math.max(0, sp.getQuantity() - sp.getReservedQuantity()))
                .sum();
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
        Product product = item.getProduct();

        if (productRepository.findVisibleById(product.getId()).isEmpty()) {
            throw new BadRequestException("quantity", NOT_ON_SALE + " Bạn có thể xóa sản phẩm khỏi giỏ.");
        }

        StoreProduct storeProduct = item.getStoreProduct();
        int available;
        if (storeProduct != null && storeProductRepository.findSellableIdsIn(List.of(storeProduct.getId())).contains(storeProduct.getId())) {
            available = storeProduct.getQuantity() - storeProduct.getReservedQuantity();
        } else {
            available = getChainAvailableStock(product.getId());
        }

        if (available <= 0) {
            throw new BadRequestException("quantity", OUT_OF_STOCK);
        }
        if (quantity > available) {
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

        List<Long> productIds = items.stream().map(item -> item.getProduct().getId()).distinct().toList();
        Map<Long, String> images = productService.getPrimaryImageUrls(productIds);

        List<StoreProduct> sellableStoreProducts = storeProductRepository.findSellableByProductIds(productIds);
        Map<Long, List<StoreProduct>> sellableByProduct = sellableStoreProducts.stream()
                .collect(Collectors.groupingBy(sp -> sp.getProduct().getId()));

        List<CartItemResponse> flatItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = item.getProduct();
            List<StoreProduct> storeProductsForSku = sellableByProduct.getOrDefault(product.getId(), List.of());

            int chainAvailableStock = storeProductsForSku.stream()
                    .mapToInt(sp -> Math.max(0, sp.getQuantity() - sp.getReservedQuantity()))
                    .sum();

            boolean productVisible = product.getStatus() == ProductStatus.ACTIVE
                    && (product.getCategory() == null || product.getCategory().getStatus() == VisibilityStatus.ACTIVE)
                    && (product.getBrand() == null || product.getBrand().getStatus() == VisibilityStatus.ACTIVE);

            CartItemStatus status;
            if (!productVisible) {
                status = CartItemStatus.UNAVAILABLE;
            } else if (chainAvailableStock <= 0) {
                status = CartItemStatus.OUT_OF_STOCK;
            } else if (chainAvailableStock < item.getQuantity()) {
                status = CartItemStatus.INSUFFICIENT_STOCK;
            } else {
                status = CartItemStatus.AVAILABLE;
            }

            BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
            BigDecimal originalPrice = product.getOriginalPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            Long storeProductId = item.getStoreProduct() != null ? item.getStoreProduct().getId() : null;
            if (storeProductId == null && !storeProductsForSku.isEmpty()) {
                storeProductId = storeProductsForSku.get(0).getId();
            }

            CartItemResponse response = new CartItemResponse(
                    item.getId(),
                    storeProductId,
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    images.get(product.getId()),
                    unitPrice,
                    originalPrice,
                    item.getQuantity(),
                    subtotal,
                    status
            );
            flatItems.add(response);

            if (response.selectable()) {
                total = total.add(subtotal);
            }
        }

        // Build legacy groups for backwards compatibility with V2 checkout and tests
        Map<Store, List<CartItemResponse>> byStore = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            CartItemResponse response = flatItems.get(i);
            Store store = null;
            if (item.getStoreProduct() != null && item.getStoreProduct().getStore() != null) {
                store = item.getStoreProduct().getStore();
            } else {
                List<StoreProduct> sps = sellableByProduct.get(item.getProduct().getId());
                if (sps != null && !sps.isEmpty()) {
                    store = sps.get(0).getStore();
                }
            }
            if (store != null) {
                byStore.computeIfAbsent(store, s -> new ArrayList<>()).add(response);
            }
        }

        List<CartStoreGroupResponse> groups = byStore.entrySet().stream()
                .map(entry -> new CartStoreGroupResponse(
                        entry.getKey().getId(),
                        entry.getKey().getName(),
                        entry.getKey().getAddress(),
                        entry.getKey().getStatus() == ActiveStatus.ACTIVE,
                        entry.getKey().isDeliveryEnabled(),
                        entry.getKey().isPickupEnabled(),
                        entry.getValue(),
                        purchasableSum(entry.getValue())))
                .toList();

        return new CartResponse(flatItems, flatItems.size(), total, groups);
    }

    private static BigDecimal purchasableSum(List<CartItemResponse> items) {
        return items.stream().filter(CartItemResponse::selectable).map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
