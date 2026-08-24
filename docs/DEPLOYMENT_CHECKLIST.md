# Places & Plates 커밋·배포 체크리스트

## 한국어

### 커밋 전

- 최신 `main`에서 `codex/<scope>` 브랜치를 만들었는지 확인한다.
- `git status`로 의도하지 않은 사진, 설정, 로그, DB, 빌드 파일이 없는지 확인한다.
- 실제 `.env`, API 키, 인증서, 서비스 계정, 원본 사진과 EXIF 포함 파일은 커밋하지 않는다.
- `scripts/check-secrets.ps1`과 `scripts/verify-all.ps1`을 통과시킨다.
- 한 가지 목적만 담아 날짜와 영어·한국어·일본어가 포함된 제목으로 커밋한다.

### PR과 병합

- 검증이 끝나기 전에는 Draft PR로 두고, 완료 후 Ready로 바꾼다.
- 필수 CI와 리뷰 결과를 확인하고 Rebase and merge만 사용한다.
- 병합 후 로컬 `main`을 동기화하고 전체 검증을 다시 실행한다.

### 배포 전후

- 운영 키는 GitHub Environment 또는 호스팅사의 비밀 저장소에서만 주입한다.
- 배포 대상 브랜치·프로젝트·환경·도메인과 롤백 커밋을 확인한다.
- 실제 배포 산출물에 `scripts/check-public-artifact.ps1`을 실행한다.
- 배포 후 HTTPS, 주요 페이지, `/api/v1/health`, 지도 로딩, 이미지 워터마크를 확인한다.
- 운영 API의 `FRONTEND_ORIGINS`를 실제 프론트 도메인으로 제한하고 세션 쿠키에 `HttpOnly`, `Secure`, `SameSite=None`이 적용됐는지 확인한다.
- 최초 관리자 생성 확인 후 `ADMIN_BOOTSTRAP_ENABLED=false`로 바꾸고 `ADMIN_PASSWORD`를 운영 환경변수에서 제거한다.
- Supabase에서 PostGIS가 `extensions` 스키마에 활성화됐는지 확인하고 관리자 연결로 `scripts/provision-supabase-database.ps1`을 실행한다.
- 운영 PostgreSQL에서 V1~V10 이력, 애플리케이션 테이블 14개, 세션 테이블 2개와 `FORCE ROW LEVEL SECURITY`가 13개 개인 데이터 테이블에 적용됐는지 확인한다.
- 애플리케이션 배포 전에 V9·V10을 적용하고, `placesplates_app`만 `spring_session`·`spring_session_attributes`를 CRUD하며 `PUBLIC`·`anon`·`authenticated`는 접근할 수 없는지 확인한다.
- Spring Boot에는 `placesplates_app` 접속 정보만 주입하고 `FLYWAY_ENABLED=false`, `DATABASE_MAX_POOL_SIZE=5`로 시작한다. Supabase 관리자 비밀번호는 호스팅사에 저장하지 않는다.
- Cloud Run 소스 배포의 빌드 루트는 `backend`로 지정하고 `backend/project.toml`의 `GOOGLE_RUNTIME_VERSION=21`이 적용되는지 빌드 로그에서 확인한다.
- 최신 리비전 이미지가 `gcr.io/cloudrun/placeholder`가 아닌 Artifact Registry의 애플리케이션 이미지인지 확인하고, `/api/v1/health` 응답 본문이 `{"status":"UP"}`인지 검증한다. HTTP 200만으로 배포 성공을 판정하지 않는다.
- 최초 관리자 계정은 `ADMIN_PASSWORD` Secret Manager 참조로 한 번만 생성하고 로그인 확인 후 `ADMIN_BOOTSTRAP_ENABLED=false`와 비밀번호 참조 제거 상태로 다시 배포한다.
- Supabase Storage에 비공개 `temporary-uploads` 버킷을 만들고 `SUPABASE_STORAGE_API_URL`, `SUPABASE_TEMPORARY_UPLOAD_BUCKET`을 일반 환경변수로, `SUPABASE_STORAGE_SERVICE_ROLE_KEY`를 Secret Manager 참조로 주입한다. 서비스 역할 키는 Vercel에 설정하지 않는다.
- 브라우저 네트워크에서 사진 본문은 서명 전용 `/storage/v1/upload/resumable/sign`에 `x-signature`와 함께 TUS 6MB 청크로 전송되고, 제어·진행률·완료 요청은 Spring Boot API로만 전달되는지 확인한다.
- 비로그인 공개 요청, 소유자 A, 소유자 B로 초안·정제 마스터·임시 업로드 격리 스모크 테스트를 수행한다.
- 로그인 상태에서 새 Cloud Run 리비전으로 트래픽을 전환한 뒤에도 세션이 복구되는지 확인하고, 로그아웃 후 같은 쿠키의 보호 API 접근이 401인지 확인한다.
- 결과와 URL, 검증 내용, 위험 및 롤백 지점을 당일 보고서에 남긴다.

