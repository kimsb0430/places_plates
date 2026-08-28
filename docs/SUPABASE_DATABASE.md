# Places & Plates Supabase 데이터베이스 운영

## 한국어

### 현재 구성

- Supabase 프로젝트 `placesplates`는 GitHub 저장소와 연결되어 있다.
- PostgreSQL 리전은 서울이며 무료 `nano` 컴퓨팅을 사용한다.
- PostGIS 3.3.7은 Supabase의 `extensions` 스키마에 활성화되어 있다.
- 운영 DB의 실제 적용 버전은 `flyway_schema_history`로 확인한다. 저장소 기준선은 V16이며 C29 애플리케이션 배포 전에 V15 `places_public_select`와 좌표 연결 게시물의 지도 공개 상태를 보정하는 V16까지 프로비저닝해야 한다. 애플리케이션 테이블은 14개, 서버 전용 세션 테이블은 2개, 강제 RLS 테이블은 13개다.
- `placesplates_app` 역할은 로그인만 허용되며 `SUPERUSER`·`CREATEROLE`·`CREATEDB`·`REPLICATION`·`BYPASSRLS` 권한이 없다.
- GitHub 연결은 저장소 연동일 뿐이며 Spring Boot의 Flyway 마이그레이션을 자동 실행하지 않는다.
- 프론트엔드는 Supabase Database·Data API를 직접 사용하지 않는다. 사진 제어 권한은 Spring Boot에서 받고, 사진 본문만 단기 서명 토큰으로 비공개 Storage TUS 엔드포인트에 직접 전송한다.

### 권한 분리

```text
postgres.<project-ref>       일회성 역할 생성·Flyway 마이그레이션
          ↓
placesplates_app             Spring Boot 운영 쿼리 전용, RLS 우회 불가
          ↓
public 테이블                anon/authenticated Data API 권한 없음
```

Supabase 관리자 자격 증명을 실행 중인 백엔드에 저장하지 않는다. 운영 Spring Boot에는 `placesplates_app`의 접속 정보만 주입하고 `FLYWAY_ENABLED=false`로 둔다. 스키마 변경은 검증된 마이그레이션을 별도 실행한 뒤 애플리케이션을 배포한다.

`spring_session`과 `spring_session_attributes`는 Spring Session JDBC가 사용하는 서버 인증 인프라다. 사용자 소유 행이 아니므로 RLS를 적용하지 않고, `placesplates_app`의 CRUD만 허용하며 `PUBLIC`·`anon`·`authenticated` 권한은 제거한다. 브라우저와 Supabase Data API는 이 테이블에 직접 접근하지 않는다.

서명 토큰을 사용하는 브라우저 TUS 요청은 일반 인증 경로인 `/upload/resumable`이 아니라 `/upload/resumable/sign`으로 전송한다. 브라우저에는 `x-signature`만 전달하고 서비스 역할 키는 전달하지 않는다.

### 최초 프로비저닝

Supabase Dashboard의 **Connect → Session pooler**에서 프로젝트 참조값과 풀러 호스트를 확인한다. 무료 프로젝트 또는 IPv4 네트워크에서는 Session pooler 5432를 사용한다.

```powershell
.\scripts\provision-supabase-database.ps1 `
  -ProjectReference '<project-ref>' `
  -PoolerHost '<region-pooler-host>'
```

스크립트는 다음 값을 마스킹된 입력으로 요청한다.

1. Supabase 데이터베이스 관리자 비밀번호
2. 새 `placesplates_app` 비밀번호(20자 이상)
3. `placesplates_app` 비밀번호 확인

비밀번호는 파일·명령행·로그에 기록하지 않고 실행 중인 프로세스 환경에서만 사용한 뒤 제거한다. 관리자 비밀번호를 모르면 Supabase Dashboard에서 사용자가 직접 재설정한 후 실행한다.

프로비저닝은 다음을 한 번에 수행한다.

1. `placesplates_app` 로그인 역할 생성 또는 비밀번호 교체
2. Flyway V1~V16 적용
3. PostGIS·마이그레이션 이력·애플리케이션 테이블 14개·세션 테이블 2개·13개 강제 RLS 테이블 확인
4. Supabase `anon`·`authenticated` 역할의 애플리케이션·세션 테이블 권한 제거 확인
5. 운영 역할이 `SUPERUSER`·`BYPASSRLS`가 아님을 확인
6. 운영 역할의 세션 테이블 CRUD, 요청 범위가 없는 연결의 게시물 조회 결과 0건, 후보 소유자 정리 함수의 제한된 실행 권한과 공개 연결 장소 정책 확인

