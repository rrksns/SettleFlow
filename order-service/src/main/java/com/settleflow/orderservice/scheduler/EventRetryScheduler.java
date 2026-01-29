package com.settleflow.orderservice.scheduler;

import com.settleflow.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PENDING_EVENT 상태의 주문에 대해 이벤트 재발행을 시도하는 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRetryScheduler {

    private final OrderService orderService;

    /**
     * PENDING_EVENT 상태의 주문에 대해 이벤트 재발행 시도
     * 재시도 간격과 초기 지연은 application.yml에서 설정 가능
     */
    @Scheduled(
            fixedDelayString = "${settlement.retry-interval-ms}",
            initialDelayString = "${settlement.initial-delay-ms}"
    )
    public void retryPendingEvents() {
        log.info("===== 이벤트 재발행 스케줄러 시작 =====");
        try {
            orderService.retryPendingEventOrders();
        } catch (Exception e) {
            log.error("이벤트 재발행 스케줄러 실행 중 오류 발생", e);
        }
        log.info("===== 이벤트 재발행 스케줄러 종료 =====");
    }
}
