# Places & Plates 관리자 인증

문서 버전: v1.2
작성일: 2026-08-24

## 1. 인증 방식

- Spring Security가 DB의 `app_users` 관리자 계정을 BCrypt 해시로 검증한다.
- 로그인 성공 시 서버 세션을 만들고 브라우저에는 `HttpOnly` 세션 쿠키만 보낸다.
- 로그인 직전에 발급된 세션 ID는 인증 성공 시 교체해 세션 고정 공격을 방지한다.
- 로그인과 로그아웃 요청은 `/api/v1/auth/csrf`에서 받은 CSRF 토큰이 있어야 처리한다.
- 프론트엔드는 세션 쿠키나 비밀번호를 JavaScript 저장소에 별도로 보관하지 않는다.
- 인증된 보호 API는 같은 트랜잭션에 사용자 UUID와 `OWNER` DB 모드를 설정해 PostgreSQL RLS와 연결한다.
- 공개 API는 인증 세션 유무와 관계없이 `PUBLIC` DB 모드로 실행해 개인 초안이 공개 응답에 섞이지 않게 한다.

## 2. API 계약

| 메서드 | 경로 | 공개 여부 | 역할 |
|---|---|---|---|
| `GET` | `/api/v1/auth/csrf` | 공개 | 현재 세션의 CSRF 헤더명과 토큰 발급 |
| `POST` | `/api/v1/auth/login` | 공개 + CSRF | 이메일·비밀번호 검증과 관리자 세션 생성 |
| `GET` | `/api/v1/auth/session` | 인증 필요 | 로그인 세션 복구와 관리자 정보 확인 |
| `POST` | `/api/v1/auth/logout` | 인증 필요 + CSRF | 세션 무효화와 쿠키 삭제 |

인증되지 않은 보호 API는 `401 AUTH_UNAUTHORIZED`, 잘못된 로그인은 `401 AUTH_INVALID_CREDENTIALS`, CSRF 검증 실패는 `403 AUTH_ACCESS_DENIED`를 반환한다.

## 3. 최초 관리자 준비

최초 한 번만 호스팅사의 비밀 환경변수 저장소에서 다음 값을 주입한다.

```text
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=<administrator-email>
ADMIN_PASSWORD=<at-least-12-character-password>
```

애플리케이션이 이메일을 소문자로 정규화하고 비밀번호를 BCrypt로 해시해 `ADMIN` 계정을 만든다. 생성 확인 후 `ADMIN_BOOTSTRAP_ENABLED=false`로 바꾸고 `ADMIN_PASSWORD`를 제거한다. 기존 계정의 비밀번호는 부팅 과정에서 자동 변경하지 않는다.

운영에서는 실제 Spring Boot 상태 API가 `{"status":"UP"}`인지 먼저 확인한 뒤 Cloud Run의 `ADMIN_PASSWORD`를 Secret Manager 참조로만 주입한다. 계정 생성 리비전이 준비되면 로그인 성공을 확인하고 즉시 부트스트랩을 끈 새 리비전을 배포하며 비밀번호 환경변수 참조를 제거한다. 이메일과 비밀번호를 Git, Cloud Build 인수 또는 일반 환경변수 평문에 넣지 않는다.

## 4. 환경별 쿠키와 CORS

로컬 개발은 HTTP에서 다음 값을 사용한다.

```text
FRONTEND_ORIGINS=http://localhost:3000,http://localhost:3100
SESSION_COOKIE_SECURE=false
SESSION_COOKIE_SAME_SITE=lax
```

프론트와 API가 서로 다른 사이트에 배포되는 운영 환경은 HTTPS를 전제로 다음처럼 설정한다.

```text
FRONTEND_ORIGINS=https://<exact-frontend-domain>
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
```

`FRONTEND_ORIGINS`에는 와일드카드를 사용하지 않고 실제 프론트 도메인만 허용한다. 프론트의 `NEXT_PUBLIC_API_BASE_URL`에는 HTTPS API 주소를 넣는다.

## 5. 운영 확인

- 공개 페이지는 로그인 없이 접근할 수 있어야 한다.
- 비로그인 `/manage` 접근은 로그인 화면으로 이동해야 한다.
- 새로고침 후 관리자 세션이 복구되어야 한다.
- 로그아웃 후 기존 세션으로 `/api/v1/auth/session`에 접근하면 401이어야 한다.
- 운영 응답의 세션 쿠키에 `HttpOnly`, `Secure`, `SameSite=None`이 적용되어야 한다.
- 로그인 오류나 서버 로그에 비밀번호·해시·세션 ID·CSRF 토큰이 기록되지 않아야 한다.

