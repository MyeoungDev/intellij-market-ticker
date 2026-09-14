# 네이버 리서치 API v2 이관 계획

## Requirements Summary

현재 리서치 탭은 네이버의 구 endpoint를 호출한다. `category-lastest`, `recent-popular`, `industry-research`는 현재 404이며, 그 결과 `ResearchFacadeService.loadResearchHome()`이 빈 결과를 받아 홈 목록이 비어 보인다. 네이버 최신 리서치 페이지는 `/api/stockSecurity/researches/v2/*` 계약을 사용하므로, 리서치 전용 호출 경로를 최신 계약으로 이관한다. 뉴스 조회 경로와 리서치 경로의 분리는 유지한다.

범위:

- 리서치 홈의 카테고리별 최신 리포트 조회
- 리서치 홈의 인기/주간/분석 영역에 필요한 최신 API 대응
- 랭킹 리서치 및 종목별 리서치 API의 v2 계약 검증·이관
- DTO/domain 매핑과 UI의 빈 결과·실패 상태 처리
- 실 API 계약을 고정하는 WireMock 회귀 테스트

비범위:

- 뉴스 API 및 뉴스 더보기 로직의 추가 변경
- 새로운 외부 의존성 추가
- 네이버 리서치 페이지 전체 UI 재설계
- API 키/인증 체계 변경

## Evidence and Current Boundaries

- 기본 URL과 리서치 URL은 `NaverClient.kt:96-102`에 정의되어 있다.
- 홈 로딩은 `ResearchFacadeService.kt:36-45`에서 카테고리 최신 API와 랭킹 API를 조합한다.
- 랭킹 탭은 `ResearchView.kt:366-403`, 종목 리서치는 `ResearchView.kt:406-452`에서 별도 로딩한다.
- 현재 라이브 확인 결과 구 endpoint는 대부분 404이고, 구 `ranking` endpoint만 200이다.
- 네이버 최신 리서치 페이지 번들에서 확인된 v2 경로는 다음과 같다.
  - `/api/stockSecurity/researches/v2/latestResearch?size=...`
  - `/api/stockSecurity/researches/v2/analysis-focus`
  - `/api/stockSecurity/researches/v2/industry/industries?...`
  - `/api/stockSecurity/researches/v2/brokers`
  - `/api/stockSecurity/researches/v2/{researchType}/{nid}/...`
  - `/api/stockSecurity/researches/v2/company/...`
  - 랭킹은 현재 `/api/domestic/research/ranking`이 200이므로 응답 계약을 확인한 뒤 유지 또는 v2로 전환한다.

## Acceptance Criteria

1. 리서치 홈 진입 시 구 `category-lastest`, `category-latest`, `recent-popular`, `industry-research` endpoint가 호출되지 않는다.
2. 최신 리포트 영역은 v2 `latestResearch`의 실제 응답을 domain 모델로 변환해 카테고리별로 표시한다.
3. 랭킹 리서치는 라이브 계약에 맞는 요청 파라미터와 응답 매핑으로 표시되며, 기존 랭킹 타입 4종 회귀 테스트가 통과한다.
4. 종목 리서치는 국내 종목 선택 후 v2 회사 리서치 목록을 표시하고, 결과 없음과 HTTP/파싱 실패를 구분해 사용자에게 표시한다.
5. 홈 API 하나가 실패해도 랭킹 또는 종목 리서치처럼 독립적인 영역은 가능한 범위에서 계속 표시된다.
6. 각 네트워크 호출에는 연결/응답 timeout이 있고, 실패 시 UI 로딩 상태가 영구히 남지 않는다.
7. 뉴스 탭 로딩 및 뉴스 더보기 동작에는 리서치 API 호출이 발생하지 않는다.
8. 전체 테스트와 `buildPlugin`이 성공하고, `git diff --check`가 통과한다.

## Implementation Steps

### 1. 라이브 계약과 응답 fixture 확정

- 네이버 최신 번들의 실제 요청 파라미터와 각 응답 shape을 다시 캡처한다.
- `latestResearch`, `analysis-focus`, `industry/industries`, `brokers`, 회사 목록/상세 endpoint를 대상으로 최소 fixture를 만든다.
- 랭킹 구 endpoint를 유지할지 v2 endpoint로 전환할지 응답 계약과 기능 범위를 기준으로 결정한다.

### 2. NaverClient endpoint 및 DTO 이관

- `src/main/kotlin/com/github/myeoungdev/marketticker/infrastructure/naver/NaverClient.kt`의 리서치 URL 기본값을 v2 계약으로 교체한다.
- 기존 `NaverResearchLatestResponse`, `NaverResearchArticle`, `NaverResearchRankingResponse`, 종목 리서치 DTO가 실제 JSON을 안전하게 수용하는지 확인하고 필요한 필드만 보완한다.
- 공통 HTTP header, timeout, non-200/파싱 오류 로깅 패턴을 재사용한다.
- 더 이상 호출되지 않는 구 endpoint와 dead fallback은 제거하되, 테스트용 URL 주입 구조는 유지한다.

