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
     * 1분마다 PENDING_EVENT 상태의 주문에 대해 이벤트 재발행 시도
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000) // 1분마다, 최초 10초 후 시작
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