## 6. 자동 보안 테스트 범위

| 경계 | 테스트 수준 | 자동 검증 |
|---|---|---|
| 로그인·세션 | Spring MVC 통합 | 일반 회원·정지·탈퇴 계정 차단, 실패한 로그인 후 비인증 유지, 로그인 시 세션 ID 교체 |
| CSRF | Spring MVC 통합 | 토큰 없는 로그인·로그아웃 거부, 거부된 로그아웃이 기존 세션을 무효화하지 않음 |
| 소유자 전달 | 필터 단위 | 인증된 `AdministratorPrincipal.userId`만 DB 범위에 사용하고 요청 헤더의 위조 UUID는 무시 |
| 공개 범위 | 필터 단위 | 로그인 세션이 있어도 `/api/v1/public/**`는 사용자 UUID 없이 `PUBLIC` 모드 사용 |
| 행 격리 | PostgreSQL 통합 | 12개 RLS 테이블에서 다른 소유자의 행을 조회할 수 없고 다른 소유자의 게시물을 수정·삭제할 수 없음 |
| 하위 데이터 연결 | PostgreSQL 통합 | 다른 소유자의 사진에 자산을 연결하는 권한 상승 시도 차단 |
| 공개 쓰기 | PostgreSQL 통합 | 공개 방문자는 공개 게시물을 읽을 수 있지만 수정·삭제할 수 없음 |
| 운영 역할 | PostgreSQL 통합 | `placesplates_app`이 `SUPERUSER`·`BYPASSRLS` 없이 필요한 애플리케이션 객체 권한만 보유 |

보안 경계 커버리지 목표는 인증 계정 상태 3종(`ACTIVE`·`SUSPENDED`·`DEACTIVATED`)과 RLS 보호 테이블 12개를 모두 자동 검증하는 것이다. PostgreSQL RLS 통합 테스트는 로컬 PostgreSQL이 없으면 건너뛰며, pull request의 PostGIS PostgreSQL 17 서비스에서는 항상 실행한다. 향후 실제 공개 게시물 API가 추가되면 응답 계약 테스트에서 `visited_on`, 비공개 `storage_key`, 원본 파일명과 이미지 메타데이터가 직렬화되지 않는지도 별도로 검증한다.

## 日本語 — 自動セキュリティテスト範囲

| 境界 | テストレベル | 自動検証 |
|---|---|---|
| ログイン・セッション | Spring MVC統合 | 一般会員・停止・退会アカウントの拒否、ログイン失敗後の未認証維持、ログイン時のセッションID交換 |
| CSRF | Spring MVC統合 | トークンなしのログイン・ログアウト拒否、拒否されたログアウトで既存セッションを無効化しないこと |
| 所有者の伝達 | フィルター単体 | 認証済み`AdministratorPrincipal.userId`だけをDBスコープに使い、リクエストヘッダーの偽装UUIDを無視 |
| 公開スコープ | フィルター単体 | ログイン中でも`/api/v1/public/**`はユーザーUUIDなしの`PUBLIC`モードを使用 |
| 行分離 | PostgreSQL統合 | 12個のRLSテーブルで他の所有者の行を参照できず、他の所有者の投稿を更新・削除できないこと |
| 子データの関連付け | PostgreSQL統合 | 他の所有者の写真へアセットを関連付ける権限昇格を拒否 |
| 公開書き込み | PostgreSQL統合 | 公開訪問者は公開投稿を参照できるが更新・削除は不可 |
| 実行ロール | PostgreSQL統合 | `placesplates_app`が`SUPERUSER`・`BYPASSRLS`なしで必要なアプリケーションオブジェクト権限だけを保持 |

セキュリティ境界のカバレッジ目標は、認証アカウント状態3種類（`ACTIVE`・`SUSPENDED`・`DEACTIVATED`）とRLS保護テーブル12個をすべて自動検証することである。PostgreSQL RLS統合テストはローカルPostgreSQLがない場合はスキップし、pull requestのPostGIS PostgreSQL 17サービスでは必ず実行する。実際の公開投稿APIを追加するときは、レスポンス契約テストで`visited_on`、非公開`storage_key`、元ファイル名、画像メタデータがシリアライズされないことも別途検証する。
