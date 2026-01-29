package com.settleflow.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 정산 관련 설정값
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "settlement")
public class SettlementProperties {

    /**
     * 수수료율 (기본값: 0.03 = 3%)
     */
    private BigDecimal feeRate = new BigDecimal("0.03");

    /**
     * 이벤트 재시도 간격 (밀리초, 기본값: 60000ms = 1분)
     */
    private Long retryIntervalMs = 60000L;

    /**
     * 초기 지연 시간 (밀리초, 기본값: 10000ms = 10초)
     */
    private Long initialDelayMs = 10000L;
}
