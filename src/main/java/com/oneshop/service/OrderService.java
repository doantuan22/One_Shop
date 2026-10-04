package com.oneshop.service;

/**
 * Order state machine, cancellation, history, Store scope and pickup code (BR-11 to BR-14).
 *
 * <p>Skeleton for Phases 9, 10 and 12. Every status change is validated against the fulfillment type and recorded in
 * OrderStatusHistory; cancel, stock restore and history happen in one transaction. Staff access is always scoped by the
 * Store of the Staff assignment, never by a request parameter.
 */
public interface OrderService {
}
