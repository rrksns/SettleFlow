package com.settleflow.common.event;

/**
 * 주문 상태 Enum
 */
public enum OrderStatus {
    /**
     * 주문 완료 (DB 저장 성공, Kafka 이벤트 발행 성공)
     */
    ORDERED,

    /**
     * 이벤트 발행 대기 중 (DB 저장 성공, Kafka 이벤트 발행 실패)
     * 재시도 대상
     */
    PENDING_EVENT,

    /**
     * 주문 취소
     */
    CANCELLED
}
