# Places & Plates 개발 일정 및 커밋 계획

문서 버전: v2.0
작성일: 2026-08-25
개발 시작일: 2026-08-24
목표 공개일: 2026-10-09

## 1. 일정 가정

- 개발 인원: 1명
- 작업 시간: 주 5일, 하루 4~6시간
- 기술 방향: `frontend/` Next.js + TypeScript, `backend/` Java + Spring Boot + Gradle, PostgreSQL + 객체 저장소, Google Maps JavaScript API + Places API (New)
- 저장소 방향: 한 Git 저장소 안의 프론트엔드·백엔드 모노레포, 두 애플리케이션은 독립 빌드·배포
- 배포 방향: 정적·서버 렌더링 프론트엔드와 Spring Boot API 서버를 분리 배포하고 PostgreSQL·객체 저장소는 관리형 서비스를 사용
- 개발 용량 배분: 기능 70%, 품질·보안 20%, 예상 밖 작업 10%
- 모든 신규 게시물과 사진은 기본적으로 비공개 상태에서 시작한다.
- 지도·장소 검색은 Google Maps Platform의 SKU별 월 10,000건 무료 한도 안에서 시작하며 초기 지도 예산은 월 0원을 목표로 한다.
- 모든 개인 데이터에는 v1부터 소유자 ID를 저장하고, 향후 회원마다 독립된 페이지를 만들 수 있게 한다.

예상 일정은 구현 과정에서 발견되는 이미지 처리 제약과 Google Cloud 결제·API 키 설정에 따라 1주 정도 변동될 수 있다.

## 2. 릴리스 범위

### Must have — v1 공개 필수

- 관리자 로그인
- 맛집·여행지 게시물 작성
- 다중 사진 업로드와 초안 자동 저장
- 서버 업로드 원본 미보관과 비공개 정제 마스터 생성
- EXIF·XMP·IPTC 제거
- 공개용 이미지 축소와 서버 워터마크
- 전체·맛집·여행지 리스트 탭
- 게시물 상세 페이지
- 공개 방문 날짜 월 단위 표시
- 지도 마커, 묶음 숫자, 현재 지도 영역 게시물 수
- 리스트·지도 상태 연동
- 비공개·링크 공유·전체 공개
- 모바일 반응형 UI
- 자동 테스트, 배포, 오류 기록

### Should have — v1.1 후보

- 사진 자동 묶기
- 일괄 메타데이터 편집
- 연도·지역·태그·평점 필터
- 동일 장소 방문 이력 개선
- 이미지 외부 삽입 방지
- 운영 통계와 비용 알림

### 이번 릴리스에서 제외

- 타인 페이지 공동 편집
- 소셜 피드·팔로우
- 여행 예약·결제
- 사용자별 추적 워터마크
- 이동 경로 자동 생성
- 네이티브 모바일 앱
- 일반 회원가입·사용자별 개인 페이지·커뮤니티 탐색

## 3. 커밋 운영 원칙

1. 한 커밋에는 하나의 의도만 담는다.
2. 커밋은 가능한 한 0.5~1일 안에 완료할 크기로 나눈다.
3. 각 커밋은 `lint`, 타입 검사, 관련 테스트를 통과해야 한다.
4. 데이터베이스 변경은 마이그레이션과 롤백 설명을 함께 포함한다.
5. 환경변수, API 비밀키, 촬영 원본과 실제 개인정보는 커밋하지 않는다.
6. 리팩터링과 기능 변경은 가능한 한 다른 커밋으로 분리한다.
7. 화면 변경 커밋에는 데스크톱·모바일 검증 내용을 남긴다.
8. `main`에는 검증된 커밋만 합치고 기능 브랜치는 짧게 유지한다.
9. 모든 작업은 최신 `main`에서 만든 `codex/<scope>` 브랜치에 커밋하고 pull request로 제출한다.
10. 저장소 소유자가 검토 후 **Rebase and merge**하며 작업 브랜치에서 `main`으로 직접 push하지 않는다.
11. 커밋 제목은 `YYYY/MM/DD <type>: <English> | <한국어> | <日本語>` 형식을 사용한다.

권장 커밋 형식:

```text
feat: add restaurant and destination category tabs
fix: delete temporary originals after sanitized image verification
test: verify published images contain no EXIF metadata
docs: record map count semantics and launch checklist
chore: configure linting and CI checks
refactor: separate place and visit query services
```

