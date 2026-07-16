<div align="center">

# 🎬 모두의 플리 (Mople)

**대규모 트래픽이 예상되는 글로벌 콘텐츠 평점 플랫폼**

영화·드라마·스포츠 등 다양한 콘텐츠를 큐레이팅하고 공유하며,
실시간 같이 보기 기능까지 제공하는 소셜 콘텐츠 서비스입니다.

[![Coverage](https://img.shields.io/badge/coverage-pending-lightgrey)](#)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green)

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

---

## 📌 프로젝트 소개

| 항목 | 내용 |
|---|---|
| 프로젝트명 | 모두의 플리 (Mople) |
| 진행 기간 | 2026.07.27 ~ 2026.08.28 (4.5주) |
| 팀 구성 | 6인 (Backend) |
| 프로젝트 목표 | 인증/인가 설계, 복잡한 DB 설계, 실시간 통신 구현, 분산 환경 설계, 안정성 확보 |

---

## 👥 팀원 소개

| 이름 | 역할 | 담당 도메인 |
|---|---|---|
| (이름 입력) | 팀장 | 인증/인가·사용자관리, 어드민 |
| (이름 입력) | 팀원 | 콘텐츠 관리·배치·외부 API |
| (이름 입력) | 팀원 | 평가·큐레이팅(플레이리스트) |
| (이름 입력) | 팀원 | 소셜(팔로우·구독 연결) |
| (이름 입력) | 팀원 | 실시간(WebSocket) 같이보기·DM |
| (이름 입력) | 팀원 | 알림(SSE)·어드민 보조 |

---

## 🛠 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.16, Spring Security, Spring Batch |
| Auth | JWT (jjwt), Spring OAuth2 Client (심화) |
| Build Tool | Gradle 8.14.4 |
| DB & ORM | PostgreSQL 16, Spring Data JPA, QueryDSL 5.0.0, H2 |
| Real-time | Spring WebSocket (STOMP), SSE |
| Cache | Redis (심화) |
| Messaging | Kafka (심화) |
| Search | Elasticsearch (심화) |
| Infra & Deploy | AWS (ECS, ECR, S3), Docker, Docker Compose, Nginx (심화) |
| CI/CD | GitHub Actions |
| Test & QA | JUnit 5, JaCoCo (커버리지 80% 이상 목표) |
| API Docs | Springdoc OpenAPI (Swagger) |
| External API | TMDB API, The Sports DB API (Spring Cloud OpenFeign) |
| Utility | Lombok |

> 심화 표시된 항목은 Phase 5에서 우선순위에 따라 선택적으로 도입됩니다.

---

## 🗂 ERD

```
docs/erd.mermaid 참고
```

<!-- 이미지로 대체 시:
![ERD](docs/erd.png)
-->

주요 엔티티: `users`, `contents`, `ratings`, `playlists`, `playlist_contents`,
`playlist_subscriptions`, `follows`, `conversations`, `direct_messages`, `notifications`

---

## 🌿 브랜치 전략 & 커밋 컨벤션

### 브랜치 전략
- `main` : 배포 가능한 안정 버전만 유지
- `develop` : 통합 개발 브랜치
- `feature/{담당}-{기능명}` : 예) `feature/A-login`, `feature/E-watch-session`

### 커밋 컨벤션 (Conventional Commits)
| type | 의미 |
|---|---|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| test | 테스트 추가/수정 |
| refactor | 기능 변화 없는 코드 개선 |
| docs | 문서 수정 |
| chore | 빌드/설정 등 잡무성 변경 |

```
feat(auth): JWT 로그인 기능 구현
test(rating): 평점 등록 실패 케이스 테스트 추가
```

### PR 규칙
- 최소 1인 승인 후 병합 (공통 모듈 변경 시 2인 이상)
- PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`) 필수 작성
- 24시간 내 리뷰 응답 원칙

---

## 🚀 실행 방법

### 1. 저장소 클론
```bash
git clone https://github.com/{조직명}/{repo명}.git
cd {repo명}
```

### 2. 로컬 DB 실행 (Docker Compose)
```bash
docker-compose up -d
```

### 3. 환경변수 설정
`src/main/resources/application-local.yml` 파일에 아래 값을 채워주세요.
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mople
    username: mople
    password: mople1234
jwt:
  secret: (로컬 개발용 시크릿 키 입력)
```

### 4. 애플리케이션 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 5. API 문서 확인
```
http://localhost:8080/swagger-ui/index.html
```

### 6. 테스트 & 커버리지 확인
```bash
./gradlew test
./gradlew jacocoTestReport
# 리포트 경로: build/reports/jacoco/test/html/index.html
```

---

## 📁 프로젝트 구조

```
com.codeit.mople
├── batch          # 콘텐츠 수집 배치 파이프라인
├── realtime       # WebSocket · SSE 공통 인프라
├── domain         # 도메인별 비즈니스 로직
│   ├── user
│   ├── content
│   ├── rating
│   ├── playlist
│   ├── follow
│   ├── watch
│   ├── chat
│   ├── directmessage
│   └── notification
├── infra          # 외부 API 연동 (TMDB, SportsDB)
└── global         # 공통 설정, 예외처리, 응답 포맷
```

---

## ✨ 주요 기능

- **사용자 관리**: 회원가입/로그인(JWT), 어드민(권한변경·계정잠금), 비밀번호 초기화, 소셜 로그인(심화)
- **콘텐츠 관리**: TMDB/SportsDB 연동 콘텐츠 수집(Batch), 콘텐츠 CRUD
- **평가 & 큐레이팅**: 평점/의견 등록, 개인 플레이리스트, 플레이리스트 구독
- **소셜**: 팔로우, 팔로우 활동 알림
- **실시간 같이보기**: WebSocket 기반 시청 세션 공유, 콘텐츠 실시간 채팅
- **DM**: 실시간(WebSocket) + 비활성 대화(SSE) 쪽지
- **알림**: SSE 기반 실시간 알림 (권한변경/구독/팔로우/DM 등)

---