package com.settleflow.orderservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 특정 상태의 주문 목록 조회
     * @param status 주문 상태 (ORDERED, PENDING_EVENT, CANCELLED)
     * @return 해당 상태의 주문 목록
     */
    List<Order> findByStatus(String status);
}
