# Places & Plates 프로젝트 폴더 구조

문서 버전: v2.6
작성일: 2026-08-27

## 1. 구조 결정

Places & Plates는 하나의 Git 저장소 안에서 프론트엔드와 백엔드를 독립 애플리케이션으로 관리한다.

- `frontend`: Next.js + TypeScript
- `backend`: Java + Spring Boot + Gradle
- 두 프로젝트 모두 기술 계층만 나열하지 않고 `post`, `place`, `photo`, `profile` 같은 도메인 중심으로 구성한다.
- 프론트엔드는 Spring Boot 자체를 사용하지 않지만, `controller → service → repository`처럼 책임이 드러나는 폴더 규칙을 적용한다.
- 프론트엔드의 인증·게시물·저장소 제어 요청은 백엔드 REST API만 호출한다. 사진 본문만 백엔드가 발급한 단기 서명 토큰으로 Supabase Storage TUS 엔드포인트에 직접 전송해 Cloud Run 프록시 비용과 시간 제한을 피한다.
- 로그인 세션은 Spring Session JDBC로 운영 PostgreSQL에 저장해 Cloud Run 리비전 교체와 최대 2개 인스턴스 사이에서도 복구한다.

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
│   ├── Dockerfile                # Java 21·LCMS2를 포함한 Cloud Run 실행 이미지
│   ├── .dockerignore             # 컨테이너 빌드 제외 대상
│   ├── cloudbuild.yaml           # Docker 이미지 빌드·푸시·Cloud Run 배포
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

Cloud Run 배포는 저장소의 `backend/cloudbuild.yaml`을 Cloud Build 트리거 구성 파일로 사용한다. 이 구성은 `backend/`를 Docker 빌드 컨텍스트로 지정하고 `backend/Dockerfile`로 이미지를 빌드·푸시한 뒤 기존 Cloud Run 서비스 이미지만 갱신한다. 빌드 단계와 실행 단계 모두 Java 21을 사용하며, 실행 이미지에는 Java ImageIO의 ICC 색상 프로필 처리에 필요한 `liblcms2.so.2`를 제공하는 `liblcms2-2` 패키지를 명시적으로 설치한다. 애플리케이션은 UID 10001 비루트 사용자로 실행하고 CI에서 실제 이미지를 빌드해 라이브러리와 실행 사용자를 확인한다. Cloud Build 트리거가 인라인 Buildpacks 구성을 사용하면 Dockerfile을 무시하므로 금지한다.

## 3. 프론트엔드 구조

