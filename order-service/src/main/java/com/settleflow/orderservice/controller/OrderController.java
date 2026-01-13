package com.settleflow.orderservice.controller;

import com.settleflow.orderservice.service.OrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public String createOrder(@RequestBody CreateOrderRequest request) {
        Long orderId = orderService.createOrder(request.getUserId(), request.getAmount());
        return "Order Created. ID: " + orderId;
    }

    @Data
    static class CreateOrderRequest {
        private Long userId;
        private BigDecimal amount;
    }
}