# Places & Plates 운영·백업·장애 대응 런북

문서 버전: v1.0  
작성일: 2026-08-31

이 문서는 운영 장애를 확인하고 서비스를 복구하거나, Supabase 프로젝트를 새 환경으로 복원할 때 사용한다. 현재 운영 구성은 Vercel 프론트엔드, Google Cloud Run Spring Boot API, Supabase PostgreSQL·비공개 Storage, Google Maps Platform이다.

## 1. 가장 먼저 할 일

1. 변경 작업을 멈추고 장애 시작 시각, 발견 경로, 영향 화면을 기록한다.
2. `main`의 마지막 정상 커밋과 현재 Vercel·Cloud Run 커밋을 비교한다.
3. 데이터 노출 가능성이 있으면 기능 확인보다 먼저 공개 접근을 줄이고 관련 비밀값을 회전한다.
4. 코드 장애는 배포 롤백으로, 데이터 장애는 백업 복원으로 분리한다. DB 마이그레이션이 포함된 장애에서는 코드만 되돌리지 않는다.
5. 복구 후 `scripts/test-production-smoke.ps1`과 실제 관리자 로그인·대표 사진 조회를 확인한다.

초기 운영 목표이며 외부 SLA는 아니다.

| 항목 | 목표 | 의미 |
|---|---:|---|
| RPO | 24시간 | 마지막 정상 백업 이후 최대 24시간의 변경을 다시 입력할 수 있음 |
| RTO | 4시간 | 장애 판단부터 핵심 공개 조회와 관리자 기록 기능 복구까지 목표 시간 |
| 백업 보존 | 최근 4주 주간본 + 최근 3개월 월간본 | 암호화된 저장소에 Git 저장소와 분리하여 보관 |

## 2. 운영 자산과 접근 권한

| 자산 | 운영 식별자 | 필요한 접근 |
|---|---|---|
| 프론트엔드 | `https://placesplates.vercel.app` | Vercel 프로젝트 Owner 또는 배포 관리 권한 |
| API | `places-plates-api`, `asia-northeast3`, GCP 프로젝트 `placesplates` | Cloud Run Viewer, Logs Viewer, 롤백 시 Cloud Run Admin |
| 데이터베이스 | Supabase 프로젝트의 PostgreSQL 17 | Dashboard Owner와 DB 관리자 비밀번호 |
| 사진 저장소 | Supabase 비공개 버킷, `sanitized/`·`variants/` | Storage S3 서버 전용 접근 키 |
| 소스·자동 검증 | `kimsb0430/places_plates` | GitHub Actions 읽기, 복구 PR 생성 권한 |
| 지도·장소 검색 | Google Maps Platform | API 키·할당량·결제 보기 권한 |

운영 비밀값, DB URL, S3 접근 키, 관리자 비밀번호, 사진 파일과 백업은 Git에 저장하지 않는다. 명령에 사용하는 실제 값은 현재 셸이나 비밀 저장소에서만 주입하고 작업 후 제거한다.

## 3. 정상 상태 기준

다음 항목을 모두 만족해야 정상이다.

- Vercel `/api/deployment`와 Cloud Run `/api/v1/health`가 `200`, `status=UP`을 반환한다.
- 두 응답의 `X-Places-Plates-Commit`이 `main`의 같은 40자리 커밋이다.
- `/`, `/posts`, `/map`, 공개 게시물 API와 지도 API가 `200`이다.
- 공개 응답에 `temporary/`, `sanitized/`, `variants/` Storage 키가 없다.
- 관리자 로그인 후 초안 목록이 보이고, 사진 업로드·저장·게시가 가능하다.
- 공개 대표 사진은 Vercel의 같은 출처 `/api/public-images/**`에서 표시된다.

읽기 전용 자동 점검:

```powershell
$expectedCommit = git rev-parse origin/main
./scripts/test-production-smoke.ps1 `
  -FrontendBaseUrl 'https://placesplates.vercel.app' `
  -ApiBaseUrl 'https://places-plates-api-481849639838.asia-northeast3.run.app' `
  -ExpectedCommitSha $expectedCommit `
  -RetryCount 3 `
  -RetryDelaySeconds 10