권장 브랜치 형식:

```text
feat/project-foundation
feat/photo-upload
feat/image-processing
feat/post-editor
feat/public-exploration
feat/map-exploration
chore/production-deployment
```

## 4. 전체 일정

| 구간 | 날짜 | 목표 | 예상 커밋 |
|---|---|---|---:|
| Sprint 0 | 08-24 ~ 08-25 | 저장소·프로젝트 기반 | 4개 |
| Sprint 1 | 08-26 ~ 09-01 | UI 기반·DB·인증 | 6개 |
| Sprint 2 | 09-02 ~ 09-08 | 업로드·이미지 보호 파이프라인 | 7개 |
| Sprint 3 | 09-09 ~ 09-15 | 게시물 작성·장소·공개 | 6개 |
| Sprint 4 | 09-16 ~ 09-22 | 공개 리스트·상세 화면 | 5개 |
| Sprint 5 | 09-23 ~ 09-29 | 지도·숫자 집계·상태 연동 | 5개 |
| Sprint 6 | 09-30 ~ 10-06 | 보안·접근성·성능·배포 | 6개 |
| Launch buffer | 10-07 ~ 10-09 | 회귀 수정·운영 전환·공개 | 2~4개 |

총 예상 커밋: 41~43개

## 5. Sprint 0 — 저장소와 프로젝트 기반

기간: 2026-08-23 ~ 2026-08-25

진행 상태: C01~C04 구현·병합 및 자동 검증 완료

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C01 | `docs: add product specification and development roadmap` | 설계서와 일정 문서를 저장소에 추가 | 문서 링크와 버전이 맞고 민감 정보가 없음 |
| C02 | `chore: initialize frontend nextjs application` | `frontend/` Next.js·TypeScript 프로젝트와 도메인 폴더 초기화 | 로컬 개발 서버와 production build 성공 |
| C03 | `chore: initialize backend spring boot application` | `backend/` Spring Boot·Gradle·도메인 패키지 초기화 | 애플리케이션 기동과 기본 테스트 성공 |
| C04 | `ci: add monorepo verification workflow` | 프론트 lint·타입·빌드와 백엔드 테스트·빌드 자동 검사 | 변경된 애플리케이션의 GitHub Actions 검증 성공 |

Sprint 종료 게이트:

- 빈 페이지가 로컬과 미리보기 배포에서 열린다.
- 비밀키와 사진 원본이 Git에 포함되지 않는다.
- PR 검증이 실패한 코드는 `main`에 합치지 않는다.

## 6. Sprint 1 — UI 기반, 데이터 모델과 인증

기간: 2026-08-26 ~ 2026-09-01

진행 상태: C05~C10 구현·병합 및 운영 API 상태 검사 완료

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C07 | `feat: add owner scoped database schema and migrations` | profile, trip, place, post, photo_asset, tag 테이블과 owner_id | 새 환경에서 마이그레이션 재현 가능 |
| C05 | `feat: add design tokens and responsive application shell` | 색상·타이포·레이아웃·반응형 셸 | 390px·1440px에서 가로 넘침 없음 |
| C06 | `feat: add public navigation and empty states` | 홈·리스트·지도·로그인 기본 라우트 | 각 라우트와 빈 상태가 정상 표시 |
| C08 | `feat: add administrator authentication flow` | 관리자 로그인·로그아웃·세션 복구 | 비로그인 사용자는 관리 화면 접근 불가 |
| C09 | `feat: enforce owner scoped row level security policies` | 공개 상태와 소유자 기준 접근 정책 | 다른 계정의 비공개 데이터·정제 마스터·초안 조회 차단 |
| C10 | `test: cover authentication and data access policies` | 인증·권한 자동 테스트 | 권한 상승과 비공개 데이터 누출 테스트 통과 |

Sprint 종료 게이트:

- 관리자와 공개 방문자의 권한이 분리된다.
- 데이터 모델에 맛집·여행지 대표 카테고리가 존재한다.
- 공개 API로 비공개 사진 경로나 EXIF 좌표를 조회할 수 없다.

사용자 요청에 따라 Sprint 1은 DB 관계와 조회 인덱스를 먼저 확정한 뒤 UI 기반 작업을 이어간다.

## 7. Sprint 2 — 사진 업로드와 이미지 보호

기간: 2026-09-02 ~ 2026-09-08