역할 비밀번호 갱신 직후 Session pooler의 인증 정보 전파가 늦으면 `28P01 password authentication failed`가 일시적으로 발생할 수 있다. 프로비저닝 도구는 이 SQL 상태에만 10초 간격으로 최대 4회 재연결하며, 다른 연결·권한 오류는 즉시 실패시킨다. 반복 실패 시 잘못된 비밀번호로 계속 접속하지 말고 Supabase의 **Database Settings → Network Bans**와 Pooler Logs를 확인한다.

### Spring Boot 운영 환경변수

```text
DATABASE_URL=jdbc:postgresql://<region-pooler-host>:5432/postgres?sslmode=require
DATABASE_USERNAME=placesplates_app.<project-ref>
DATABASE_PASSWORD=<placesplates_app-password>
DATABASE_MAX_POOL_SIZE=5
DATABASE_MIN_IDLE=0
DATABASE_CONNECTION_TIMEOUT=30000
FLYWAY_ENABLED=false
SUPABASE_STORAGE_API_URL=https://<project-ref>.storage.supabase.co/storage/v1
SUPABASE_TEMPORARY_UPLOAD_BUCKET=temporary-uploads
SUPABASE_SANITIZED_PHOTO_BUCKET=temporary-uploads
SUPABASE_STORAGE_SERVICE_ROLE_KEY=<secret-manager-reference>
IMAGE_MAX_PIXELS=25000000
IMAGE_MASTER_JPEG_QUALITY=0.92
IMAGE_VARIANT_JPEG_QUALITY=0.88
IMAGE_WATERMARK_VERSION=places-plates-corner-v1
IMAGE_WATERMARK_OPACITY=0.28
IMAGE_WATERMARK_TARGET_WIDTH_RATIO=0.16
IMAGE_WATERMARK_MARGIN_RATIO=0.03
TEMPORARY_ORIGINAL_CLEANUP_ENABLED=true
TEMPORARY_ORIGINAL_CLEANUP_INITIAL_DELAY=PT30S
TEMPORARY_ORIGINAL_CLEANUP_INTERVAL=PT15M
TEMPORARY_ORIGINAL_CLEANUP_BATCH_SIZE=25
```

관리자 사용자명과 데이터베이스 비밀번호는 백엔드 호스팅 환경변수에 추가하지 않는다. Storage 서비스 역할 키는 데이터베이스 관리자 비밀번호와 다른 비밀이며 Cloud Run Secret Manager에만 저장한다. Supabase 무료 `nano`의 연결 수를 보호하기 위해 인스턴스당 최대 풀 크기를 5로 시작하고, 백엔드 인스턴스 수가 증가하면 전체 연결 합계를 다시 계산한다.

### 임시 사진 버킷

Supabase Dashboard의 **Storage → New bucket**에서 `temporary-uploads` 비공개 버킷을 만든다. 객체 키는 `temporary/<owner-uuid>/<batch-uuid>/<item-uuid>.<safe-extension>` 형태로 생성되며 원래 파일명을 포함하지 않는다. Spring Boot만 서비스 역할 키로 단기 업로드 서명을 발급하고 브라우저에는 서명 토큰·버킷명·UUID 객체명만 반환한다. TUS 업로드 URL과 DB 항목은 24시간 만료를 기준으로 관리하며, C17 정리 작업이 만료 또는 처리 완료 원본을 삭제한다.

C14 정제 마스터는 기본적으로 같은 비공개 버킷의 `sanitized/<owner-uuid>/<job-uuid>.jpg`에 저장한다. 객체 키와 파일 바이트에는 원래 파일명을 넣지 않으며 `SUPABASE_SANITIZED_PHOTO_BUCKET`으로 별도 비공개 버킷을 지정할 수도 있다. 만료 정리는 `temporary/`만 대상으로 하고 `sanitized/`는 삭제하지 않는다. JPG·PNG는 2,500만 픽셀 한도와 품질 0.92 JPEG 재인코딩을 적용하며, HEIC·HEIF는 검증된 서버 디코더가 추가될 때까지 실패 상태와 JPEG 변환 안내를 반환한다.

