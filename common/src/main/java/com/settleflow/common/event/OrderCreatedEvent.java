package com.settleflow.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private Long orderId;           // 주문 ID
    private Long userId;            // 구매자 ID
    private BigDecimal totalAmount; // 총 결제 금액 (중요: BigDecimal)
    private double feeRate;         // 적용된 수수료율 (예: 0.03 = 3%)
    private String orderedAt;       // 주문 일시 (ISO 8601 문자열 권장)

}