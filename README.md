<div align="center">

# 🎬 모두의 플리 (Mople)

**대규모 트래픽이 예상되는 글로벌 콘텐츠 평점 플랫폼**

영화·드라마·스포츠 등 다양한 콘텐츠를 큐레이팅하고 공유하며,
실시간 같이 보기 기능까지 제공하는 소셜 콘텐츠 서비스입니다.

![Coverage](https://img.shields.io/badge/coverage-line%2080%25%20enforced-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green)
![Gradle](https://img.shields.io/badge/Gradle-9.5.1-02303A)

<br/>

<img width="1200" height="1310" alt="4컷(4)" src="https://github.com/user-attachments/assets/21fa2545-b0e7-46e6-80d9-bc7391538327" alt="모두의 플리 소개 만화" width="800"/>

</div>

---

## 📖 목차
- [프로젝트 소개](#-프로젝트-소개)
- [팀원 소개](#-팀원-소개)
- [기술 스택](#-기술-스택)
- [ERD](#-erd)
- [브랜치 전략 & 커밋 컨벤션](#-브랜치-전략--커밋-컨벤션)
- [실행 방법](#-실행-방법)
- [프로젝트 구조](#-프로젝트-구조)
- [주요 기능](#-주요-기능)
- [API](#-api)
- [CI/CD & 테스트](#-cicd--테스트)
- [문서](#-문서)

---

## 📌 프로젝트 소개

| 항목 | 내용 |
|---|---|
| 프로젝트명 | 모두의 플리 (Mople) |
| 진행 기간 | 2026.07.27 ~ 2026.08.28 (4.5주) |
| 팀 구성 | 6인 (Backend) |
| 저장소 | `dkegldh/sb11-mople-team2` |
| 프로젝트 목표 | 인증/인가 설계, 복잡한 DB 설계, 실시간 통신 구현, 분산 환경 설계, 안정성 확보 |

---

## 👥 팀원 소개

| 이름   | GitHub | 담당 도메인 |
|--------|---|---|
| 김진혁 | `dkegldh` | 인증·인가(JWT/OAuth2), 사용자, ECS·CD 인프라, Elasticsearch 운영 |
| 김지성 | `jsKim1219` | 콘텐츠·외부 API(TMDB/SportsDB)·수집 배치, 실시간 같이 보기(WebSocket) |
| 엄주혁 | `eomoff` | 팔로우, 플레이리스트, 콘텐츠·외부 API(TMDB), Kafka 발행/소비 경로, Redis 락·캐시 |
| 김명근 | `DonToong2` | 리뷰(평점·의견), 플레이리스트, Elasticsearch 매핑·CD 보조 |
| 강성민 | `seongmin0244` | DM 대화방·다이렉트 메시지(WebSocket + SSE) |
| 노정빈 | `vincent865` | 사용자 관리·어드민, 알림(SSE) |

---

## 🛠 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.16, Spring Security, Spring Batch, Spring Cloud 2025.0.1 |
| Auth | JWT (jjwt), Spring OAuth2 Client (Google · Kakao) |
| Build Tool | Gradle 9.5.1 |
| DB & ORM | PostgreSQL 16, Spring Data JPA, QueryDSL 5.0.0, H2(테스트) |
| Real-time | Spring WebSocket (STOMP over SockJS), SSE |
| Cache & Lock | Redis 7, ShedLock (스케줄러 분산 락) |
| Messaging | Apache Kafka 3.9.0 (KRaft) |
| Search | Elasticsearch 8.18.8 |
| Storage & Mail | AWS S3, Spring Mail (Brevo SMTP) |
| Infra & Deploy | AWS ECS Fargate, ECR, ALB, Docker, Docker Compose, Nginx |
| CI/CD | GitHub Actions |
| Monitoring | Spring Actuator, Micrometer + Prometheus, Discord Webhook |
| Test & QA | JUnit 5, Mockito, Testcontainers, JaCoCo, k6 |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| External API | TMDB API, The Sports DB API (OpenFeign) |

---

## 🗂 ERD

전체 다이어그램과 컬럼 정의는 [`erd.md`](./erd.md)에 있습니다.

주요 테이블: `users`, `contents`, `reviews`, `playlists`, `playlist_contents`,
`playlist_subscriptions`, `follows`, `conversations`, `direct_messages`, `notifications`

PK는 **UUID**, 시간 컬럼은 **`timestamptz`(Java `Instant`)** 로 통일합니다.

---

## 🌿 브랜치 전략 & 커밋 컨벤션

전체 규칙은 [`conventions.md`](./conventions.md) §11을 따릅니다.

```
main (production)
└── develop (staging)
    └── {type}/{#이슈번호}-{기능명}
        예) feature/#5-user-register, fix/#12-login-bug
```

- type: `feature` `fix` `refactor` `docs` `test` `chore` `batch` `deploy`
- `develop` / `main` 직접 push 금지 — fork에서 작업 후 upstream `develop`으로 PR
- **2인 이상** 리뷰 승인 후 **Squash and Merge** (PR 제목 = squash 커밋 메시지)
- 이슈 제목은 `[FEAT] 팔로우 생성 구현` 형식

```
feat: 팔로우 생성 구현
fix: 중복 구독 방지 버그 수정
```

---

## 🚀 실행 방법

### 1. 미들웨어 실행

```bash
docker compose up -d      # Kafka(9093), Elasticsearch(19201)
```

PostgreSQL(`localhost:5432`, DB `mople_db`, `mople`/`mople1234`)과 Redis(`localhost:6380`)는 로컬에 직접 준비합니다.

### 2. 환경변수 설정

| 변수 | 필수 | 설명 |
|---|---|---|
| `TMDB_API_KEY` | ✅ | TMDB API 키 |
| `AWS_S3_ACCESS_KEY_ID` / `AWS_S3_SECRET_ACCESS_KEY` | ✅ | S3 이미지 업로드 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | ✅ | 구글 소셜 로그인 |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | ✅ | 카카오 소셜 로그인 |
| `SPORTSDB_API_KEY`, `JWT_SECRET`, `BREVO_SMTP_*`, `DISCORD_WEBHOOK_URL` | ⬜ | 미지정 시 기본값 사용 / 해당 기능 비활성 |

### 3. 실행

```bash
./gradlew bootRun         # 기본 프로파일 dev
```

- Swagger UI — http://localhost:8080/swagger-ui.html
- 어드민 계정 — `admin@mople.com` / `Admin1234!` (dev 부팅 시 생성)

프로파일은 `dev`(기본) · `test` · `load`(부하 테스트) · `prod`(ECS) 네 가지입니다.

---

## 📁 프로젝트 구조

```
com.codeit.mople
├── domain
│   ├── auth              # 로그인·토큰 재발급·비밀번호 초기화·OAuth2
│   ├── user              # 회원가입·프로필·어드민(권한/잠금)
│   ├── content           # 콘텐츠 CRUD + TMDB·SportsDB 수집 배치
│   ├── review            # 평점·의견
│   ├── playlist          # 플레이리스트·콘텐츠 연결·구독
│   ├── follow            # 팔로우
│   ├── conversation      # DM 대화방
│   ├── directmessage     # 다이렉트 메시지
│   ├── notification      # 알림 (이벤트 기반, SSE 전달)
│   └── watchingsession   # 실시간 같이 보기 · 콘텐츠 채팅
├── realtime              # WebSocket 세션 레지스트리 · 강제 종료
└── global                # config · error · event · jwt · sse · storage …
```

각 도메인은 `controller → service → repository` 레이어와
`controller/api`(Swagger 인터페이스) · `dto`(record) · `entity` · `exception` 서브패키지로 구성합니다.

---

## ✨ 주요 기능

| 도메인 | 기능 |
|---|---|
| 인증 | JWT 로그인·토큰 재발급, Google/Kakao 소셜 로그인, 비밀번호 초기화(메일), 재로그인 시 기존 기기 세션 무효화 |
| 사용자 | 회원가입, 프로필 수정(S3 이미지), 어드민 권한 변경·계정 잠금 |
| 콘텐츠 | 콘텐츠 CRUD(어드민), TMDB·SportsDB 수집 배치, Elasticsearch 검색 |
| 평가 | 평점·의견 CRUD, 콘텐츠 평균 평점·리뷰 수 집계 |
| 큐레이팅 | 플레이리스트 CRUD, 콘텐츠 추가/삭제, 구독 |
| 소셜 | 팔로우·팔로우 취소, 팔로우 알림 |
| 실시간 같이 보기 | 시청 세션 입장/퇴장 브로드캐스트, 콘텐츠 실시간 채팅 |
| DM | WebSocket 실시간 전송 + SSE 대화 목록 갱신, Redis 읽음 워터마크 |
| 알림 | 권한 변경·구독·팔로우·DM 이벤트를 SSE로 실시간 전달 |

배치 Job은 `tmdbCollectJob`(TMDB 영화·시리즈), `sportsContentJob`(스포츠) 두 개입니다.

---

## 📋 API

전체 명세는 [`api.md`](./api.md), 실행 중 확인은 `/swagger-ui.html`.

- **인증** — `Authorization: Bearer {accessToken}` + CSRF(`XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더). 리프레시 토큰은 `REFRESH_TOKEN` 쿠키
- **목록 조회** — `cursor` + `idAfter` 복합 커서 페이지네이션
- **에러 응답** — `{ "success": false, "error": { "code": "FOLLOW-002", "message": "..." } }`. 성공 응답은 DTO를 그대로 반환

### 실시간

| 채널 | 엔드포인트 |
|---|---|
| SSE | `GET /api/sse` — `notifications`, `direct-messages` 이벤트 |<img width="1200" height="1310" alt="4컷(4)" src="https://github.com/user-attachments/assets/f918d9b1-a7aa-44fc-8b7b-044ef4847dd1" />

| WebSocket | `/ws` (STOMP over SockJS) — 시청 세션·콘텐츠 채팅·DM |

STOMP destination은 구독 `/sub/...`, 발행 `/pub/...` 접두사를 씁니다.

---

## ⚙️ CI/CD & 테스트

| 워크플로우 | 트리거 | 내용 |
|---|---|---|
| `ci.yml` | `main`·`develop` PR / push | 빌드 + 테스트 + 커버리지 검증 |
| `cd.yml` | CI 성공 후 `main` push | 이미지 빌드 → ECR → ECS Fargate 배포 |
| `promote-to-main.yml` | `develop` 머지 | 검증된 커밋만 `main`으로 승격 |

```bash
./gradlew test
./gradlew jacocoTestCoverageVerification
# 리포트: build/reports/jacoco/test/html/index.html
```

테스트는 `service/`(Mockito) · `repository/`(`@DataJpaTest`) · `controller/`(`@WebMvcTest`) · `batch/`(`@SpringBatchTest`)로 나뉘고, 실시간·Redis 통합 테스트는 Testcontainers를 씁니다.
커버리지는 `domain/**/service/**`의 **클래스별 라인 80%** 미만이면 `check`가 실패합니다.

---

## 📚 문서

| 문서 | 내용 |
|---|---|
| [`doc.md`](./doc.md) | 프로젝트 요구사항 |
| [`api.md`](./api.md) | API 명세 — **계약의 기준** |
| [`erd.md`](./erd.md) | ERD 다이어그램 및 컬럼 정의 |
| [`conventions.md`](./conventions.md) | 팀 개발 컨벤션 |