C15 파생본은 같은 비공개 버킷의 `variants/<owner-uuid>/<job-uuid>/<variant>.jpg`에 저장한다. 긴 변 기준 320px `THUMBNAIL`, 960px `MAP_CARD`, 2,000px `PUBLIC_DETAIL`을 품질 0.88 JPEG로 만들고 작은 정제 마스터는 확대하지 않는다.

C16은 세 파생본 하단 오른쪽에 `Places & Plates`를 너비 16%, 여백 3%, 불투명도 28%로 픽셀 합성하고 배경 평균 밝기에 따라 흰색 또는 검은색을 선택한다. 메타데이터 재검사를 통과한 파생본은 `access_level=PUBLIC`, `watermark_applied=TRUE`, `watermark_version=places-plates-corner-v1`, `watermark_position=BOTTOM_RIGHT`로 기록한다. Storage 버킷은 계속 비공개이며 실제 공개 읽기는 게시물의 `PUBLISHED`·`PUBLIC` 조건과 백엔드 제공 경로를 함께 거쳐야 한다. 기존 C15 파생본이나 다른 정책 버전은 비공개 정제 마스터에서 다시 생성한다.

C17은 신규 파생본을 먼저 논리적 `PRIVATE`로 저장한다. 서버가 저장소에서 정제 마스터와 세 파생본을 다시 내려받아 JPEG 디코딩, 바이트 크기, 해상도, EXIF·XMP·IPTC 0건을 검사하고 정제 마스터로 동일 워터마크 파생본을 재생성해 저장 바이트와 일치할 때만 `PUBLIC`으로 전환한다. 이후 `temporary/` 원본 삭제까지 성공해야 `upload_items=COMPLETED`, `photos=READY`가 된다. 저장소 삭제 실패는 키와 `PROCESSING` 상태를 유지해 재시도하며, 검증 실패는 사진을 `FAILED`로 차단하고 원본을 삭제한다.

V11은 `COMPLETED` 이미지 작업, 결과 사진 연결, 비공개 `SANITIZED_MASTER`, `metadata_scan_passed=TRUE`를 모두 확인한 뒤 과거 버전에서 `PROCESSING`에 남은 사진만 `READY`로 변경한다. 검사 미통과·실패·미완료 사진은 변경하지 않으며 프로비저닝 검증은 조건을 충족하면서 `PROCESSING`에 남은 행이 0건인지 확인한다.

V12는 `photo_assets.watermark_position`을 추가하고 워터마크 적용 자산에 정책 버전과 지원 위치가 반드시 존재하도록 제한한다. `SANITIZED_MASTER`에는 워터마크를 적용할 수 없으며 프로비저닝 검증은 안전 조건을 빠뜨린 공개 자산이 0건인지 확인한다.

V13은 `EXPIRED` 업로드 항목에 원본 키가 없고 삭제 시각이 있으며 결과 사진이 없도록 강제하고 원본 정리 인덱스를 추가한다. V14의 `SECURITY DEFINER` 함수는 런타임 역할에 정리 후보 소유자 UUID만 반환한다. 실제 항목 조회·잠금·삭제 상태 변경은 각 소유자의 `OWNER` RLS 컨텍스트에서 수행한다. 예약 작업은 Cloud Run 인스턴스가 실행 중일 때 시작 30초 후와 이후 15분마다 최대 25개를 처리하므로, 무인 상태에서 정확한 실행 시각 보장이 필요해지면 Cloud Scheduler 호출형 작업을 추가한다.

### 검증과 롤백

