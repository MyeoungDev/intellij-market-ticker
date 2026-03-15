# Market Ticker (IntelliJ Plugin)

IntelliJ IDE 안에서 국내주식, 해외주식, 코인 관심 종목을 검색/등록하고, 실시간 시세와 뉴스·리서치·포트폴리오 정보를 함께 확인할 수 있는 플러그인입니다.

## 주요 기능

- 종목 검색: 국내주식, 해외주식, 지수, 코인까지 자동완성 검색
- 관심종목 관리: 더블클릭으로 추가, 그룹/태그 분류, 컨텍스트 메뉴 삭제/편집
- 실시간 시세 갱신: `자동(장중/비장중 분리)` / `고정(3·6·10초)` / `수동` 지원
- 하단 정보 패널: 선택 종목 기준 `뉴스 / 리서치 / 차트 / 히트맵` 패널 제공
- 뉴스: 국내/해외/코인 뉴스, 종목 개요 카드, 주요 기사 헤드라인 표시
- 리서치: 핵심/랭킹/종목 리서치 조회 및 종목 검색 기반 리서치 탐색
- 가격 알림: 목표가/변동률 + 반복 간격 + 1회성/지속 + 장중만 + 소리 옵션
- 포트폴리오: 수량, 평균단가, 현재가, 평가금액, 평가손익, 수익률 중심의 간결한 보기
- 차트: 일/주/월 전환, 캔들, 거래량, MA5/MA20
- Heatmap: 등락률 기반 컬러 시각화
- 상태바 위젯: 관심종목 가격 순환 표시 및 ToolWindow 연동

## 현재 데이터 소스

- 시세/뉴스/리서치/종목 개요: Naver 증권/마켓 공개 웹 엔드포인트 기반
- 일부 보조 데이터: 제3자 공개 웹 엔드포인트 기반
- 장기적으로는 증권사 API 기반 데이터 소스 전환을 고려하고 있습니다.

<!-- Plugin description -->
Market Ticker is an IntelliJ Platform plugin for tracking Korean stocks, global stocks, and crypto inside the IDE with watchlists, portfolio view, alerts, charts, news, and research.

Some market, news, and research data may be retrieved from third-party public web endpoints. Market Ticker is not affiliated with or endorsed by Naver, Yahoo Finance or any brokerage or exchange. Data availability and accuracy may change without notice.
<!-- Plugin description end -->

## GitHub Releases / Marketplace Short Copy

### Short Description

Market Ticker is an IntelliJ plugin for tracking Korean stocks, global stocks, and crypto in real time, with watchlists, portfolio view, alerts, charts, news, and research inside the IDE.

### Short Disclaimer

Some market, news, and research data may be retrieved from third-party public web endpoints. Market Ticker is not affiliated with or endorsed by Naver, Yahoo Finance, Finviz, or any brokerage or exchange. Data availability and accuracy may change without notice.

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

## 외부 데이터 및 면책

- 이 플러그인은 일부 시세, 뉴스, 리서치, 캘린더 정보를 제3자 웹 엔드포인트에서 조회할 수 있습니다.
- Market Ticker는 Naver, Yahoo Finance 또는 기타 거래소/브로커와 제휴되어 있지 않습니다.
- 제공되는 데이터는 참고용이며, 정확성, 완전성, 실시간성, 지속적 가용성을 보장하지 않습니다.
- 제3자 서비스의 응답 형식, 접근 정책, 지연 시간, 제공 범위는 사전 고지 없이 변경될 수 있습니다.
- 투자 판단과 주문 실행의 최종 책임은 사용자에게 있습니다.

## 프로젝트 구조

```text
src/main/kotlin/com/github/myeoungdev/marketticker
├── application      # 서비스/리스너/리포지토리/포트
├── domain           # 도메인 모델
├── infrastructure   # 외부 API 연동(Naver)
└── ui               # ToolWindow/StatusBar/View
```

## 주요 UI 구성

- 상단 탭: `주식 / 뉴스 / 리서치`
- 주식 탭:
  - 검색 결과
  - 관심종목 / 포트폴리오
  - 하단 패널 `뉴스 / 리서치 / 차트 / 히트맵`
- 뉴스 탭:
  - 헤드라인
  - 많이 본 뉴스
- 리서치 탭:
  - 핵심 리서치
  - 랭킹 리서치
  - 종목 리서치

## 배포 전 체크리스트

- `plugin.xml`의 표시 이름/설명/벤더 최종 점검
- JetBrains Marketplace ID 반영
- 플러그인 서명/배포 토큰 CI 시크릿 구성
- Plugin Verifier 결과 확인

## 라이선스
