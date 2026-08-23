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
- 운영 PostgreSQL에서 V4 마이그레이션과 `FORCE ROW LEVEL SECURITY`가 12개 개인 데이터 테이블에 적용됐는지 확인한다.
- 비로그인 공개 요청, 소유자 A, 소유자 B로 초안·정제 마스터·임시 업로드 격리 스모크 테스트를 수행한다.
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
- 本番PostgreSQLでV4マイグレーションと`FORCE ROW LEVEL SECURITY`が12個の個人データテーブルへ適用されたことを確認する。
- 未ログイン公開リクエスト、所有者A、所有者Bで下書き・サニタイズ済みマスター・一時アップロードの分離smoke testを実施する。
- 結果、URL、検証内容、リスク、ロールバック地点を当日レポートへ記録する。

フロントエンドVercelプロジェクトのRoot Directoryは`frontend`、Framework Presetは`Next.js`、Output Directoryは既定値を維持する。`frontend/vercel.json`は`pnpm build:vercel`を実行して`.next/routes-manifest.json`を生成する。Spring Boot APIの本番ホスティング確定時に公開URL smoke testを本チェックリストへ接続する。