진행 상태: C11 다중 사진 TUS 임시 업로드, C12 비공개 초안, C13 이미지 처리 작업 큐의 운영 검증 완료. C13A PostgreSQL 세션 지속성·운영 DB V10·Cloud Run 배포와 새로고침 복구 검증 완료. C14 정제 마스터와 V11 READY 백필 운영 검증 완료. C15는 320px 썸네일·960px 카드·2,000px 상세 파생본 생성, 무확대 비율 유지, 메타데이터 재검사와 기존 완료 사진 누락 보충을 구현했다. C15 운영 배포 후 발견된 Cloud Run LCMS2 누락은 Java 21·`liblcms2-2` 명시 컨테이너와 CI 이미지 검사로 보완했고, 저장소의 `backend/cloudbuild.yaml`로 Docker 배포 경로를 고정했다. C16은 하단 오른쪽 `Places & Plates` 픽셀 워터마크, 밝기 기반 색상 선택, 정책 버전·위치 기록과 기존 파생본 재생성을 구현했다. C17은 저장소에서 다시 읽은 마스터·파생본의 디코딩·크기·메타데이터·워터마크 픽셀을 검증한 뒤 공개하고 임시 원본을 즉시 삭제하며, 실패 재시도와 24시간 만료 정리를 구현했다. 운영 DB V13·V14 적용, Cloud Run 배포, 비공개 초안 운영 스모크 검증까지 완료했다.

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C11 | `feat: add resumable temporary photo uploads` | 최대 100장, 6MB TUS 청크, 일시정지·재개, 진행률, 실패 재시도, 24시간 만료 추적 | 서명 권한은 백엔드가 발급하고 서비스 역할 키·원래 파일명은 객체 키에 포함되지 않음 |
| C12 | `feat: create private drafts when uploads begin` | 맛집·여행지 선택 후 업로드 즉시 비공개 초안을 만들고 업로드 묶음과 연결 | 완료 시 초안으로 자동 이동하고 관리 화면에서 초안을 다시 열 수 있음 |
| C13 | `feat: queue image processing jobs after upload` | 업로드 항목별 단일 이미지 처리 작업, 처리 시도 횟수·재시도 시각·실패 원인 추적 | 중복 완료 요청에도 작업이 한 개만 생성되고 실패 작업을 즉시 또는 지연 재실행 가능 |
| C13A | `feat: persist authenticated sessions in postgresql` | Spring Session JDBC, 세션·속성 테이블, 런타임 역할 전용 권한 | Cloud Run 재배포·다중 인스턴스 전환 후에도 기존 인증 세션 복구 |
| C14 | `feat: create sanitized masters without image metadata` | 인증된 업로드 완료 요청에서 방향 보정·JPEG 재인코딩 후 EXIF·XMP·IPTC와 원래 파일명 제거, 2,500만 픽셀 제한과 실패 코드 기록 | 비공개 정제 마스터에서 민감 메타데이터 0건, 사진 상태 READY, 중복 요청은 동일 처리 작업으로 직렬화·완료 상태 복구 |
| C15 | `feat: generate responsive image variants and thumbnails` | 썸네일·카드·상세용 이미지 생성 | 화면별로 적합한 크기 선택 |
| C16 | `feat: burn places and plates watermarks into public images` | `Places & Plates` 서버 합성 워터마크와 정책 버전 | CSS 제거 후에도 표준 문구 유지 |
| C17 | `feat: verify images and purge temporary originals` | 저장된 정제본·공개본 바이트 재검사, 성공 원본 즉시 삭제, 실패 재시도·24시간 만료 정리와 자동 테스트 | 검증·삭제 완료 전 사진은 READY가 아니며 처리 완료 후 원본 잔존 0건, 실패 이미지는 게시 차단 |

Sprint 종료 게이트:

- 휴대폰·PC의 원본 파일은 변경되지 않고 서버의 임시 원본은 처리 완료 후 삭제된다.
- 공개 이미지에는 위치·촬영 시각·카메라 정보가 남지 않는다.
- 이미지 처리 실패 시 원본이 대신 노출되지 않으며 만료 후 삭제된다.

위험과 대응:

- 현재 Java 런타임은 HEIC·HEIF 픽셀 디코더가 없으므로 정제 단계에서 `HEIC_DECODER_UNAVAILABLE`로 비공개 실패 처리하고 JPEG 변환 후 재업로드를 안내한다. 운영용 디코더가 검증되기 전에는 성공으로 간주하지 않는다.
- 이미지 워커의 메모리 사용량이 높으면 동시 처리 수를 제한하고 큐를 사용한다.