- Table Editor의 `public` 스키마에서 애플리케이션 테이블을 확인한다.
- Database → Migrations 대신 `flyway_schema_history`를 Flyway 이력의 기준으로 사용한다.
- Database → Roles에서 `placesplates_app`의 `SUPERUSER`와 `BYPASSRLS`가 꺼져 있어야 한다.
- `placesplates_app`만 `spring_session`·`spring_session_attributes`를 CRUD할 수 있고 `anon`·`authenticated`는 조회할 수 없어야 한다.
- 로그인 후 Cloud Run 리비전이 교체되어도 같은 쿠키로 세션이 복구되고, 로그아웃 후 세션 행과 속성 행이 제거되어야 한다.
- 운영 배포 전 비로그인 공개 모드와 두 소유자 계정의 격리 테스트를 실행한다.
- C17 배포 전 V13·V14까지 적용하고 제한 정리 함수의 실행 권한과 만료 원본 제약을 확인한다. 배포 후 처리된 세 파생본의 저장 바이트 검사, 워터마크 정책·위치, 임시 원본 키 제거와 삭제 시각을 함께 확인한다.
- C27 배포 전 V15를 적용해 `places_public_select`가 존재하는지 확인한다. `PUBLIC` 요청은 전체 공개·게시 완료 게시물에 연결된 장소만 읽고, 비공개·링크 공개 기록에만 연결된 장소는 읽지 못해야 한다.
- C29 배포 전 V16을 적용해 위도·경도 쌍이 연결된 기존 게시물의 `coordinate_visibility=EXACT` 보정을 확인한다. 좌표가 없는 게시물은 `HIDDEN`으로 유지되어야 한다.
- 스키마 마이그레이션은 자동 역실행하지 않는다. 실패 시 애플리케이션 배포를 중단하고 Flyway 실패 원인을 수정하며, 데이터가 생긴 뒤의 복구는 Supabase 백업 또는 새 프로젝트 복원을 사용한다.

