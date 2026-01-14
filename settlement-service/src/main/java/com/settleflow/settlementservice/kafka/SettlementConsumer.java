package com.settleflow.settlementservice.kafka;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException; // Spring Data 예외
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
        log.info("Consumer Received Message: OrderId={}", event.getOrderId());

        try {
            // 1. 계산 로직
            BigDecimal fee = event.getTotalAmount().multiply(BigDecimal.valueOf(event.getFeeRate()));
            BigDecimal settleAmount = event.getTotalAmount().subtract(fee);

            // 2. 저장 시도
            Settlement settlement = Settlement.builder()
                    .orderId(event.getOrderId())
                    .userId(event.getUserId())
                    .totalAmount(event.getTotalAmount())
                    .fee(fee)
                    .settleAmount(settleAmount)
                    .orderedAt(event.getOrderedAt())
                    .status("WAITING")
                    .build();

            settlementRepository.save(settlement);
            log.info("Successfully Saved: OrderId={}", event.getOrderId());

        } catch (Exception e) {
            // ▼ [수정됨] 모든 예외를 일단 잡습니다.

            // 에러 메시지나 클래스 이름에 'Duplicate'가 포함되어 있는지 확인
            if (e.getClass().getSimpleName().contains("Duplicate") || e.getMessage().contains("duplicate")) {
                // 중복 에러라면: 로그만 남기고 정상 종료(Ack) 처리 -> Kafka가 다음 메시지로 넘어감
                log.warn("Duplicate Order Detected (Idempotency check): OrderId={}", event.getOrderId());
            } else {
                // 중복이 아닌 진짜 다른 에러라면: 로그 찍고 그냥 넘어갈지, 재시도할지 결정
                // (여기서는 일단 로그 찍고 넘어가는 것으로 처리하여 무한 루프 방지)
                log.error("Unknown Error processing settlement: OrderId={}", event.getOrderId(), e);
            }
        }
    }
}