# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

SettleFlow는 이벤트 기반 아키텍처를 활용한 주문-정산 시스템입니다. Kafka를 통해 주문 서비스와 정산 서비스를 비동기로 연결하며, 멀티 모듈 Gradle 프로젝트로 구성되어 있습니다.

## 빌드 및 실행 명령어

### 인프라 구성 (Docker)
```bash
cd docker
docker-compose up -d
```

### Gradle 빌드 (멀티 모듈)
```bash
# 전체 빌드 (테스트 제외)
./gradlew clean build -x test

# 개별 모듈 빌드
./gradlew :common:clean :common:build -x test
./gradlew :order-service:clean :order-service:build -x test
./gradlew :settlement-service:clean :settlement-service:build -x test

# 테스트 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :order-service:test
```

### 서비스 실행
```bash
# Order Service (8081 포트)
java -jar order-service/build/libs/order-service-0.0.1-SNAPSHOT.jar

# Settlement Service (8082 포트)
java -jar settlement-service/build/libs/settlement-service-0.0.1-SNAPSHOT.jar
```

### 개발 환경 접속 정보
- Order Service Swagger: http://localhost:8081/swagger-ui/index.html
- Settlement Service Swagger: http://localhost:8082/swagger-ui/index.html
- Kafka UI: http://localhost:8989
- Jenkins: http://localhost:9090
- MySQL: localhost:3306 (root/root)
- MongoDB: localhost:27017 (root/root)
- Redis: localhost:6379

## 아키텍처 구조

### 멀티 모듈 구성
이 프로젝트는 3개의 Gradle 서브 모듈로 구성됩니다:

1. **common** - 공통 DTO, 이벤트, 예외 처리
   - `com.settleflow.common.event.OrderCreatedEvent`: Kafka 메시지 스키마
   - `com.settleflow.common.event.SettlementStatus`: 정산 상태 Enum
   - `com.settleflow.common.exception.GlobalExceptionHandler`: 전역 예외 처리
   - bootJar 비활성화 (라이브러리 모듈)

2. **order-service** - 주문 관리 및 Kafka Producer
   - MySQL 사용 (주문 데이터 저장)
   - `OrderCreatedEvent`를 Kafka로 발행
   - 포트: 8081

3. **settlement-service** - 정산 처리 및 Kafka Consumer
   - MongoDB 사용 (정산 데이터 저장)
   - Redis 캐싱 (Look-Aside Pattern)
   - Kafka Consumer로 주문 이벤트 수신 및 정산 계산
   - 포트: 8082

### 이벤트 흐름
```
Client → OrderController → OrderService → MySQL
                              ↓
                       OrderProducer → Kafka (order-create-topic)
                                          ↓
                              SettlementConsumer → MongoDB
                                          ↓
                              SettlementController (Redis Cache)
```

### 핵심 설계 패턴

#### 1. 멱등성 보장
정산 서비스는 중복 메시지 처리를 방지합니다:
- **DB 레벨**: `Settlement.orderId`에 `@Indexed(unique = true)` 적용
- **App 레벨**: `SettlementConsumer`에서 `DuplicateKeyException` 처리
- 중복 메시지 수신 시 WARN 로그만 남기고 정상 Ack 처리하여 Retry Storm 방지

#### 2. Redis 캐싱 (Look-Aside Pattern)
- `SettlementController.getSettlementByOrderId()`에 `@Cacheable` 적용
- TTL: 10분
- 직렬화: `GenericJackson2JsonRedisSerializer` (JSON 가시성)
- Cache Miss 시에만 MongoDB 조회

#### 3. Kafka 설정
- **Producer** (order-service):
  - `JsonSerializer` 사용
  - `spring.json.trusted.packages: "*"`

- **Consumer** (settlement-service):
  - `JsonDeserializer` 사용
  - `spring.json.value.default.type: "com.settleflow.common.event.OrderCreatedEvent"` (헤더 없을 때 기본 타입)
  - `spring.json.use.type.headers: false` (타입 헤더 미사용)
  - `group-id: settlement-group`
  - `auto-offset-reset: earliest`

## 주요 파일 위치

### 설정 파일
- `docker/docker-compose.yml` - 인프라 구성 (Kafka, MySQL, MongoDB, Redis, Jenkins)
- `order-service/src/main/resources/application.yml` - Order Service 설정
- `settlement-service/src/main/resources/application.yml` - Settlement Service 설정
- `Jenkinsfile` - CI/CD 파이프라인 정의

### 핵심 비즈니스 로직
- `order-service/src/main/java/com/settleflow/orderservice/kafka/OrderProducer.java` - Kafka Producer
- `settlement-service/src/main/java/com/settleflow/settlementservice/kafka/SettlementConsumer.java` - Kafka Consumer (멱등성 처리 포함)
- `settlement-service/src/main/java/com/settleflow/settlementservice/controller/SettlementController.java` - Redis 캐싱 조회 API

### 도메인 모델
- `order-service/src/main/java/com/settleflow/orderservice/domain/Order.java` - MySQL Entity
- `settlement-service/src/main/java/com/settleflow/settlementservice/domain/Settlement.java` - MongoDB Document (Unique Index)

## Jenkins CI/CD

Jenkins 파이프라인은 다음 단계로 구성됩니다:
1. Checkout - GitHub 코드 가져오기
2. Permission Grant - `chmod +x ./gradlew`
3. Build Common - 공통 모듈 빌드 (다른 모듈의 의존성)
4. Build Order Service
5. Build Settlement Service

**중요**: Gradle Wrapper 파일들(`gradle/wrapper/*`, `gradlew`, `gradlew.bat`)은 `.gitignore`에 있더라도 `git add -f`로 강제 커밋되어야 CI 환경에서 빌드 가능합니다.

## 트러블슈팅 가이드

### Kafka Retry Storm
**증상**: Consumer에서 무한 재시도 루프
**원인**: JSON Deserialization 실패 (헤더 정보 부재)
**해결**: `application.yml`에 `spring.json.value.default.type` 명시

### 중복 정산 데이터
**증상**: 동일 주문에 대한 정산이 여러 번 저장됨
**원인**: Kafka At-least-once 특성으로 중복 메시지 수신
**해결**: `Settlement.orderId`에 Unique Index + `SettlementConsumer`에서 중복 예외 처리

### MongoDB Unique Index 미작동
**증상**: `@Indexed(unique = true)`가 적용되지 않음
**원인**: `spring.data.mongodb.auto-index-creation: true` 설정 누락
**해결**: `settlement-service/application.yml`에 설정 추가

### Jenkins 빌드 실패 (gradlew 없음)
**증상**: `./gradlew: No such file or directory`
**원인**: Gradle Wrapper 파일이 Git에 없음
**해결**: `git add -f gradle/wrapper gradlew gradlew.bat` 강제 커밋

## 개발 시 주의사항

1. **공통 모듈 변경 시**: `common` 모듈 변경 후 반드시 `:common:build` 먼저 실행
2. **BigDecimal 사용**: 금액 계산은 `BigDecimal`로 처리 (float/double 사용 금지)
3. **Kafka 메시지 스키마 변경**: `OrderCreatedEvent` 변경 시 Producer와 Consumer 모두 재배포 필요
4. **Redis 캐시 무효화**: Settlement 데이터 업데이트 시 캐시 eviction 고려
5. **MongoDB 인덱스**: 새로운 Unique 제약 추가 시 `auto-index-creation: true` 확인
