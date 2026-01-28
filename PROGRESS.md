# SettleFlow 개선 진행 상황

> 스터디 프로젝트 고도화 체크리스트

## 📊 현재 상태 분석

### ✅ 잘 구현된 부분
- [x] 이벤트 기반 아키텍처 (Kafka를 통한 서비스 분리)
- [x] 멱등성 보장 (Unique Index + Exception Handling)
- [x] Redis 캐싱 (Look-Aside Pattern)
- [x] 멀티 모듈 구조 (common, order-service, settlement-service)
- [x] Docker Compose 기반 인프라 구성
- [x] Jenkins CI/CD 파이프라인
- [x] Swagger API 문서화 설정

### 🚨 개선이 필요한 부분
아래 항목들을 우선순위별로 정리했습니다.

---

## 🎯 Phase 1: 핵심 기능 개선 (필수)

### 1. 테스트 코드 작성 ✅ 완료
**현재 상태**: ~~테스트 코드 없음~~ → **핵심 단위 테스트 완료**
**목표**: 핵심 로직에 대한 테스트 커버리지 확보

- [x] **OrderService 단위 테스트** ✅
  - [x] 주문 생성 정상 케이스
  - [x] 금액이 0인 경우
  - [x] Kafka 전송 실패 시나리오

- [x] **SettlementConsumer 단위 테스트** ✅
  - [x] 정산 계산 로직 검증
  - [x] 중복 메시지 처리 (멱등성)
  - [x] 수수료 계산 정확도
  - [x] BigDecimal 정밀도 테스트

- [x] **Repository 테스트** ✅
  - [x] `@DataJpaTest` - OrderRepository (H2 사용)
  - [x] `@DataMongoTest` - SettlementRepository (Embedded MongoDB - 향후 활성화 예정)
  - [x] Unique Index 동작 확인

- [ ] **Kafka 통합 테스트** (향후 작업)
  - [ ] `@EmbeddedKafka` 사용
  - [ ] Producer → Consumer 전체 흐름 검증
  - 참고: 현재 `@Disabled` 처리 (로컬 인프라 실행 시 수동 테스트)

**생성된 파일**:
```
order-service/src/test/java/
  ├── com/settleflow/orderservice/service/OrderServiceTest.java ✅
  └── com/settleflow/orderservice/domain/OrderRepositoryTest.java ✅

settlement-service/src/test/java/
  ├── com/settleflow/settlementservice/kafka/SettlementConsumerTest.java ✅
  ├── com/settleflow/settlementservice/domain/SettlementRepositoryTest.java ✅
  └── com/settleflow/settlementservice/integration/KafkaIntegrationTest.java (Disabled)

order-service/src/test/resources/application-test.yml ✅
settlement-service/src/test/resources/application-test.yml ✅
```

**추가된 의존성**:
- `testImplementation 'org.springframework.kafka:spring-kafka-test'`
- `testImplementation 'org.awaitility:awaitility:4.2.0'`
- `testRuntimeOnly 'com.h2database:h2'` (OrderService용)
- `testImplementation 'de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring30x:4.11.0'` (Settlement용)

**테스트 실행 방법**:
```bash
# 전체 테스트 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :order-service:test
./gradlew :settlement-service:test

# HTML 리포트 확인
open order-service/build/reports/tests/test/index.html
```

---

### 2. 입력 검증 (Validation) 추가 ✅ 완료
**현재 상태**: ~~요청 검증 없음~~ → **Bean Validation 적용 완료**
**목표**: Bean Validation으로 잘못된 입력 차단

- [x] **의존성 추가** ✅
  ```gradle
  implementation 'org.springframework.boot:spring-boot-starter-validation'
  ```

- [x] **OrderController 검증 추가** ✅
  - [x] `@Valid` 어노테이션 적용
  - [x] `CreateOrderRequest`에 `@NotNull`, `@Positive`, `@DecimalMin` 추가
  - [x] 응답 DTO 생성 (`OrderResponse`)
  - [x] `ResponseEntity<OrderResponse>` 반환

- [x] **GlobalExceptionHandler 확장** ✅
  - [x] `MethodArgumentNotValidException` 처리 (400 Bad Request)
  - [x] `IllegalArgumentException` 처리 (400)
  - [x] `EntityNotFoundException` 처리 (404 Not Found)
  - [x] Validation 에러 메시지 필드별로 수집

