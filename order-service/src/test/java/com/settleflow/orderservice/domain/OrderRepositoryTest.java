package com.settleflow.orderservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OrderRepository 테스트")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("주문 저장 및 조회")
    void saveAndFind() {
        // given
        Order order = Order.builder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000.00"))
                .status("ORDERED")
                .createdAt(LocalDateTime.now())
                .build();

        // when
        Order savedOrder = orderRepository.save(order);

        // then
        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getUserId()).isEqualTo(1L);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(savedOrder.getStatus()).isEqualTo("ORDERED");
    }

    @Test
    @DisplayName("주문 ID로 조회")
    void findById() {
        // given
        Order order = Order.builder()
                .userId(2L)
                .totalAmount(new BigDecimal("20000.00"))
                .status("ORDERED")
                .createdAt(LocalDateTime.now())
                .build();
        Order savedOrder = orderRepository.save(order);

        // when
        Optional<Order> foundOrder = orderRepository.findById(savedOrder.getId());

        // then
        assertThat(foundOrder).isPresent();
        assertThat(foundOrder.get().getUserId()).isEqualTo(2L);
        assertThat(foundOrder.get().getTotalAmount()).isEqualByComparingTo(new BigDecimal("20000.00"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회")
    void findById_NotFound() {
        // when
        Optional<Order> foundOrder = orderRepository.findById(999L);

        // then
        assertThat(foundOrder).isEmpty();
    }

    @Test
    @DisplayName("여러 주문 저장 및 전체 조회")
    void saveMultipleOrders() {
        // given
        Order order1 = Order.builder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000.00"))
                .status("ORDERED")
                .createdAt(LocalDateTime.now())
                .build();

        Order order2 = Order.builder()
                .userId(2L)
                .totalAmount(new BigDecimal("20000.00"))
                .status("ORDERED")
                .createdAt(LocalDateTime.now())
                .build();

        // when
        orderRepository.save(order1);
        orderRepository.save(order2);

        // then
        long count = orderRepository.count();
        assertThat(count).isEqualTo(2);
    }
}
