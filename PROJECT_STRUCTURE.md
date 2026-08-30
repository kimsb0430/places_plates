# Places & Plates 프로젝트 폴더 구조

문서 버전: v2.7
작성일: 2026-08-30

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
├── .github/workflows/           # 프론트·백엔드 CI와 운영 스모크
├── .gitignore
└── README.md
```

기존 디자인 목업은 Sprint 0에서 `frontend/` Next.js 애플리케이션으로 이전했다. 공개된 기존 미리보기는 유지하며 이후 개발과 배포의 기준 소스는 `frontend/`로 통일한다.

프론트엔드는 동일한 App Router 소스에서 두 배포 산출물을 만든다. Vercel은 `pnpm build:vercel`로 표준 `.next` 산출물을 만들고, 기존 OpenAI Sites 미리보기는 `pnpm build`로 Vinext `dist` 산출물을 만든다. 두 산출물은 CI에서 각각 빌드하고 비밀정보를 검사한다.

Cloud Run 배포는 저장소의 `backend/cloudbuild.yaml`을 Cloud Build 트리거 구성 파일로 사용한다. 이 구성은 `backend/`를 Docker 빌드 컨텍스트로 지정하고 `backend/Dockerfile`로 이미지를 빌드·푸시한 뒤 기존 Cloud Run 서비스 이미지만 갱신한다. 빌드 단계와 실행 단계 모두 Java 21을 사용하며, 실행 이미지에는 Java ImageIO의 ICC 색상 프로필 처리에 필요한 `liblcms2.so.2`를 제공하는 `liblcms2-2` 패키지를 명시적으로 설치한다. 애플리케이션은 UID 10001 비루트 사용자로 실행하고 CI에서 실제 이미지를 빌드해 라이브러리와 실행 사용자를 확인한다. Cloud Build 트리거가 인라인 Buildpacks 구성을 사용하면 Dockerfile을 무시하므로 금지한다.

C39의 `.github/workflows/production-smoke.yml`은 `main`의 `Verify` 성공 이벤트 뒤에만 실행한다. Vercel의 `VERCEL_GIT_COMMIT_SHA`와 Cloud Build가 `APP_COMMIT_SHA`로 Cloud Run에 주입한 동일 커밋을 각 상태 응답의 `X-Places-Plates-Commit` 헤더로 비교한다. 배포가 아직 진행 중이면 최대 30회 재시도하고, 더 최신 커밋이 병합되면 이전 스모크를 취소해 이미 대체된 커밋 때문에 거짓 실패가 발생하지 않게 한다. 기본 운영 URL은 공개 값으로 저장소에 두되 다른 환경은 GitHub Repository Variables `PRODUCTION_FRONTEND_URL`, `PRODUCTION_API_URL`로 교체한다. 스모크는 읽기 전용 페이지·공개 API만 호출하고 Google Maps SDK 로드, 로그인, 업로드, 게시 같은 과금·상태 변경 동작은 수행하지 않는다.

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
- 게시 패널은 `post/components/draft-publication-panel.tsx`가 담당한다. 비공개·링크 공개·전체 공개를 선택하고 서버의 게시 준비 검사 결과를 항목별로 표시하며, 입력 자동 저장이 끝나고 모든 검사에 통과했을 때만 게시 요청을 보낸다. 공개 목록·상세 URL 제공은 C24~C27의 공개 조회 화면에서 연결한다.
- 공개 기록 화면은 `/posts` 서버 컴포넌트가 `post/api/public-post-api.ts`로 비로그인 목록을 읽고, 쿼리 문자열의 카테고리와 `LATEST`·`OLDEST` 정렬을 링크에 반영한다. `public-post-index.tsx`는 검증된 `MAP_CARD` 대표 사진·카테고리·제목·한줄평·월 단위 방문 시기를 반응형 카드로 표시하고 `/posts/[postId]` 상세로 연결한다. 상세 서버 컴포넌트는 `GET /api/v1/public/posts/{postId}`에서 `PUBLIC + PUBLISHED` 기록만 읽고 공통 본문·월 단위 방문 시기·장소명·Google 지도 링크와 현재 카테고리의 전용 정보만 렌더링한다. 상세 사진은 `READY` 사진의 검증된 현재 워터마크 `PUBLIC_DETAIL`만 `/api/v1/public/posts/{postId}/photos/{photoId}`로 제공하며 소유자 ID·내부 장소 ID·좌표·게시 일자·Storage 키는 응답하지 않는다. C34의 `protected-public-image.tsx`는 목록·상세·장소 이력의 공개 사진에 우클릭·드래그·복사와 모바일 길게 누르기 억제를 일관되게 적용한다. 브라우저는 Cloud Run 주소나 Vercel `/_next/image` 대신 같은 출처 `/api/public-images/posts/**` Route Handler에서 이미 정제·리사이즈·워터마크된 파생본을 `unoptimized`로 읽는다. 이 중계 응답과 Cloud Run 사진 API는 `Cross-Origin-Resource-Policy: same-origin`, 프레임 차단 CSP·`X-Frame-Options: DENY`를 반환해 외부 브라우저 직접 삽입을 억제한다. 이 보호 레이어와 헤더는 일반적인 저장·삽입만 줄이며 브라우저 표시 사진의 개발자 도구 접근·프록시 복사·화면 캡처를 완전히 차단한다고 표현하지 않는다. 실제 보호 경계는 원본 미제공·메타데이터 제거·픽셀 워터마크다.
- C36에서 `protected-public-image.tsx`는 목록 첫 대표 사진과 상세 hero만 Next.js 16의 `preload`로 앞당기고 나머지는 `loading="lazy"`·`decoding="async"`로 처리한다. 이미지 중계 fetch는 1시간 Next Data Cache 재검증을 사용하며 Cloud Run·Vercel 응답은 `max-age=3600`, `s-maxage=3600`, `stale-while-revalidate=86400`을 공유한다. URL 뒤의 사진 자산이 바뀔 수 있으므로 immutable 장기 캐시는 사용하지 않고, C34 보안 헤더를 잃는 `/_next/image` 재최적화도 사용하지 않는다.
- C37에서 전역 포커스 표시는 링크·버튼뿐 아니라 입력·선택·텍스트 영역까지 3px 고대비 윤곽선으로 통일한다. 홈 카테고리 탭은 roving `tabIndex`와 좌우 화살표·Home·End를 지원하고 미리보기 대화상자는 초기 포커스·Tab 순환·Escape 닫기·호출 버튼 복귀를 관리한다. 공개 목록·지도 카테고리는 페이지 이동 링크이므로 `role="tab"` 대신 `aria-current="page"`를 사용한다. `post/public-photo-alt.ts`는 비어 있는 공개 사진 설명을 기록 제목 기반 대체 문구로 보완하며 지도 카드 선택은 `aria-controls`로 실제 지도 영역과 연결한다.
- C40에서 홈은 하드코딩 목업을 제거하고 `GET /api/v1/public/posts`의 실제 합계와 최근 카드를 서버에서 읽는다. C40A는 헤더를 기록·지도 두 메뉴로 단순화하고 영웅 영역의 대표 게시물 사진 대신 `/manage` 기록 작성 카드를 배치한다. C40B는 홈 제목을 `나의 기록`으로 바꾸고 영웅 영역의 전체 기록·지도 링크와 기록 작성 카드의 설명을 모두 제거해, 큰 `기록하기` 링크 하나만 남긴다. 고정된 `Kyoto, Spring 2026` 하단 영역은 표시하지 않는다. 공개 상세의 `CATEGORY NOTE`는 사진보다 위에 배치하며, 갤러리 선택 시 원본 업로드가 아닌 최대 2,000px의 메타데이터 제거·워터마크 적용 `PUBLIC_DETAIL`을 키보드 닫기·포커스 복귀가 가능한 확대 대화상자로 표시한다.
- 관리 홈의 `draft-list.tsx`·`published-post-list.tsx`와 초안 편집 화면은 확인 대화상자 뒤 소유자 전용 삭제 API를 호출한다. `PostManagementService`는 요청자의 ID와 `DRAFT` 또는 `PUBLISHED` 상태를 다시 확인하고, 연결된 임시 객체와 `sanitized/`·`variants/` 자산을 비공개 Storage에서 삭제한 다음 업로드 배치·사진·게시물 행을 삭제한다. Storage 정리가 실패하면 DB 삭제를 시작하지 않으며 공유될 수 있는 장소 행은 유지한다.
- `frontend/next.config.ts`는 모든 페이지와 Route Handler에 공통 보안 헤더를 적용한다. CSP는 같은 출처를 기본으로 Google Maps 공식 도메인, Google Fonts, 비공개 Supabase TUS 연결과 운영 API origin만 허용하고 `frame-ancestors`·`object-src`를 차단한다. 지도 팝업 호환성을 위해 `Cross-Origin-Opener-Policy`는 `same-origin-allow-popups`를 사용하며 카메라·마이크·위치·결제·USB 권한은 `Permissions-Policy`로 비활성화한다. API origin은 `NEXT_PUBLIC_API_BASE_URL`의 origin만 CSP에 넣고 경로나 쿼리는 포함하지 않는다.
- 같은 장소 방문 기록은 내부 장소 ID 대신 이미 공개된 게시물 ID를 진입점으로 하는 `/posts/[postId]/place` 서버 컴포넌트가 담당한다. `GET /api/v1/public/posts/{postId}/place`는 진입 게시물의 소유자와 장소를 내부적으로 해석하고 같은 `owner_user_id + place_id`의 `PUBLIC + PUBLISHED`만 공개 방문 월 오래된 순으로 반환한다. `public-place-history.tsx`는 방문 수·장소명·Google 지도 링크·검증된 `MAP_CARD`·각 상세 링크를 타임라인으로 표시하며 다른 회원의 기록, 비공개·링크 공개 게시물, 내부 소유자·장소 ID, 좌표, 게시 시각은 응답하지 않는다. PostgreSQL V15는 공개 게시물에 연결된 장소 행만 `PUBLIC` 모드에서 읽도록 허용한다.
- 공개 지도는 `/map` 서버 컴포넌트가 `map/api/public-map-api.ts`로 `GET /api/v1/map/posts`를 읽고 URL의 전체·맛집·여행지 필터를 API 쿼리와 일치시킨다. `google-map-explorer.tsx`는 사용자가 버튼을 누른 뒤에만 `@googlemaps/js-api-loader`와 `@googlemaps/markerclusterer` 모듈을 병렬 dynamic import하고, 이어서 필요한 `maps`·`marker` 라이브러리만 요청한다. 따라서 일반 `/map` 진입과 `/posts` 목록 진입은 외부 지도 스크립트·Dynamic Maps 호출을 만들지 않는다. Map ID가 있으면 Advanced Marker를 사용하며 없으면 호환 기본 마커를 사용한다. 맛집은 주황색 ‘맛’, 여행지는 초록색 ‘여’ 마커로 구분하고 안전한 DOM 조립 정보창에서 공개 상세로 이동한다. 지도 API는 `PUBLIC + PUBLISHED`, 좌표 공개 허용, 위도·경도 쌍을 모두 검사하며 30일이 지난 Google 좌표를 제외하고 `APPROXIMATE`는 소수점 둘째 자리로 낮춘다. C30은 Supercluster를 반경 72px·최대 묶음 확대 17로 적용하고 묶음 마커 숫자를 포함 게시물 수로 직접 표시한다. `map-cluster-presentation.ts`는 실제 포함 마커 카테고리 배열에서 숫자·색상·접근성 문구를 계산한다. 단일 카테고리 묶음은 해당 색상, 혼합 묶음은 브랜드 색상을 사용하며 묶음 선택은 기본 범위 확대 동작을 유지한다. C31의 `map-viewport-count.ts`는 날짜 변경선까지 고려해 현재 경계 안의 개별 게시물을 전체·맛집·여행지로 계산하고, 탐색 컴포넌트가 지도 `idle` 이벤트마다 표시를 즉시 갱신한다. C32의 `map-split-explorer.tsx`는 지도 SDK 경계를 넓히지 않고 검색·현재 영역 카드 제한·마커/카드 선택 상태를 관리한다. PC는 70:30 분할, 모바일은 지도 다음 가로 카드를 사용하고 검색어·선택 ID·중심·확대·로드 상태를 URL에 보존한다. 검색 결과 변경은 기존 클러스터 마커 집합만 교체하며 `/posts` 목록 UI는 변경하지 않는다.

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

공개 API도 요청별 PostgreSQL RLS 컨텍스트를 유지하기 위해 보안 필터의 트랜잭션 안에서 실행한다. 컨트롤러가 `4xx`·`5xx` 오류 응답을 정상적으로 작성한 경우 필터는 해당 요청 트랜잭션을 명시적으로 롤백해 이미 작성된 응답이 `UnexpectedRollbackException`으로 바뀌지 않게 한다. Spring의 `ERROR` 디스패치는 인증 없이 통과시키되 직접 보호 API 접근은 기존 인증 규칙을 유지하며, 잘못된 enum 쿼리는 전역 예외 처리에서 `400 COMMON_INVALID_QUERY`로 통일한다.

`SecurityConfig`는 정상·인증 실패·오류를 포함한 모든 API 응답에 실행 불가능한 API 전용 CSP, 브라우저 권한 차단, `no-referrer`, MIME 추측 방지와 프레임 차단 헤더를 기록한다. 공개 DTO는 브라우저용 상대 이미지 경로만 반환하며 소유자 ID와 `temporary/`·`sanitized/`·`variants/` Storage 키를 직렬화하지 않는다. 백그라운드 정리 실패 로그는 예외 클래스명만 남겨 원본 키가 포함될 수 있는 예외 메시지와 스택 추적을 운영 로그에 기록하지 않는다.

## 5. 핵심 도메인과 API 경계

| 도메인 | 주요 책임 | API 예시 |
|---|---|---|
| auth | CSRF 발급·로그인·PostgreSQL 지속 세션·로그아웃 | `/api/v1/auth/**` |
| profile | 회원별 개인 페이지 | `/api/v1/profiles/**` |
| post | 맛집·여행지 게시물, 업로드 시작 초안, 공통·카테고리 전용 필드 자동 저장, 공개 범위·게시 전 안전 검사, 소유자 삭제와 공개 카테고리·정렬 목록 | `GET/PATCH/DELETE /api/v1/manage/drafts/**`, `GET/DELETE /api/v1/manage/posts/**`, `PUT/DELETE /api/v1/manage/drafts/{draftId}/place`, `GET /api/v1/manage/drafts/{draftId}/publication-readiness`, `POST /api/v1/manage/drafts/{draftId}/publication`, `GET /api/v1/public/posts` |
| place | 인증 소유자의 Places API (New) 검색, Google Place ID·좌표·직접 입력 장소 | `GET /api/v1/manage/places/search` |
| photo | 초안과 연결된 임시 업로드, 중복 방지 이미지 처리 큐, 정제 마스터·워터마크 반응형 파생본·사진 READY 전환·삭제 상태, 소유자 전용 썸네일·순서·대표·대체 텍스트 편집, 공개 카드 대표 사진 | `/api/v1/manage/photo-uploads/**`, `/api/v1/manage/photo-uploads/{batchId}/items/{itemId}/sanitize`, `/api/v1/manage/image-processing-jobs/**`, `GET/PUT /api/v1/manage/drafts/{draftId}/photos`, `GET /api/v1/manage/drafts/{draftId}/photos/{photoId}/thumbnail`, `GET /api/v1/public/posts/{postId}/cover`, `/api/v1/photos/**` |
| trip | 여행 묶음·대표 여행 | `/api/v1/trips/**` |
| map | 공개 좌표·30일 Google 스냅샷·카테고리별 개별 마커, 클라이언트 확대 수준별 묶음 숫자와 현재 지도 경계의 전체·카테고리 합계 | `GET /api/v1/map/posts` |

## 6. 테스트 구조

```text
frontend/tests/
├── unit/
├── component/
└── e2e/                        # Playwright 사용자 흐름과 격리형 API·TUS fixture

backend/src/test/java/com/placesplates/
├── domain/                      # 서비스·Repository 테스트
├── api/                         # Controller 통합 테스트
└── support/                     # fixture·테스트 설정
```

- 프론트엔드는 공개 카테고리·정렬 쿼리 유지, 합계·대표 사진 카드·빈 목록·API 장애, 목록·지도 전환, 업로드 입력과 초안 공통·카테고리·사진 구성 자동 저장 상태를 중심으로 테스트한다. C33 단위 테스트는 일반 경계·날짜 변경선 영역의 전체·카테고리 게시물 수와 단일·혼합 카테고리 클러스터의 실제 포함 게시물 수·색상·문구를 검증한다.
- C38 Playwright E2E는 별도 로컬 API·TUS fixture를 기동해 실제 운영 계정·Supabase Storage·Google Maps 과금 호출 없이 사진 업로드, 정제 완료, 초안 공통 필드와 직접 좌표 장소 저장, 전체 공개 게시, 공개 목록과 지도 축소 목록 노출을 하나의 흐름으로 검증한다. 같은 시나리오를 1440px 데스크톱 Chromium과 Pixel 7 모바일 Chromium에서 실행하고 두 공개 화면의 가로 넘침도 확인한다. 로컬은 필요하면 `PLAYWRIGHT_BROWSER_CHANNEL=chrome|msedge`로 설치된 브라우저를 사용하며 CI는 Playwright Chromium을 설치한다.
- C39 배포 계약 검사는 Vercel 상태 route, Cloud Run health 헤더, Cloud Build 커밋 주입, `Verify` 성공 후 운영 스모크 연결이 함께 유지되는지 정적으로 확인한다. 실제 운영 스모크는 홈·목록·지도와 공개 API 상태·보안 헤더·비공개 경로 부재·병합 커밋 일치를 확인한다.
- 백엔드는 소유자 권한, 초안 공통 필드와 카테고리 전용 필드 검증, 사진 전체 집합·대표 한 장 제약, 비공개 썸네일, 원본 자동 삭제, 메타데이터 제거를 중심으로 테스트한다. C28 공개 범위 회귀 묶음은 전체·카테고리 목록의 합계와 항목, 상세·대표 사진·상세 사진·장소 이력의 직접 URL을 함께 호출해 `PRIVATE`, `UNLISTED`, `DRAFT`가 모두 닫히는지 확인한다. C33 API 테스트는 같은 좌표·장소의 재방문도 각각 하나의 지도 게시물로 세고, 장소 이력은 같은 소유자의 `PUBLIC + PUBLISHED` 방문만 월순으로 집계하는지 확인한다. 실제 PostgreSQL 통합 테스트는 `PUBLIC + PUBLISHED`만 게시물·카테고리 상세·연결 장소·`READY` 사진·안전한 공개 자산에서 보이고 나머지 공개 범위·상태 조합은 RLS에서 제외되는지 검증한다.
- C40~C40B 삭제 테스트는 CSRF 없는 변경 요청을 거부하고, 소유자가 선택한 상태의 기록만 삭제하며, Storage 객체가 하나라도 정리되지 않으면 게시물·사진·업로드 DB 행을 유지하는지 검증한다. 프론트 계약 테스트는 홈의 목업 데이터 제거와 실제 공개 API 연결, 기록·지도 전용 메뉴, 설명 없는 대형 기록하기 CTA, 인증된 관리자에게만 보이는 공개 상세 삭제 진입점, 확대 화면의 대화상자 이름·Escape 닫기를 확인한다.
- 루트 검증 스크립트가 프론트엔드 빌드와 백엔드 테스트를 한 번에 실행한다.

## 7. 환경변수 원칙

- `frontend/.env`: 브라우저에 공개 가능한 `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY`, 선택적인 `NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID`와 백엔드 API 주소만 둔다. Supabase 서비스 역할 키나 Places Web Service 키는 두지 않는다.
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
- 공개 카드 목록은 대표 `Photo`가 `READY`이고 `MAP_CARD` 자산이 `PUBLIC`·메타데이터 검사 통과·워터마크 적용·현재 정책 버전·`BOTTOM_RIGHT`를 모두 만족할 때만 사진 경로와 크기를 응답한다. 사진 바이트는 비로그인 `GET /api/v1/public/posts/{postId}/cover`가 같은 조건과 게시물의 `PUBLIC + PUBLISHED`를 다시 검사한 뒤 비공개 Storage에서 읽어 스트리밍한다. 저장 키와 정제 마스터는 응답하지 않는다.
- 초안 사진 편집 API는 `photos`의 기존 `display_order`, `is_cover`, `alt_text`를 사용한다. 배열 순서를 0부터 연속된 표시 순서로 저장하고, 대표 사진은 최대 한 장만 허용하며, 다른 소유자나 다른 초안의 사진 ID가 섞인 부분 갱신을 거부한다. 관리자 썸네일 응답은 `no-store`로 제공한다.
- 게시 서비스는 요청 때마다 소유자 `DRAFT`를 다시 조회한다. 제목·방문 월·한줄평·장소·사진 한 장 이상·대표 사진 정확히 한 장, 모든 사진 `READY`, 모든 업로드 항목 `COMPLETED`와 임시 원본 키 제거·삭제 시각, 정제 마스터의 비공개·메타데이터 검사, 세 공개 파생본의 현재 워터마크 버전과 위치를 모두 확인한다. 하나라도 실패하면 `POST_PUBLICATION_NOT_READY`로 상태 전환을 거부하며 통과하면 선택한 범위와 `PUBLISHED`·게시 시각을 한 트랜잭션에서 저장한다.
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
