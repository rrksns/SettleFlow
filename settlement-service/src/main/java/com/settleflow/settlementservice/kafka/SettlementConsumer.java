package com.settleflow.settlementservice.kafka;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementConsumer {

    private final SettlementRepository settlementRepository;

    @KafkaListener(topics = "order-create-topic", groupId = "settlement-group")
    public void consume(OrderCreatedEvent event) {
        log.info("Consumer Received Message: {}", event);

        // 1. 비즈니스 로직: 수수료 계산 (단순화: 3%)
        BigDecimal fee = event.getTotalAmount().multiply(BigDecimal.valueOf(event.getFeeRate()));
        BigDecimal settleAmount = event.getTotalAmount().subtract(fee);

        // 2. NoSQL(MongoDB) 저장
        Settlement settlement = Settlement.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .totalAmount(event.getTotalAmount())
                .fee(fee)
                .settleAmount(settleAmount)
                .orderedAt(event.getOrderedAt())
                .status("WAITING") // 초기 상태
                .build();

        settlementRepository.save(settlement);
        log.info("Saved to MongoDB: OrderId={}", event.getOrderId());
    }
}