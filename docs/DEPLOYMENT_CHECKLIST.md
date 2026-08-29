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
- Vercel의 `/`, `/posts`, `/map` 응답에 `Content-Security-Policy`, `Permissions-Policy`, `Referrer-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`가 있는지 확인한다. CSP는 `frame-ancestors 'none'`·`object-src 'none'`을 유지하면서 Google Maps와 Supabase TUS 연결을 허용해야 하며, 지도 불러오기·장소 검색·사진 업로드를 각각 다시 실행해 브라우저 CSP 위반이 없는지 점검한다. Cloud Run의 `/api/v1/health`, 공개 API의 정상·오류 응답에도 API 전용 `default-src 'none'` CSP와 같은 보호 헤더가 있어야 한다. 공개 JSON과 Cloud Run 정리 실패 로그에는 `storageKey`, `temporaryStorageKey`, `temporary/`, `sanitized/`, `variants/` 실제 경로가 없어야 한다.
- 공개 목록·상세·장소 이력 사진에서 우클릭 메뉴와 드래그가 억제되는지 확인한다. Cloud Run 대표·상세 사진 API와 Vercel 같은 출처 `/api/public-images/**` 응답은 `Cross-Origin-Resource-Policy: same-origin`, `Content-Security-Policy`의 `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`를 유지해야 한다. `scripts/check-production-image-protection.ps1`로 실제 공개 대표 사진의 두 응답을 함께 검사하고, 브라우저 사진 요청이 `/_next/image`나 Cloud Run 직접 URL이 아니라 `/api/public-images/**`인지 확인한다. 다른 출처의 단순 `<img>` 직접 삽입은 차단되는지 별도 테스트 페이지에서 확인하되 서버 프록시와 화면 캡처까지 막는 기능으로 판단하지 않는다.
- 운영 API의 `FRONTEND_ORIGINS`를 실제 프론트 도메인으로 제한하고 세션 쿠키에 `HttpOnly`, `Secure`, `SameSite=None`이 적용됐는지 확인한다.
- 최초 관리자 생성 확인 후 `ADMIN_BOOTSTRAP_ENABLED=false`로 바꾸고 `ADMIN_PASSWORD`를 운영 환경변수에서 제거한다.
- Supabase에서 PostGIS가 `extensions` 스키마에 활성화됐는지 확인하고 관리자 연결로 `scripts/provision-supabase-database.ps1`을 실행한다.
- 운영 PostgreSQL에서 V1~V16 이력, 애플리케이션 테이블 14개, 세션 테이블 2개와 `FORCE ROW LEVEL SECURITY`가 13개 개인 데이터 테이블에 적용됐는지 확인한다. V11 적용 후 완료 작업·검사 통과 정제 마스터가 있으면서 `PROCESSING`에 남은 사진은 0건이어야 하며, V12 적용 후 안전 조건이 누락된 공개 사진 자산은 0건이어야 한다. V13의 만료 원본 제약·정리 인덱스, V14의 제한된 후보 소유자 함수, V15의 `places_public_select`, V16의 좌표 연결 게시물 지도 공개 보정이 있어야 한다. 공개 모드에서는 전체 공개·게시 완료 기록에 연결된 장소만 읽을 수 있어야 한다.
- Vercel에는 HTTP 리퍼러와 Maps JavaScript API로 제한한 `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY`를 설정한다. Advanced Marker를 사용하려면 JavaScript용 Map ID를 만들고 `NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID`도 설정한다. 브라우저 Network를 비운 뒤 `/posts`와 일반 `/map` 진입만으로 `maps.googleapis.com/maps/api/js` 요청이나 Dynamic Maps 호출이 발생하지 않고 “Google 지도 불러오기” 버튼 뒤에 SDK·클러스터러 chunk와 지도 요청이 로드되는지 확인한다. 전체·맛집·여행지 필터와 마커 색상·글자가 일치해야 한다. 가까운 게시물이 두 개 이상인 fixture에서는 축소 시 묶음 숫자가 포함 게시물 수와 일치하고 묶음을 누르면 개별 마커 방향으로 확대되는지 확인한다. 지도를 이동·확대·축소한 뒤 500ms 안에 현재 영역의 전체·맛집·여행지 합계가 화면 안의 개별 게시물과 일치하는지도 확인한다. PC 70:30 지도·축소 목록과 모바일 지도 아래 가로 카드가 겹치지 않는지, 검색·현재 영역 제한이 마커와 카드를 함께 갱신하는지, 카드 선택·hover·focus와 마커 선택이 양방향으로 동기화되는지 확인한다. 카테고리를 바꿔도 URL의 검색어·지도 중심·확대 수준·선택·로드 상태가 복원되고 `/posts` 큰 카드가 그대로인지 점검한다.
- `/posts` HTML은 첫 번째 대표 사진만 preload하고 나머지 카드·장소 이력·상세 보조 사진은 `loading=lazy`여야 한다. Cloud Run 직접 이미지와 Vercel `/api/public-images/**`는 `Cache-Control`에 `max-age=3600`과 `stale-while-revalidate=86400`을 포함하고, Vercel 중계가 Cloud Run 응답의 `Content-Length`를 보존하는지 확인한다. 사진을 바꾼 뒤 최장 1시간 캐시 지연을 허용하되 장기 immutable 캐시는 사용하지 않는다.
- 배포 산출물 검증에서 `scripts/check-map-lazy-load-artifact.ps1 -Path frontend/.next`를 실행해 Google Maps loader URL이 `/map` 초기 entry chunk에는 없고 별도 dynamic chunk에만 있는지 확인한다.
- 키보드만 사용해 본문 바로가기, 공개 목록·지도 카테고리 링크, 로그인, 사진 선택, 초안 편집·게시 버튼까지 이동할 수 있는지 확인한다. 홈 카테고리는 좌우 화살표·Home·End로 전환되고 미리보기는 Enter·Space로 열린 뒤 Tab이 대화상자 밖으로 빠지지 않으며 Escape로 닫을 때 호출 카드에 포커스가 돌아와야 한다. 모든 폼 컨트롤에는 3px 포커스 윤곽선이 보이고 빈 사진 설명은 제목 기반 대체 문구로 읽혀야 한다.
- 애플리케이션 배포 전에 V9·V10을 적용하고, `placesplates_app`만 `spring_session`·`spring_session_attributes`를 CRUD하며 `PUBLIC`·`anon`·`authenticated`는 접근할 수 없는지 확인한다.
- 역할 비밀번호 갱신 직후 `28P01`이 발생하면 도구의 제한 재연결 결과를 기다리고, 반복 실패 시 추가 시도를 멈춘 뒤 Supabase Network Bans와 Pooler Logs를 확인한다.
- Spring Boot에는 `placesplates_app` 접속 정보만 주입하고 `FLYWAY_ENABLED=false`, `DATABASE_MAX_POOL_SIZE=5`로 시작한다. Supabase 관리자 비밀번호는 호스팅사에 저장하지 않는다.
- Cloud Run Secret Manager에 `GOOGLE_PLACES_API_KEY`를 서버 전용으로 연결하고 Places API (New)만 허용한다. 브라우저 Maps JavaScript 키와 분리하고 월별 예산 알림·API 할당량을 설정한다. 로그인 후 장소 검색 결과가 최대 5건이며 `Google Maps` 출처가 같은 컨테이너 안에 보이는지, 검색 실패 시 직접 입력이 가능한지 확인한다.
- Places API를 운영에 사용하기 전에 Google Maps Platform 약관과 개인정보처리방침을 반영한 공개 이용약관·개인정보처리방침 URL을 준비한다.
- Cloud Build 트리거 유형은 `Cloud Build 구성 파일(YAML 또는 JSON)`, 위치는 `저장소`, 파일 경로는 `backend/cloudbuild.yaml`로 지정한다. 인라인 Buildpacks 구성은 Dockerfile을 무시하므로 사용하지 않는다. 구성 파일은 `backend` 컨텍스트에서 `backend/Dockerfile`로 이미지를 빌드·푸시하고 기존 Cloud Run 서비스 이미지를 갱신해야 한다. 배포 이미지에서 `liblcms2.so.2`가 조회되고 애플리케이션이 UID 10001 비루트 사용자로 실행되어야 한다.
- 최신 리비전 이미지가 `gcr.io/cloudrun/placeholder`가 아닌 Artifact Registry의 애플리케이션 이미지인지 확인하고, `/api/v1/health` 응답 본문이 `{"status":"UP"}`인지 검증한다. HTTP 200만으로 배포 성공을 판정하지 않는다.
- 최초 관리자 계정은 `ADMIN_PASSWORD` Secret Manager 참조로 한 번만 생성하고 로그인 확인 후 `ADMIN_BOOTSTRAP_ENABLED=false`와 비밀번호 참조 제거 상태로 다시 배포한다.
- Supabase Storage에 비공개 `temporary-uploads` 버킷을 만들고 `SUPABASE_STORAGE_API_URL`, `SUPABASE_TEMPORARY_UPLOAD_BUCKET`을 일반 환경변수로, `SUPABASE_STORAGE_SERVICE_ROLE_KEY`를 Secret Manager 참조로 주입한다. 서비스 역할 키는 Vercel에 설정하지 않는다.
- `SUPABASE_SANITIZED_PHOTO_BUCKET`을 비공개 버킷으로 지정한다. 기본값은 `temporary-uploads`이며 이 경우 `temporary/`, `sanitized/`, `variants/` 접두사를 분리하고 만료 정리가 정제 마스터·파생본을 삭제하지 않는지 확인한다. `IMAGE_MAX_PIXELS=25000000`, `IMAGE_MASTER_JPEG_QUALITY=0.92`, `IMAGE_VARIANT_JPEG_QUALITY=0.88`, `IMAGE_WATERMARK_VERSION=places-plates-corner-v1`, `IMAGE_WATERMARK_OPACITY=0.28`, `IMAGE_WATERMARK_TARGET_WIDTH_RATIO=0.16`, `IMAGE_WATERMARK_MARGIN_RATIO=0.03`으로 시작한다.
- 브라우저 네트워크에서 사진 본문은 서명 전용 `/storage/v1/upload/resumable/sign`에 `x-signature`와 함께 TUS 6MB 청크로 전송되고, 제어·진행률·완료 요청은 Spring Boot API로만 전달되는지 확인한다.
- ICC 색상 프로필이 있는 JPG를 포함한 JPG·PNG 업로드 후 `/sanitize` 응답과 `image_processing_jobs.status`가 `COMPLETED`, `photos.processing_status`가 `READY`인지 확인한다. Cloud Run 로그에 `liblcms2.so.2` 관련 `UnsatisfiedLinkError`가 없어야 한다. `photo_assets`에는 `metadata_scan_passed=TRUE`인 비공개 `SANITIZED_MASTER`가 하나 생성되고, 저장 키에 원래 파일명이 없으며 결과 EXIF·XMP·IPTC가 0건이어야 한다. 완료 요청을 반복해도 같은 사진이 `READY`로 복구되어야 하며, HEIC·HEIF는 `HEIC_DECODER_UNAVAILABLE`과 JPEG 변환 안내를 반환해야 한다.
- `THUMBNAIL`·`MAP_CARD`·`PUBLIC_DETAIL`은 `PUBLIC`, `metadata_scan_passed=TRUE`, `watermark_applied=TRUE`, 정책 `places-plates-corner-v1`, 위치 `BOTTOM_RIGHT`여야 한다. 세 JPEG의 하단 오른쪽 픽셀에 `Places & Plates`가 보이고 CSS를 제거해도 유지되는지 확인하며, 기존 무워터마크 파생본은 정제 요청 재호출로 현재 정책에 맞게 교체되어야 한다.
- C17 스모크에서는 `/sanitize` 성공 뒤 `upload_items.processing_status=COMPLETED`, `temporary_storage_key IS NULL`, `original_deleted_at IS NOT NULL`, `photos.processing_status=READY`를 함께 확인한다. 삭제 실패를 주입한 테스트에서는 사진이 `PROCESSING`으로 공개 차단되고 원본 키가 재시도용으로 남아야 하며, 24시간 만료 항목은 예약 작업 뒤 `EXPIRED`와 삭제 시각을 가져야 한다.
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
- Vercelの`/`、`/posts`、`/map` responseに`Content-Security-Policy`、`Permissions-Policy`、`Referrer-Policy`、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`があることを確認する。CSPは`frame-ancestors 'none'`・`object-src 'none'`を維持しながらGoogle MapsとSupabase TUS接続を許可し、map load・place search・photo uploadを再実行してbrowser CSP violationがないことを点検する。Cloud Runの`/api/v1/health`、public APIの正常・error responseにもAPI専用`default-src 'none'` CSPと同じ保護headerが必要である。Public JSONとCloud Run cleanup failure logには`storageKey`、`temporaryStorageKey`、`temporary/`、`sanitized/`、`variants/`の実pathが存在してはならない。
- Public list・detail・place history写真でcontext menuとdragが抑制されることを確認する。Cloud Run cover・detail photo APIとVercel same-origin `/api/public-images/**` responseは`Cross-Origin-Resource-Policy: same-origin`、`Content-Security-Policy`の`frame-ancestors 'none'`、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`を維持しなければならない。`scripts/check-production-image-protection.ps1`で実際のpublic cover写真の両responseを検査し、browser photo requestが`/_next/image`やCloud Run direct URLではなく`/api/public-images/**`であることを確認する。別originの単純な`<img>`直接埋込みが拒否されることも別test pageで確認するが、server proxyやscreen captureまで防ぐ機能とは判断しない。
- 本番APIの`FRONTEND_ORIGINS`を実際のフロントエンドドメインに限定し、セッションCookieに`HttpOnly`、`Secure`、`SameSite=None`が適用されていることを確認する。
- 初回管理者の作成確認後、`ADMIN_BOOTSTRAP_ENABLED=false`へ変更し、`ADMIN_PASSWORD`を本番環境変数から削除する。
- Supabaseの`extensions`スキーマでPostGISを有効化し、管理者接続で`scripts/provision-supabase-database.ps1`を実行する。
- 本番PostgreSQLでV1〜V16履歴、アプリケーション14テーブル、session 2テーブルと`FORCE ROW LEVEL SECURITY`が13個の個人データテーブルへ適用されたことを確認する。V11適用後、完了job・検査通過sanitized masterがありながら`PROCESSING`に残る写真は0件で、V12適用後は安全条件が欠落した公開写真assetが0件でなければならない。V13の期限切れ原本制約・cleanup index、V14の限定候補owner関数、V15の`places_public_select`、V16のcoordinate接続post map公開補正が存在し、public modeでは公開・配備済み記録へ接続されたplaceだけを読めなければならない。
- VercelへHTTP referrerとMaps JavaScript APIで制限した`NEXT_PUBLIC_GOOGLE_MAPS_API_KEY`を設定する。Advanced Markerを使う場合はJavaScript用Map IDを作成し`NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID`も設定する。Browser Networkを消去してから`/posts`と通常`/map`へ入っただけでは`maps.googleapis.com/maps/api/js` requestやDynamic Maps callが発生せず、「Google 지도 불러오기」button後にSDK・clusterer chunkとmap requestがloadされることを確認する。All・restaurant・destination filterとmarker色・文字が一致しなければならない。近接する投稿が2件以上あるfixtureでは、縮小時のcluster数が含まれる投稿数と一致し、cluster選択で個別markerへ向けて拡大することを確認する。Mapを移動・拡大・縮小した後500ms以内にcurrent boundsのall・restaurant・destination countが画面内の個別投稿と一致することも確認する。PC 70:30 map・compact listとmobile map下のhorizontal cardが重ならず、search・current bounds制限がmarkerとcardを同時更新し、card選択・hover・focusとmarker選択が双方向同期することを確認する。Category変更後もURLのquery・center・zoom・selection・load stateが復元され、`/posts` large cardが変わらないことを確認する。
- `/posts` HTMLはfirst coverだけをpreloadし、残りのcard・place history・detail secondary photoは`loading=lazy`でなければならない。Cloud Run direct imageとVercel `/api/public-images/**`は`Cache-Control`へ`max-age=3600`と`stale-while-revalidate=86400`を含み、Vercel proxyがCloud Run responseの`Content-Length`を保持することを確認する。写真変更後の最大1時間cache delayを許容するがlong-term immutable cacheは使用しない。
- Deployment artifact検証で`scripts/check-map-lazy-load-artifact.ps1 -Path frontend/.next`を実行し、Google Maps loader URLが`/map` initial entry chunkには存在せず、別dynamic chunkだけにあることを確認する。
- Keyboardだけでskip link、public list・map category link、login、photo選択、draft edit・publish buttonまで移動できることを確認する。Home categoryは左右矢印・Home・Endで切り替わり、previewはEnter・Spaceで開き、Tabがdialog外へ出ず、Escapeで閉じたとき呼出cardへfocusが戻らなければならない。すべてのform controlに3px focus outlineが見え、空のphoto descriptionはtitle由来のfallback altとして読まれることを確認する。
- アプリ配備前にV9・V10を適用し、`placesplates_app`だけが`spring_session`・`spring_session_attributes`をCRUDでき、`PUBLIC`・`anon`・`authenticated`はアクセスできないことを確認する。
- Role password更新直後に`28P01`が発生した場合はtoolの限定再接続結果を待ち、繰り返し失敗するときは追加試行を止めてSupabase Network BansとPooler Logsを確認する。
- Spring Bootには`placesplates_app`接続情報だけを注入し、`FLYWAY_ENABLED=false`、`DATABASE_MAX_POOL_SIZE=5`から開始する。Supabase管理者パスワードはホスティングへ保存しない。
- Cloud RunのSecret Managerへserver専用`GOOGLE_PLACES_API_KEY`を接続し、Places API (New)だけを許可する。Browser用Maps JavaScript keyとは分離し、月次budget alertとAPI quotaを設定する。Login後の場所検索が最大5件で同一container内に`Google Maps` attributionが見えること、検索失敗時にmanual inputを利用できることを確認する。
- Places APIを本番利用する前にGoogle Maps Platform規約とPrivacy Policyを反映した公開利用規約・Privacy Policy URLを準備する。
- Cloud Build trigger typeを`Cloud Build構成ファイル（YAMLまたはJSON）`、locationをrepository、file pathを`backend/cloudbuild.yaml`へ設定する。Dockerfileを無視するinline Buildpacks構成は使用しない。構成fileは`backend` contextで`backend/Dockerfile`からimageをbuild・pushし、既存Cloud Run serviceのimageを更新しなければならない。配備imageで`liblcms2.so.2`を参照でき、applicationがUID 10001の非root userとして実行されることを確認する。
- 最新リビジョンのイメージが`gcr.io/cloudrun/placeholder`ではなくArtifact Registryのアプリケーションイメージであることを確認し、`/api/v1/health`のレスポンス本文が`{"status":"UP"}`であることを検証する。HTTP 200だけでデプロイ成功と判定しない。
- 初回管理者は`ADMIN_PASSWORD`をSecret Manager参照として一度だけ作成し、ログイン確認後に`ADMIN_BOOTSTRAP_ENABLED=false`とパスワード参照削除の状態で再配備する。
- Supabase Storageへ非公開`temporary-uploads`バケットを作成し、Storage API URLとバケット名は通常環境変数、サービスロールキーはSecret Manager参照としてCloud Runだけへ注入する。Vercelには保存しない。
- `SUPABASE_SANITIZED_PHOTO_BUCKET`を非公開バケットへ指定する。既定値が`temporary-uploads`の場合は`temporary/`、`sanitized/`、`variants/` prefixを分離し、期限切れ処理がsanitized master・variantを削除しないことを確認する。`IMAGE_MAX_PIXELS=25000000`、`IMAGE_MASTER_JPEG_QUALITY=0.92`、`IMAGE_VARIANT_JPEG_QUALITY=0.88`、`IMAGE_WATERMARK_VERSION=places-plates-corner-v1`、`IMAGE_WATERMARK_OPACITY=0.28`、`IMAGE_WATERMARK_TARGET_WIDTH_RATIO=0.16`、`IMAGE_WATERMARK_MARGIN_RATIO=0.03`から開始する。
- 写真本文が`x-signature`付きで署名専用`/storage/v1/upload/resumable/sign`へTUSの6MBチャンクとして送信され、制御・進捗・完了要求はSpring Boot APIだけへ送信されることを確認する。
- ICC color profileを含むJPGを含め、JPG・PNG upload後に`/sanitize` responseと`image_processing_jobs.status`が`COMPLETED`、`photos.processing_status`が`READY`であることを確認する。Cloud Run logに`liblcms2.so.2`関連の`UnsatisfiedLinkError`がないことも確認する。`photo_assets`には`metadata_scan_passed=TRUE`の非公開`SANITIZED_MASTER`が一つ作成され、storage keyに元file名がなく、結果EXIF・XMP・IPTCが0件でなければならない。完了requestを繰り返しても同じ写真が`READY`へ復元され、HEIC・HEIFは`HEIC_DECODER_UNAVAILABLE`とJPEG変換案内を返すことを確認する。
- `THUMBNAIL`・`MAP_CARD`・`PUBLIC_DETAIL`は`PUBLIC`、`metadata_scan_passed=TRUE`、`watermark_applied=TRUE`、policy `places-plates-corner-v1`、位置`BOTTOM_RIGHT`でなければならない。3 JPEGの右下pixelに`Places & Plates`が表示され、CSSを除去しても残ることを確認する。既存のwatermarkなしvariantはsanitize再呼出しで現policyへ置換されなければならない。
- C17 smoke testでは`/sanitize`成功後に`upload_items.processing_status=COMPLETED`、`temporary_storage_key IS NULL`、`original_deleted_at IS NOT NULL`、`photos.processing_status=READY`を同時に確認する。削除失敗を注入したtestでは写真が`PROCESSING`のまま公開遮断され、原本keyが再試行用に残ること、24時間期限切れitemは定期処理後に`EXPIRED`と削除時刻を持つことを確認する。
- 未ログイン公開リクエスト、所有者A、所有者Bで下書き・サニタイズ済みマスター・一時アップロードの分離smoke testを実施する。
- Login状態で新しいCloud Run revisionへtrafficを切り替えた後もsessionを復元でき、logout後に同じCookieで保護APIへアクセスすると401になることを確認する。
- 結果、URL、検証内容、リスク、ロールバック地点を当日レポートへ記録する。

フロントエンドVercelプロジェクトのRoot Directoryは`frontend`、Framework Presetは`Next.js`、Output Directoryは既定値を維持する。`frontend/vercel.json`は`pnpm build:vercel`を実行して`.next/routes-manifest.json`を生成する。Spring Boot APIの本番ホスティング確定時に公開URL smoke testを本チェックリストへ接続する。