공식 참고: [Supabase 데이터베이스 연결](https://supabase.com/docs/guides/database/connecting-to-postgres), [Postgres 역할](https://supabase.com/docs/guides/database/postgres/roles), [Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)

## 日本語

### 現在の構成

- Supabaseの`placesplates`プロジェクトはGitHubリポジトリへ接続済みである。
- PostgreSQLはソウルリージョンの無料`nano`コンピュートを使用する。
- PostGIS 3.3.7はSupabaseの`extensions`スキーマで有効化済みである。
- 本番DBの実適用versionは`flyway_schema_history`で確認する。Repository基準はV16であり、C29 application配備前にV15 `places_public_select`とcoordinate接続postのmap公開状態を補正するV16までprovisioningする。Application tableは14個、server専用session tableは2個、強制RLS tableは13個である。
- `placesplates_app`はログインだけが許可され、`SUPERUSER`・`CREATEROLE`・`CREATEDB`・`REPLICATION`・`BYPASSRLS`権限を持たない。
- GitHub接続だけではSpring BootのFlywayマイグレーションは自動実行されない。
- フロントエンドはSupabase Database・Data APIへ直接接続しない。写真制御権限はSpring Bootから取得し、写真本文だけを短期署名トークンで非公開Storage TUSエンドポイントへ直接送信する。

### 権限分離

```text
postgres.<project-ref>       一時的なロール作成・Flywayマイグレーション
          ↓
placesplates_app             Spring Boot実行専用、RLSバイパス不可
          ↓
publicテーブル               anon/authenticated Data API権限なし
```

Supabase管理者資格情報を実行中のバックエンドへ保存しない。本番Spring Bootには`placesplates_app`の接続情報だけを注入し、`FLYWAY_ENABLED=false`にする。スキーマ変更は検証済みマイグレーションを別途実行してからアプリケーションを配備する。

`spring_session`と`spring_session_attributes`はSpring Session JDBC用のサーバー認証基盤である。ユーザー所有行ではないためRLSは適用せず、`placesplates_app`のCRUDだけを許可して`PUBLIC`・`anon`・`authenticated`権限を除去する。ブラウザとSupabase Data APIから直接アクセスしない。

署名トークンを使用するブラウザTUSリクエストは、通常認証用の`/upload/resumable`ではなく`/upload/resumable/sign`へ送信する。ブラウザには`x-signature`だけを渡し、サービスロールキーは渡さない。

### 初回プロビジョニング

Supabase Dashboardの**Connect → Session pooler**でプロジェクト参照値とプーラーホストを確認する。無料プロジェクトまたはIPv4ネットワークではSession poolerの5432番ポートを使用する。

```powershell
.\scripts\provision-supabase-database.ps1 `
  -ProjectReference '<project-ref>' `
  -PoolerHost '<region-pooler-host>'
```

スクリプトは管理者パスワード、新しい`placesplates_app`パスワード（20文字以上）、確認用パスワードをマスク入力で受け取る。値はファイル・コマンドライン・ログへ保存せず、実行プロセスの環境からも終了時に削除する。管理者パスワードが不明な場合は、ユーザー自身がDashboardで再設定してから実行する。

処理内容は、実行ロールの作成またはパスワード更新、Flyway V1〜V16、PostGIS・マイグレーション履歴・アプリケーション14テーブル・session 2テーブル・13個の強制RLSテーブル、Data API権限の除去、実行ロールの非管理者性、session CRUD、リクエスト範囲なしでの0件表示、候補owner cleanup関数の限定権限、公開接続place policyをまとめて検証する。

Role password更新直後にSession poolerへの認証情報伝播が遅れると、一時的に`28P01 password authentication failed`となる場合がある。Provisioning toolはこのSQL stateだけを10秒間隔で最大4回再接続し、他の接続・権限errorは直ちに失敗させる。繰り返し失敗する場合は誤ったpasswordで接続を続けず、Supabaseの**Database Settings → Network Bans**とPooler Logsを確認する。

### Spring Boot本番環境変数

```text
DATABASE_URL=jdbc:postgresql://<region-pooler-host>:5432/postgres?sslmode=require
DATABASE_USERNAME=placesplates_app.<project-ref>
DATABASE_PASSWORD=<placesplates_app-password>
DATABASE_MAX_POOL_SIZE=5
DATABASE_MIN_IDLE=0
DATABASE_CONNECTION_TIMEOUT=30000
FLYWAY_ENABLED=false
SUPABASE_STORAGE_API_URL=https://<project-ref>.storage.supabase.co/storage/v1
SUPABASE_TEMPORARY_UPLOAD_BUCKET=temporary-uploads
SUPABASE_SANITIZED_PHOTO_BUCKET=temporary-uploads
SUPABASE_STORAGE_SERVICE_ROLE_KEY=<secret-manager-reference>
IMAGE_MAX_PIXELS=25000000
IMAGE_MASTER_JPEG_QUALITY=0.92
IMAGE_VARIANT_JPEG_QUALITY=0.88
IMAGE_WATERMARK_VERSION=places-plates-corner-v1
IMAGE_WATERMARK_OPACITY=0.28
IMAGE_WATERMARK_TARGET_WIDTH_RATIO=0.16
IMAGE_WATERMARK_MARGIN_RATIO=0.03
TEMPORARY_ORIGINAL_CLEANUP_ENABLED=true
TEMPORARY_ORIGINAL_CLEANUP_INITIAL_DELAY=PT30S
TEMPORARY_ORIGINAL_CLEANUP_INTERVAL=PT15M
TEMPORARY_ORIGINAL_CLEANUP_BATCH_SIZE=25
```

管理者ユーザー名とDBパスワードはバックエンドホスティングへ登録しない。StorageサービスロールキーはDB管理者パスワードとは別の秘密であり、Cloud Run Secret Managerだけへ保存する。無料`nano`の接続数を守るため、インスタンス当たりの最大プールサイズは5から開始し、バックエンドの台数増加時に接続合計を再計算する。

### 一時写真バケット

Supabase Dashboardの**Storage → New bucket**で非公開`temporary-uploads`バケットを作成する。オブジェクトキーは`temporary/<owner-uuid>/<batch-uuid>/<item-uuid>.<safe-extension>`で生成し、元ファイル名を含めない。Spring Bootだけがサービスロールキーで短期アップロード署名を発行し、ブラウザへは署名トークン・バケット名・UUIDオブジェクト名だけを返す。TUS URLとDB項目は24時間有効とし、C17で期限切れまたは処理済み原本を削除する。

C14のsanitized masterは既定で同じ非公開bucketの`sanitized/<owner-uuid>/<job-uuid>.jpg`へ保存する。Object keyとfile bytesには元file名を含めず、`SUPABASE_SANITIZED_PHOTO_BUCKET`で別の非公開bucketも指定できる。期限切れ削除は`temporary/`だけを対象とし、`sanitized/`は削除しない。JPG・PNGは2,500万pixel上限と品質0.92のJPEG再encodeを適用する。HEIC・HEIFは検証済みserver decoderを追加するまで失敗状態とJPEG変換案内を返す。

C15のvariantは同じ非公開bucketの`variants/<owner-uuid>/<job-uuid>/<variant>.jpg`へ保存する。長辺基準320pxの`THUMBNAIL`、960pxの`MAP_CARD`、2,000pxの`PUBLIC_DETAIL`を品質0.88 JPEGで生成し、小さいsanitized masterは拡大しない。

C16は3 variantの右下へ`Places & Plates`を幅16%、余白3%、不透明度28%でpixel合成し、背景の平均輝度に応じて白または黒を選択する。Metadata再検査を通過したvariantは`access_level=PUBLIC`、`watermark_applied=TRUE`、`watermark_version=places-plates-corner-v1`、`watermark_position=BOTTOM_RIGHT`として記録する。Storage bucketは非公開のままとし、実際の公開読込にはpostの`PUBLISHED`・`PUBLIC`条件とbackend配信経路も必要である。既存C15 variantまたは異なるpolicy versionは非公開sanitized masterから再生成する。

C17は新規variantを最初に論理`PRIVATE`として保存する。Serverがstorageからsanitized masterと3 variantを再downloadし、JPEG decode、byte size、解像度、EXIF・XMP・IPTC 0件を検査する。Sanitized masterから同一watermark policyで再生成したbytesと保存bytesが一致した場合だけ`PUBLIC`へ変更する。その後`temporary/`原本削除まで成功して初めて`upload_items=COMPLETED`、`photos=READY`となる。Storage削除失敗時はkeyと`PROCESSING`を保持して再試行し、検証失敗時は写真を`FAILED`で遮断して原本を削除する。

V11は`COMPLETED` image job、結果写真の関連付け、非公開`SANITIZED_MASTER`、`metadata_scan_passed=TRUE`をすべて確認し、過去versionで`PROCESSING`に残った写真だけを`READY`へ変更する。検査未通過・失敗・未完了写真は変更せず、provisioning検証は条件を満たしながら`PROCESSING`に残る行が0件であることを確認する。

V12は`photo_assets.watermark_position`を追加し、watermark適用assetへpolicy versionと対応位置を必須化する。`SANITIZED_MASTER`にはwatermarkを適用できず、provisioning検証は安全条件が欠落した公開assetが0件であることを確認する。

V13は`EXPIRED` upload itemに原本keyがなく削除時刻があり、結果写真を持たないことを強制してcleanup indexを追加する。V14の`SECURITY DEFINER`関数はruntime roleへcleanup候補owner UUIDだけを返す。実際のitem照会・lock・削除状態変更は各ownerの`OWNER` RLS contextで実行する。定期処理はCloud Run instance起動中に初回30秒後、その後15分ごとに最大25件を処理するため、無人時にも正確な実行時刻が必要になればCloud Scheduler呼出し型jobを追加する。

### 検証とロールバック

- Table Editorの`public`スキーマでアプリケーションテーブルを確認する。
- Flyway履歴の基準は`flyway_schema_history`とする。
- `placesplates_app`の`SUPERUSER`と`BYPASSRLS`が無効であることを確認する。
- `placesplates_app`だけが`spring_session`・`spring_session_attributes`をCRUDでき、`anon`・`authenticated`からは参照できないことを確認する。
- Login後にCloud Run revisionを交換しても同じCookieで認証を復元でき、logout後にsessionと属性行が削除されることを確認する。
- 本番配備前に公開モードと二所有者の分離テストを実行する。
- C17配備前にV13・V14まで適用し、限定cleanup関数の実行権限と期限切れ原本制約を確認する。配備後は3 variantの保存bytes検査、watermark policy・位置、一時原本key削除と削除時刻を同時に確認する。
- C27配備前にV15を適用して`places_public_select`の存在を確認する。`PUBLIC` requestは公開・配備済みpostへ接続されたplaceだけを読み、private・unlisted記録だけに接続されたplaceは読めないことを確認する。
- C29配備前にV16を適用し、緯度・経度pairへ接続された既存postが`coordinate_visibility=EXACT`へ補正され、座標のないpostは`HIDDEN`のままか確認する。
- スキーママイグレーションは自動で逆実行しない。失敗時はアプリ配備を止めて原因を修正し、データ作成後の復旧にはSupabaseバックアップまたは新規プロジェクトへの復元を使用する。

公式資料: [Supabaseデータベース接続](https://supabase.com/docs/guides/database/connecting-to-postgres)、[Postgresロール](https://supabase.com/docs/guides/database/postgres/roles)、[Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