프론트엔드 Vercel 프로젝트의 Root Directory는 `frontend`, Framework Preset은 `Next.js`, Output Directory는 기본값으로 유지한다. `frontend/vercel.json`은 `pnpm build:vercel`을 실행해 `.next/routes-manifest.json`을 생성한다. Spring Boot API 운영 호스팅은 확정 시 공개 URL 스모크 테스트와 함께 이 체크리스트에 연결한다.

## 日本語

### コミット前

- 最新の`main`から`codex/<scope>`ブランチを作成したことを確認する。
- `git status`で意図しない写真、設定、ログ、DB、ビルドファイルがないことを確認する。
- 実際の`.env`、APIキー、証明書、サービスアカウント、元写真、EXIFを含むファイルはコミットしない。
- `scripts/check-secrets.ps1`と`scripts/verify-all.ps1`を成功させる。
- 一つの目的だけを含め、日付と英語・韓国語・日本語の件名でコミットする。

### PRとマージ

- 検証完了前はDraft PRとし、完了後にReadyへ変更する。
- 必須CIとレビューを確認し、Rebase and mergeだけを使用する。
- マージ後にローカル`main`を同期し、全検証を再実行する。

### デプロイ前後

- 本番キーはGitHub Environmentまたはホスティング事業者のシークレットストアだけで注入する。
- 対象ブランチ、プロジェクト、環境、ドメイン、ロールバックコミットを確認する。
- 実際のデプロイ成果物に`scripts/check-public-artifact.ps1`を実行する。
- デプロイ後にHTTPS、主要ページ、`/api/v1/health`、地図読込、画像透かしを確認する。
- 本番APIの`FRONTEND_ORIGINS`を実際のフロントエンドドメインに限定し、セッションCookieに`HttpOnly`、`Secure`、`SameSite=None`が適用されていることを確認する。
- 初回管理者の作成確認後、`ADMIN_BOOTSTRAP_ENABLED=false`へ変更し、`ADMIN_PASSWORD`を本番環境変数から削除する。
- Supabaseの`extensions`スキーマでPostGISを有効化し、管理者接続で`scripts/provision-supabase-database.ps1`を実行する。
- 本番PostgreSQLでV1〜V10履歴、アプリケーション14テーブル、session 2テーブルと`FORCE ROW LEVEL SECURITY`が13個の個人データテーブルへ適用されたことを確認する。
- アプリ配備前にV9・V10を適用し、`placesplates_app`だけが`spring_session`・`spring_session_attributes`をCRUDでき、`PUBLIC`・`anon`・`authenticated`はアクセスできないことを確認する。
- Spring Bootには`placesplates_app`接続情報だけを注入し、`FLYWAY_ENABLED=false`、`DATABASE_MAX_POOL_SIZE=5`から開始する。Supabase管理者パスワードはホスティングへ保存しない。
- Cloud Runソースデプロイのビルドルートを`backend`に指定し、`backend/project.toml`の`GOOGLE_RUNTIME_VERSION=21`がビルドログへ反映されることを確認する。
- 最新リビジョンのイメージが`gcr.io/cloudrun/placeholder`ではなくArtifact Registryのアプリケーションイメージであることを確認し、`/api/v1/health`のレスポンス本文が`{"status":"UP"}`であることを検証する。HTTP 200だけでデプロイ成功と判定しない。
- 初回管理者は`ADMIN_PASSWORD`をSecret Manager参照として一度だけ作成し、ログイン確認後に`ADMIN_BOOTSTRAP_ENABLED=false`とパスワード参照削除の状態で再配備する。
- Supabase Storageへ非公開`temporary-uploads`バケットを作成し、Storage API URLとバケット名は通常環境変数、サービスロールキーはSecret Manager参照としてCloud Runだけへ注入する。Vercelには保存しない。
- 写真本文が`x-signature`付きで署名専用`/storage/v1/upload/resumable/sign`へTUSの6MBチャンクとして送信され、制御・進捗・完了要求はSpring Boot APIだけへ送信されることを確認する。
- 未ログイン公開リクエスト、所有者A、所有者Bで下書き・サニタイズ済みマスター・一時アップロードの分離smoke testを実施する。
- Login状態で新しいCloud Run revisionへtrafficを切り替えた後もsessionを復元でき、logout後に同じCookieで保護APIへアクセスすると401になることを確認する。
- 結果、URL、検証内容、リスク、ロールバック地点を当日レポートへ記録する。

フロントエンドVercelプロジェクトのRoot Directoryは`frontend`、Framework Presetは`Next.js`、Output Directoryは既定値を維持する。`frontend/vercel.json`は`pnpm build:vercel`を実行して`.next/routes-manifest.json`を生成する。Spring Boot APIの本番ホスティング確定時に公開URL smoke testを本チェックリストへ接続する。
