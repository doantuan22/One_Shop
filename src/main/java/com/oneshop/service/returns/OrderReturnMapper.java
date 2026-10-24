package com.oneshop.service.returns;

import com.oneshop.dto.response.OrderReturnResponse;
import com.oneshop.entity.OrderReturn;
import com.oneshop.entity.OrderReturnImage;
import com.oneshop.entity.OrderReturnItem;
import com.oneshop.entity.ReturnDisposition;
import com.oneshop.entity.User;

import java.util.List;

/** Entity to screen model for a return. Names of Administrators / Staff are included only for the Admin view. */
final class OrderReturnMapper {

    private OrderReturnMapper() {
    }

    static OrderReturnResponse toResponse(OrderReturn r, List<OrderReturnItem> items, List<OrderReturnImage> images,
                                          boolean withActors) {
        User customer = r.getUser();
        return new OrderReturnResponse(r.getId(), r.getOrder().getId(), customer.getFullName(), customer.getEmail(),
                r.getStatus(), r.getReason(), r.getReceivedAt(), r.getRequestedAt(), r.getRefundAmount(),
                withActors && r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null, r.getReviewedAt(),
                r.getReviewNote(), r.getCompletedAt(),
                items.stream().map(i -> item(i, withActors)).toList(),
                images.stream().map(OrderReturnImage::getImageUrl).toList());
    }

    private static OrderReturnResponse.Item item(OrderReturnItem i, boolean withActors) {
        return new OrderReturnResponse.Item(i.getId(), i.getProductName(), i.getQuantity(), i.getUnitPrice(),
                i.getSubtotal(), i.getStore().getName(), i.getDisposition(),
                withActors && i.getDisposition() != ReturnDisposition.PENDING && i.getHandledBy() != null
                        ? i.getHandledBy().getFullName() : null,
                i.getHandledAt(), i.getHandleNote());
    }
}
