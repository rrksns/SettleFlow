package com.settleflow.orderservice.kafka;

import com.settleflow.common.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Kafka Topic 이름 정의
    private static final String TOPIC = "order-create-topic";

    public void sendOrderCreateEvent(OrderCreatedEvent event) {
        log.info("Produce message: {}", event);
        // Key는 주문 ID로 설정하여, 동일 주문에 대한 이벤트가 동일 파티션으로 가도록 보장 (순서 보장)
        kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), event);
    }
}