# Places & Plates 데이터베이스 설계

문서 버전: v1.5
작성일: 2026-08-24

## 1. 적용 범위

- PostgreSQL을 운영 데이터베이스로 사용한다.
- 지도 영역 검색은 PostGIS `geography(Point, 4326)`와 GiST 인덱스를 사용한다.
- Flyway가 새 데이터베이스에 스키마와 인덱스를 순서대로 적용한다.
- 모든 개인 기록은 `owner_user_id`를 통해 소유자 경계를 가진다.
- 업로드 원본은 임시 경로에만 존재하며 처리 완료 후 경로와 실제 파일을 제거한다.
- 정확한 방문일은 비공개 데이터이고 공개 응답에는 연도와 월만 제공한다.

## 2. 핵심 관계

```text
APP_USER.id ───────────── PROFILE.user_id
      │
      ├────────────────── TRIP.owner_user_id
      ├────────────────── POST.owner_user_id
      ├────────────────── PHOTO.owner_user_id
      ├────────────────── UPLOAD_BATCH.owner_user_id
      └────────────────── TAG.owner_user_id

TRIP.id ──────────────── POST.trip_id
PLACE.id ─────────────── POST.place_id
POST.id ──────────────── RESTAURANT_DETAIL.post_id
POST.id ──────────────── DESTINATION_DETAIL.post_id
POST.id ──────────────── POST_TAG.post_id
TAG.id ───────────────── POST_TAG.tag_id
POST.id ──────────────── PHOTO.post_id
POST.id ──────────────── UPLOAD_BATCH.post_id
UPLOAD_BATCH.id ──────── UPLOAD_ITEM.upload_batch_id
POST.id ──────────────── IMAGE_PROCESSING_JOB.post_id
UPLOAD_ITEM.id ───────── IMAGE_PROCESSING_JOB.upload_item_id (UNIQUE)
PHOTO.id ─────────────── UPLOAD_ITEM.result_photo_id
PHOTO.id ─────────────── PHOTO_ASSET.photo_id
```

`POST.category`는 `RESTAURANT` 또는 `DESTINATION` 중 하나다. PostgreSQL 트리거가 카테고리와 전용 상세 테이블의 불일치를 차단한다.

## 3. 마이그레이션 구성

| 파일 | 적용 대상 | 내용 |
|---|---|---|
| `db/migration/common/V1__create_owner_scoped_schema.sql` | 모든 DB | 테이블·외래키·검사 제약·공통 인덱스 |
| `db/migration/postgresql/V2__add_postgis_and_partial_indexes.sql` | PostgreSQL | PostGIS 위치 컬럼·GiST·공개 부분 인덱스·카테고리 트리거 |
| `db/migration/common/V3__add_account_role.sql` | 모든 DB | 관리자·일반 회원 역할 컬럼과 검사 제약 |
| `db/migration/postgresql/V4__enforce_owner_scoped_row_security.sql` | PostgreSQL | 소유자·공개 모드 함수와 12개 개인 데이터 테이블의 강제 RLS 정책 |
| `db/migration/postgresql/V5__grant_runtime_role_and_restrict_data_api.sql` | PostgreSQL | 제한된 런타임 역할 권한과 Supabase Data API의 백엔드 테이블 접근 차단 |
| `db/migration/common/V6__track_resumable_upload_progress.sql` | 모든 DB | TUS 업로드 진행률·재시도·실패 원인과 만료 인덱스 |
| `db/migration/common/V7__create_image_processing_jobs.sql` | 모든 DB | 업로드 항목별 단일 이미지 처리 작업과 재시도 상태·인덱스 |
| `db/migration/postgresql/V8__secure_image_processing_jobs.sql` | PostgreSQL | 이미지 처리 작업 강제 RLS·런타임 권한·Data API 차단 |

Spring Boot는 데이터베이스 종류에 맞춰 `db/migration/{vendor}` 경로를 추가한다. 테스트에서는 H2에 공통 마이그레이션을 적용해 관계와 안전 제약을 빠르게 확인한다.

### PostgreSQL 행 수준 보안

- 보호 API 요청은 트랜잭션 시작 시 `app.current_user_id=<인증 사용자 UUID>`, `app.request_mode=OWNER`를 설정한다.
- 공개 API 요청은 사용자 UUID를 비우고 `app.request_mode=PUBLIC`을 설정한다.
- 프로필·여행·장소·게시물·전용 상세·태그 관계·사진·사진 자산·업로드 테이블은 `ENABLE`과 `FORCE ROW LEVEL SECURITY`를 모두 적용한다.
- `OWNER` 모드는 직접 `owner_user_id`를 비교하거나 부모 테이블의 소유자를 확인한다.
- `PUBLIC` 모드는 전체 공개·게시 완료 행과 안전 검사를 통과한 공개 사진 자산만 읽을 수 있다.
- 임시 업로드, 정제 마스터와 다른 사용자의 초안은 공개 정책이 없으므로 조회 결과에 포함되지 않는다.
- `app_users`는 로그인 시 이메일로 계정을 찾아야 하므로 RLS 대상에서 제외하고 인증 Repository 외의 접근과 API 노출을 금지한다.

RLS는 행 경계를 보호하며 열 마스킹을 대신하지 않는다. 공개 API DTO는 `visited_on`, 정확한 좌표, 비공개 저장 키를 선택하지 않고 공개 연월·허용 좌표·공개 자산만 명시적으로 투영해야 한다.

## 4. 주요 무결성 규칙

