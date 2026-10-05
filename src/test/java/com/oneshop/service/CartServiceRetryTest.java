package com.oneshop.service;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.entity.Cart;
import com.oneshop.entity.CartItem;
import com.oneshop.entity.StoreProduct;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.CartItemRepository;
import com.oneshop.repository.CartRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The race the database constraints protect against: two requests of the same customer both find no cart (or no
 * line) and both insert. The loser's transaction fails on UQ_carts_user / UQ_cart_items_cart_store_product; the
 * service must then try once more and add to what the winner committed, instead of showing an SQL error.
 */
class CartServiceRetryTest {

    private static final String EMAIL = "customer@oneshop.test";

    private final CartRepository cartRepository = mock(CartRepository.class);
    private final CartItemRepository cartItemRepository = mock(CartItemRepository.class);
    private final StoreProductRepository storeProductRepository = mock(StoreProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final StoreProduct storeProduct = mock(StoreProduct.class);
    private final Cart winnersCart = mock(Cart.class);

    private CartService cartService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(storeProduct.getId()).thenReturn(10L);
        when(storeProduct.getQuantity()).thenReturn(5);
        when(storeProductRepository.findSellableById(10L)).thenReturn(Optional.of(storeProduct));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(new User()));
        when(winnersCart.getId()).thenReturn(7L);
        cartService = new CartServiceImpl(cartRepository, cartItemRepository, storeProductRepository, userRepository,
                mock(ProductService.class), transactionManager);
    }

    @Test
    void losingTheRaceToCreateTheCartIsRetriedOnTheCartThatWon() {
        // 1st attempt: no cart yet, the insert hits UQ_carts_user. 2nd attempt: the other request's cart is there.
        when(cartRepository.findByUserEmailForUpdate(EMAIL)).thenReturn(Optional.empty(), Optional.of(winnersCart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenThrow(new DataIntegrityViolationException("UQ_carts_user"));
        when(cartItemRepository.findByCartIdAndStoreProductId(7L, 10L)).thenReturn(Optional.empty());

        cartService.addItem(EMAIL, new AddCartItemRequest(10L, 2));

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCart()).isSameAs(winnersCart);
        assertThat(saved.getValue().getQuantity()).isEqualTo(2);
        // the failed attempt was rolled back, the second one committed
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test
    void losingTheRaceToCreateTheLineAddsToTheLineThatWon() {
        CartItem winnersLine = new CartItem();
        winnersLine.setQuantity(1);
        when(cartRepository.findByUserEmailForUpdate(EMAIL)).thenReturn(Optional.of(winnersCart));
        when(cartItemRepository.findByCartIdAndStoreProductId(7L, 10L))
                .thenReturn(Optional.empty(), Optional.of(winnersLine));
        when(cartItemRepository.saveAndFlush(any(CartItem.class)))
                .thenThrow(new DataIntegrityViolationException("UQ_cart_items_cart_store_product"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        cartService.addItem(EMAIL, new AddCartItemRequest(10L, 2));

        assertThat(winnersLine.getQuantity()).as("merged into the existing line").isEqualTo(3);
        verify(cartItemRepository, times(2)).saveAndFlush(any(CartItem.class));
    }

    @Test
    void theRetryStillChecksStockAgainstTheMergedQuantity() {
        CartItem winnersLine = new CartItem();
        winnersLine.setQuantity(4);                 // stock is 5
        when(cartRepository.findByUserEmailForUpdate(EMAIL)).thenReturn(Optional.of(winnersCart));
        when(cartItemRepository.findByCartIdAndStoreProductId(7L, 10L))
                .thenReturn(Optional.empty(), Optional.of(winnersLine));
        when(cartItemRepository.saveAndFlush(any(CartItem.class)))
                .thenThrow(new DataIntegrityViolationException("UQ_cart_items_cart_store_product"));

        assertThatThrownBy(() -> cartService.addItem(EMAIL, new AddCartItemRequest(10L, 2)))
                .isInstanceOf(BadRequestException.class);

        assertThat(winnersLine.getQuantity()).isEqualTo(4);
    }

    @Test
    void aBusinessRefusalIsNotRetried() {
        when(storeProductRepository.findSellableById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(EMAIL, new AddCartItemRequest(10L, 1)))
                .isInstanceOf(BadRequestException.class);

        verify(storeProductRepository, times(1)).findSellableById(10L);
        verify(cartItemRepository, never()).saveAndFlush(any(CartItem.class));
    }
}
