package com.settleflow.orderservice.controller;

import com.settleflow.orderservice.domain.Order;
import com.settleflow.orderservice.dto.OrderResponse;
import com.settleflow.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long orderId = orderService.createOrder(request.getUserId(), request.getAmount());

        // TODO: OrderService에서 Order 객체를 반환하도록 수정하면 더 나은 응답 생성 가능
        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .userId(request.getUserId())
                .totalAmount(request.getAmount())
                .status("ORDERED")
                .build();

        return ResponseEntity.ok(response);
    }

    @Data
    static class CreateOrderRequest {
        @NotNull(message = "사용자 ID는 필수입니다")
        @Positive(message = "사용자 ID는 양수여야 합니다")
        private Long userId;

        @NotNull(message = "주문 금액은 필수입니다")
        @DecimalMin(value = "0.01", message = "주문 금액은 0보다 커야 합니다")
        private BigDecimal amount;
    }
}