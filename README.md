# Market Ticker (IntelliJ Plugin)

IntelliJ IDE 안에서 관심 종목을 검색/등록하고, 실시간 시세와 포트폴리오 손익을 확인할 수 있는 플러그인입니다.

## 주요 기능

- 종목 검색: Naver 모바일 주식 검색 API 기반 자동완성 검색
- 관심종목 관리: 더블클릭으로 추가, 컨텍스트 메뉴로 삭제
- 실시간 시세 갱신: `자동(장중/비장중 분리)` / `고정(3·6·10초)` / `수동` 지원
- 가격 알림: 목표가/변동률 + 반복 간격 + 1회성/지속 + 장중만 + 소리 옵션
- 포트폴리오: 목표비중/편차, 실현손익/미실현손익/총손익 분리 표시
- 종목 그룹/태그: 국내/해외 기본 분류 + 사용자 태그 편집 + 그룹 필터
- 차트: 일/주/월 전환, 캔들, 거래량, MA5/MA20
- Heatmap: 등락률 기반 컬러 시각화
- 다국어/로케일: KO/EN 전환 및 숫자 포맷 통일
- 상태바 위젯: 관심종목 가격 순환 표시 및 ToolWindow 연동

<!-- Plugin description -->
Market Ticker is an IntelliJ Platform plugin that lets you track watchlist symbols in real time, review portfolio PnL, and receive price alerts directly inside your IDE.

It provides watchlist search/add/remove, periodic price refresh, target/volatility alerts, status bar ticker, and heatmap visualization.
<!-- Plugin description end -->

## 기술 스택

- Kotlin, Coroutines, StateFlow
- IntelliJ Platform SDK (2024.3.x)
- Gradle (IntelliJ Platform Gradle Plugin 2.x)
- Jackson, Java HttpClient
- JUnit 5, AssertJ, WireMock

## 개발 환경

- JDK 21
- IntelliJ IDEA 2024.3+
- macOS/Linux/Windows (Gradle Wrapper 사용)

### SDKMAN 사용 예시 (세션 한정 JDK 21)

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.9-tem
./gradlew test
```

## 로컬 실행

```bash
./gradlew runIde
```

실행 후 샌드박스 IDE에서 `Market Ticker` ToolWindow를 열어 기능을 확인할 수 있습니다.

## 테스트

```bash
./gradlew test
```

최근 확인 결과: Java 21 세션에서 `BUILD SUCCESSFUL`.

## 저장 데이터

플러그인은 IDE 설정 디렉터리에 다음 상태 파일을 저장합니다.

- `market_ticker_watchlist.xml`
- `market_ticker_alerts.xml`

## 프로젝트 구조

```text
src/main/kotlin/com/github/myeoungdev/marketticker
├── application      # 서비스/리스너/리포지토리/포트
├── domain           # 도메인 모델
├── infrastructure   # 외부 API 연동(Naver)
└── ui               # ToolWindow/StatusBar/View
```

## 배포 전 체크리스트

- `plugin.xml`의 표시 이름/설명/벤더 최종 점검
- JetBrains Marketplace ID 반영
- 플러그인 서명/배포 토큰 CI 시크릿 구성
- Plugin Verifier 결과 확인

## 라이선스
