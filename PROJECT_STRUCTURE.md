# Places & Plates 프로젝트 폴더 구조

문서 버전: v2.1
작성일: 2026-08-24

## 1. 구조 결정

Places & Plates는 하나의 Git 저장소 안에서 프론트엔드와 백엔드를 독립 애플리케이션으로 관리한다.

- `frontend`: Next.js + TypeScript
- `backend`: Java + Spring Boot + Gradle
- 두 프로젝트 모두 기술 계층만 나열하지 않고 `post`, `place`, `photo`, `profile` 같은 도메인 중심으로 구성한다.
- 프론트엔드는 Spring Boot 자체를 사용하지 않지만, `controller → service → repository`처럼 책임이 드러나는 폴더 규칙을 적용한다.
- 프론트엔드의 인증·게시물·저장소 제어 요청은 백엔드 REST API만 호출한다. 사진 본문만 백엔드가 발급한 단기 서명 토큰으로 Supabase Storage TUS 엔드포인트에 직접 전송해 Cloud Run 프록시 비용과 시간 제한을 피한다.

## 2. 저장소 전체 구조

```text
places-plates/
├── frontend/                    # Next.js 사용자 화면·관리 화면
│   ├── src/
│   ├── public/
│   ├── tests/
│   ├── package.json
│   └── .env.example
├── backend/                     # Spring Boot REST API
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── src/test/java/
│   ├── build.gradle.kts
│   ├── project.toml              # Google Buildpack Java 21 빌드 설정
│   ├── settings.gradle.kts
│   └── gradlew
├── docs/                        # 제품·API·운영 문서
├── infra/                       # 로컬 DB·배포 설정
├── scripts/                     # 전체 검증·개발 실행 스크립트
├── .github/workflows/           # 프론트·백엔드 CI
├── .gitignore
└── README.md
```

기존 디자인 목업은 Sprint 0에서 `frontend/` Next.js 애플리케이션으로 이전했다. 공개된 기존 미리보기는 유지하며 이후 개발과 배포의 기준 소스는 `frontend/`로 통일한다.

프론트엔드는 동일한 App Router 소스에서 두 배포 산출물을 만든다. Vercel은 `pnpm build:vercel`로 표준 `.next` 산출물을 만들고, 기존 OpenAI Sites 미리보기는 `pnpm build`로 Vinext `dist` 산출물을 만든다. 두 산출물은 CI에서 각각 빌드하고 비밀정보를 검사한다.

Cloud Run 소스 배포는 저장소의 `backend/`를 빌드 루트로 사용한다. `backend/project.toml`의 `GOOGLE_RUNTIME_VERSION=21`을 기준으로 Google Buildpack이 Gradle Java Toolchain과 일치하는 JDK 21을 설치해야 한다.

## 3. 프론트엔드 구조

```text
frontend/src/
├── app/                         # Next.js 라우트와 레이아웃
│   ├── page.tsx                 # 공개 홈
│   ├── posts/page.tsx           # 공개 기록 목록
│   ├── map/page.tsx             # 공개 기록 지도
│   ├── login/page.tsx           # 관리자 로그인 진입점
│   ├── manage/page.tsx          # 세션 확인 후 표시하는 관리 진입점
│   ├── manage/drafts/[draftPostId]/page.tsx # 업로드와 연결된 비공개 초안
│   ├── not-found.tsx            # 공통 404 빈 상태
│   └── layout.tsx
├── domain/
│   ├── auth/
│   │   ├── api/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── model/
│   │   └── types/
│   ├── post/
│   ├── place/
│   ├── photo/
│   ├── trip/
│   ├── map/
│   └── profile/
├── shared/
│   ├── api/                     # 공통 API 클라이언트
│   ├── config/                  # 공개 환경설정
│   ├── lib/                     # 날짜·문자열 등 순수 함수
│   ├── styles/                  # 토큰·전역 스타일
│   ├── types/                   # 공통 응답 타입
│   └── ui/                      # 버튼·모달·카드 등 공통 UI
└── middleware.ts
```