```

## 4. 백업 정책

Supabase 데이터베이스 백업은 Storage 객체 바이트를 포함하지 않는다. Storage는 객체 버전 관리도 제공하지 않으므로 DB와 사진을 같은 백업 시각에 별도로 보관해야 한다. `temporary/`는 24시간 안에 삭제해야 하는 업로드 원본이므로 백업하지 않는다. 복구 대상은 다음뿐이다.

- PostgreSQL `public` 스키마의 애플리케이션 데이터
- `sanitized/`의 메타데이터 제거 정제 마스터
- `variants/`의 워터마크 적용 공개 파생본
- 백업 매니페스트: UTC 시각, 소스 커밋, DB 파일 SHA-256, Storage 파일 수·전체 크기

복구하지 않는 데이터:

- `spring_session`, `spring_session_attributes`: 복구 후 다시 로그인한다.
- `flyway_schema_history`: 대상 환경의 저장소 마이그레이션이 생성한다.
- `temporary/`: 원본 미보관 정책을 유지한다.
- 실제 비밀번호·API 키·서비스 역할 키: 대상 호스팅 비밀 저장소에서 새로 설정한다.

### 실행 주기

| 시점 | 작업 |
|---|---|
| 매주 | DB 데이터와 `sanitized/`·`variants/` 전체 백업, 해시·개수 기록 |
| 매월 | 별도 Supabase 프로젝트 또는 격리 환경으로 전체 복구 훈련 |
| DB 마이그레이션 전 | 배포 직전 백업과 정상 커밋 기록 |
| 대량 삭제·키 회전 전 | 즉시 수동 백업 |
| 백업 후 | 로컬 임시 사본을 암호화된 외부 저장소로 이동하고 평문 임시 파일 삭제 |

현재 Free 플랜을 유지하는 동안 수동 논리 백업을 단일 기준으로 사용한다. 유료 플랜의 Dashboard 일일 백업 또는 PITR을 사용하더라도 Storage 객체는 별도 백업한다.

## 5. 백업 실행

### 5.1 준비

- Supabase CLI와 Docker Desktop
- PostgreSQL `psql`
- AWS CLI 또는 다른 S3 호환 클라이언트
- Supabase Dashboard의 **Connect → Session pooler** 연결 문자열
- Supabase Dashboard의 **Storage → Configuration → S3**에서 만든 서버 전용 임시 접근 키

백업 디렉터리는 저장소 밖에 만든다.

```powershell
$backupStamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$backupRoot = Join-Path 'C:\PlacesPlatesBackups' $backupStamp
New-Item -ItemType Directory -Path $backupRoot, "$backupRoot\database", "$backupRoot\storage" -Force
git rev-parse origin/main | Set-Content "$backupRoot\source-commit.txt"
```

### 5.2 PostgreSQL 백업

`<SOURCE_SESSION_POOLER_URL>`에는 현재 셸에서만 실제 연결 문자열을 넣는다. 명령 기록이나 보고서에는 남기지 않는다.

```powershell
supabase db dump `
  --db-url '<SOURCE_SESSION_POOLER_URL>' `
  --schema public `
  --file "$backupRoot\database\schema-audit.sql"

supabase db dump `
  --db-url '<SOURCE_SESSION_POOLER_URL>' `
  --schema public `
  --data-only `
  --use-copy `
  --exclude public.spring_session `
  --exclude public.spring_session_attributes `
  --exclude public.flyway_schema_history `
  --file "$backupRoot\database\data.sql"

Get-FileHash "$backupRoot\database\schema-audit.sql", "$backupRoot\database\data.sql" `
  -Algorithm SHA256 |
  Export-Csv "$backupRoot\database-sha256.csv" -NoTypeInformation
```

`schema-audit.sql`은 구조 비교용이다. 새 환경 복원은 저장소의 Flyway V1~V16을 먼저 적용하고 `data.sql`만 넣는다.

### 5.3 비공개 사진 백업

S3 연결은 Supabase Dashboard에서 활성화하고 서버 전용 접근 키를 현재 셸 환경변수로만 설정한다. 기본 버킷이 `temporary-uploads`가 아니거나 정제 버킷을 분리했다면 `<SANITIZED_BUCKET>`을 실제 비공개 버킷명으로 바꾼다.

```powershell
$env:AWS_ACCESS_KEY_ID = '<SOURCE_S3_ACCESS_KEY_ID>'
$env:AWS_SECRET_ACCESS_KEY = '<SOURCE_S3_SECRET_ACCESS_KEY>'
$env:AWS_DEFAULT_REGION = '<SOURCE_S3_REGION>'
$sourceEndpoint = 'https://<SOURCE_PROJECT_REF>.storage.supabase.co/storage/v1/s3'
$sourceBucket = '<SANITIZED_BUCKET>'

