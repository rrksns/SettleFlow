package com.settleflow.common.enums;

public enum SettlementStatus {
    WAITING,    // 정산 대기 (주문 발생 직후)
    COMPLETE,   // 정산 완료 (배치 처리 후)
    FAILED      // 정산 실패 (오류 발생)
}