## 8. Sprint 3 — 게시물 작성과 공개 관리

기간: 2026-09-09 ~ 2026-09-15

진행 상태: C18~C23 게시물 작성·장소·사진 구성·공개 범위와 게시 전 안전 검사의 운영 검증을 완료했다.

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C18 | `feat: add common post editor fields and autosave` | 제목·방문 월·한줄평·본문을 700ms 지연 후 CSRF 보호 자동 저장 | 제목·방문 월·한줄평 3개를 작성 완료 기준으로 안내하고 부분 초안도 소유자 DB에 저장·재조회 가능 |
| C19 | `feat: add restaurant category fields` | 평점·메뉴·가격대·대기시간·재방문을 기존 700ms 자동 저장에 포함 | 맛집 초안에만 선택 항목을 표시·저장하고 여행지 요청은 거부하며 전체 값을 비우면 상세 행 제거 |
| C20 | `feat: add destination category fields` | 소요시간·추천시간·볼거리·여행 팁을 기존 700ms 자동 저장에 포함 | 여행지 초안에만 선택 항목을 표시·저장하고 맛집 요청은 거부하며 전체 값을 비우면 상세 행 제거 |
| C21 | `feat: connect posts with google places and coordinates` | 명시적 Places API (New) 검색·Google Maps 링크·직접 좌표, Google 출처 표기와 30일 좌표 갱신 시각 | 소유자 초안에 Place ID와 좌표를 연결·해제하고 검색 실패·미설정 시 자유 장소 저장 가능 |
| C22 | `feat: add photo ordering cover selection and alt text` | 인증 썸네일·배열 기반 정렬·대표 사진 최대 한 장·500자 대체 텍스트를 600ms 자동 저장 | 기본 버튼의 Tab·Enter·Space 동작으로 사진 순서와 대표 사진을 바꾸고 재조회 시 유지 가능 |
| C23 | `feat: add private link and public publishing states` | 비공개·링크 공개·전체 공개 선택, 제목·방문 월·한줄평·장소·대표 사진과 원본 삭제·메타데이터·워터마크 정책 재검사 | 모든 조건을 통과한 소유자 초안만 선택 범위의 `PUBLISHED`로 전환하고 한 항목이라도 실패하면 게시 차단 |

Sprint 종료 게이트:

- 맛집 또는 여행지 게시물을 처음부터 끝까지 작성할 수 있다.
- 작성 중 브라우저를 닫아도 입력 내용이 복구된다.
- 게시 전 공개 이미지 검사와 필수 입력 검증이 실행된다.

## 9. Sprint 4 — 공개 리스트와 상세 페이지

기간: 2026-09-16 ~ 2026-09-22

진행 상태: C24 공개 목록·카테고리 합계, C25 안전한 대표 사진 카드·정렬, C25A 공개 오류 응답 보완, C26 카테고리별 상세, C27 같은 장소 반복 방문 기록, C28 공개 범위 회귀 행렬을 완료했다.

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C24 | `feat: add all restaurant and destination list tabs` | `GET /api/v1/public/posts`, 전체·맛집·여행지 탭과 전역 합계, 로딩·빈 목록·API 장애 상태 | 비공개·링크·초안을 제외하고 카테고리 합계가 실제 전체 공개 게시물 수와 일치 |
| C25 | `feat: add readable post cards and sorting` | 검증된 `MAP_CARD` 대표 사진 스트리밍, 반응형 카드, URL 기반 최신순·오래된순 | 비로그인 카드에서 워터마크 대표 사진·카테고리·제목·한줄평·월 단위 방문 시기를 확인하고 카테고리 전환 후에도 정렬 유지 |
| C25A | `fix: preserve public api error responses` | 오류 응답 시 요청 트랜잭션 명시 롤백, `ERROR` 디스패치 허용, 잘못된 쿼리 응답 표준화 | 없는 공개 대표 사진은 `404`, 지원하지 않는 enum 쿼리는 `400 COMMON_INVALID_QUERY`로 유지되고 인증 오류나 `502`로 덮이지 않음 |
| C26 | `feat: add category aware post detail pages` | `/posts/{postId}` 상세, 공통·맛집·여행지 분리 레이아웃, 검증된 `PUBLIC_DETAIL` 사진 API와 공개 방문월 | 사진과 개인 기록이 먼저 표시되고 일자·좌표·내부 ID·Storage 키 없이 장소명과 Google 지도 링크만 공개됨 |
| C27 | `feat: add place details and repeat visit history` | `/posts/{postId}/place`, 같은 소유자·장소 공개 방문 수·월별 타임라인·대표 사진, V15 공개 연결 장소 RLS | 내부 소유자·장소 ID·좌표와 비공개·링크 공개 기록 없이 장소명·Google 지도 링크와 각 공개 상세 링크 확인 |
| C28 | `test: cover category lists post details and visibility` | 전체·카테고리 목록, 상세·대표 사진·상세 사진·장소 이력, PostgreSQL 게시물·카테고리 상세·장소·사진·자산 공개 범위 회귀 행렬 | `PUBLIC + PUBLISHED`만 공개되고 `PRIVATE`, `UNLISTED`, `DRAFT`는 목록·합계에서 제외되며 모든 직접 조회가 404 |