```text
frontend/src/
├── app/                         # Next.js 라우트와 레이아웃
│   ├── page.tsx                 # 공개 홈
│   ├── posts/page.tsx           # 공개 기록 목록
│   ├── map/page.tsx             # 공개 기록 지도
│   ├── login/page.tsx           # 관리자 로그인 진입점
│   ├── manage/page.tsx          # 세션 확인 후 표시하는 관리 진입점
│   ├── manage/drafts/[draftPostId]/page.tsx # 비공개 초안 공통·카테고리 필드 편집과 자동 저장
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
- 초안 편집 화면은 제목·방문 월·한줄평·본문과 카테고리 전용 값을 로컬 입력 상태로 유지하고 700ms 동안 입력이 멈추면 CSRF 보호 `PATCH` 요청으로 자동 저장한다. 제목을 비우면 서버 요청을 중단하고, 저장 실패 시 입력값을 유지한 채 명시적 재시도를 제공한다.
- 맛집 초안은 `restaurant-detail-fields.tsx`에서 평점·추천 메뉴·가격대·대기시간·재방문 의사를 선택 입력으로 제공한다. 여행지 초안에는 이 컴포넌트를 렌더링하지 않으며 서버도 여행지 게시물의 맛집 상세 요청을 거부한다.
- 사진 편집은 `photo/components/draft-photo-editor.tsx`가 담당한다. 소유자 전용 썸네일을 자격 증명 포함 Blob으로 읽어 원격 최적화 캐시에 노출하지 않고, 기본 버튼의 키보드 동작으로 순서·대표 사진을 바꾸며 대체 텍스트를 600ms 지연 자동 저장한다.

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
    ├── image/                   # 방향 보정·JPEG 재인코딩·저장 바이트 검사·반응형 크기·서버 워터마크
    ├── storage/                 # 임시 원본 읽기·삭제와 비공개 정제 마스터·파생본 저장·재조회
    └── persistence/             # 복잡한 조회·운영 DB 프로비저닝·RLS 정리 대상 열거
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
| auth | CSRF 발급·로그인·PostgreSQL 지속 세션·로그아웃 | `/api/v1/auth/**` |
| profile | 회원별 개인 페이지 | `/api/v1/profiles/**` |
| post | 맛집·여행지 게시물, 업로드 시작 초안, 공통·카테고리 전용 필드 자동 저장과 공개 범위 | `GET/PATCH /api/v1/manage/drafts/**`, `PUT/DELETE /api/v1/manage/drafts/{draftId}/place`, `/api/v1/posts/**` |
| place | 인증 소유자의 Places API (New) 검색, Google Place ID·좌표·직접 입력 장소 | `GET /api/v1/manage/places/search` |
| photo | 초안과 연결된 임시 업로드, 중복 방지 이미지 처리 큐, 정제 마스터·워터마크 반응형 파생본·사진 READY 전환·삭제 상태, 소유자 전용 썸네일·순서·대표·대체 텍스트 편집 | `/api/v1/manage/photo-uploads/**`, `/api/v1/manage/photo-uploads/{batchId}/items/{itemId}/sanitize`, `/api/v1/manage/image-processing-jobs/**`, `GET/PUT /api/v1/manage/drafts/{draftId}/photos`, `GET /api/v1/manage/drafts/{draftId}/photos/{photoId}/thumbnail`, `/api/v1/photos/**` |
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

- 프론트엔드는 목록·지도 전환, 필터 유지, 업로드 입력과 초안 공통·카테고리·사진 구성 자동 저장 상태를 중심으로 테스트한다.
- 백엔드는 소유자 권한, 초안 공통 필드와 카테고리 전용 필드 검증, 사진 전체 집합·대표 한 장 제약, 비공개 썸네일, 원본 자동 삭제, 메타데이터 제거, 공개 범위를 중심으로 테스트한다.
- 루트 검증 스크립트가 프론트엔드 빌드와 백엔드 테스트를 한 번에 실행한다.

## 7. 환경변수 원칙

- `frontend/.env`: 브라우저에 공개 가능한 Maps JavaScript API 키와 백엔드 API 주소만 둔다. Supabase 서비스 역할 키나 Places Web Service 키는 두지 않는다.
- `backend/src/main/resources/application-local.yml`은 로컬 전용이며 Git에 추가하지 않는다. 추적되는 `application-local.example.yml`을 복사하고 실제 값은 환경변수로 주입한다.
- Maps JavaScript 브라우저 키는 HTTP 리퍼러와 Maps JavaScript API로 제한한다. Places API (New) 검색 키는 `GOOGLE_PLACES_API_KEY`로 Spring Boot에만 주입하고 Places API만 허용하며 별도 할당량을 둔다.
- 장소 검색은 사용자가 검색 버튼을 누를 때만 서버에서 최대 5건을 요청한다. 결과 영역 안에 `Google Maps` 출처를 표시하고, 검색이 실패하거나 키가 없으면 직접 장소명과 좌표 쌍을 저장할 수 있다.
- Google Place ID는 장기 보관하되 Places API 좌표 스냅샷은 `refreshed_at`을 기준으로 30일 안에 재선택·갱신한다. 지도 조회에서는 만료 좌표를 그대로 재사용하지 않는 규칙을 C29에 적용한다.
- `backend` 비밀값은 운영 환경에서만 주입하며 저장소에 커밋하지 않는다.
- Supabase Storage 서비스 역할 키는 Cloud Run Secret Manager에만 저장하며, 백엔드는 소유자와 객체 키를 검증한 뒤 단기 업로드 토큰만 반환한다.
- 정제 요청은 인증된 소유자 API에서 작업 행을 잠근 뒤 실행한다. JPG·PNG 픽셀을 최대 2,500만 픽셀까지 디코딩하고 방향 보정 후 품질 0.92 JPEG로 새로 인코딩하며, EXIF·XMP·IPTC 재검사를 통과한 결과만 `SANITIZED_MASTER`로 저장한다.
- 정제 마스터에서 `THUMBNAIL` 320px, `MAP_CARD` 960px, `PUBLIC_DETAIL` 2,000px 파생본을 품질 0.88 JPEG로 생성한다. 작은 사진은 확대하지 않으며 세 결과 모두 메타데이터 검사를 통과해야 신규 사진을 `READY`로 전환한다.
- `JavaServerWatermarkRenderer`는 각 파생본 하단 오른쪽 픽셀에 `Places & Plates`를 합성한다. 기본 정책은 너비 16%, 여백 3%, 불투명도 28%, 밝기 기반 흰색·검은색 자동 선택이며 버전과 위치를 `photo_assets`에 기록한다.
- 신규 파생본은 먼저 논리적 `PRIVATE`로 기록한다. `PhotoAssetVerificationService`가 저장소에서 마스터와 세 파생본을 다시 내려받아 JPEG 디코딩·바이트 크기·해상도·EXIF/XMP/IPTC 0건을 확인하고, 정제 마스터에서 동일 정책으로 재생성한 파생본과 바이트가 일치할 때만 `PUBLIC`으로 전환한다.
- 초안 사진 편집 API는 `photos`의 기존 `display_order`, `is_cover`, `alt_text`를 사용한다. 배열 순서를 0부터 연속된 표시 순서로 저장하고, 대표 사진은 최대 한 장만 허용하며, 다른 소유자나 다른 초안의 사진 ID가 섞인 부분 갱신을 거부한다. 관리자 썸네일 응답은 `no-store`로 제공한다.
- 검증을 통과한 뒤 `temporary/` 객체 삭제까지 성공해야 업로드 항목은 `COMPLETED`, 사진은 `READY`가 된다. 삭제 실패는 원본 키와 `PROCESSING` 상태를 유지해 다음 정제 요청 또는 예약 작업에서 재시도한다. 검증 실패 사진은 `FAILED`로 닫고 임시 원본을 삭제한다.
- `TemporaryOriginalCleanupWorker`는 시작 30초 후, 이후 15분 간격으로 처리 완료 원본과 24시간 만료 미처리 원본을 최대 25개씩 정리한다. PostgreSQL `SECURITY DEFINER` 함수는 후보 소유자 UUID만 반환하고, 실제 조회·상태 변경은 각 소유자의 강제 RLS 범위에서 수행한다. Cloud Run이 0개 인스턴스로 축소된 동안에는 실행되지 않으므로 정확한 시각 보장이 필요해지면 Cloud Scheduler 호출형 작업으로 분리한다.
- `SUPABASE_SANITIZED_PHOTO_BUCKET`은 비공개 버킷이어야 한다. 미설정 시 기존 비공개 `temporary-uploads` 버킷의 `sanitized/` 접두사를 사용하되 C17 만료 정리는 `temporary/` 접두사만 삭제한다.
- HEIC·HEIF는 검증된 JVM 픽셀 디코더가 배포되기 전까지 실패 상태와 JPEG 변환 안내를 반환하며 원본이나 불완전한 파생본을 공개하지 않는다.
- 데이터베이스 비밀번호, 저장소 비밀키, 관리자 비밀번호는 프론트엔드에 전달하지 않는다.
- 운영 세션 쿠키는 `HttpOnly`, `Secure`, `SameSite=None`으로 설정하고 CORS는 실제 프론트 도메인만 허용한다.
- `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`는 Spring Boot만 접근하는 인프라 테이블이다. 소유자 RLS는 적용하지 않고 `placesplates_app`에만 CRUD를 허용하며 Supabase `anon`·`authenticated`와 `PUBLIC` 권한은 제거한다.
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
