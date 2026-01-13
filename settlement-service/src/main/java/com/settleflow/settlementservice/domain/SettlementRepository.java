package com.settleflow.settlementservice.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SettlementRepository extends MongoRepository<Settlement, String> {
    // 필요한 쿼리 메서드 자동 생성 (예: orderId로 조회)
}