### 프론트엔드 의존 규칙

```text
app → domain → shared
```

- `app`은 페이지 조립과 라우팅만 담당한다.
- `domain`은 게시물·지도·사진 등 도메인별 화면 로직을 담당한다.
- `shared`는 특정 도메인을 알지 못하는 공통 코드만 가진다.
- `shared`가 `domain` 또는 `app`을 참조하는 역방향 의존은 금지한다.
- 백엔드 응답 타입과 화면 전용 상태를 분리한다.
- 공통 헤더·본문·푸터는 `shared/ui/application-shell.tsx`에서 조립하고 모든 공개 라우트가 같은 셸을 사용한다.
- 현재 경로 표시는 `shared/ui/public-navigation.tsx`, 데이터가 없는 안내 화면은 `shared/ui/empty-state.tsx`를 공통으로 사용한다.
- 색상·타이포·간격·모서리·그림자·모션은 `shared/styles/tokens.css`를 기준으로 하며 화면 컴포넌트는 의미 기반 토큰을 우선 사용한다.
- 지원 최소 너비는 320px이며 사용자 화면 변경은 390px와 1440px에서 가로 넘침을 확인한다.

## 4. 백엔드 구조

기본 패키지명은 `com.placesplates`로 한다.

```text
backend/src/main/java/com/placesplates/
├── PlacesPlatesApplication.java
├── domain/
│   ├── auth/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   └── config/
│   ├── profile/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   └── exception/
│   ├── post/
│   ├── place/
│   ├── photo/
│   ├── trip/
│   └── map/
├── global/
│   ├── auth/                    # 인증 사용자·권한 검사
│   ├── common/                  # BaseEntity·공통 응답
│   ├── config/                  # CORS·JPA·웹 설정
│   ├── error/                   # ErrorCode·전역 예외 처리
│   └── security/                # Spring Security와 요청별 DB 소유자 컨텍스트
└── infra/
    ├── googlemaps/              # Google Places 서버 연동
    ├── image/                   # EXIF 제거·리사이즈·워터마크
    ├── storage/                 # 정제 마스터·공개 이미지 저장소
    └── persistence/             # 복잡한 조회·운영 DB 프로비저닝
```

```text
backend/src/main/resources/
├── application.yml
├── application-local.example.yml       # 로컬 설정 복사용 예시
├── application-test.yml
└── db/migration/                # Flyway 데이터베이스 변경 이력
    ├── common/                  # 공통 테이블·관계·기본 인덱스
    └── postgresql/              # PostGIS·부분 인덱스·DB 전용 제약
```

### 백엔드 계층 책임

| 계층 | 책임 |
|---|---|
| Controller | `/api/v1` 요청 검증과 응답 변환 |
| Service | 게시·공개·사진 처리 등 유스케이스와 트랜잭션 |
| Repository | JPA 기반 데이터 조회·저장 |
| Entity | 도메인 상태와 비즈니스 규칙 |
| DTO | 요청·응답 계약, Entity 직접 노출 금지 |
| Infra | Google Maps·이미지 처리·저장소 같은 외부 연동 |
| Global | 인증·설정·공통 오류 처리 |

## 5. 핵심 도메인과 API 경계

| 도메인 | 주요 책임 | API 예시 |
|---|---|---|
| auth | CSRF 발급·로그인·서버 세션·로그아웃 | `/api/v1/auth/**` |
| profile | 회원별 개인 페이지 | `/api/v1/profiles/**` |
| post | 맛집·여행지 게시물, 업로드 시작 초안과 공개 범위 | `/api/v1/manage/drafts/**`, `/api/v1/posts/**` |
| place | Google Place ID·주소·좌표 | `/api/v1/places/**` |
| photo | 초안과 연결된 임시 업로드·정제·삭제 상태 | `/api/v1/manage/photo-uploads/**`, `/api/v1/photos/**` |
| trip | 여행 묶음·대표 여행 | `/api/v1/trips/**` |
| map | 지도 경계·마커·묶음 숫자 조회 | `/api/v1/map/posts` |

