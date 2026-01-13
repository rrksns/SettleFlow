package com.settleflow.settlementservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "settlements")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    private String id;              // MongoDB ID

    private Long orderId;           // 주문 ID
    private Long userId;            // 유저 ID

    private BigDecimal totalAmount; // 결제 총액
    private BigDecimal fee;         // 수수료
    private BigDecimal settleAmount;// 정산 지급액 (총액 - 수수료)

    private String orderedAt;       // 주문 시간
    private String status;          // 정산 상태 (WAITING 등)
}