aws s3 sync "s3://$sourceBucket/sanitized" "$backupRoot\storage\sanitized" `
  --endpoint-url $sourceEndpoint --no-progress
aws s3 sync "s3://$sourceBucket/variants" "$backupRoot\storage\variants" `
  --endpoint-url $sourceEndpoint --no-progress

$storageFiles = Get-ChildItem "$backupRoot\storage" -File -Recurse
$storageFiles |
  ForEach-Object {
    $hash = Get-FileHash $_.FullName -Algorithm SHA256
    [pscustomobject]@{ Path = $_.FullName.Substring($backupRoot.Length + 1); Size = $_.Length; Sha256 = $hash.Hash }
  } |
  Export-Csv "$backupRoot\storage-manifest.csv" -NoTypeInformation

[pscustomobject]@{
  CreatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
  StorageFileCount = $storageFiles.Count
  StorageBytes = ($storageFiles | Measure-Object Length -Sum).Sum
} | ConvertTo-Json | Set-Content "$backupRoot\backup-summary.json"

Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_DEFAULT_REGION -ErrorAction SilentlyContinue
```

백업에 `storage\temporary` 디렉터리가 생겼다면 실패로 간주하고 암호화 보관 전에 삭제한다. 원본 사진, EXIF 포함 파일, 비밀값은 매니페스트에도 기록하지 않는다.

### 5.4 백업 완료 판정

- `data.sql`과 두 SHA-256 기록이 존재한다.
- DB 백업에 `spring_session` 데이터와 실제 비밀번호가 없다.
- Storage 매니페스트 파일 수가 `sanitized/`와 `variants/` 다운로드 수와 같다.
- `temporary/` 파일 수가 0이다.
- 백업을 Git 저장소 밖의 암호화 저장소로 복사했다.
- 당일 보고서에는 경로 대신 백업 시각, 파일 수, 검증 성공 여부만 기록했다.

## 6. 새 Supabase 프로젝트로 복원

복원 훈련은 운영 프로젝트를 덮어쓰지 않고 새 프로젝트에서 수행한다. 대상 프로젝트가 외부 트래픽을 받기 전에 완료한다.

### 6.1 대상 환경 준비

1. 소스와 같은 리전에 새 Supabase 프로젝트를 만든다.
2. PostGIS를 `extensions` 스키마에 활성화한다.
3. 소스와 같은 이름의 비공개 Storage 버킷을 만든다.
4. 대상 DB 관리자 비밀번호와 20자 이상의 새 `placesplates_app` 비밀번호를 준비한다.
5. 대상 S3 서버 전용 접근 키를 만든다.

### 6.2 Storage를 먼저 복원

```powershell
$env:AWS_ACCESS_KEY_ID = '<TARGET_S3_ACCESS_KEY_ID>'
$env:AWS_SECRET_ACCESS_KEY = '<TARGET_S3_SECRET_ACCESS_KEY>'
$env:AWS_DEFAULT_REGION = '<TARGET_S3_REGION>'
$targetEndpoint = 'https://<TARGET_PROJECT_REF>.storage.supabase.co/storage/v1/s3'
$targetBucket = '<SANITIZED_BUCKET>'

aws s3 sync "$backupRoot\storage\sanitized" "s3://$targetBucket/sanitized" `
  --endpoint-url $targetEndpoint --no-progress
aws s3 sync "$backupRoot\storage\variants" "s3://$targetBucket/variants" `
  --endpoint-url $targetEndpoint --no-progress

Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_DEFAULT_REGION -ErrorAction SilentlyContinue
```

대상 Storage 파일 수와 로컬 `storage-manifest.csv` 개수가 일치할 때만 다음 단계로 간다.

### 6.3 Flyway 스키마와 데이터 복원

저장소 루트에서 대상 프로젝트에 마이그레이션과 제한 런타임 역할을 만든다. 스크립트는 두 비밀번호를 화면에 표시하지 않고 입력받는다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
./scripts/provision-supabase-database.ps1 `
  -ProjectReference '<TARGET_PROJECT_REF>' `
  -PoolerHost '<TARGET_POOLER_HOST>'
```

그 다음 세션과 Flyway 이력을 제외한 데이터만 관리자 연결로 넣는다.

```powershell
psql `
  --single-transaction `
  --set ON_ERROR_STOP=on `
  --command 'SET session_replication_role = replica' `
  --file "$backupRoot\database\data.sql" `
  --dbname '<TARGET_SESSION_POOLER_URL>'