## 6. 테스트 구조

```text
frontend/tests/
├── unit/
├── component/
└── e2e/

backend/src/test/java/com/placesplates/
├── domain/                      # 서비스·Repository 테스트
├── api/                         # Controller 통합 테스트
└── support/                     # fixture·테스트 설정
```

- 프론트엔드는 목록·지도 전환, 필터 유지, 업로드 입력을 중심으로 테스트한다.
- 백엔드는 소유자 권한, 원본 자동 삭제, 메타데이터 제거, 공개 범위를 중심으로 테스트한다.
- 루트 검증 스크립트가 프론트엔드 빌드와 백엔드 테스트를 한 번에 실행한다.

## 7. 환경변수 원칙

- `frontend/.env`: 브라우저에 공개 가능한 Google Maps 키와 백엔드 API 주소만 둔다. Supabase 서비스 역할 키는 두지 않는다.
- `backend/src/main/resources/application-local.yml`은 로컬 전용이며 Git에 추가하지 않는다. 추적되는 `application-local.example.yml`을 복사하고 실제 값은 환경변수로 주입한다.
- Google Maps 브라우저 키는 HTTP 리퍼러와 Maps JavaScript API·Places API로 제한한다.
- `backend` 비밀값은 운영 환경에서만 주입하며 저장소에 커밋하지 않는다.
- Supabase Storage 서비스 역할 키는 Cloud Run Secret Manager에만 저장하며, 백엔드는 소유자와 객체 키를 검증한 뒤 단기 업로드 토큰만 반환한다.
- 데이터베이스 비밀번호, 저장소 비밀키, 관리자 비밀번호는 프론트엔드에 전달하지 않는다.
- 운영 세션 쿠키는 `HttpOnly`, `Secure`, `SameSite=None`으로 설정하고 CORS는 실제 프론트 도메인만 허용한다.
- `/api/v1/public/**`는 `PUBLIC`, 나머지 보호 API는 인증 사용자의 UUID를 가진 `OWNER` DB 모드로 실행한다.
- PostgreSQL RLS는 운영에서 항상 활성화하며 H2 테스트 프로필만 DB 엔진 차이 때문에 비활성화한다.
- Supabase 운영 DB는 관리자 역할로 Flyway를 별도 실행하고, Spring Boot 런타임은 `SUPERUSER`·`BYPASSRLS` 권한이 없는 `placesplates_app`만 사용한다.
- Supabase `anon`·`authenticated` Data API 역할에는 백엔드 소유 `public` 테이블 권한을 부여하지 않는다.
- 운영 애플리케이션은 `FLYWAY_ENABLED=false`로 실행해 관리자 DB 자격 증명을 호스팅 환경에 저장하지 않는다.
- `.env.example`에는 값이 없는 변수명과 설명만 남긴다.

## 8. 개발 착수 시 적용 순서

1. `frontend/` Next.js 프로젝트에서 기존 목업을 기준 화면으로 유지한다.
2. `backend/` Spring Boot + Gradle 프로젝트를 생성한다.
3. 루트 README와 전체 실행·검증 스크립트를 추가한다.
4. 프론트·백엔드 각각의 환경설정 예시와 Git 제외 규칙을 만든다.
5. 프로필·게시물·장소·사진의 소유자 중심 데이터 모델부터 연결한다.

데이터베이스 관계, 제약조건, 인덱스와 마이그레이션 실행 규칙은 `docs/DATABASE_SCHEMA.md`를 기준으로 한다.
Supabase 연결·역할 분리·최초 프로비저닝은 `docs/SUPABASE_DATABASE.md`를 기준으로 한다.
관리자 계정 준비, 세션·CSRF 계약과 환경별 쿠키 설정은 `docs/AUTHENTICATION.md`를 기준으로 한다.
