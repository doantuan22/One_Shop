package com.oneshop.service.impl;

import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.OrderItemResponse;
import com.oneshop.dto.response.OrderSummaryResponse;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.CheckoutSession;
import com.oneshop.entity.CheckoutStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.CheckoutSessionRepository;
import com.oneshop.repository.CustomerAddressRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    private static final String CART_CHANGED =
            "Có sản phẩm đã chọn không còn trong giỏ hàng của bạn. Vui lòng kiểm tra lại giỏ hàng.";
    private static final String GROUPS_MISMATCH =
            "Thông tin nhận hàng không khớp với các chi nhánh của sản phẩm đã chọn. Vui lòng thực hiện lại từ giỏ hàng.";
    private static final String NOTE_CONFIRMED = "Hệ thống tự xác nhận đơn hợp lệ";
    private static final String NOTE_PENDING_PAYMENT = "Tạo đơn ONLINE";
    private static final String PHONE_PATTERN = "^[0-9+ ]{8,20}$";

    private final CartService cartService;
    private final InventoryService inventoryService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final StoreProductRepository storeProductRepository;
    private final UserRepository userRepository;
    private final CustomerAddressRepository addressRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;

    public CheckoutServiceImpl(CartService cartService, InventoryService inventoryService,
                               CartRepository cartRepository, CartItemRepository cartItemRepository,
                               StoreProductRepository storeProductRepository, UserRepository userRepository,
                               CustomerAddressRepository addressRepository,
                               CheckoutSessionRepository checkoutSessionRepository, OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               OrderStatusHistoryRepository historyRepository) {
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.storeProductRepository = storeProductRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
    }

    // ------------------------------------------------------------------ checkout page

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse prepare(String customerEmail, Collection<Long> cartItemIds) {
        CartResponse selection = cartService.getSelection(customerEmail, cartItemIds);
        User user = user(customerEmail);
        // only a suggestion for the form: what is ordered is what the customer submits
        return addressRepository.findByUserEmailOrderByDefaultAddressDescCreatedAtDesc(customerEmail).stream().findFirst()
                .map(address -> new CheckoutPreviewResponse(selection, address.getReceiverName(),
                        address.getReceiverPhone(), address.getAddressLine()))
                .orElseGet(() -> new CheckoutPreviewResponse(selection, user.getFullName(), user.getPhone(), null));
    }

    // ------------------------------------------------------------------ place the order

    @Override
    public CheckoutResultResponse placeOrder(String customerEmail, CheckoutRequest request) {
        Set<Long> wantedIds = new LinkedHashSet<>(request.cartItemIds() == null ? List.<Long>of() : request.cartItemIds());
        wantedIds.remove(null);
        if (wantedIds.isEmpty()) {
            throw new BadRequestException("cartItemIds", "Vui lòng chọn ít nhất một sản phẩm để đặt hàng.");
        }
        User user = user(customerEmail);

        // 1. The customer's own cart, locked: a second click or tab of the same customer waits here and then finds
        //    these lines already gone.
        Cart cart = cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new BadRequestException("cartItemIds", CART_CHANGED));
        List<CartItem> lines = cartItemRepository.findByCartId(cart.getId()).stream()
                .filter(line -> wantedIds.contains(line.getId()))
                .toList();
        if (lines.size() != wantedIds.size()) {
            // an id that is not a line of this cart (deleted, never existed, or someone else's): refuse everything
            throw new BadRequestException("cartItemIds", CART_CHANGED);
        }

        // 2. Lock the StoreProducts, always in ascending id order, and read price / stock / status under the lock.
        Map<Long, StoreProduct> locked = storeProductRepository.findAllByIdForUpdate(
                        lines.stream().map(line -> line.getStoreProduct().getId()).sorted().toList())
                .stream().collect(Collectors.toMap(StoreProduct::getId, Function.identity()));
        Set<Long> onSale = new HashSet<>(storeProductRepository.findSellableIdsIn(locked.keySet()));

        // 3. Check every line, then group by the Store of its StoreProduct (ordered by Store id).
        Map<Long, List<CartItem>> linesByStore = new TreeMap<>();
        for (CartItem line : lines) {
            StoreProduct storeProduct = locked.get(line.getStoreProduct().getId());
            requireBuyable(line, storeProduct, onSale);
            linesByStore.computeIfAbsent(storeProduct.getStore().getId(), id -> new ArrayList<>()).add(line);
        }

        // 4. The choices of the request, matched to those Stores and checked.
        Map<Long, StoreGroupCheckoutRequest> choices = choicesByStore(request, linesByStore.keySet());
        linesByStore.forEach((storeId, storeLines) ->
                requireValidChoice(locked.get(storeLines.get(0).getStoreProduct().getId()).getStore(), choices.get(storeId)));

        // 5. Everything is valid: create the session, one Order per Store, snapshots, stock movements.
        Map<Long, BigDecimal> totalByStore = new LinkedHashMap<>();
        linesByStore.forEach((storeId, storeLines) -> totalByStore.put(storeId, storeLines.stream()
                .map(line -> subtotal(locked.get(line.getStoreProduct().getId()), line))
                .reduce(BigDecimal.ZERO, BigDecimal::add)));

        CheckoutSession session = new CheckoutSession();
        session.setUser(user);
        session.setStatus(CheckoutStatus.CREATED);
        session.setTotalAmount(totalByStore.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        checkoutSessionRepository.save(session);

        linesByStore.forEach((storeId, storeLines) -> {
            StoreGroupCheckoutRequest choice = choices.get(storeId);
            Store store = locked.get(storeLines.get(0).getStoreProduct().getId()).getStore();
            Order order = orderRepository.save(newOrder(session, user, store, choice, totalByStore.get(storeId)));

            for (CartItem line : storeLines) {
                StoreProduct storeProduct = locked.get(line.getStoreProduct().getId());
                orderItemRepository.save(snapshot(order, storeProduct, line));
                inventoryService.deductForOrder(storeProduct, line.getQuantity(), order);
            }
            historyRepository.save(createdEntry(order));
        });

        // 6. Only the lines that were checked out leave the cart.
        cartItemRepository.deleteAll(lines);
        cartItemRepository.flush();

        return toResult(session);
    }

    private static void requireBuyable(CartItem line, StoreProduct storeProduct, Set<Long> onSale) {
        if (storeProduct == null || !onSale.contains(storeProduct.getId())) {
            throw new BadRequestException("cartItemIds", describe(line, storeProduct) + " hiện không còn bán. "
                    + "Vui lòng bỏ sản phẩm này khỏi lựa chọn.");
        }
        if (storeProduct.getQuantity() <= 0) {
            throw new BadRequestException("cartItemIds", describe(line, storeProduct) + " đã hết hàng. "
                    + "Vui lòng bỏ sản phẩm này khỏi lựa chọn.");
        }
        if (line.getQuantity() > storeProduct.getQuantity()) {
            throw new BadRequestException("cartItemIds", describe(line, storeProduct)
                    + " không còn đủ số lượng bạn chọn. Vui lòng giảm số lượng trong giỏ hàng.");
        }
    }

    private static String describe(CartItem line, StoreProduct storeProduct) {
        return storeProduct == null
                ? "Một sản phẩm đã chọn"
                : "\"" + storeProduct.getProduct().getName() + "\" tại " + storeProduct.getStore().getName();
    }

    /** One choice per Store that really has chosen lines; the storeId of the request is only the key to match them. */
    private static Map<Long, StoreGroupCheckoutRequest> choicesByStore(CheckoutRequest request, Set<Long> storeIds) {
        Map<Long, StoreGroupCheckoutRequest> choices = new LinkedHashMap<>();
        for (StoreGroupCheckoutRequest group : request.groups() == null ? List.<StoreGroupCheckoutRequest>of() : request.groups()) {
            if (group == null || group.storeId() == null || choices.put(group.storeId(), group) != null) {
                throw new BadRequestException("groups", GROUPS_MISMATCH);
            }
        }
        if (!choices.keySet().equals(storeIds)) {
            throw new BadRequestException("groups", GROUPS_MISMATCH);
        }
        return choices;
    }

    private static void requireValidChoice(Store store, StoreGroupCheckoutRequest choice) {
        String at = " (" + store.getName() + ")";
        FulfillmentType fulfillment = choice.fulfillmentType();
        PaymentMethod payment = choice.paymentMethod();
        if (fulfillment == null) {
            throw new BadRequestException("groups", "Vui lòng chọn cách nhận hàng" + at + ".");
        }
        if (payment == null) {
            throw new BadRequestException("groups", "Vui lòng chọn phương thức thanh toán" + at + ".");
        }
        // Roadmap V2 6.4: DELIVERY pays by COD or ONLINE, STORE_PICKUP by PAY_AT_STORE or ONLINE
        boolean allowed = fulfillment == FulfillmentType.DELIVERY
                ? payment == PaymentMethod.COD || payment == PaymentMethod.ONLINE
                : payment == PaymentMethod.PAY_AT_STORE || payment == PaymentMethod.ONLINE;
        if (!allowed) {
            throw new BadRequestException("groups", (fulfillment == FulfillmentType.DELIVERY
                    ? "Đơn giao hàng chỉ thanh toán khi nhận hàng (COD) hoặc trực tuyến"
                    : "Đơn nhận tại cửa hàng chỉ thanh toán tại cửa hàng hoặc trực tuyến") + at + ".");
        }
        if (fulfillment == FulfillmentType.DELIVERY && !store.isDeliveryEnabled()) {
            throw new BadRequestException("groups", "Chi nhánh " + store.getName() + " không hỗ trợ giao hàng.");
        }
        if (fulfillment == FulfillmentType.STORE_PICKUP && !store.isPickupEnabled()) {
            throw new BadRequestException("groups", "Chi nhánh " + store.getName() + " không hỗ trợ nhận tại cửa hàng.");
        }
        if (!StringUtils.hasText(choice.receiverName()) || choice.receiverName().trim().length() > 150) {
            throw new BadRequestException("groups", "Vui lòng nhập tên người nhận (tối đa 150 ký tự)" + at + ".");
        }
        if (choice.receiverPhone() == null || !choice.receiverPhone().trim().matches(PHONE_PATTERN)) {
            throw new BadRequestException("groups", "Số điện thoại người nhận không hợp lệ" + at + ".");
        }
        if (fulfillment == FulfillmentType.DELIVERY
                && (!StringUtils.hasText(choice.shippingAddress()) || choice.shippingAddress().trim().length() > 500)) {
            throw new BadRequestException("groups", "Vui lòng nhập địa chỉ giao hàng (tối đa 500 ký tự)" + at + ".");
        }
    }

    private static Order newOrder(CheckoutSession session, User user, Store store, StoreGroupCheckoutRequest choice,
                                  BigDecimal total) {
        Order order = new Order();
        order.setCheckoutSession(session);
        order.setUser(user);
        order.setStore(store);
        order.setFulfillmentType(choice.fulfillmentType());
        order.setPaymentMethod(choice.paymentMethod());
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        // Roadmap V2 6.4: an ONLINE order waits for its payment; COD and PAY_AT_STORE are confirmed by the system
        // straight away because stock has just been checked and taken. No Staff approval (BR-10).
        order.setOrderStatus(choice.paymentMethod() == PaymentMethod.ONLINE
                ? OrderStatus.PENDING_PAYMENT
                : OrderStatus.CONFIRMED);
        order.setReceiverName(choice.receiverName().trim());
        order.setReceiverPhone(choice.receiverPhone().trim());
        // a pickup order has no shipping address: it is collected at its own Store
        order.setShippingAddress(choice.fulfillmentType() == FulfillmentType.DELIVERY
                ? choice.shippingAddress().trim()
                : null);
        order.setTotalAmount(total);
        return order;
    }

    private static BigDecimal subtotal(StoreProduct storeProduct, CartItem line) {
        return storeProduct.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
    }

    /** Freezes what is bought: name, unit price (from the locked StoreProduct), quantity, subtotal. */
    private static OrderItem snapshot(Order order, StoreProduct storeProduct, CartItem line) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setStoreProduct(storeProduct);
        item.setProductName(storeProduct.getProduct().getName());
        item.setUnitPrice(storeProduct.getPrice());
        item.setQuantity(line.getQuantity());
        item.setSubtotal(subtotal(storeProduct, line));
        return item;
    }

    /** First timeline entry of an Order: created by the system, no previous status. */
    private static OrderStatusHistory createdEntry(Order order) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(order);
        entry.setOldStatus(null);
        entry.setNewStatus(order.getOrderStatus());
        entry.setChangedBy(null);
        entry.setNote(order.getOrderStatus() == OrderStatus.PENDING_PAYMENT ? NOTE_PENDING_PAYMENT : NOTE_CONFIRMED);
        return entry;
    }

    // ------------------------------------------------------------------ read a checkout

    @Override
    @Transactional(readOnly = true)
    public CheckoutResultResponse getCheckout(String customerEmail, Long checkoutId) {
        return toResult(checkoutSessionRepository.findByIdAndUserEmail(checkoutId, customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần đặt hàng này.")));
    }

    private CheckoutResultResponse toResult(CheckoutSession session) {
        List<Order> orders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(session.getId());
        Map<Long, List<OrderItemResponse>> itemsByOrder = orders.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdInOrderByIdAsc(orders.stream().map(Order::getId).toList()).stream()
                        .collect(Collectors.groupingBy(item -> item.getOrder().getId(), Collectors.mapping(
                                item -> new OrderItemResponse(item.getProductName(), item.getUnitPrice(),
                                        item.getQuantity(), item.getSubtotal()), Collectors.toList())));
        return new CheckoutResultResponse(session.getId(), session.getStatus(), session.getTotalAmount(),
                session.getCreatedAt(), orders.stream().map(order -> new OrderSummaryResponse(order.getId(),
                        order.getStore().getId(), order.getStore().getName(), order.getStore().getAddress(),
                        order.getFulfillmentType(), order.getPaymentMethod(), order.getPaymentStatus(),
                        order.getOrderStatus(), order.getReceiverName(), order.getReceiverPhone(),
                        order.getShippingAddress(), order.getTotalAmount(),
                        itemsByOrder.getOrDefault(order.getId(), List.of()))).toList());
    }

    private User user(String customerEmail) {
        return userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + customerEmail));
    }
}