```

복원 후 `scripts/provision-supabase-database.ps1`을 같은 런타임 비밀번호로 다시 실행해 `0 migration(s)`와 런타임 접속 검증을 확인한다.

### 6.4 Cloud Run을 대상 프로젝트에 연결

1. 대상 `DATABASE_URL`, `DATABASE_USERNAME`, 런타임 DB 비밀번호 Secret을 설정한다.
2. 대상 Storage API URL, 비공개 버킷명, 새 서비스 역할 키 Secret을 설정한다.
3. `FLYWAY_ENABLED=false`, `ADMIN_BOOTSTRAP_ENABLED=false`를 유지한다.
4. 새 리비전을 트래픽 0%로 배포해 health와 관리자 로그인을 검사한다.
5. 공개 목록·대표 사진·지도 API가 정상이고 Storage 키가 노출되지 않을 때만 트래픽을 전환한다.

복원된 세션은 없으므로 모든 브라우저에서 다시 로그인하는 것이 정상이다. 관리자 계정은 `app_users` 데이터로 복원되며 비밀번호를 모르면 별도의 승인된 비밀번호 재설정 절차를 사용한다.

### 6.5 복원 완료 판정

- Flyway V1~V16과 애플리케이션 14개·세션 2개 테이블이 존재한다.
- RLS 검증과 `placesplates_app` 제한 접속이 성공한다.
- 게시물·사진·자산·장소 수가 백업 전 기록과 일치한다.
- 임의의 공개 기록 대표 사진과 상세 사진이 표시된다.
- `temporary/` 객체는 복원되지 않았다.
- 로그인, 초안 저장, 사진 업로드, 게시, 공개 목록과 지도가 정상이다.
- Vercel·Cloud Run 커밋이 같고 운영 스모크가 통과한다.

## 7. 배포 롤백

### 7.1 Vercel 프론트엔드

장애가 프론트엔드에만 있고 API·DB 계약이 이전 버전과 호환될 때 사용한다.

```powershell
vercel list --prod
vercel rollback
vercel rollback status
```

Vercel Hobby 플랜은 바로 이전 운영 배포로만 롤백할 수 있다. 롤백하면 운영 도메인 자동 할당이 중지될 수 있으므로 수정 배포 후 `vercel promote <GOOD_DEPLOYMENT_URL>` 또는 Dashboard의 **Undo Rollback**으로 정상 배포 흐름을 복구한다.

### 7.2 Cloud Run API

```powershell
gcloud run revisions list `
  --service places-plates-api `
  --region asia-northeast3 `
  --project placesplates

gcloud run services update-traffic places-plates-api `
  --to-revisions '<KNOWN_GOOD_REVISION>=100' `
  --region asia-northeast3 `
  --project placesplates
```

이전 리비전의 환경변수와 Secret 버전이 여전히 유효한지 먼저 확인한다. DB 마이그레이션이 이전 코드와 호환되지 않으면 API만 롤백하지 말고, 배포 전 백업을 새 Supabase 프로젝트로 복원해 함께 전환한다.

### 7.3 롤백 검증

1. health와 커밋 헤더를 확인한다.
2. 공개 목록·지도·대표 사진을 확인한다.
3. 관리자 로그인과 초안 읽기를 확인한다.
4. 자동 운영 스모크를 실행한다.
5. 장애 커밋, 정상 커밋, 롤백 리비전과 확인 시각을 일일 보고서에 남긴다.

## 8. 장애 등급과 대응

| 등급 | 예시 | 최초 대응 | 목표 |
|---|---|---|---|
| P1 | 비공개 사진·Storage 키 노출, 원본 잔존 공개, DB·Storage 손실, 자격 증명 유출 | 쓰기·공개 범위를 줄이고 키 회전, 정상 리비전 또는 새 복구 프로젝트로 전환 | 즉시 착수, 30분마다 상태 기록 |
| P2 | 로그인 전체 실패, 업로드·게시 실패, 공개 API 5xx, 지도 과금 급증 | 마지막 배포·환경변수·서비스 상태 확인, 영향 기능 롤백 또는 일시 비활성화 | 4시간 안에 핵심 기능 복구 |
| P3 | 일부 사진 지연, 지도 한 기능 실패, 모바일 표시 오류 | 재현 정보와 브라우저·커밋 기록 후 일반 수정 PR | 다음 작업 단위에서 수정 |

### 공통 진단 순서

```powershell
gh run list --repo kimsb0430/places_plates --branch main --limit 10
gcloud run services describe places-plates-api --region asia-northeast3 --project placesplates
gcloud logging read `
  'resource.type="cloud_run_revision" AND resource.labels.service_name="places-plates-api" AND severity>=ERROR' `
  --project placesplates --limit 50
