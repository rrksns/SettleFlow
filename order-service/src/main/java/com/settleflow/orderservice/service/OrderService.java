package com.settleflow.orderservice.service;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.orderservice.domain.Order;
import com.settleflow.orderservice.domain.OrderRepository;
import com.settleflow.orderservice.kafka.OrderProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    @Transactional
    public Long createOrder(Long userId, BigDecimal amount) {
        // 1. 주문 데이터 DB 저장
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(amount)
                .status("ORDERED")
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // 2. Kafka 이벤트 발행 (정산 서비스가 이걸 가져감)
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .userId(savedOrder.getUserId())
                .totalAmount(savedOrder.getTotalAmount())
                .feeRate(0.03) // 수수료 3% 가정
                .orderedAt(savedOrder.getCreatedAt().toString())
                .build();

        orderProducer.sendOrderCreateEvent(event);

        return savedOrder.getId();
    }
}