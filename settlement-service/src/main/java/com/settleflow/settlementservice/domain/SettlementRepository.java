package com.settleflow.settlementservice.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SettlementRepository extends MongoRepository<Settlement, String> {
    /**
     * orderId로 정산 데이터 조회
     * @param orderId 주문 ID
     * @return 정산 데이터 (Optional)
     */
    Optional<Settlement> findByOrderId(Long orderId);
}