Sprint 종료 게이트:

- 사용자가 카테고리 탭에서 게시물을 한 개씩 열람할 수 있다.
- 모바일과 데스크톱에서 본문 가독성과 사진 비율이 유지된다.
- 모든 공개 썸네일과 상세 사진에 보호 정책이 적용된다.

## 10. Sprint 5 — 지도 탐색과 게시물 수

기간: 2026-09-23 ~ 2026-09-29

진행 상태: C29는 공개 지도 API, 전체·맛집·여행지 URL 필터, 맛집 주황색 ‘맛’·여행지 초록색 ‘여’ Google 지도 마커와 명시적 지연 로딩을 구현했다. V16은 좌표가 연결된 기존 게시물의 지도 공개 상태를 보정하며 Google 좌표는 30일 유효 기간 안에서만 사용한다. C30은 공식 Supercluster 기반 확대 수준별 인접 마커 묶음, 포함 게시물 숫자와 클릭 확대를 구현했다. C31은 지도 이동·확대가 끝나는 `idle` 이벤트마다 현재 경계의 전체·맛집·여행지 게시물 수를 즉시 갱신한다. C32는 PC 70:30 지도·축소 목록, 모바일 지도 우선·가로 카드, 검색·현재 영역 제한과 마커·카드 양방향 선택을 구현하고 URL에 탐색 상태를 보존한다. C33은 전역·카테고리·영역·클러스터·동일 장소 재방문 숫자를 프런트 단위 테스트와 Spring API 통합 테스트로 고정했다. Sprint 5를 완료했으며 다음은 C34 이미지 보조 억제 기능이다.

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C29 | `feat: add category styled map markers` | 맛집·여행지 마커 색상과 아이콘 | 카테고리 필터와 마커가 일치 |
| C30 | `feat: cluster nearby posts and show post counts` | 지도 확대 수준별 마커 묶음 | 묶음 숫자와 포함 게시물 수 일치 |
| C31 | `feat: count posts within the current map bounds` | 현재 화면 게시물 합계 | 지도 이동 후 500ms 안에 숫자 갱신 |
| C32 | `feat: add map first split view and synchronize selection` | PC 지도 70%·축소 목록 30%, 모바일 지도+가로 카드, 양방향 선택 | 필터·검색·지도 위치·선택 상태가 유지되고 리스트 탭의 기존 카드 구성은 변경되지 않음 |
| C33 | `test: cover map counts filtering and repeat visits` | 지도 집계와 재방문 테스트 | 전체·영역·묶음·장소별 숫자 정확성 검증 |

Sprint 종료 게이트:

- 전체·맛집·여행지 필터가 리스트와 지도에 동시에 반영된다.
- 지도 축소 시 묶음 숫자, 확대 시 개별 장소가 표시된다.
- 동일 장소의 여러 게시물을 한 마커 카드에서 확인할 수 있다.

## 11. Sprint 6 — 보안, 접근성, 성능과 배포

기간: 2026-09-30 ~ 2026-10-06

