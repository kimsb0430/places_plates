# Places & Plates Supabase 데이터베이스 운영

## 한국어

### 현재 구성

- Supabase 프로젝트 `placesplates`는 GitHub 저장소와 연결되어 있다.
- PostgreSQL 리전은 서울이며 무료 `nano` 컴퓨팅을 사용한다.
- PostGIS 3.3.7은 Supabase의 `extensions` 스키마에 활성화되어 있다.
- Flyway V1~V10, 애플리케이션 테이블 14개, 서버 전용 세션 테이블 2개와 강제 RLS 테이블 13개가 운영 DB에 적용되어 있다.
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
2. Flyway V1~V10 적용
3. PostGIS·마이그레이션 이력·애플리케이션 테이블 14개·세션 테이블 2개·13개 강제 RLS 테이블 확인
4. Supabase `anon`·`authenticated` 역할의 애플리케이션·세션 테이블 권한 제거 확인
5. 운영 역할이 `SUPERUSER`·`BYPASSRLS`가 아님을 확인
6. 운영 역할의 세션 테이블 CRUD와 요청 범위가 없는 연결의 게시물 조회 결과 0건 확인

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
SUPABASE_STORAGE_SERVICE_ROLE_KEY=<secret-manager-reference>
```

관리자 사용자명과 데이터베이스 비밀번호는 백엔드 호스팅 환경변수에 추가하지 않는다. Storage 서비스 역할 키는 데이터베이스 관리자 비밀번호와 다른 비밀이며 Cloud Run Secret Manager에만 저장한다. Supabase 무료 `nano`의 연결 수를 보호하기 위해 인스턴스당 최대 풀 크기를 5로 시작하고, 백엔드 인스턴스 수가 증가하면 전체 연결 합계를 다시 계산한다.

### 임시 사진 버킷

Supabase Dashboard의 **Storage → New bucket**에서 `temporary-uploads` 비공개 버킷을 만든다. 객체 키는 `temporary/<owner-uuid>/<batch-uuid>/<item-uuid>.<safe-extension>` 형태로 생성되며 원래 파일명을 포함하지 않는다. Spring Boot만 서비스 역할 키로 단기 업로드 서명을 발급하고 브라우저에는 서명 토큰·버킷명·UUID 객체명만 반환한다. TUS 업로드 URL과 DB 항목은 24시간 만료를 기준으로 관리하며, C17 정리 작업이 만료 또는 처리 완료 원본을 삭제한다.

### 검증과 롤백

- Table Editor의 `public` 스키마에서 애플리케이션 테이블을 확인한다.
- Database → Migrations 대신 `flyway_schema_history`를 Flyway 이력의 기준으로 사용한다.
- Database → Roles에서 `placesplates_app`의 `SUPERUSER`와 `BYPASSRLS`가 꺼져 있어야 한다.
- `placesplates_app`만 `spring_session`·`spring_session_attributes`를 CRUD할 수 있고 `anon`·`authenticated`는 조회할 수 없어야 한다.
- 로그인 후 Cloud Run 리비전이 교체되어도 같은 쿠키로 세션이 복구되고, 로그아웃 후 세션 행과 속성 행이 제거되어야 한다.
- 운영 배포 전 비로그인 공개 모드와 두 소유자 계정의 격리 테스트를 실행한다.
- 스키마 마이그레이션은 자동 역실행하지 않는다. 실패 시 애플리케이션 배포를 중단하고 Flyway 실패 원인을 수정하며, 데이터가 생긴 뒤의 복구는 Supabase 백업 또는 새 프로젝트 복원을 사용한다.

공식 참고: [Supabase 데이터베이스 연결](https://supabase.com/docs/guides/database/connecting-to-postgres), [Postgres 역할](https://supabase.com/docs/guides/database/postgres/roles), [Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)

## 日本語

### 現在の構成

- Supabaseの`placesplates`プロジェクトはGitHubリポジトリへ接続済みである。
- PostgreSQLはソウルリージョンの無料`nano`コンピュートを使用する。
- PostGIS 3.3.7はSupabaseの`extensions`スキーマで有効化済みである。
- Flyway V1〜V10、アプリケーションテーブル14個、サーバー専用sessionテーブル2個、強制RLSテーブル13個は本番DBへ適用済みである。
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

処理内容は、実行ロールの作成またはパスワード更新、Flyway V1〜V10、PostGIS・マイグレーション履歴・アプリケーション14テーブル・session 2テーブル・13個の強制RLSテーブル、Data API権限の除去、実行ロールの非管理者性、session CRUD、リクエスト範囲なしでの0件表示をまとめて検証する。

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
SUPABASE_STORAGE_SERVICE_ROLE_KEY=<secret-manager-reference>
```

管理者ユーザー名とDBパスワードはバックエンドホスティングへ登録しない。StorageサービスロールキーはDB管理者パスワードとは別の秘密であり、Cloud Run Secret Managerだけへ保存する。無料`nano`の接続数を守るため、インスタンス当たりの最大プールサイズは5から開始し、バックエンドの台数増加時に接続合計を再計算する。

### 一時写真バケット

Supabase Dashboardの**Storage → New bucket**で非公開`temporary-uploads`バケットを作成する。オブジェクトキーは`temporary/<owner-uuid>/<batch-uuid>/<item-uuid>.<safe-extension>`で生成し、元ファイル名を含めない。Spring Bootだけがサービスロールキーで短期アップロード署名を発行し、ブラウザへは署名トークン・バケット名・UUIDオブジェクト名だけを返す。TUS URLとDB項目は24時間有効とし、C17で期限切れまたは処理済み原本を削除する。

### 検証とロールバック

- Table Editorの`public`スキーマでアプリケーションテーブルを確認する。
- Flyway履歴の基準は`flyway_schema_history`とする。
- `placesplates_app`の`SUPERUSER`と`BYPASSRLS`が無効であることを確認する。
- `placesplates_app`だけが`spring_session`・`spring_session_attributes`をCRUDでき、`anon`・`authenticated`からは参照できないことを確認する。
- Login後にCloud Run revisionを交換しても同じCookieで認証を復元でき、logout後にsessionと属性行が削除されることを確認する。
- 本番配備前に公開モードと二所有者の分離テストを実行する。
- スキーママイグレーションは自動で逆実行しない。失敗時はアプリ配備を止めて原因を修正し、データ作成後の復旧にはSupabaseバックアップまたは新規プロジェクトへの復元を使用する。

公式資料: [Supabaseデータベース接続](https://supabase.com/docs/guides/database/connecting-to-postgres)、[Postgresロール](https://supabase.com/docs/guides/database/postgres/roles)、[Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
