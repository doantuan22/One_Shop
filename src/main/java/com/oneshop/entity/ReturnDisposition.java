package com.oneshop.entity;

/** Values of {@code order_return_items.disposition}: what Staff did with a returned line. */
public enum ReturnDisposition {
    /** waiting for the Staff of the Store that delivered the line */
    PENDING,
    /** put back on the shelf: the StoreProduct stock grew by the returned quantity */
    RESTOCKED,
    /** handled outside the system (real-world disposal / supplier): stock is not touched */
    DISPOSED
}
