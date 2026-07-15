# coupon-lab

선착순 쿠폰 발급 시스템 — 동시성 제어 방식을 단계별로 적용하며
부하 테스트로 개선 효과를 검증하는 프로젝트

## 요약

rush 시나리오(재고 100 / 500명 동시 요청) 기준. 괄호 수치는 sustained(지속 부하) 측정값.

| 버전 | 방식 | 초과 발급 | p95 응답시간 | TPS |
|---|---|---|---|---|
| **V0** | 동시성 제어 없음 | **400건 (500/100)** | 2.94s | ~154 |
| **V1** | DB 비관적 락 | **0건** | 1.74s | ~256 (지속 부하 시 ~121) |
| V2 | Redis 원자 연산 | (진행 예정) | | |
| V3 | Kafka 비동기 발급 | (진행 예정) | | |

## 기술 스택

Java 17, Spring Boot 4.1.0, Spring Data JPA, MySQL 8.0,
k6, Prometheus, Grafana, Docker Compose

## 실행 방법

````bash
# 인프라 기동 (MySQL, Prometheus, Grafana)
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun
````

- Grafana: http://localhost:3000 (대시보드: `monitoring/dashboard.json` import)
- Prometheus: http://localhost:9090

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/coupons` | 쿠폰 생성 (name, totalQuantity) |
| POST | `/api/coupons/{couponId}/issue?userId={userId}` | 쿠폰 발급 |

응답 코드: 발급 성공 `200` / 매진 `409 Conflict` / 존재하지 않는 쿠폰 `404 Not Found`
— 비즈니스 거절을 서버 장애(5xx)와 분리해 Error Rate 지표가 실제 장애만 반영

````bash
# 쿠폰 생성 — 생성된 쿠폰 ID 반환
curl -X POST http://localhost:8080/api/coupons \
  -H "Content-Type: application/json" \
  -d '{"name": "test", "totalQuantity": 100}'

# 쿠폰 발급
curl -X POST "http://localhost:8080/api/coupons/1/issue?userId=1"
````

> 유저/인증 도메인은 범위에서 제외 — `userId`는 검증된 외부 식별자로 가정
> (동시성 제어라는 핵심 문제에 집중하기 위한 의도적 범위 축소)

## 데이터 모델

같은 사건(발급)을 두 방식으로 기록하고, 둘의 일치 여부로 정합성을 검증한다.

| 테이블 | 역할 | 기록 방식 |
|---|---|---|
| `coupon` | 쿠폰 정보 + 발급 집계 | `issued_quantity` 컬럼을 발급마다 +1 |
| `issued_coupon` | 발급 이력 (누가·언제) | 발급마다 행 추가 |

- 매진 판정: `issued_quantity >= total_quantity`
- **UPDATE는 동시 요청 시 덮어쓰기(lost update)가 발생할 수 있고, INSERT는 그렇지 않다**
- 검증 원칙: `k6 성공 응답 수 = COUNT(issued_coupon) = coupon.issued_quantity`
  — 세 값이 일치하면 유실·초과 없이 정확히 기록된 것

## 측정 환경 및 방법

````mermaid
flowchart LR
    k6["k6 (Docker)"] -->|HTTP| app["Spring Boot"]
    app -->|JPA| mysql[("MySQL (Docker)")]
    prom["Prometheus (Docker)"] -.->|scrape 5s| app
    prom --> grafana["Grafana (Docker)"]
````

- 로컬 단일 머신, 앱·DB·부하 생성기 동일 호스트
- **절대 성능이 아닌 버전 간 상대 비교 목적** — 동일 조건 고정이 원칙
- HikariCP 커넥션 풀: 기본값 10 / SQL 로그 비활성화 상태에서 측정
- 부하 시나리오 2종 (목적별 분리):
  - **rush** (`k6/issue-coupon.js`): VU 500 × 1회, 재고 100 — 오픈 순간 동시 진입 재현, **정합성 검증** (spike test)
  - **sustained** (`k6/sustained.js`): VU 200 × 30s 반복, 재고 100,000 — 지속 부하에서의 시스템 거동 관측: 처리량 천장, 커넥션 풀 포화 (load test)
- 두 시나리오 모두 k6 `setup()`에서 쿠폰 생성 API를 호출해 시작 상태 고정
  (setup 요청은 부하 통계에서 분리됨)

측정 절차:

````bash
# 1. 초기화 (테이블 및 auto_increment 리셋)
docker compose exec mysql mysql -uroot -proot coupon \
  -e "TRUNCATE TABLE issued_coupon; TRUNCATE TABLE coupon;"

# 2. 부하 테스트 (sustained는 /scripts/sustained.js 로 교체)
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v ${PWD}/k6:/scripts grafana/k6 run /scripts/issue-coupon.js

# 3. 결과 검증 — 발급 이력 수와 발급 집계가 일치하는지 대조
docker compose exec mysql mysql -uroot -proot coupon \
  -e "SELECT COUNT(*) AS issued_count FROM issued_coupon WHERE coupon_id = 1;
      SELECT issued_quantity, total_quantity FROM coupon WHERE id = 1;"
````

## 개선 여정

### V0 — 동시성 제어 없음 (tag: `v0`)

평범한 `@Transactional` + JPA dirty checking 구현. 단일 스레드에서는
완벽하게 동작하지만, 동시 요청에서 어떤 일이 벌어지는지 측정한 baseline.

**결과**

| 지표 | 값 | 의미 |
|---|---|---|
| 발급 성공 응답 | 500 / 500 | 전원 200 OK — 매진 검증 미작동 |
| 발급 이력 (`issued_coupon` 행 수) | **500건** | 재고(100)의 5배 초과 발급 |
| 발급 집계 (`coupon.issued_quantity`) | **50** | 500회 증가 중 90% 유실 (lost update) |
| 응답 시간 | avg 1.64s / p95 2.94s | 커넥션 풀(10) 대기가 지배적 |
| TPS | ~154 | 500건 / 3.2s |
| 에러율 | 0% | 거절됐어야 할 400건까지 전부 성공 |

<img src="docs/images/v0-k6-result.png" alt="V0 k6 측정 결과" width="600">

**원인 분석 — lost update**

발급 로직은 `조회(SELECT) → 검증+증가(JVM 메모리) → 저장(커밋 시 UPDATE)`
세 단계로 분리되어 있다. 동시에 진입한 트랜잭션들이 같은 스냅샷을 읽고
서로의 갱신을 덮어쓴다:

````
[Tx A] SELECT → issuedQuantity = 0 읽음
[Tx B] SELECT → issuedQuantity = 0 읽음   ← 같은 값을 읽음
[Tx A] 0 < 100 검증 통과 → 1로 커밋
[Tx B] 0 < 100 검증 통과 → 1로 커밋      ← A의 갱신이 유실됨
````

이로 인해 두 가지가 연쇄적으로 무너진다:

1. **집계 유실**: 500회의 `+1` 중 450회가 덮어쓰기로 증발 (최종값 50)
2. **방어 로직 무력화**: 집계가 100에 도달한 적이 없으므로
   `isSoldOut()`은 한 번도 true가 되지 않음 — 전원 발급 성공

`@Transactional`은 원자성(all-or-nothing)을 보장할 뿐,
MySQL 기본 격리 수준(REPEATABLE READ)에서 read-modify-write 경합을
막아주지 않는다. 격리 수준이 보장하는 것은 "내 트랜잭션 안에서 읽기의
일관성"이지 "내가 읽은 값을 남이 못 바꾸게 하는 것"이 아니기 때문이다.

한편 `issued_coupon`은 INSERT만 발생하므로 유실 없이 500건이 전부 남았다.
**발급 이력(500)과 발급 집계(50)의 불일치** 자체가 race condition의
가장 선명한 증거다.

**한계 기록**

- 부하가 약 3초에 종료되어 5s scrape 간격의 Grafana에는 순간 부하가
  온전히 반영되지 않음 → k6 출력을 1차 자료로 사용
- 초과 발급 "발생" 자체는 안정적으로 재현되나, 구체적 수치(유실률,
  응답시간)는 스레드 스케줄링에 따라 실행마다 달라짐
- 로컬 측정으로 부하 생성기와 서버가 CPU를 공유 — 절대치 해석 불가

### V1 — DB 비관적 락 (tag: `v1`)

`findById` → `findByIdWithLock` (`@Lock(PESSIMISTIC_WRITE)`, SELECT FOR UPDATE).
읽는 순간 행을 잠가 "조회→검증→갱신"을 독점 구간으로 만든다.

**측정 1 — rush (재고 100 / 500명 × 1회): 정합성 검증**

| 지표 | V0 | V1 |
|---|---|---|
| 초과 발급 | 400건 | **0건** |
| 발급 이력 vs 집계 | 500 vs 50 (불일치) | **100 = 100 (일치)** |
| p95 | 2.94s | **1.74s (개선)** |
| TPS | ~154 | ~256 |

**예측과 결과가 달랐다**: 직렬화로 성능 악화를 예상했으나 오히려 개선.
원인 — ① 커넥션 풀(10)이 이미 동시성을 제한해 V0도 사실상 직렬이었고
② 매진 후 400건이 예외로 조기 종료되어(INSERT/UPDATE 없이 락 즉시 반납)
총 작업량 자체가 감소. "락 = 느려짐"은 경합 형태와 실패 경로 비용에 따라
성립하지 않을 수 있다.

<img src="docs/images/v1-grafana-rush.png" width="600">

**측정 2 — sustained (재고 10만 / 200 VU × 30s): 부하 특성 관측**

조기 종료 효과를 제거하고(전 요청이 검증→INSERT→UPDATE 전 과정을 수행)
락의 직렬화 비용을 직접 측정.

| 지표 | 값 |
|---|---|
| 처리량 천장 | **TPS ~121** |
| p95 / max | 1.78s / 1.99s |
| 정합성 | **3,848 = 3,848 = 3,848** (k6 성공 응답 = 발급 이력 = 발급 집계) — 대량 경합에서 유실 0건 |
| 커넥션 풀 | **active 10 고정, pending ~190** (아래 그래프) |
| timeout | 미발생 (200 VU 기준: pending이 30s 한도 내 소화됨) |

<img src="docs/images/v1-k6-result.png" width="600">
<img src="docs/images/v1-grafana-sustained.png" width="600">

커넥션 10개 전부가 락 대기에 묶이고 190개 요청이 풀 앞에 대기하는
정상 상태(steady state). med(1.61s)와 p95(1.78s)가 근접 — 락 줄서기에서는
대기 시간이 확률이 아니라 대기열 길이로 결정되므로 "모두가 균등하게 느린"
분포가 나타난다.

**V1의 결론과 한계**: 정합성은 완전하나, 처리량이 단일 행 락의 직렬 처리
속도에 종속된다. 부하가 커질수록(VU 증가) 대기열만 길어지고 처리량은
~121에서 불변 — 서버를 늘려도 병목(DB의 한 행)이 그대로이므로 수평 확장이
불가능한 구조. → V2에서 재고 판정을 DB 밖(Redis 원자 연산)으로 분리.

