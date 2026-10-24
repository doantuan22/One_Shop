package com.oneshop.service.impl;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.config.PaymentProperties;
import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.AllocationPlanResponse;
import com.oneshop.dto.response.CartItemResponse;
import com.oneshop.dto.response.CartResponse;
import com.oneshop.dto.response.CheckoutPreviewResponse;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.FulfillmentSummaryResponse;
import com.oneshop.dto.response.OrderItemResponse;
import com.oneshop.dto.response.OrderSummaryResponse;
import com.oneshop.dto.response.PickupStoreOption;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.CheckoutSession;
import com.oneshop.entity.CheckoutStatus;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderFulfillmentStatusHistory;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Product;
import com.oneshop.entity.ProductStatus;
import com.oneshop.entity.Store;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.CheckoutSessionRepository;
import com.oneshop.repository.CustomerAddressRepository;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderFulfillmentStatusHistoryRepository;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.InventoryService;
import com.oneshop.service.PaymentDeadlinePolicy;
import com.oneshop.service.PaymentService;
import com.oneshop.service.StoreAllocationService;
import com.oneshop.service.allocation.AllocatedLine;
import com.oneshop.service.allocation.AllocationDemand;
import com.oneshop.service.allocation.AllocationResult;
import com.oneshop.service.allocation.DeliveryServiceAreaPolicy;
import com.oneshop.service.allocation.LockedAllocation;
import com.oneshop.service.allocation.ShippingAddressValidator;
import com.oneshop.service.allocation.ShippingFeeCalculator;
import com.oneshop.service.allocation.StoreAllocation;
import com.oneshop.service.promotion.PromotionCalculator;
import com.oneshop.service.promotion.PromotionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V3 checkout. See {@link CheckoutService} for the business rules; this class is the one transaction that applies
 * them. Lock order inside {@link #placeOrder}: the customer's Cart row, then every candidate StoreProduct of the
 * demanded SKUs in ascending id. Nothing is read from a Store, a price or a stock figure supplied by the request.
 */
@Service
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    private static final String CART_CHANGED =
            "Có sản phẩm đã chọn không còn trong giỏ hàng của bạn. Vui lòng kiểm tra lại giỏ hàng.";
    private static final String NOTE_CONFIRMED = "Hệ thống tự xác nhận đơn hợp lệ";
    private static final String NOTE_FULFILLMENT = "Phân bổ phần hàng cho chi nhánh khi tạo đơn";
    private static final String PHONE_PATTERN = "^[0-9+ ]{8,20}$";

    private final CartService cartService;
    private final StoreAllocationService allocationService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CustomerAddressRepository addressRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderFulfillmentRepository fulfillmentRepository;
    private final OrderFulfillmentStatusHistoryRepository fulfillmentHistoryRepository;
    private final StoreRepository storeRepository;
    private final ShippingFeeCalculator shippingFees;
    private final DeliveryServiceAreaPolicy areaPolicy;
    private final ShippingAddressValidator addressValidator;
    private final CheckoutProperties checkoutProperties;
    private final PaymentProperties paymentProperties;
    private final PaymentDeadlinePolicy deadlines;
    private final PromotionService promotionService;

    public CheckoutServiceImpl(CartService cartService, StoreAllocationService allocationService,
                               PaymentService paymentService, InventoryService inventoryService,
                               CartRepository cartRepository, CartItemRepository cartItemRepository,
                               UserRepository userRepository, CustomerAddressRepository addressRepository,
                               CheckoutSessionRepository checkoutSessionRepository, OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository, OrderStatusHistoryRepository historyRepository,
                               OrderFulfillmentRepository fulfillmentRepository,
                               OrderFulfillmentStatusHistoryRepository fulfillmentHistoryRepository,
                               StoreRepository storeRepository, ShippingFeeCalculator shippingFees,
                               DeliveryServiceAreaPolicy areaPolicy, CheckoutProperties checkoutProperties,
                               PaymentProperties paymentProperties, PaymentDeadlinePolicy deadlines,
                               ShippingAddressValidator addressValidator, PromotionService promotionService) {
        this.cartService = cartService;
        this.allocationService = allocationService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.fulfillmentHistoryRepository = fulfillmentHistoryRepository;
        this.storeRepository = storeRepository;
        this.shippingFees = shippingFees;
        this.areaPolicy = areaPolicy;
        this.addressValidator = addressValidator;
        this.checkoutProperties = checkoutProperties;
        this.paymentProperties = paymentProperties;
        this.deadlines = deadlines;
        this.promotionService = promotionService;
    }

    // ------------------------------------------------------------------ checkout page

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse prepare(String customerEmail, Collection<Long> cartItemIds) {
        return preview(customerEmail, cartItemIds, FulfillmentType.DELIVERY, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(String customerEmail, Collection<Long> cartItemIds,
                                           FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                           Long pickupStoreId, String shippingAddress, String promotionCode) {
        CartResponse selection = cartService.getSelection(customerEmail, cartItemIds);
        User user = user(customerEmail);
        var address = addressRepository.findByUserEmailOrderByDefaultAddressDescCreatedAtDesc(customerEmail).stream()
                .findFirst();
        String receiverName = address.map(a -> a.getReceiverName()).orElse(user.getFullName());
        String receiverPhone = address.map(a -> a.getReceiverPhone()).orElse(user.getPhone());
        String suggestedAddress = StringUtils.hasText(shippingAddress) ? shippingAddress.trim()
                : address.map(a -> a.getAddressLine()).orElse(null);

        FulfillmentType type = fulfillmentType == null ? FulfillmentType.DELIVERY : fulfillmentType;
        PaymentMethod method = paymentMethod != null ? paymentMethod
                : type == FulfillmentType.DELIVERY ? PaymentMethod.COD : PaymentMethod.PAY_AT_STORE;

        List<AllocationDemand> demands = selection.items().stream()
                .map(item -> new AllocationDemand(item.productId(), item.quantity())).toList();
        boolean onlineEnabled = checkoutProperties.isOnlineEnabled();
        String deliveryBlocked = deliveryBlockedReason();
        List<PickupStoreOption> pickupStores = allocationService.pickupStoresCovering(demands).stream()
                .map(store -> new PickupStoreOption(store.getId(), store.getName(), store.getAddress())).toList();

        AllocationPlanResponse plan = null;
        String problem = null;
        BigDecimal fee = shippingFees.feePerOrder(type).orElse(null);
        int orderCount = 1;
        BigDecimal grandTotal = null;
        boolean splitConsent = false;
        PromotionService.Applied applied = null;
        String promotionProblem = null;
        List<PromotionService.Option> promotionOptions = List.of();
        BigDecimal discount = BigDecimal.ZERO;

        if (type == FulfillmentType.DELIVERY && deliveryBlocked != null) {
            problem = deliveryBlocked;
        } else if (type == FulfillmentType.DELIVERY && StringUtils.hasText(suggestedAddress)
                && addressProblem(suggestedAddress) != null) {
            problem = addressProblem(suggestedAddress);
        } else if (type == FulfillmentType.STORE_PICKUP && pickupStoreId == null) {
            problem = pickupStores.isEmpty()
                    ? "Hiện chưa có chi nhánh nào đủ toàn bộ sản phẩm để nhận tại cửa hàng."
                    : "Vui lòng chọn chi nhánh nhận hàng.";
        } else {
            AllocationResult result = allocationService.preview(demands, type, pickupStoreId, suggestedAddress);
            if (!result.success()) {
                problem = failureMessage(result, productNames(selection));
            } else {
                plan = planOf(result, selection);
                splitConsent = method == PaymentMethod.COD && type == FulfillmentType.DELIVERY
                        && result.fulfillmentCount() > 1;
                orderCount = splitConsent ? result.fulfillmentCount() : 1;
                // the promotion takes off the goods only; the shipping fee of every Order is added after it
                promotionOptions = promotionService.options(user.getId(), plan.itemsTotal());
                PromotionService.Check check = promotionService.check(user.getId(), promotionCode, plan.itemsTotal());
                applied = check.applied();
                promotionProblem = check.problem();
                discount = applied == null ? BigDecimal.ZERO : applied.discount();
                if (fee != null) {
                    grandTotal = plan.itemsTotal().subtract(discount).add(fee.multiply(BigDecimal.valueOf(orderCount)));
                }
                // the fingerprint covers the whole plan the customer is shown: who, what, from where, how, how much
                plan = new AllocationPlanResponse(contextSignature(user.getId(), type, method, pickupStoreId,
                        suggestedAddress, demands, result, fee, orderCount, grandTotal,
                        applied == null ? null : applied.code(), discount),
                        plan.fulfillmentCount(), plan.itemsTotal(), plan.parts());
            }
        }
        return new CheckoutPreviewResponse(selection, receiverName, receiverPhone, suggestedAddress, type, method,
                pickupStoreId, onlineEnabled, deliveryBlocked == null, deliveryBlocked, pickupStores, plan, problem,
                fee, orderCount, grandTotal, splitConsent, paymentProperties.getPendingTimeoutMinutes(), applied,
                promotionProblem, promotionOptions, discount);
    }

    private AllocationPlanResponse planOf(AllocationResult result, CartResponse selection) {
        Map<Long, CartItemResponse> items = selection.items().stream()
                .collect(Collectors.toMap(CartItemResponse::productId, Function.identity(), (a, b) -> a));
        Map<Long, Store> stores = storeRepository.findAllById(result.stores().stream().map(StoreAllocation::storeId).toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        List<AllocationPlanResponse.Part> parts = new ArrayList<>();
        for (StoreAllocation allocation : result.stores()) {
            BigDecimal subtotal = BigDecimal.ZERO;
            List<AllocationPlanResponse.Line> lines = new ArrayList<>();
            for (AllocatedLine line : allocation.lines()) {
                CartItemResponse item = items.get(line.productId());
                BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
                subtotal = subtotal.add(lineTotal);
                lines.add(new AllocationPlanResponse.Line(line.productId(), item.productName(), line.quantity(),
                        item.unitPrice(), lineTotal));
            }
            Store store = stores.get(allocation.storeId());
            parts.add(new AllocationPlanResponse.Part(allocation.storeId(), store.getName(), store.getAddress(),
                    subtotal, lines));
            total = total.add(subtotal);
        }
        return new AllocationPlanResponse(result.signature(), result.fulfillmentCount(), total, parts);
    }

    /** Why DELIVERY is closed right now, or {@code null} when it is open. */
    private String deliveryBlockedReason() {
        if (!areaPolicy.isDecided()) {
            return "Giao hàng tận nơi tạm thời chưa mở (đang được tắt trong cấu hình hệ thống).";
        }
        if (shippingFees.feePerOrder(FulfillmentType.DELIVERY).isEmpty()) {
            return "Giao hàng tận nơi tạm thời chưa mở (phí giao hàng đang được tắt trong cấu hình hệ thống).";
        }
        return null;
    }

    private static Map<Long, String> productNames(CartResponse selection) {
        return selection.items().stream().collect(Collectors.toMap(CartItemResponse::productId,
                CartItemResponse::productName, (a, b) -> a));
    }

    private static String failureMessage(AllocationResult result, Map<Long, String> names) {
        String which = result.unfulfilledProductIds().stream().map(id -> "\"" + names.getOrDefault(id, "#" + id) + "\"")
                .collect(Collectors.joining(", "));
        return switch (result.failure()) {
            case OUT_OF_STOCK -> "Hiện không đủ hàng (tổng các chi nhánh có thể giao) cho: " + which
                    + ". Vui lòng giảm số lượng hoặc bỏ sản phẩm này khỏi lựa chọn.";
            case INSUFFICIENT_AT_PICKUP_STORE -> "Chi nhánh nhận hàng đã chọn không đủ: " + which
                    + ". Vui lòng chọn chi nhánh khác, điều chỉnh giỏ hàng hoặc chuyển sang giao hàng.";
            case NO_ELIGIBLE_STORE -> "Hiện chưa có chi nhánh phù hợp để phục vụ đơn này.";
            case NO_DEMAND -> "Vui lòng chọn ít nhất một sản phẩm để đặt hàng.";
        };
    }

    // ------------------------------------------------------------------ place the order

    @Override
    public CheckoutResultResponse placeOrder(String customerEmail, PlaceOrderRequest request) {
        Set<Long> wantedIds = new LinkedHashSet<>(request.cartItemIds() == null ? List.<Long>of() : request.cartItemIds());
        wantedIds.remove(null);
        if (wantedIds.isEmpty()) {
            throw new BadRequestException("cartItemIds", "Vui lòng chọn ít nhất một sản phẩm để đặt hàng.");
        }
        requireValidChoice(request);
        requireOpenFlow(request);
        FulfillmentType type = request.fulfillmentType();
        PaymentMethod method = request.paymentMethod();
        User user = user(customerEmail);

        // 1. The customer's own cart, locked: a second click or tab finds these lines already gone.
        Cart cart = cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new BadRequestException("cartItemIds", CART_CHANGED));
        List<CartItem> lines = cartItemRepository.findByCartId(cart.getId()).stream()
                .filter(line -> wantedIds.contains(line.getId()))
                .toList();
        if (lines.size() != wantedIds.size()) {
            throw new BadRequestException("cartItemIds", CART_CHANGED);
        }

        // 2. SKUs and the single online price. The StoreProduct of a cart line, if any, is deliberately not read.
        Map<Long, CartItem> lineByProduct = new LinkedHashMap<>();
        for (CartItem line : lines) {
            Product product = line.getProduct();
            if (product.getStatus() != ProductStatus.ACTIVE || product.getPrice() == null
                    || product.getPrice().signum() <= 0) {
                throw new BadRequestException("cartItemIds", "\"" + product.getName() + "\" hiện không còn bán. "
                        + "Vui lòng bỏ sản phẩm này khỏi lựa chọn.");
            }
            lineByProduct.put(product.getId(), line);
        }
        List<AllocationDemand> demands = lineByProduct.entrySet().stream()
                .map(entry -> new AllocationDemand(entry.getKey(), entry.getValue().getQuantity())).toList();

        // 3. Allocation on StoreProduct rows locked in ascending id order, read under that lock.
        LockedAllocation locked = allocationService.allocateForUpdate(demands, type, request.pickupStoreId(),
                request.shippingAddress());
        AllocationResult plan = locked.result();
        if (!plan.success()) {
            Map<Long, String> names = lineByProduct.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> entry.getValue().getProduct().getName()));
            throw new CheckoutConflictException(CheckoutConflictException.CODE_NOT_ALLOCATABLE,
                    failureMessage(plan, names));
        }

        // 4. What the customer confirmed must still be what will be created.
        boolean split = method == PaymentMethod.COD && type == FulfillmentType.DELIVERY && plan.fulfillmentCount() > 1;
        if (split && (!request.consentsToSplit() || !StringUtils.hasText(request.planSignature()))) {
            throw new CheckoutConflictException(CheckoutConflictException.CODE_COD_SPLIT_CONSENT_REQUIRED,
                    "Đơn thanh toán khi nhận hàng cần lấy từ " + plan.fulfillmentCount()
                            + " chi nhánh. Bạn có thể chuyển sang thanh toán trực tuyến (một đơn), hoặc đồng ý tách "
                            + "thành " + plan.fulfillmentCount() + " đơn độc lập, mỗi đơn thu tiền riêng.");
        }
        BigDecimal fee = shippingFees.feePerOrder(type).orElseThrow(this::deliveryPolicyPending);

        // 5. Orders: one for everything, or (consented COD split) one per Store.
        List<List<StoreAllocation>> groups = new ArrayList<>();
        if (split) {
            plan.stores().forEach(store -> groups.add(List.of(store)));
        } else {
            groups.add(plan.stores());
        }
        Map<Long, BigDecimal> priceByProduct = lineByProduct.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().getProduct().getPrice()));
        List<BigDecimal> orderGoods = new ArrayList<>();
        for (List<StoreAllocation> group : groups) {
            BigDecimal items = BigDecimal.ZERO;
            for (StoreAllocation store : group) {
                for (AllocatedLine line : store.lines()) {
                    items = items.add(priceByProduct.get(line.productId()).multiply(BigDecimal.valueOf(line.quantity())));
                }
            }
            orderGoods.add(items);
        }
        BigDecimal goodsTotal = orderGoods.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4b. The ONE promotion code of this checkout (a COD split spends a single use): locked last, decided again
        // with the goods of this very checkout. The discount comes off the goods, never off the shipping fee.
        PromotionService.Evaluation promotion = promotionService.lockAndEvaluate(user, request.promotionCode(), goodsTotal);
        BigDecimal discount = promotion == null ? BigDecimal.ZERO : promotion.discount();
        List<BigDecimal> orderDiscounts = PromotionCalculator.allocate(discount, orderGoods);
        List<BigDecimal> orderTotals = new ArrayList<>();
        BigDecimal sessionTotal = BigDecimal.ZERO;
        for (int i = 0; i < groups.size(); i++) {
            BigDecimal total = orderGoods.get(i).subtract(orderDiscounts.get(i)).add(fee);
            orderTotals.add(total);
            sessionTotal = sessionTotal.add(total);
        }
        if (request.expectedTotal() != null && request.expectedTotal().compareTo(sessionTotal) != 0) {
            throw new CheckoutConflictException(CheckoutConflictException.CODE_TOTAL_CHANGED,
                    "Tổng tiền đã thay đổi so với lúc bạn xem (giá hoặc phí cập nhật). Vui lòng kiểm tra và xác nhận lại.");
        }
        if (StringUtils.hasText(request.planSignature())) {
            String current = contextSignature(user.getId(), type, method, request.pickupStoreId(),
                    request.shippingAddress(), demands, plan, fee, groups.size(), sessionTotal,
                    promotion == null ? null : promotion.promotion().getCode(), discount);
            if (!current.equals(request.planSignature())) {
                throw new CheckoutConflictException(CheckoutConflictException.CODE_PLAN_CHANGED,
                        "Nguồn hàng, phí hoặc cách nhận của đơn đã thay đổi so với lúc bạn xem. "
                                + "Vui lòng kiểm tra và xác nhận lại.");
            }
        }

        CheckoutSession session = new CheckoutSession();
        session.setUser(user);
        session.setStatus(CheckoutStatus.CREATED);
        session.setTotalAmount(sessionTotal);
        checkoutSessionRepository.save(session);

        if (promotion != null) {
            promotionService.commitUse(promotion, user, session);
        }
        for (int i = 0; i < groups.size(); i++) {
            createOrder(session, user, request, groups.get(i), orderTotals.get(i), fee, lineByProduct, locked,
                    orderDiscounts.get(i), promotion == null ? null : promotion.promotion().getCode());
        }

        // 6. Only the lines that were checked out leave the cart.
        cartItemRepository.deleteAll(lines);
        cartItemRepository.flush();

        return toResult(session);
    }

    private void createOrder(CheckoutSession session, User user, PlaceOrderRequest request,
                             List<StoreAllocation> stores, BigDecimal total, BigDecimal fee,
                             Map<Long, CartItem> lineByProduct, LockedAllocation locked, BigDecimal orderDiscount,
                             String promotionCode) {
        PaymentMethod method = request.paymentMethod();
        boolean single = stores.size() == 1;
        Store onlyStore = single ? locked.lockedStoreProducts()
                .get(stores.get(0).lines().get(0).storeProductId()).getStore() : null;

        Order order = new Order();
        order.setCheckoutSession(session);
        order.setUser(user);
        // legacy column: kept for the single-Store case so V2 readers keep working; never an authorization source
        order.setStore(onlyStore);
        order.setFulfillmentType(request.fulfillmentType());
        order.setPaymentMethod(method);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setOrderStatus(method == PaymentMethod.ONLINE ? OrderStatus.PENDING_PAYMENT : OrderStatus.CONFIRMED);
        order.setReceiverName(request.receiverName().trim());
        order.setReceiverPhone(request.receiverPhone().trim());
        order.setShippingAddress(request.fulfillmentType() == FulfillmentType.DELIVERY
                ? request.shippingAddress().trim() : null);
        order.setTotalAmount(total);
        order.setShippingFee(fee);
        order.setDiscountAmount(orderDiscount.setScale(2));
        order.setPromotionCode(promotionCode);
        orderRepository.save(order);

        // the Order discount is shared between its lines in proportion to their value, to the exact VND
        List<AllocatedLine> allLines = stores.stream().flatMap(store -> store.lines().stream()).toList();
        List<BigDecimal> lineDiscounts = PromotionCalculator.allocate(orderDiscount, allLines.stream()
                .map(line -> lineByProduct.get(line.productId()).getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(line.quantity()))).toList());
        int lineNo = 0;

        for (StoreAllocation allocation : stores) {
            Store store = locked.lockedStoreProducts().get(allocation.lines().get(0).storeProductId()).getStore();
            OrderFulfillment fulfillment = new OrderFulfillment();
            fulfillment.setOrder(order);
            fulfillment.setStore(store);
            fulfillment.setFulfillmentType(request.fulfillmentType());
            fulfillment.setStatus(OrderFulfillmentStatus.CONFIRMED);
            fulfillmentRepository.save(fulfillment);

            for (AllocatedLine line : allocation.lines()) {
                StoreProduct storeProduct = locked.lockedStoreProducts().get(line.storeProductId());
                Product product = lineByProduct.get(line.productId()).getProduct();
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setFulfillment(fulfillment);
                item.setStoreProduct(storeProduct);
                item.setProductName(product.getName());
                item.setUnitPrice(product.getPrice());
                item.setQuantity(line.quantity());
                item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
                item.setDiscountAmount(lineDiscounts.get(lineNo++));
                orderItemRepository.save(item);
                // stock leaves the StoreProduct now, with an ORDER movement; no reservation, no later deduction
                inventoryService.deductForOrder(storeProduct, line.quantity(), order);
            }

            OrderFulfillmentStatusHistory fulfillmentEntry = new OrderFulfillmentStatusHistory();
            fulfillmentEntry.setFulfillment(fulfillment);
            fulfillmentEntry.setOldStatus(null);
            fulfillmentEntry.setNewStatus(OrderFulfillmentStatus.CONFIRMED);
            fulfillmentEntry.setChangedBy(null);
            fulfillmentEntry.setNote(NOTE_FULFILLMENT);
            fulfillmentHistoryRepository.save(fulfillmentEntry);
        }

        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(order);
        entry.setOldStatus(null);
        entry.setNewStatus(order.getOrderStatus());
        entry.setChangedBy(null);
        entry.setNote(method == PaymentMethod.ONLINE
                ? "Tạo đơn ONLINE; chờ thanh toán trong " + paymentProperties.getPendingTimeoutMinutes() + " phút"
                : NOTE_CONFIRMED);
        historyRepository.save(entry);

        if (method == PaymentMethod.ONLINE) {
            paymentService.initializeOnlineAttempt(order);
        }
    }

    // ------------------------------------------------------------------ plan fingerprint

    /**
     * Fingerprint of everything the customer confirms: who (user), what (SKUs and quantities), from where (the
     * allocation, StoreProduct by StoreProduct), how (fulfillment type, payment method, pickup Store or delivery
     * address), how many Orders and how much (shipping fee per Order, total). The same method builds it for the
     * preview and for placing the order, so a change of any of them between the two is detected; it is recomputed
     * from the database and never taken from the browser.
     */
    private static String contextSignature(Long userId, FulfillmentType type, PaymentMethod method,
                                           Long pickupStoreId, String address, Collection<AllocationDemand> demands,
                                           AllocationResult result, BigDecimal fee, int orderCount, BigDecimal total,
                                           String promotionCode, BigDecimal discount) {
        StringBuilder plain = new StringBuilder("v1|u=").append(userId)
                .append("|f=").append(type).append("|m=").append(method)
                .append("|p=").append(type == FulfillmentType.STORE_PICKUP ? String.valueOf(pickupStoreId) : "-")
                .append("|a=").append(type == FulfillmentType.DELIVERY ? normalizedAddress(address) : "-")
                .append("|d=");
        demands.stream().sorted(Comparator.comparing(AllocationDemand::productId))
                .forEach(d -> plain.append(d.productId()).append('x').append(d.quantity()).append(','));
        plain.append("|r=").append(result.signature())
                .append("|fee=").append(money(fee)).append("|n=").append(orderCount).append("|t=").append(money(total))
                .append("|promo=").append(promotionCode == null ? "-" : promotionCode)
                .append("|disc=").append(money(discount == null ? BigDecimal.ZERO : discount));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(plain.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String normalizedAddress(String address) {
        return address == null ? "" : address.trim().replaceAll("\\s+", " ");
    }

    /** The reason a typed address cannot be used, or {@code null}. */
    private String addressProblem(String address) {
        try {
            addressValidator.requireValid(address);
            return null;
        } catch (BadRequestException ex) {
            return ex.getMessage();
        }
    }

    // ------------------------------------------------------------------ checks

    private void requireValidChoice(PlaceOrderRequest request) {
        FulfillmentType fulfillment = request.fulfillmentType();
        PaymentMethod payment = request.paymentMethod();
        if (fulfillment == null) {
            throw new BadRequestException("fulfillmentType", "Vui lòng chọn cách nhận hàng.");
        }
        if (payment == null) {
            throw new BadRequestException("paymentMethod", "Vui lòng chọn phương thức thanh toán.");
        }
        // Roadmap V2 6.4: DELIVERY pays by COD or ONLINE, STORE_PICKUP by PAY_AT_STORE or ONLINE
        boolean allowed = fulfillment == FulfillmentType.DELIVERY
                ? payment == PaymentMethod.COD || payment == PaymentMethod.ONLINE
                : payment == PaymentMethod.PAY_AT_STORE || payment == PaymentMethod.ONLINE;
        if (!allowed) {
            throw new BadRequestException("paymentMethod", fulfillment == FulfillmentType.DELIVERY
                    ? "Đơn giao hàng chỉ thanh toán khi nhận hàng (COD) hoặc trực tuyến."
                    : "Đơn nhận tại cửa hàng chỉ thanh toán tại cửa hàng hoặc trực tuyến.");
        }
        if (!StringUtils.hasText(request.receiverName()) || request.receiverName().trim().length() > 150) {
            throw new BadRequestException("receiverName", "Vui lòng nhập tên người nhận (tối đa 150 ký tự).");
        }
        if (request.receiverPhone() == null || !request.receiverPhone().trim().matches(PHONE_PATTERN)) {
            throw new BadRequestException("receiverPhone", "Số điện thoại người nhận không hợp lệ.");
        }
        if (fulfillment == FulfillmentType.DELIVERY) {
            addressValidator.requireValid(request.shippingAddress());
        }
        if (fulfillment == FulfillmentType.STORE_PICKUP && request.pickupStoreId() == null) {
            throw new BadRequestException("pickupStoreId", "Vui lòng chọn chi nhánh nhận hàng.");
        }
    }

    /** Flows that stay closed until their business decision / dependency exists (fail closed). */
    private void requireOpenFlow(PlaceOrderRequest request) {
        if (request.paymentMethod() == PaymentMethod.ONLINE && !checkoutProperties.isOnlineEnabled()) {
            throw new CheckoutConflictException(CheckoutConflictException.CODE_ONLINE_CHECKOUT_DISABLED,
                    "Thanh toán trực tuyến tạm thời chưa mở. Vui lòng chọn phương thức thanh toán khác.");
        }
        if (request.fulfillmentType() == FulfillmentType.DELIVERY && deliveryBlockedReason() != null) {
            throw deliveryPolicyPending();
        }
    }

    private CheckoutConflictException deliveryPolicyPending() {
        return new CheckoutConflictException(CheckoutConflictException.CODE_DELIVERY_POLICY_PENDING,
                deliveryBlockedReason() != null ? deliveryBlockedReason()
                        : "Giao hàng tận nơi tạm thời chưa mở.");
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
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderItem> allItems = orders.isEmpty() ? List.of()
                : orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds);
        List<OrderFulfillment> allFulfillments = orders.isEmpty() ? List.of()
                : fulfillmentRepository.findByOrderIdInOrderByIdAsc(orderIds);

        List<OrderSummaryResponse> summaries = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItemResponse> items = allItems.stream()
                    .filter(item -> item.getOrder().getId().equals(order.getId()))
                    .map(CheckoutServiceImpl::itemOf).toList();
            List<OrderFulfillment> fulfillments = allFulfillments.stream()
                    .filter(f -> f.getOrder().getId().equals(order.getId())).toList();
            List<FulfillmentSummaryResponse> parts = fulfillments.stream().map(f -> new FulfillmentSummaryResponse(
                    f.getId(), f.getStore().getId(), f.getStore().getName(), f.getStore().getAddress(),
                    f.getFulfillmentType(), f.getStatus(),
                    allItems.stream().filter(item -> item.getFulfillment().getId().equals(f.getId()))
                            .map(CheckoutServiceImpl::itemOf).toList())).toList();
            Store single = fulfillments.size() == 1 ? fulfillments.get(0).getStore() : null;
            summaries.add(new OrderSummaryResponse(order.getId(), single == null ? null : single.getId(),
                    single != null ? single.getName() : "Nhiều chi nhánh (" + fulfillments.size() + ")",
                    single == null ? null : single.getAddress(), order.getFulfillmentType(), order.getPaymentMethod(),
                    order.getPaymentStatus(), order.getOrderStatus(), order.getReceiverName(),
                    order.getReceiverPhone(), order.getShippingAddress(), order.getTotalAmount(), items, parts,
                    deadlines.paymentDeadline(order), order.getShippingFee()));
        }
        return new CheckoutResultResponse(session.getId(), session.getStatus(), session.getTotalAmount(),
                session.getCreatedAt(), summaries);
    }

    private static OrderItemResponse itemOf(OrderItem item) {
        return new OrderItemResponse(item.getProductName(), item.getUnitPrice(), item.getQuantity(),
                item.getSubtotal());
    }

    private User user(String customerEmail) {
        return userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + customerEmail));
    }
}
