package com.settleflow.settlementservice.controller;

import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementRepository settlementRepository;

    // 전체 정산 내역 조회 (테스트용)
    @GetMapping("/settlements")
    public List<Settlement> getAllSettlements() {
        return settlementRepository.findAll();
    }

    // 특정 주문의 정산 내역 조회
    @GetMapping("/settlements/{orderId}")
    public Settlement getSettlementByOrderId(@PathVariable Long orderId) {
        return settlementRepository.findAll().stream()
                .filter(s -> s.getOrderId().equals(orderId))
                .findFirst()
                .orElse(null);
        // 실무에선 Repository FindByOrderId 사용 권장
    }
}