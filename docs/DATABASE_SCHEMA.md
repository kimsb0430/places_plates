# Places & Plates 데이터베이스 설계

문서 버전: v1.0  
작성일: 2026-08-23

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
UPLOAD_BATCH.id ──────── UPLOAD_ITEM.upload_batch_id
PHOTO.id ─────────────── UPLOAD_ITEM.result_photo_id
PHOTO.id ─────────────── PHOTO_ASSET.photo_id
```

`POST.category`는 `RESTAURANT` 또는 `DESTINATION` 중 하나다. PostgreSQL 트리거가 카테고리와 전용 상세 테이블의 불일치를 차단한다.

## 3. 마이그레이션 구성

| 파일 | 적용 대상 | 내용 |
|---|---|---|
| `db/migration/common/V1__create_owner_scoped_schema.sql` | 모든 DB | 테이블·외래키·검사 제약·공통 인덱스 |
| `db/migration/postgresql/V2__add_postgis_and_partial_indexes.sql` | PostgreSQL | PostGIS 위치 컬럼·GiST·공개 부분 인덱스·카테고리 트리거 |

Spring Boot는 데이터베이스 종류에 맞춰 `db/migration/{vendor}` 경로를 추가한다. 테스트에서는 H2에 공통 마이그레이션을 적용해 관계와 안전 제약을 빠르게 확인한다.

## 4. 주요 무결성 규칙

- 이메일과 프로필·여행·태그 주소명은 소문자로 저장한다.
- 게시물 대표 카테고리는 맛집 또는 여행지 중 정확히 하나다.
- 공개 게시물은 장소, 공개 방문 연월, 게시 시각을 가져야 한다.
- 여행에 포함된 게시물 순서는 한 여행 안에서 중복될 수 없다.
- 한 게시물의 대표 사진은 최대 한 장이다.
- 공개 이미지 자산은 메타데이터 검사와 워터마크 적용을 모두 통과해야 한다.
- 정제 마스터는 항상 비공개 자산이다.
- 완료된 업로드 항목에는 임시 원본 저장 경로가 남을 수 없다.

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

운영 또는 로컬 PostgreSQL에는 PostGIS 확장을 생성할 수 있는 권한이 필요하다. 관리형 서비스에서 애플리케이션 계정의 확장 생성이 제한된 경우 데이터베이스 관리 화면에서 PostGIS를 먼저 활성화한다.

필수 환경변수:

```text
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>
```

실제 값은 Git에 저장하지 않는다. 애플리케이션 시작 시 Flyway가 마이그레이션을 적용하고 Hibernate는 `ddl-auto=validate`로 결과만 검증한다.

## 8. 규모 증가 후 재검토

- 커뮤니티 전체 지도에는 `place_id`가 선두인 공개 게시물 인덱스를 추가한다.
- 연도·월, 평점, 가격대 필터가 실제로 추가될 때만 해당 인덱스를 만든다.
- 본문 검색은 일반 B-Tree 대신 PostgreSQL Full Text Search와 GIN을 검토한다.
- 운영 데이터가 쌓이면 `EXPLAIN (ANALYZE, BUFFERS)`와 느린 쿼리 로그로 사용되지 않는 인덱스를 제거한다.
- 운영 중 대형 인덱스를 추가할 때는 비트랜잭션 Flyway 마이그레이션에서 `CREATE INDEX CONCURRENTLY`를 사용한다.