**수정된 파일**:
- `order-service/src/main/java/com/settleflow/orderservice/controller/OrderController.java` ✅
- `order-service/src/main/java/com/settleflow/orderservice/dto/OrderResponse.java` (신규)
- `common/src/main/java/com/settleflow/common/exception/GlobalExceptionHandler.java` ✅
- `common/src/main/java/com/settleflow/common/exception/EntityNotFoundException.java` (신규)

**검증 규칙**:
- `userId`: @NotNull, @Positive
- `amount`: @NotNull, @DecimalMin("0.01")

---

### 3. Kafka 전송 실패 시 트랜잭션 처리 ✅ 완료 (Option C)
**현재 상태**: ~~DB 저장 후 Kafka 실패 시 데이터 불일치 가능~~ → **재시도 로직으로 정합성 보장**
**목표**: 데이터 정합성 보장

**구현된 방식: Option C (최소 개선 + 자동 재시도)** ✅
- [x] **주문 상태 관리 강화**
  - [x] `OrderStatus` Enum 추가 (ORDERED, PENDING_EVENT, CANCELLED)
  - [x] 초기 상태: PENDING_EVENT (이벤트 발행 전)
  - [x] Kafka 전송 성공 시: ORDERED로 변경
  - [x] Kafka 전송 실패 시: PENDING_EVENT 유지

- [x] **예외 처리 및 로깅**
  - [x] try-catch로 Kafka 전송 실패 감지
  - [x] 실패 시 에러 로그 기록
  - [x] 주문 ID는 즉시 반환 (사용자에게는 정상 응답)

- [x] **자동 재시도 로직 (Scheduler)**
  - [x] `EventRetryScheduler` 추가 (1분마다 실행)
  - [x] PENDING_EVENT 상태의 주문 조회
  - [x] Kafka 이벤트 재발행 시도
  - [x] 성공 시 상태를 ORDERED로 업데이트

- [x] **테스트 작성**
  - [x] Kafka 전송 실패 시 PENDING_EVENT 유지 확인
  - [x] 재시도 로직 정상 동작 확인
  - [x] 재시도 대상 없을 때 동작 확인

**생성/수정된 파일**:
- `common/src/main/java/com/settleflow/common/event/OrderStatus.java` (신규) ✅
- `order-service/src/main/java/com/settleflow/orderservice/service/OrderService.java` ✅
- `order-service/src/main/java/com/settleflow/orderservice/domain/Order.java` (상태 변경 메서드 추가) ✅
- `order-service/src/main/java/com/settleflow/orderservice/domain/OrderRepository.java` (findByStatus 추가) ✅
- `order-service/src/main/java/com/settleflow/orderservice/scheduler/EventRetryScheduler.java` (신규) ✅
- `order-service/src/main/java/com/settleflow/orderservice/OrderServiceApplication.java` (@EnableScheduling) ✅
- `order-service/src/test/java/com/settleflow/orderservice/service/OrderServiceTest.java` (테스트 추가) ✅

**개선 효과**:
- Kafka 일시적 장애에도 데이터 정합성 유지
- 자동 재시도로 수동 개입 최소화
- 주문 생성 → 정산 이벤트 발행 흐름 안정성 향상
- 운영 모니터링 용이 (PENDING_EVENT 상태 조회로 실패 건 파악)

**향후 개선 가능 사항**:
- [ ] **Option A: Transactional Outbox Pattern** (완벽한 정합성)
  - [ ] `outbox_events` 테이블 추가
  - [ ] Debezium CDC 연동
- [ ] **재시도 정책 고도화**
  - [ ] 재시도 횟수 제한 (3회 이상 실패 시 FAILED 상태로)
  - [ ] Exponential Backoff (재시도 간격 점진적 증가)

---

### 4. SettlementController 조회 최적화 ✅ 완료
**현재 상태**: ~~`findAll()` 후 Stream 필터링~~ → **쿼리 메서드로 직접 조회**
**목표**: 쿼리 메서드로 직접 조회

- [x] **SettlementRepository 메서드 추가** ✅
  ```java
  Optional<Settlement> findByOrderId(Long orderId);
  ```