- 이메일과 프로필·여행·태그 주소명은 소문자로 저장한다.
- 계정 역할은 `ADMIN` 또는 향후 커뮤니티 회원을 위한 `MEMBER` 중 하나다.
- 게시물 대표 카테고리는 맛집 또는 여행지 중 정확히 하나다.
- 사진 업로드 묶음을 만들 때 `PRIVATE`·`DRAFT` 게시물을 먼저 생성하고 `upload_batches.post_id`로 연결한다.
- 공개 게시물은 장소, 공개 방문 연월, 게시 시각을 가져야 한다.
- 여행에 포함된 게시물 순서는 한 여행 안에서 중복될 수 없다.
- 한 게시물의 대표 사진은 최대 한 장이다.
- 공개 이미지 자산은 메타데이터 검사와 워터마크 적용을 모두 통과해야 한다.
- 정제 마스터는 항상 비공개 자산이다.
- 완료된 업로드 항목에는 임시 원본 저장 경로가 남을 수 없다.
- `upload_items`는 비공개 업로드 화면을 위해 파일 표시명·MIME·선언 크기·업로드 바이트·시도 횟수·실패 코드·24시간 만료를 추적한다. 객체 키는 원래 파일명 대신 소유자·묶음·항목 UUID로 생성한다.
- `image_processing_jobs.upload_item_id`는 UNIQUE이며 업로드 완료 요청이 반복되어도 처리 작업은 하나만 생성한다. 작업은 최대 5회까지 처리 시도 횟수, 다음 실행 시각, 마지막 실패 코드를 추적하고 소유자 RLS 밖으로 노출되지 않는다.

## 5. 조회 인덱스

### 공통 인덱스

- 사용자별 여행 시작일: `trips(owner_user_id, started_on DESC)`
- 관리자 게시물 목록: `posts(owner_user_id, status, updated_at DESC)`
- 장소·카테고리 게시물: `posts(place_id, category)`
- 태그별 게시물: `post_tags(tag_id, post_id)`
- 게시물 사진 순서: `photos(post_id, display_order)`
- 사용자별 사진 처리 상태: `photos(owner_user_id, processing_status)`
- 사용자별 업로드 묶음: `upload_batches(owner_user_id, created_at DESC)`
- 업로드 묶음별 파일 상태: `upload_items(upload_batch_id, processing_status)`
- 만료 정리 대상: `upload_items(expires_at, processing_status)`
- 처리 가능한 작업 조회: `image_processing_jobs(owner_user_id, status, next_attempt_at)`
- 초안별 처리 작업: `image_processing_jobs(post_id, created_at DESC)`

### PostgreSQL 전용 인덱스

- 지도 영역 검색: `places.location` GiST
- 공개 게시물 최신순: `posts(owner_user_id, published_at DESC)` 부분 인덱스
- 카테고리별 공개 게시물: `posts(owner_user_id, category, published_at DESC)` 부분 인덱스
- 지도 게시물: `posts(owner_user_id, place_id, category)` 부분 인덱스
- 게시물별 대표 사진 한 장: `photos(post_id) WHERE is_cover = TRUE` UNIQUE
- 만료 업로드 정리: `expires_at` 활성 상태 부분 인덱스

카테고리·상태·공개 여부·불리언 컬럼에는 단독 인덱스를 만들지 않는다. 값의 종류가 적어 효율이 낮으므로 소유자·날짜·장소와 결합하거나 부분 인덱스 조건으로만 사용한다.

## 6. 사진 원본 보호

```text
UPLOAD_ITEM.temporary_storage_key
        ↓ 메타데이터 제거·재인코딩
PHOTO
        ↓ 파생본 생성
PHOTO_ASSET
        ↓ 검사 성공
임시 원본 삭제 + temporary_storage_key = NULL
```

`PHOTO_ASSET.variant_type`에는 `SANITIZED_MASTER`, `PUBLIC_DETAIL`, `THUMBNAIL`, `MAP_CARD`만 허용한다. 업로드 원본 유형은 존재하지 않는다.

## 7. 실행 준비

운영 또는 로컬 PostgreSQL에는 PostGIS 확장을 생성할 수 있는 권한이 필요하다. Supabase `placesplates` 프로젝트는 서울 리전의 무료 `nano` 구성이며 PostGIS 3.3.7을 `extensions` 스키마에 미리 활성화한다.

필수 환경변수:

```text
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
```

실제 값은 Git에 저장하지 않는다. 로컬 개발은 애플리케이션 시작 시 Flyway를 적용한다. Supabase 운영 DB는 `scripts/provision-supabase-database.ps1`에서 관리자 연결로 Flyway를 실행하고, 런타임은 제한된 `placesplates_app` 연결과 `FLYWAY_ENABLED=false`를 사용한다. 세부 절차는 `docs/SUPABASE_DATABASE.md`를 기준으로 한다.

GitHub Actions는 `postgis/postgis:17-3.5` 서비스에서 전체 PostgreSQL 마이그레이션을 실행하고, 두 소유자와 공개 모드의 게시물·사진 자산·업로드 격리를 실제 엔진으로 검증한다. 로컬에 PostgreSQL이 없는 경우 H2 검증은 수행되지만 PostgreSQL RLS 통합 테스트는 건너뛴다.

## 8. 규모 증가 후 재검토

- 커뮤니티 전체 지도에는 `place_id`가 선두인 공개 게시물 인덱스를 추가한다.
- 연도·월, 평점, 가격대 필터가 실제로 추가될 때만 해당 인덱스를 만든다.
- 본문 검색은 일반 B-Tree 대신 PostgreSQL Full Text Search와 GIN을 검토한다.
- 운영 데이터가 쌓이면 `EXPLAIN (ANALYZE, BUFFERS)`와 느린 쿼리 로그로 사용되지 않는 인덱스를 제거한다.
- 운영 중 대형 인덱스를 추가할 때는 비트랜잭션 Flyway 마이그레이션에서 `CREATE INDEX CONCURRENTLY`를 사용한다.
