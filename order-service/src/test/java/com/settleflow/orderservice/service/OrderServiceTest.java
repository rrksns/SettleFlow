package com.settleflow.orderservice.service;

import com.settleflow.common.event.OrderCreatedEvent;
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
    }

    @Test
    @DisplayName("주문 생성 - 정상 케이스")
    void createOrder_Success() {
        // given
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        // when
        Long orderId = orderService.createOrder(testUserId, testAmount);

        // then
        assertThat(orderId).isEqualTo(100L);

        // Repository save 호출 확인
        verify(orderRepository, times(1)).save(argThat(order ->
                order.getUserId().equals(testUserId) &&
                order.getTotalAmount().equals(testAmount) &&
                order.getStatus().equals("ORDERED")
        ));

        // Kafka Producer 호출 확인
        verify(orderProducer, times(1)).sendOrderCreateEvent(argThat(event ->
                event.getOrderId().equals(100L) &&
                event.getUserId().equals(testUserId) &&
                event.getTotalAmount().equals(testAmount) &&
                event.getFeeRate() == 0.03
        ));
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
    @DisplayName("주문 생성 - Kafka 전송 실패 시 예외 전파")
    void createOrder_KafkaFailure() {
        // given
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);
        doThrow(new RuntimeException("Kafka 전송 실패"))
                .when(orderProducer).sendOrderCreateEvent(any(OrderCreatedEvent.class));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            orderService.createOrder(testUserId, testAmount);
        });

        // 주문은 저장되었지만 Kafka 전송이 실패한 상태
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderProducer, times(1)).sendOrderCreateEvent(any(OrderCreatedEvent.class));
    }
}
