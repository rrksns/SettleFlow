package com.settleflow.orderservice.service;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.common.event.OrderStatus;
import com.settleflow.orderservice.domain.Order;
import com.settleflow.orderservice.domain.OrderRepository;
import com.settleflow.orderservice.kafka.OrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    @Transactional
    public Long createOrder(Long userId, BigDecimal amount) {
        // 1. 주문 데이터 DB 저장 (초기 상태: PENDING_EVENT)
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(amount)
                .status(OrderStatus.PENDING_EVENT.name()) // 이벤트 발행 전까지 PENDING
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // 2. Kafka 이벤트 발행 시도
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .userId(savedOrder.getUserId())
                .totalAmount(savedOrder.getTotalAmount())
                .feeRate(0.03) // 수수료 3% 가정
                .orderedAt(savedOrder.getCreatedAt().toString())
                .build();

        try {
            orderProducer.sendOrderCreateEvent(event);

            // Kafka 전송 성공 시 상태 변경 (PENDING_EVENT -> ORDERED)
            savedOrder.completeEventPublish();
            log.info("주문 생성 및 이벤트 발행 성공: orderId={}", savedOrder.getId());

        } catch (Exception e) {
            // Kafka 전송 실패 시 로그만 남기고 PENDING_EVENT 상태 유지
            log.error("Kafka 이벤트 발행 실패: orderId={}, 재시도 대상으로 등록됨", savedOrder.getId(), e);
            // 주문은 생성되었지만 이벤트 발행은 실패 (재시도 필요)
        }

        return savedOrder.getId();
    }

    /**
     * PENDING_EVENT 상태의 주문에 대해 이벤트 재발행
     * Scheduler에서 주기적으로 호출
     */
    @Transactional
    public void retryPendingEventOrders() {
        // PENDING_EVENT 상태의 주문 조회
        var pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING_EVENT.name());

        log.info("재시도 대상 주문 {}건 발견", pendingOrders.size());

        for (Order order : pendingOrders) {
            try {
                OrderCreatedEvent event = OrderCreatedEvent.builder()
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .totalAmount(order.getTotalAmount())
                        .feeRate(0.03)
                        .orderedAt(order.getCreatedAt().toString())
                        .build();

                orderProducer.sendOrderCreateEvent(event);

                // 성공 시 상태 업데이트 (PENDING_EVENT -> ORDERED)
                order.completeEventPublish();
                log.info("이벤트 재발행 성공: orderId={}", order.getId());

            } catch (Exception e) {
                log.error("이벤트 재발행 실패: orderId={}", order.getId(), e);
                // 계속 PENDING_EVENT 상태로 유지하여 다음 재시도 대상이 됨
            }
        }
    }
}