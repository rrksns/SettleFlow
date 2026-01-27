package com.settleflow.settlementservice.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Disabled;

@Disabled("MongoDB Repository 테스트는 로컬 MongoDB 실행 시에만 수행")
@DataMongoTest
@ActiveProfiles("test")
@DisplayName("SettlementRepository 테스트")
class SettlementRepositoryTest {

    @Autowired
    private SettlementRepository settlementRepository;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 데이터 초기화
        settlementRepository.deleteAll();
    }

    @Test
    @DisplayName("정산 데이터 저장 및 조회")
    void saveAndFind() {
        // given
        Settlement settlement = Settlement.builder()
                .orderId(100L)
                .userId(1L)
                .totalAmount(new BigDecimal("10000.00"))
                .fee(new BigDecimal("300.00"))
                .settleAmount(new BigDecimal("9700.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        // when
        Settlement savedSettlement = settlementRepository.save(settlement);

        // then
        assertThat(savedSettlement.getId()).isNotNull();
        assertThat(savedSettlement.getOrderId()).isEqualTo(100L);
        assertThat(savedSettlement.getUserId()).isEqualTo(1L);
        assertThat(savedSettlement.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(savedSettlement.getFee()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(savedSettlement.getSettleAmount()).isEqualByComparingTo(new BigDecimal("9700.00"));
        assertThat(savedSettlement.getStatus()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("Unique Index - 동일 orderId 중복 저장 방지")
    void uniqueIndex_DuplicateOrderId() {
        // given
        Settlement settlement1 = Settlement.builder()
                .orderId(200L)  // 동일한 orderId
                .userId(1L)
                .totalAmount(new BigDecimal("10000.00"))
                .fee(new BigDecimal("300.00"))
                .settleAmount(new BigDecimal("9700.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        Settlement settlement2 = Settlement.builder()
                .orderId(200L)  // 동일한 orderId (중복!)
                .userId(2L)
                .totalAmount(new BigDecimal("20000.00"))
                .fee(new BigDecimal("600.00"))
                .settleAmount(new BigDecimal("19400.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        // when
        settlementRepository.save(settlement1);

        // then
        // 동일한 orderId로 저장 시도 시 DuplicateKeyException 발생
        assertThatThrownBy(() -> settlementRepository.save(settlement2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("전체 정산 데이터 조회")
    void findAll() {
        // given
        Settlement settlement1 = Settlement.builder()
                .orderId(100L)
                .userId(1L)
                .totalAmount(new BigDecimal("10000.00"))
                .fee(new BigDecimal("300.00"))
                .settleAmount(new BigDecimal("9700.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        Settlement settlement2 = Settlement.builder()
                .orderId(200L)
                .userId(2L)
                .totalAmount(new BigDecimal("20000.00"))
                .fee(new BigDecimal("600.00"))
                .settleAmount(new BigDecimal("19400.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        // when
        settlementRepository.save(settlement1);
        settlementRepository.save(settlement2);

        // then
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(2);
    }

    @Test
    @DisplayName("ID로 정산 데이터 조회")
    void findById() {
        // given
        Settlement settlement = Settlement.builder()
                .orderId(300L)
                .userId(3L)
                .totalAmount(new BigDecimal("30000.00"))
                .fee(new BigDecimal("900.00"))
                .settleAmount(new BigDecimal("29100.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        Settlement savedSettlement = settlementRepository.save(settlement);

        // when
        Optional<Settlement> foundSettlement = settlementRepository.findById(savedSettlement.getId());

        // then
        assertThat(foundSettlement).isPresent();
        assertThat(foundSettlement.get().getOrderId()).isEqualTo(300L);
        assertThat(foundSettlement.get().getUserId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("존재하지 않는 정산 데이터 조회")
    void findById_NotFound() {
        // when
        Optional<Settlement> foundSettlement = settlementRepository.findById("non-existent-id");

        // then
        assertThat(foundSettlement).isEmpty();
    }

    @Test
    @DisplayName("정산 데이터 삭제")
    void delete() {
        // given
        Settlement settlement = Settlement.builder()
                .orderId(400L)
                .userId(4L)
                .totalAmount(new BigDecimal("40000.00"))
                .fee(new BigDecimal("1200.00"))
                .settleAmount(new BigDecimal("38800.00"))
                .orderedAt(LocalDateTime.now().toString())
                .status("WAITING")
                .build();

        Settlement savedSettlement = settlementRepository.save(settlement);

        // when
        settlementRepository.delete(savedSettlement);

        // then
        Optional<Settlement> foundSettlement = settlementRepository.findById(savedSettlement.getId());
        assertThat(foundSettlement).isEmpty();
    }
}
