package com.settleflow.settlementservice.integration;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Disabled;

@Disabled("통합 테스트는 로컬 인프라가 실행 중일 때만 수행")
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"},
        topics = {"order-create-topic"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Kafka 통합 테스트 (Producer → Consumer)")
class KafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private SettlementRepository settlementRepository;

    private static final String TOPIC = "order-create-topic";

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
    }

    @Test
    @DisplayName("Kafka 메시지 발행 및 Consumer 처리 검증")
    void kafkaMessagePublishAndConsume() {
        // given
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(1000L)
                .userId(10L)
                .totalAmount(new BigDecimal("50000.00"))
                .feeRate(0.03)
                .orderedAt(LocalDateTime.now().toString())
                .build();

        // when
        kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), event);

        // then
        // Kafka Consumer가 메시지를 소비하고 DB에 저장할 때까지 대기 (최대 10초)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Settlement> settlements = settlementRepository.findAll();
            assertThat(settlements).hasSize(1);

            Settlement settlement = settlements.get(0);
            assertThat(settlement.getOrderId()).isEqualTo(1000L);
            assertThat(settlement.getUserId()).isEqualTo(10L);
            assertThat(settlement.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));

            // 수수료: 50000 * 0.03 = 1500
            BigDecimal expectedFee = new BigDecimal("50000.00")
                    .multiply(BigDecimal.valueOf(0.03));
            assertThat(settlement.getFee()).isEqualByComparingTo(expectedFee);

            // 정산액: 50000 - 1500 = 48500
            BigDecimal expectedSettleAmount = new BigDecimal("50000.00")
                    .subtract(expectedFee);
            assertThat(settlement.getSettleAmount()).isEqualByComparingTo(expectedSettleAmount);

            assertThat(settlement.getStatus()).isEqualTo("WAITING");
        });
    }

    @Test
    @DisplayName("중복 메시지 발행 시 한 번만 저장 (멱등성)")
    void kafkaDuplicateMessageIdempotency() {
        // given
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(2000L)
                .userId(20L)
                .totalAmount(new BigDecimal("100000.00"))
                .feeRate(0.05)
                .orderedAt(LocalDateTime.now().toString())
                .build();

        // when
        // 동일한 메시지를 2번 발행
        kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), event);
        kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), event);

        // then
        // 중복 메시지 처리 완료까지 대기
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Settlement> settlements = settlementRepository.findAll();
            // Unique Index로 인해 한 번만 저장되어야 함
            assertThat(settlements).hasSize(1);
            assertThat(settlements.get(0).getOrderId()).isEqualTo(2000L);
        });
    }

    @Test
    @DisplayName("여러 주문 이벤트 처리")
    void kafkaMultipleMessages() {
        // given
        OrderCreatedEvent event1 = OrderCreatedEvent.builder()
                .orderId(3001L)
                .userId(30L)
                .totalAmount(new BigDecimal("10000.00"))
                .feeRate(0.03)
                .orderedAt(LocalDateTime.now().toString())
                .build();

        OrderCreatedEvent event2 = OrderCreatedEvent.builder()
                .orderId(3002L)
                .userId(31L)
                .totalAmount(new BigDecimal("20000.00"))
                .feeRate(0.03)
                .orderedAt(LocalDateTime.now().toString())
                .build();

        OrderCreatedEvent event3 = OrderCreatedEvent.builder()
                .orderId(3003L)
                .userId(32L)
                .totalAmount(new BigDecimal("30000.00"))
                .feeRate(0.03)
                .orderedAt(LocalDateTime.now().toString())
                .build();

        // when
        kafkaTemplate.send(TOPIC, String.valueOf(event1.getOrderId()), event1);
        kafkaTemplate.send(TOPIC, String.valueOf(event2.getOrderId()), event2);
        kafkaTemplate.send(TOPIC, String.valueOf(event3.getOrderId()), event3);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Settlement> settlements = settlementRepository.findAll();
            assertThat(settlements).hasSize(3);

            // orderId로 정렬하여 확인
            settlements.sort((s1, s2) -> s1.getOrderId().compareTo(s2.getOrderId()));
            assertThat(settlements.get(0).getOrderId()).isEqualTo(3001L);
            assertThat(settlements.get(1).getOrderId()).isEqualTo(3002L);
            assertThat(settlements.get(2).getOrderId()).isEqualTo(3003L);
        });
    }
}
