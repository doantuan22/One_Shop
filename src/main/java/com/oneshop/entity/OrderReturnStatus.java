package com.oneshop.entity;

/** Values of {@code order_returns.status}. The Order and its Fulfillments stay COMPLETED whatever happens here. */
public enum OrderReturnStatus {
    /** the customer asked; an Administrator has not decided yet */
    REQUESTED,
    /** an Administrator approved: the goods were refunded in simulation and Staff handle the returned lines */
    APPROVED,
    /** an Administrator refused (final, with a reason) */
    REJECTED,
    /** every returned line was handled by Staff */
    COMPLETED
}