- [x] **Controller 수정** ✅
  - [x] `findByOrderId()` 사용
  - [x] `orElseThrow()` + `EntityNotFoundException` 적용
  - [x] `ResponseEntity<Settlement>` 반환

- [x] **Custom Exception 사용** ✅
  - [x] `EntityNotFoundException` (공통 모듈)

**수정된 파일**:
- `settlement-service/src/main/java/com/settleflow/settlementservice/domain/SettlementRepository.java` ✅
- `settlement-service/src/main/java/com/settleflow/settlementservice/controller/SettlementController.java` ✅

**개선 효과**:
- MongoDB 전체 조회(findAll) → 인덱스 활용 직접 조회
- 성능 대폭 향상 (O(n) → O(1))
- 404 에러 처리 명확화

---

## 🔧 Phase 2: 설계 개선 (권장)

### 5. 설정값 외부화
**현재 상태**: 수수료율 하드코딩 (`feeRate = 0.03`)
**목표**: 설정 파일로 관리

- [ ] `application.yml`에 추가
  ```yaml
  settlement:
    fee-rate: 0.03
  ```

- [ ] `@ConfigurationProperties` 클래스 생성
  ```java
  @ConfigurationProperties(prefix = "settlement")
  public class SettlementProperties {
      private BigDecimal feeRate;
  }
  ```

- [ ] `OrderService`에 주입하여 사용

**파일 위치**:
- `order-service/src/main/resources/application.yml`
- `order-service/src/main/java/com/settleflow/orderservice/config/SettlementProperties.java`

---

### 6. 환경별 설정 분리
**현재 상태**: 단일 `application.yml`
**목표**: 환경별 프로파일 분리

- [ ] **파일 생성**
  - [ ] `application-local.yml` (localhost 주소)
  - [ ] `application-dev.yml` (개발 서버)
  - [ ] `application-prod.yml` (운영 환경)

- [ ] **실행 시 프로파일 지정**
  ```bash
  java -jar -Dspring.profiles.active=local order-service.jar
  ```

---

### 7. API 응답 표준화
**현재 상태**: String 반환, 불일관한 응답 형식
**목표**: 표준 DTO 응답

- [ ] **공통 응답 DTO 생성**
  ```java
  // common 모듈
  @Data
  @Builder
  public class ApiResponse<T> {
      private String status;  // "success" or "error"
      private T data;
      private String message;
      private LocalDateTime timestamp;
  }
  ```

- [ ] **각 Controller에 적용**
  - [ ] `OrderController`: `ResponseEntity<ApiResponse<OrderResponse>>`
  - [ ] `SettlementController`: `ResponseEntity<ApiResponse<Settlement>>`

---

### 8. API 문서화 강화
**현재 상태**: Swagger 의존성만 존재
**목표**: OpenAPI 어노테이션으로 상세 문서화

- [ ] **Controller에 어노테이션 추가**
  - [ ] `@Tag` (컨트롤러 설명)
  - [ ] `@Operation` (API 설명)
  - [ ] `@ApiResponses` (응답 코드별 설명)
  - [ ] `@Schema` (DTO 필드 설명)

- [ ] **Swagger Config 생성**
  - [ ] API 정보, 작성자, 버전 등 메타데이터 설정

**참고**:
```java
@Tag(name = "주문 관리", description = "주문 생성 및 조회 API")
@RestController
public class OrderController {

    @Operation(summary = "주문 생성", description = "신규 주문을 생성하고 정산 이벤트를 발행합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 생성 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (검증 실패)")
    })
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(...) { }
}
```

---

## 🚀 Phase 3: 고급 기능 (선택)

### 9. 모니터링 및 메트릭
- [ ] **Spring Boot Actuator 활성화**
  - [ ] `/actuator/health`, `/actuator/metrics` 엔드포인트

- [ ] **Prometheus + Grafana**
  - [ ] Micrometer 의존성 추가
  - [ ] Custom Metric (주문 생성 횟수, Kafka Lag 등)
  - [ ] Grafana 대시보드 구성

- [ ] **Redis Cache Metrics**
  - [ ] Cache Hit Rate 측정
  - [ ] 캐시 Eviction 모니터링

---

