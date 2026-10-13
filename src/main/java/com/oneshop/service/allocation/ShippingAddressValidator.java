package com.oneshop.service.allocation;

import com.oneshop.exception.BadRequestException;
import org.springframework.stereotype.Component;

/**
 * Structural validation of a delivery address typed by the customer.
 *
 * <p>The customer address is one free-text line ({@code customer_addresses.address_line},
 * {@code orders.shipping_address}); there is no province/ward column and no geographic reference data, and none may
 * be added. So this checks that the text <em>looks like an address a courier could use</em>, not that it exists, and
 * it cannot prove that the address is in Vietnam. All Stores are in Vietnam and the shop delivers inside Vietnam only;
 * that stays a business statement shown to the customer, not something this class verifies.
 *
 * <p>Rules: 10 to 500 characters after trimming; at least three words (separated by space, comma, dot or slash);
 * at least two words containing a letter (Vietnamese letters included); no control characters or angle brackets.
 */
@Component
public class ShippingAddressValidator {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 500;

    /** @throws BadRequestException (field {@code shippingAddress}) when the text cannot be a delivery address */
    public String requireValid(String address) {
        String text = address == null ? "" : address.trim().replaceAll("\\s+", " ");
        if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) {
            throw invalid("Vui lòng nhập địa chỉ giao hàng đầy đủ (số nhà, đường, phường/xã, quận/huyện, tỉnh/thành).");
        }
        if (text.chars().anyMatch(ch -> Character.isISOControl(ch) || ch == '<' || ch == '>')) {
            throw invalid("Địa chỉ giao hàng chứa ký tự không hợp lệ.");
        }
        String[] words = text.split("[\\s,./;:-]+");
        long wordsWithLetters = java.util.Arrays.stream(words)
                .filter(word -> word.chars().anyMatch(Character::isLetter)).count();
        if (words.length < 3 || wordsWithLetters < 2) {
            throw invalid("Vui lòng nhập địa chỉ giao hàng đầy đủ (số nhà, đường, phường/xã, quận/huyện, tỉnh/thành).");
        }
        return text;
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("shippingAddress", message);
    }
}
