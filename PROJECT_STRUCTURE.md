# Places & Plates 프로젝트 폴더 구조

문서 버전: v1.6
작성일: 2026-08-23

## 1. 구조 결정

Places & Plates는 하나의 Git 저장소 안에서 프론트엔드와 백엔드를 독립 애플리케이션으로 관리한다.

- `frontend`: Next.js + TypeScript
- `backend`: Java + Spring Boot + Gradle
- 두 프로젝트 모두 기술 계층만 나열하지 않고 `post`, `place`, `photo`, `profile` 같은 도메인 중심으로 구성한다.
- 프론트엔드는 Spring Boot 자체를 사용하지 않지만, `controller → service → repository`처럼 책임이 드러나는 폴더 규칙을 적용한다.
- 프론트엔드는 백엔드 REST API만 호출하며 데이터베이스와 저장소에 직접 접근하지 않는다.

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

## 3. 프론트엔드 구조

```text
frontend/src/
├── app/                         # Next.js 라우트와 레이아웃
│   ├── page.tsx                 # 공개 홈
│   ├── posts/page.tsx           # 공개 기록 목록
│   ├── map/page.tsx             # 공개 기록 지도
│   ├── login/page.tsx           # 관리자 로그인 진입점
│   ├── not-found.tsx            # 공통 404 빈 상태
│   ├── (manage)/
│   │   ├── manage/posts/page.tsx
│   │   └── manage/posts/[postId]/edit/page.tsx
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
│   │   ├── dto/
│   │   └── exception/
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
│   └── security/                # Spring Security 설정
└── infra/
    ├── googlemaps/              # Google Places 서버 연동
    ├── image/                   # EXIF 제거·리사이즈·워터마크
    ├── storage/                 # 정제 마스터·공개 이미지 저장소
    └── persistence/             # 복잡한 조회 구현
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
| auth | 로그인·토큰·현재 사용자 | `/api/v1/auth/**` |
| profile | 회원별 개인 페이지 | `/api/v1/profiles/**` |
| post | 맛집·여행지 게시물과 공개 범위 | `/api/v1/posts/**` |
| place | Google Place ID·주소·좌표 | `/api/v1/places/**` |
| photo | 임시 업로드·정제·삭제 상태 | `/api/v1/photos/**` |
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

- `frontend/.env`: 브라우저에 공개 가능한 Google Maps 키와 백엔드 API 주소만 둔다.
- `backend/src/main/resources/application-local.yml`은 로컬 전용이며 Git에 추가하지 않는다. 추적되는 `application-local.example.yml`을 복사하고 실제 값은 환경변수로 주입한다.
- Google Maps 브라우저 키는 HTTP 리퍼러와 Maps JavaScript API·Places API로 제한한다.
- `backend` 비밀값은 운영 환경에서만 주입하며 저장소에 커밋하지 않는다.
- 데이터베이스 비밀번호, 저장소 비밀키, JWT 서명키는 프론트엔드에 전달하지 않는다.
- `.env.example`에는 값이 없는 변수명과 설명만 남긴다.

## 8. 개발 착수 시 적용 순서

1. `frontend/` Next.js 프로젝트에서 기존 목업을 기준 화면으로 유지한다.
2. `backend/` Spring Boot + Gradle 프로젝트를 생성한다.
3. 루트 README와 전체 실행·검증 스크립트를 추가한다.
4. 프론트·백엔드 각각의 환경설정 예시와 Git 제외 규칙을 만든다.
5. 프로필·게시물·장소·사진의 소유자 중심 데이터 모델부터 연결한다.

데이터베이스 관계, 제약조건, 인덱스와 마이그레이션 실행 규칙은 `docs/DATABASE_SCHEMA.md`를 기준으로 한다.
