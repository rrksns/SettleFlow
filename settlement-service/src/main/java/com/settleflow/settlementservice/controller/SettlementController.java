package com.settleflow.settlementservice.controller;

import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
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
    // ▼ [핵심] 'settlements' 라는 저장소에 orderId를 키값으로 저장한다.
    // 이미 있으면 메서드를 실행하지 않고 캐시 값을 리턴한다.
    @Cacheable(value = "settlements", key = "#orderId", unless = "#result == null")
    public Settlement getSettlementByOrderId(@PathVariable Long orderId) {
        log.info("Fetching settlement from MongoDB... OrderId={}", orderId);

        return settlementRepository.findAll().stream()
                .filter(s -> s.getOrderId().equals(orderId))
                .findFirst()
                .orElse(null);
    }
}