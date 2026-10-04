package com.oneshop.service.impl;

import com.oneshop.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Skeleton, see {@link OrderService}. Class level transaction so later transitions are atomic by default. */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {
}