### 3. Provider/Facade 상태와 매핑 정리

- `NaverResearchProvider.kt`에서 v2 응답을 `ResearchArticle`, `ResearchCategory`, `ResearchRankingBundle`로 변환한다.
- `ResearchFacadeService.kt`의 홈 조합이 한 영역 실패 때문에 전체가 빈 화면이 되지 않도록 독립 결과와 실패 상태를 보존한다.
- 캐시 성공 결과와 실패 결과의 재사용 정책을 확인하고, 실패가 TTL 동안 영구 고착되지 않도록 한다.

### 4. ResearchView 로딩·실패·빈 상태 보완

- `src/main/kotlin/com/github/myeoungdev/marketticker/ui/view/ResearchView.kt`에서 예외/빈 결과/로딩 상태를 명확히 표시한다.
- refresh 및 탭 전환 시 이전 요청이 늦게 도착해 최신 상태를 덮어쓰지 않도록 필요하면 요청 세대 또는 취소 정책을 적용한다.
- More/재시도 성격의 동작이 있다면 성공·실패·timeout 모두에서 spinner가 해제되는지 보장한다.

### 5. 회귀 테스트와 검증

- `NaverClientTest.kt`, `NaverResearchProviderTest.kt`, `ResearchFacadeServiceTest.kt`에 v2 응답/매핑/실패 격리 테스트를 추가한다.
- 구 endpoint가 요청되지 않는 계약 테스트를 추가한다.
- 뉴스 테스트에서 리서치 호출이 계속 0회인지 확인한다.
- 전체 테스트, plugin 빌드, diff 검사 후 최신 배포 산출물의 timestamp와 포함 소스를 확인한다.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| 네이버 v2 응답 필드가 영역별로 다름 | 라이브 응답별 fixture와 nullable/default DTO 필드 사용 |
| v2 API가 브라우저 요청과 JVM 요청을 다르게 처리함 | User-Agent/Accept/Origin을 기존 패턴으로 유지하고 WireMock 외 라이브 smoke 확인 |
| 하나의 홈 endpoint 실패가 전체 화면을 비움 | 영역별 결과와 실패 상태를 분리하고 stale 성공 데이터 보존 |
| 느린 네트워크로 spinner가 고착됨 | 요청 timeout과 finally 기반 UI 상태 해제 테스트 |
| 구 endpoint 호출이 다른 경로에 남음 | 소스 `rg` 검색과 WireMock no-request 검증을 완료 조건으로 둠 |

## Verification Steps

1. 라이브 smoke: v2 endpoint별 HTTP status와 최소 응답 shape 확인.
2. 단위/통합: `./gradlew --no-daemon test --tests '*NaverClientTest' --tests '*NaverResearchProviderTest' --tests '*ResearchFacadeServiceTest'`.
3. 전체 회귀: `./gradlew --no-daemon test`.
4. 배포 검증: `./gradlew --no-daemon buildPlugin`.
5. 정적 검증: `git diff --check`; 구 endpoint 문자열 및 뉴스→리서치 호출 경로 `rg` 확인.
6. 수동 UI smoke: Research 탭 홈, 랭킹 변경, 국내 종목 검색, 새로고침, 실패/빈 결과 상태를 확인.

## Delivery Boundary

이번 단계에서는 이 계획 문서만 커밋·푸시한다. 리서치 API 구현은 별도 실행 단계에서 진행한다. 기존 작업 트리의 뉴스 관련 변경은 이번 계획 커밋에 포함하지 않는다.

## Follow-up Staffing Guidance

- 기본 후속 경로: `$ultragoal` 단일 목표 실행.
- 병렬 실행이 필요하면 `$team`: `researcher` 1명(라이브 계약), `executor` 1명(NaverClient/DTO), `test-engineer` 1명(회귀), `verifier` 1명(최종 검증).
- 각 lane은 서로 다른 파일 영역을 소유하고, verifier는 테스트·빌드·구 endpoint 미호출 증거를 확인한 뒤 종료한다.
- `$ralph`는 사용자가 명시적으로 지속형 단일 소유자 실행을 선택할 때만 대안으로 사용한다.

## Goal-Mode Follow-up Suggestions

- 일반 구현 목표: `$ultragoal` 권장.
- 네이버 계약 자체를 조사 결과물로 남기는 연구 프로젝트가 되면 `$autoresearch-goal`을 고려한다.
- 성능 지표 최적화가 주목적이 되면 `$performance-goal`을 고려한다.