진행 상태: C34는 공개 사진 보조 억제와 같은 출처 중계를 완료했고 C35는 프런트·API 보안 헤더와 공개 응답·로그 비식별화를 고정했다. C36은 Google Maps 로더·클러스터러 모듈을 명시적 지도 요청 뒤로 분리하고 공개 사진 로딩·캐시를 최적화했다. C37은 전역 포커스 표시와 페이지 이동 링크 의미, 공개 사진 대체 문구와 지도 카드 레이블을 개선했다. C38은 격리형 API·TUS fixture와 Playwright로 업로드→게시→목록→지도 흐름을 데스크톱·모바일에서 자동 검증한다. C39는 성공한 `main` 검증 뒤 Vercel·Cloud Run의 동일 커밋 배포, 핵심 페이지·공개 API·보안 헤더·비공개 경로 부재를 재시도형 운영 스모크로 확인한다. C40은 실제 공개 데이터 홈, 소유자 전용 초안·게시 기록 삭제, 공개 사진 확대와 상세 정보 순서를 수정했다. C40A는 공개 상세 삭제 진입점을 보강하고 홈 내비게이션과 기록 작성 CTA를 단순화했다. C40B는 홈 영웅 영역을 `나의 기록` 제목과 기록하기 링크 중심으로 단순화했고, C40C는 기록하기 버튼을 첨부 수정안 크기로 줄여 나머지 영역을 공백으로 유지한다. 다음은 C41 운영·백업·장애 대응 문서다.

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C34 | `feat: deter image context menu dragging and hotlinking` | 우클릭·드래그 억제와 외부 삽입 방지 | 원본 보호와 별개인 보조 억제 기능으로 동작 |
| C35 | `fix: harden public responses and security headers` | 비밀정보 제거, CSP·보안 헤더 | 공개 HTML·API·로그에 원본 키 없음 |
| C36 | `perf: lazy load maps and optimize public images` | 지도 지연 로딩·이미지 최적화 | 목록 진입만으로 지도 과금 호출이 발생하지 않음 |
| C37 | `fix: improve keyboard navigation and accessible labels` | 3px 포커스, 탭 화살표 이동, 모달 포커스 고정·복귀, 링크 의미·사진 fallback·지도 레이블 | 홈 카테고리·미리보기와 공개 목록·지도 진입을 키보드로 완료 가능 |
| C38 | `test: add end to end upload publish and browse flows` | 업로드→게시→목록→지도 E2E | 데스크톱·모바일 핵심 시나리오 통과 |
| C39 | `ci: add production deployment and smoke tests` | Vercel·Cloud Run 원자적 운영 배포, 커밋 식별 헤더, `Verify` 성공 후 재시도형 읽기 전용 스모크 | 배포 실패 시 운영 버전 유지, 양쪽 커밋 일치와 핵심 페이지·공개 API·보안 정책 검사 통과 |

Sprint 종료 게이트:

- 공개 페이지에서 임시 업로드 경로와 촬영 메타데이터가 검출되지 않는다.
- Google Maps API 키가 운영 도메인과 허용 API로 제한되고 월 9,000회 지도 로드 운영 한도와 예산 알림이 설정된다.
- 데이터베이스 백업과 복구 절차가 문서화된다.
- 핵심 페이지의 모바일·데스크톱 smoke test가 통과한다.

## 12. Launch buffer — 공개 준비

기간: 2026-10-07 ~ 2026-10-09

| ID | 예상 커밋 | 작업 내용 | 완료 조건 |
|---|---|---|---|
| C40 | `fix: add record deletion and replace home mock data` | 소유자 전용 초안·게시 기록 영구 삭제, 실제 공개 데이터 홈, 공개 사진 확대, CATEGORY NOTE 상단 배치, 상단 여행 CTA | 저장소와 DB가 함께 정리되고 홈·상세가 실제 공개 데이터와 보호 파생본만 표시하며 요청된 6개 회귀 항목을 해소 |
| C40A | `fix: expose public record deletion and simplify home actions` | 인증된 관리자용 공개 상세 삭제, 관리 화면 게시 기록 우선 배치, 여행 메뉴 제거, 대표 게시물 영웅 카드를 기록하기 CTA로 교체 | 공개 상세와 관리 화면에서 삭제 기능을 명확히 찾을 수 있고 홈 상단은 기록·지도·기록하기 동선만 제공 |
| C40B | `fix: simplify home hero to one writing action` | 제목을 나의 기록으로 변경, 영웅 영역의 전체 기록·지도 링크 제거, 기록하기 카드의 설명 제거와 링크 확대 | 홈 상단 우측에는 부가 문구 없이 큰 기록하기 링크만 표시되고 공개 통계와 최근 기록은 유지 |
| C40C | `fix: resize home writing button` | 기록하기 버튼을 첨부 수정안의 작은 상자 크기로 축소하고 우측 나머지 영역을 공백 처리 | 데스크톱에서 약 430×270px, 모바일에서 화면 폭에 맞는 기록하기 버튼만 우측 하단에 표시 |
| C41 | `docs: add operations backup and incident runbook` | 운영·백업·비용·장애 대응 문서 | 다른 환경에서도 복구 절차 수행 가능 |
| C42 | `chore: configure production domain budgets and alerts` | 도메인·예산·사용량 알림 | API 서버·PostgreSQL·객체 저장소·Google Maps·프론트 호스팅 예산 경고 설정 |
| C43 | `chore: release places and plates v1` | 운영 배포와 버전 태그 | 공개 URL에서 출시 점검표 통과 |

