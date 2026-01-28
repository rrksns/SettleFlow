package com.settleflow.orderservice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private BigDecimal totalAmount;

    private String status; // ORDERED, PENDING_EVENT, CANCELLED

    private LocalDateTime createdAt;

    /**
     * 주문 상태 변경 (이벤트 발행 성공 시)
     */
    public void completeEventPublish() {
        this.status = "ORDERED";
    }

    /**
     * 주문 취소
     */
    public void cancel() {
        this.status = "CANCELLED";
    }
}