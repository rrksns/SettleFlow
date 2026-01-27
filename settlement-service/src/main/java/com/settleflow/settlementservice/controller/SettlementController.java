package com.settleflow.settlementservice.controller;

import com.settleflow.common.exception.EntityNotFoundException;
import com.settleflow.settlementservice.domain.Settlement;
import com.settleflow.settlementservice.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementRepository settlementRepository;

    /**
     * 전체 정산 내역 조회 (테스트용)
     */
    @GetMapping("/settlements")
    public ResponseEntity<List<Settlement>> getAllSettlements() {
        List<Settlement> settlements = settlementRepository.findAll();
        return ResponseEntity.ok(settlements);
    }

    /**
     * 특정 주문의 정산 내역 조회
     * - Redis 캐싱 적용 (Look-Aside Pattern)
     * - findByOrderId()로 직접 조회 (최적화)
     */
    @GetMapping("/settlements/{orderId}")
    @Cacheable(value = "settlements", key = "#orderId", unless = "#result == null")
    public ResponseEntity<Settlement> getSettlementByOrderId(@PathVariable Long orderId) {
        log.info("Fetching settlement from MongoDB... OrderId={}", orderId);

        Settlement settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("정산 데이터", orderId));

        return ResponseEntity.ok(settlement);
    }
}