출시를 미뤄야 하는 조건:

- 임시 업로드 경로나 정제 마스터 경로가 공개 응답에 포함됨
- 공개 파생본에서 GPS·카메라 정보가 검출됨
- 이미지 처리 실패 시 원본이 노출됨
- 비로그인 사용자가 초안이나 비공개 사진에 접근 가능함
- 지도 또는 카테고리 게시물 수가 실제 데이터와 일치하지 않음
- 데이터 백업과 복원 방법이 검증되지 않음

## 13. 의존성 순서

```text
저장소·CI
   ↓
데이터 모델·인증·권한
   ↓
비공개 업로드·이미지 처리
   ↓
게시물 편집·공개 정책
   ↓
리스트·상세
   ↓
지도·숫자 집계·상태 연동
   ↓
보안·접근성·성능
   ↓
운영 배포
```

외부 의존성:

| 의존성 | 필요한 시점 | 대응 |
|---|---|---|
| PostgreSQL·객체 저장소 | Sprint 1 시작 전 | Supabase 서울 리전 Free `nano`와 PostGIS로 시작하고, 관리자 마이그레이션 역할과 RLS 런타임 역할을 분리하며 공개 전 운영 플랜 판단 |
| Google Cloud 프로젝트·결제 계정·API 키 | Sprint 3 장소 연결 전 | 브라우저 Maps JavaScript 키와 서버 Places API (New) 키를 분리하고 API 제한·월 9,000회 운영 한도 설정 |
| 이미지 워커 환경 | Sprint 2 시작 전 | 로컬 처리 프로토타입 후 운영 환경 선택 |
| 워터마크 스타일 | Sprint 2 C16 | `Places & Plates`, 하단 오른쪽, 너비 16%, 여백 3%, 불투명도 28%, 배경 밝기 기반 흰색·검은색 자동 선택 |
| 도메인 | Sprint 6 후반 | 이름 확정 후 구매, 그전에는 미리보기 URL 사용 |

## 14. 각 커밋의 완료 정의

커밋을 완료로 판단하려면 다음 조건을 모두 만족해야 한다.

- [ ] 커밋 메시지가 변경 목적을 설명한다.
- [ ] 관련 코드와 테스트가 같은 커밋에 포함된다.
- [ ] lint와 타입 검사가 통과한다.
- [ ] 관련 단위·통합 테스트가 통과한다.
- [ ] 데이터베이스 변경에는 마이그레이션이 있다.
- [ ] 환경변수와 비밀키가 커밋되지 않았다.
- [ ] 실제 촬영 원본이나 개인정보가 fixture에 포함되지 않았다.
- [ ] 처리 완료 또는 만료된 임시 업로드 원본이 서버에 남지 않았다.
- [ ] 사용자 화면 변경은 390px와 1440px에서 확인했다.
- [ ] 설계 결정이 바뀌었다면 문서도 함께 갱신했다.

## 15. 출시 후 다음 단계

### Now — v1 출시까지

- 안전한 업로드·게시·열람의 핵심 루프
- 맛집·여행지 리스트와 지도 탐색
- 원본 미보관·메타데이터 제거·워터마크 보호

### Next — 출시 후 1~3개월

- 실제 사용 데이터 기반 입력 흐름 개선
- 사진 자동 묶기와 일괄 편집
- 연도·지역·태그·평점 필터
- 지도 성능과 비용 최적화
- 백업 내보내기

### Later — 3개월 이후

- 일반 회원가입과 사용자별 독립 페이지
- 공개 프로필·게시물 탐색과 링크 공유
- 본인 기록만 편집 가능한 계정별 권한 정책 확장
- 이동 경로와 여행 통계
- 사용자별 공유 워터마크
- 네이티브 모바일 입력 경험