### 10. Dead Letter Queue (DLQ) 구현
**목표**: Consumer 처리 실패 시 재시도 전략

- [ ] **Kafka DLQ Topic 생성**
  - [ ] `order-create-topic.DLT`

- [ ] **Retry 설정**
  ```yaml
  spring:
    kafka:
      consumer:
        properties:
          # 3회 재시도 후 DLQ로 전송
          max.poll.records: 10
  ```

- [ ] **ErrorHandler 등록**
  - [ ] `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`

---

### 11. 분산 추적 (Distributed Tracing)
- [ ] **Spring Cloud Sleuth** (Trace ID 자동 생성)
- [ ] **Zipkin 연동**
  - [ ] `docker-compose.yml`에 Zipkin 추가
  - [ ] Kafka를 통한 Trace 연결 확인

---

### 12. Circuit Breaker 패턴
- [ ] **Resilience4j 도입**
  - [ ] Kafka 전송 실패 시 Circuit Open
  - [ ] Fallback 메서드 정의

---

## 📚 학습 심화 과제 (Advanced)

### A. Transactional Outbox Pattern 완벽 구현
- [ ] Debezium CDC (Change Data Capture)
- [ ] Outbox Event 자동 발행

### B. CQRS Pattern 적용
- [ ] Command Model (Write): Order Service
- [ ] Query Model (Read): 별도 ReadModel Service
- [ ] Event Sourcing과 결합

### C. Saga Pattern 구현
- [ ] 주문 취소 시 정산 데이터도 롤백
- [ ] Choreography vs Orchestration 비교

### D. Event Sourcing
- [ ] 모든 도메인 이벤트를 Event Store에 저장
- [ ] Event Replay로 상태 복원

---

## 🎓 학습 체크포인트

### 개념 이해도 확인
- [ ] At-least-once vs Exactly-once 차이
- [ ] Idempotency가 중요한 이유
- [ ] CAP Theorem과 이 프로젝트의 선택
- [ ] Saga vs 2PC (Two-Phase Commit) 차이
- [ ] Redis Cache Aside vs Write Through 비교

### 실전 시나리오 대응
- [ ] Kafka Broker 1대가 다운되면?
- [ ] MongoDB Replica Set 구성은?
- [ ] Redis Failover 전략은?
- [ ] 특정 서비스만 재배포할 때 영향은?

---

## 📝 진행 상황 기록

### 2026-01-27
- [x] Phase 1 시작 ✅
- [x] 테스트 코드 작성 환경 구성 ✅
  - JUnit Platform 설정
  - H2, Embedded MongoDB 의존성 추가
  - Awaitility 추가

- [x] **테스트 코드 작성 완료** ✅
  - OrderServiceTest (3개 테스트)
  - SettlementConsumerTest (6개 테스트)
  - OrderRepositoryTest (4개 테스트)
  - SettlementRepositoryTest (향후 활성화)
  - KafkaIntegrationTest (향후 활성화)

- [x] **입력 검증 완료** ✅
  - Bean Validation 적용
  - OrderController에 @Valid 추가
  - OrderResponse DTO 생성

- [x] **GlobalExceptionHandler 확장** ✅
  - MethodArgumentNotValidException 처리
  - EntityNotFoundException 처리
  - 필드별 에러 메시지 수집

- [x] **SettlementController 최적화** ✅
  - findByOrderId() 쿼리 메서드 추가
  - findAll() + Stream 제거
  - 성능 개선 완료

- [x] **Kafka 전송 실패 처리 완료** ✅ (2026-01-28)
  - OrderStatus Enum 추가
  - 주문 상태 관리 (PENDING_EVENT → ORDERED)
  - EventRetryScheduler 구현 (1분마다 재시도)
  - 관련 테스트 3개 추가

**Phase 1 진행률**: 4/4 완료 (100%) 🎉

---

## 🔗 참고 자료

- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/html/)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [MongoDB Indexing Best Practices](https://www.mongodb.com/docs/manual/indexes/)
- [Redis Caching Strategies](https://redis.io/docs/manual/patterns/)
- [Spring Boot Testing](https://spring.io/guides/gs/testing-web/)

---

**Last Updated**: 2026-01-28
**Current Phase**: Phase 1 완료 ✅ / Phase 2 준비 중
