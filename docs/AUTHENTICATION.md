# Places & Plates 관리자 인증

문서 버전: v1.1
작성일: 2026-08-23

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