```

로그를 보고서에 붙일 때 세션 ID, 이메일, 객체 키, DB URL과 예외에 포함된 비밀값을 제거한다.

### P1 보안·데이터 노출

1. 공개 응답과 실제 객체 접근 여부를 확인하되 원본을 내려받아 공유하지 않는다.
2. 새 요청을 막기 위해 정상 Vercel 배포와 Cloud Run 리비전으로 되돌린다.
3. 노출 가능성이 있는 Supabase 서비스 역할 키, S3 키, DB 비밀번호, Google API 키를 회전한다.
4. Storage 경로와 DB 행을 보존해 영향 범위를 확인한다. 증거 보존 전 대량 삭제하지 않는다.
5. 공개 파생본의 EXIF·XMP·IPTC 0건과 워터마크를 다시 검사한다.
6. 원인이 제거된 새 리비전만 배포하고 운영 스모크 후 재개한다.

### P2 로그인 실패

1. `/api/v1/auth/csrf`의 CORS가 정확한 Vercel origin을 허용하는지 확인한다.
2. 세션 쿠키가 `HttpOnly; Secure; SameSite=None`인지 확인한다. 쿠키 값 자체는 기록하지 않는다.
3. `spring_session` 테이블 권한과 만료 시각, Cloud Run DB 접속을 확인한다.
4. 모바일만 실패하면 제3자 쿠키 차단 여부를 확인한다. 장기 해결은 프론트와 API의 같은 사이트 도메인 구성이다.

### P2 사진 업로드·처리 실패

1. 비공개 버킷과 서비스 역할 키 Secret 버전, TUS 서명 요청을 확인한다.
2. `liblcms2.so.2`, 메모리, 처리 작업 실패 코드를 확인한다.
3. 실패 사진은 공개하지 않고 재시도 가능한 `PROCESSING` 상태와 임시 키를 유지한다.
4. 24시간 만료 원본 정리가 동작하는지 확인한다.

### 외부 서비스 장애

- Vercel: <https://www.vercel-status.com/>
- Google Cloud: <https://status.cloud.google.com/>
- Supabase: <https://status.supabase.com/>

외부 장애 중에는 동일 변경을 반복 배포하지 않는다. 서비스 상태와 마지막 정상 리비전을 기록하고, 읽기 기능이 유지되면 데이터 변경을 최소화한다.

## 9. 비용 이상 대응

C42에서 실제 예산·할당량 알림을 설정하기 전까지 다음 대응 기준을 사용한다.

| 수준 | 확인과 대응 |
|---:|---|
| 예상 월 한도의 50% | 서비스별 증가 원인, Cloud Run 요청·인스턴스, Supabase egress·Storage, Maps load를 확인 |
| 80% | 불필요한 테스트 배포와 지도 로드를 중지하고, 지도는 명시적 로드 버튼을 유지하며 API 할당량을 낮출지 검토 |
| 100% 또는 비정상 급증 | 비용 발생 기능을 일시 중단하고 유출 키를 회전하며, 안전한 할당량 제한을 적용한 뒤 재개 |

Google Cloud 예산은 알림이지 자동 지출 상한이 아니다. 자동으로 결제를 끄면 Cloud Run과 Maps가 함께 중단될 수 있으므로 명시적 승인 없이 적용하지 않는다. Supabase Dashboard에서는 Compute, Egress, Storage Size를 함께 확인하고, Google Maps Platform에서는 Maps JavaScript API와 Places API (New)를 분리해 본다.

## 10. 정기 운영표

| 주기 | 점검 |
|---|---|
| 매 배포 | GitHub Verify·Secret protection·Production smoke, 배포 커밋 일치 |
| 매주 | DB·Storage 백업, health, 오류 로그, 만료 원본 0건, 비용 추세 |
| 매월 | 새 프로젝트 복구 훈련, 관리자 로그인·공개 사진·RLS 검증, API 키 제한 점검 |
| 분기 | 오래된 백업 폐기, 접근 권한·Secret 버전·런타임 의존성 검토 |

## 11. 장애·복구 기록 양식

```text
발견 시각(UTC/JST):
등급과 사용자 영향:
마지막 정상 커밋·리비전:
현재 프론트/API 커밋:
데이터 노출 또는 손실 가능성:
수행한 조치와 시각:
사용한 백업 시각·무결성 결과:
롤백 또는 복원 대상:
스모크 결과:
남은 위험과 후속 작업:
```

비밀번호, 키, 세션 ID, 원본 사진 경로와 실제 Storage 키는 기록하지 않는다.

## 12. 공식 참고 문서

- [Supabase Database Backups](https://supabase.com/docs/guides/platform/backups)
- [Supabase CLI Backup and Restore](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore)
- [Supabase Storage S3 Authentication](https://supabase.com/docs/guides/storage/s3/authentication)
- [Supabase Storage S3 Compatibility](https://supabase.com/docs/guides/storage/s3/compatibility)
- [Cloud Run rollbacks and traffic migration](https://cloud.google.com/run/docs/rollouts-rollbacks-traffic-migration)
- [Vercel production rollback](https://vercel.com/docs/deployments/rollback-production-deployment)
- [Google Cloud budgets and alerts](https://cloud.google.com/billing/docs/how-to/budgets)
- [Maps JavaScript API usage and billing](https://developers.google.com/maps/documentation/javascript/usage-and-billing)
- [Supabase usage management](https://supabase.com/docs/guides/platform/manage-your-usage)

---

## 日本語要約

### 目的と復旧目標

本runbookはVercel、Cloud Run、Supabase PostgreSQL・非公開Storage、Google Maps Platformの障害と新環境復旧に使用する。初期目標はRPO 24時間、RTO 4時間であり外部SLAではない。

### Backup境界

- 毎週、Supabase CLIで`public` schemaのdataを論理backupする。
- `spring_session`、`spring_session_attributes`、`flyway_schema_history`はdata backupから除外する。
- StorageはDB backupに含まれないため、S3互換endpointから`sanitized/`と`variants/`を別途保存する。
- 原本非保持方針により`temporary/`はbackup・restoreしない。
- DB file SHA-256、Storage file数・byte数、UTC時刻、source commitをmanifestへ記録する。
- BackupとmanifestはGit外の暗号化storageへ保存し、実credentialや元写真情報を記録しない。

### 新Supabase projectへのRestore順序

1. 同一regionのproject、PostGIS、同名private bucketを準備する。
2. `sanitized/`と`variants/`を先にS3 syncし、manifest件数を確認する。
3. `scripts/provision-supabase-database.ps1`でFlyway V1〜V16と制限runtime roleを作成する。
4. Admin接続で`data.sql`だけをsingle transaction restoreする。
5. Provision scriptを再実行し`0 migration(s)`とruntime接続を確認する。
6. 新しいDB・Storage SecretをCloud Runへ設定し、traffic 0% revisionでhealth、login、public photo、RLSを確認する。
7. Frontend・API commit一致とproduction smoke成功後だけtrafficを切り替える。

Session dataは復旧しないため、復旧後の再loginは正常である。DB migrationが旧codeと非互換の場合、codeだけをrollbackせずdeployment前backupを新projectへrestoreして同時に切り替える。

### Rollback

- Vercel: `vercel rollback`後に`vercel rollback status`を確認する。修正版の復帰時は`vercel promote`またはDashboardのUndo Rollbackで自動domain assignmentも復旧する。
- Cloud Run: `gcloud run services update-traffic places-plates-api --to-revisions '<KNOWN_GOOD_REVISION>=100' --region asia-northeast3 --project placesplates`で既知の正常revisionへ100% trafficを戻す。
- 復旧後はhealth、公開list・map・photo、管理者login、`scripts/test-production-smoke.ps1`を確認する。

### Incidentとcost

- P1はprivate data・original・credential露出、DB・Storage lossであり、公開範囲縮小、正常revision復帰、credential rotationを直ちに行う。
- P2はlogin・upload・publish・public API全面障害またはcost急増であり、4時間以内のcore復旧を目標とする。
- P3は一部表示・device固有問題として通常の修正PRで扱う。
- 月間想定の50%で原因調査、80%で不要なmap load・test deployment抑制、100%または異常急増で該当機能停止とkey rotationを行う。
- Budget alertは支出上限ではない。明示的承認なしにbillingを停止しない。

毎月、新Supabase projectへのrestore drillを行い、件数、RLS、管理者login、公開photo、Storage path非公開、production smokeを確認する。
