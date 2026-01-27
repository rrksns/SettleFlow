package com.settleflow.settlementservice.kafka;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementConsumer 단위 테스트")
class SettlementConsumerTest {

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementConsumer settlementConsumer;

    private OrderCreatedEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = OrderCreatedEvent.builder()
                .orderId(100L)
                .userId(1L)
                .totalAmount(new BigDecimal("10000.00"))
                .feeRate(0.03)
                .orderedAt(LocalDateTime.now().toString())
                .build();
    }

    @Test
    @DisplayName("정산 데이터 생성 - 정상 케이스")
    void consume_Success() {
        // given
        when(settlementRepository.save(any(Settlement.class))).thenReturn(null);

        // when
        settlementConsumer.consume(testEvent);

        // then
        verify(settlementRepository, times(1)).save(argThat(settlement -> {
            // 수수료 계산: 10000 * 0.03 = 300
            BigDecimal expectedFee = new BigDecimal("300.00");
            // 정산액: 10000 - 300 = 9700
            BigDecimal expectedSettleAmount = new BigDecimal("9700.00");

            return settlement.getOrderId().equals(100L) &&
                   settlement.getUserId().equals(1L) &&
                   settlement.getTotalAmount().compareTo(new BigDecimal("10000.00")) == 0 &&
                   settlement.getFee().compareTo(expectedFee) == 0 &&
                   settlement.getSettleAmount().compareTo(expectedSettleAmount) == 0 &&
                   settlement.getStatus().equals("WAITING");
        }));
    }

    @Test
    @DisplayName("정산 데이터 생성 - 수수료 계산 검증")
    void consume_FeeCalculation() {
        // given
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(200L)
                .userId(2L)
                .totalAmount(new BigDecimal("50000.00"))
                .feeRate(0.05) // 5% 수수료
                .orderedAt(LocalDateTime.now().toString())
                .build();

        when(settlementRepository.save(any(Settlement.class))).thenReturn(null);

        // when
        settlementConsumer.consume(event);

        // then
        verify(settlementRepository, times(1)).save(argThat(settlement -> {
            // 수수료: 50000 * 0.05 = 2500
            BigDecimal expectedFee = new BigDecimal("2500.00");
            // 정산액: 50000 - 2500 = 47500
            BigDecimal expectedSettleAmount = new BigDecimal("47500.00");

            return settlement.getFee().compareTo(expectedFee) == 0 &&
                   settlement.getSettleAmount().compareTo(expectedSettleAmount) == 0;
        }));
    }

    @Test
    @DisplayName("중복 메시지 처리 - 멱등성 보장")
    void consume_DuplicateMessage() {
        // given
        when(settlementRepository.save(any(Settlement.class)))
                .thenThrow(new DuplicateKeyException("Duplicate orderId"));

        // when
        settlementConsumer.consume(testEvent);

        // then
        // 예외가 발생해도 정상 종료되어야 함 (WARN 로그만)
        verify(settlementRepository, times(1)).save(any(Settlement.class));
    }

    @Test
    @DisplayName("중복 메시지 처리 - Exception 메시지에 'duplicate' 포함")
    void consume_DuplicateMessageWithGenericException() {
        // given
        when(settlementRepository.save(any(Settlement.class)))
                .thenThrow(new RuntimeException("duplicate key error"));

        // when
        settlementConsumer.consume(testEvent);

        // then
        // 'duplicate'가 메시지에 포함되어 있으므로 중복으로 간주
        verify(settlementRepository, times(1)).save(any(Settlement.class));
    }

    @Test
    @DisplayName("알 수 없는 예외 처리 - 로그만 남기고 정상 종료")
    void consume_UnknownException() {
        // given
        when(settlementRepository.save(any(Settlement.class)))
                .thenThrow(new RuntimeException("Unknown database error"));

        // when
        settlementConsumer.consume(testEvent);

        // then
        // 예외가 발생해도 정상 종료 (무한 재시도 방지)
        verify(settlementRepository, times(1)).save(any(Settlement.class));
    }

    @Test
    @DisplayName("BigDecimal 정밀도 테스트")
    void consume_BigDecimalPrecision() {
        // given
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(300L)
                .userId(3L)
                .totalAmount(new BigDecimal("12345.67"))
                .feeRate(0.03)
                .orderedAt(LocalDateTime.now().toString())
                .build();

        when(settlementRepository.save(any(Settlement.class))).thenReturn(null);

        // when
        settlementConsumer.consume(event);

        // then
        verify(settlementRepository, times(1)).save(argThat(settlement -> {
            // 수수료: 12345.67 * 0.03 = 370.3701
            BigDecimal expectedFee = new BigDecimal("12345.67")
                    .multiply(BigDecimal.valueOf(0.03));
            // 정산액: 12345.67 - 370.3701
            BigDecimal expectedSettleAmount = new BigDecimal("12345.67")
                    .subtract(expectedFee);

            return settlement.getFee().compareTo(expectedFee) == 0 &&
                   settlement.getSettleAmount().compareTo(expectedSettleAmount) == 0;
        }));
    }
}
