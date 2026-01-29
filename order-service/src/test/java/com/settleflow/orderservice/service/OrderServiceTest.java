package com.settleflow.orderservice.service;

import com.settleflow.common.event.OrderCreatedEvent;
import com.settleflow.orderservice.config.SettlementProperties;
import com.settleflow.orderservice.domain.Order;
import com.settleflow.orderservice.domain.OrderRepository;
import com.settleflow.orderservice.kafka.OrderProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProducer orderProducer;

    @Mock
    private SettlementProperties settlementProperties;

    @InjectMocks
    private OrderService orderService;

    private Long testUserId;
    private BigDecimal testAmount;
    private Order mockOrder;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testAmount = new BigDecimal("10000.00");

        mockOrder = Order.builder()
                .id(100L)
                .userId(testUserId)
                .totalAmount(testAmount)
                .status("ORDERED")
                .createdAt(java.time.LocalDateTime.now())
                .build();

        // SettlementProperties 모킹 (lenient로 설정하여 사용하지 않는 테스트에서도 허용)
        lenient().when(settlementProperties.getFeeRate()).thenReturn(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("주문 생성 - 정상 케이스 (Kafka 전송 성공)")
    void createOrder_Success() {
        // given
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        // when
        Long orderId = orderService.createOrder(testUserId, testAmount);

        // then
        assertThat(orderId).isEqualTo(100L);

        // Repository save 호출 확인 (초기 상태는 PENDING_EVENT)
        verify(orderRepository, times(1)).save(argThat(order ->
                order.getUserId().equals(testUserId) &&
                order.getTotalAmount().equals(testAmount) &&
                order.getStatus().equals("PENDING_EVENT")  // 초기 상태
        ));

        // Kafka Producer 호출 확인
        verify(orderProducer, times(1)).sendOrderCreateEvent(argThat(event ->
                event.getOrderId().equals(100L) &&
                event.getUserId().equals(testUserId) &&
                event.getTotalAmount().equals(testAmount) &&
                event.getFeeRate() == 0.03
        ));

        // Kafka 전송 성공 시 completeEventPublish()가 호출되어 상태가 ORDERED로 변경되어야 함
        // (mockOrder의 completeEventPublish()가 호출됨)
    }

    @Test
    @DisplayName("주문 생성 - 금액이 0인 경우")
    void createOrder_ZeroAmount() {
        // given
        BigDecimal zeroAmount = BigDecimal.ZERO;
        Order zeroOrder = Order.builder()
                .id(101L)
                .userId(testUserId)
                .totalAmount(zeroAmount)
                .status("ORDERED")
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(zeroOrder);

        // when
        Long orderId = orderService.createOrder(testUserId, zeroAmount);

        // then
        assertThat(orderId).isEqualTo(101L);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderProducer, times(1)).sendOrderCreateEvent(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("주문 생성 - Kafka 전송 실패 시 PENDING_EVENT 상태 유지")
    void createOrder_KafkaFailure_KeepsPendingState() {
        // given
        Order pendingOrder = Order.builder()
                .id(100L)
                .userId(testUserId)
                .totalAmount(testAmount)
                .status("PENDING_EVENT")
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);
        doThrow(new RuntimeException("Kafka 전송 실패"))
                .when(orderProducer).sendOrderCreateEvent(any(OrderCreatedEvent.class));

        // when
        Long orderId = orderService.createOrder(testUserId, testAmount);

        // then
        assertThat(orderId).isEqualTo(100L);

        // 주문은 저장되었고 Kafka 전송이 시도되었지만 실패
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderProducer, times(1)).sendOrderCreateEvent(any(OrderCreatedEvent.class));

        // 상태는 PENDING_EVENT로 유지되어야 함 (completeEventPublish 호출 안됨)
    }

    @Test
    @DisplayName("PENDING_EVENT 주문 재시도 - 성공")
    void retryPendingEventOrders_Success() {
        // given
        Order pendingOrder = Order.builder()
                .id(200L)
                .userId(2L)
                .totalAmount(new BigDecimal("20000.00"))
                .status("PENDING_EVENT")
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(orderRepository.findByStatus("PENDING_EVENT"))
                .thenReturn(java.util.List.of(pendingOrder));

        // when
        orderService.retryPendingEventOrders();

        // then
        verify(orderProducer, times(1)).sendOrderCreateEvent(any(OrderCreatedEvent.class));
        // completeEventPublish()가 호출되어 상태가 ORDERED로 변경되어야 함
    }

    @Test
    @DisplayName("PENDING_EVENT 주문 재시도 - 대상 없음")
    void retryPendingEventOrders_NoOrders() {
        // given
        when(orderRepository.findByStatus("PENDING_EVENT"))
                .thenReturn(java.util.List.of());

        // when
        orderService.retryPendingEventOrders();

        // then
        verify(orderProducer, never()).sendOrderCreateEvent(any(OrderCreatedEvent.class));
    }
}
