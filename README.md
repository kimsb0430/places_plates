# Places & Plates

여행지와 맛집 사진을 지도와 이야기로 남기는 개인 아카이브입니다. 첫 버전은 개인 페이지로 시작하고, 이후 회원마다 독립된 기록 페이지를 가질 수 있는 커뮤니티로 확장합니다.

## Current deliverables

- `frontend/`: 상호작용 가능한 Next.js·TypeScript 웹 애플리케이션
- `PLACES_AND_PLATES_PRODUCT_DESIGN.html`: 제품·UX 설계서
- `PROJECT_STRUCTURE.md`: Next.js 프론트엔드와 Spring Boot 백엔드 구조
- `DEVELOPMENT_SCHEDULE.md`: 스프린트·커밋 계획

## Confirmed direction

- 맛집·여행지 카테고리와 리스트·지도 탭
- 지도 탭의 지도 70%·축소 게시물 30% 구성
- Google Maps JavaScript API + Places API (New)
- 공개 방문 날짜 월 단위 표시
- 업로드 원본 미보관, EXIF 제거, `Places & Plates` 워터마크
- `frontend/` Next.js와 `backend/` Spring Boot 모노레포
- 모든 변경은 `codex/*` 브랜치와 pull request를 통해 반영

## Documents

- [Product design](./PLACES_AND_PLATES_PRODUCT_DESIGN.html)
- [Project structure](./PROJECT_STRUCTURE.md)
- [Development schedule](./DEVELOPMENT_SCHEDULE.md)
- [Contribution workflow](./CONTRIBUTING.md)
- [Commit and deployment checklist](./docs/DEPLOYMENT_CHECKLIST.md)

## Local development

필수 도구는 Node.js 22.13 이상, pnpm 11, JDK 21이다. 환경변수는 각 애플리케이션의 `.env.example`을 참고하고 실제 비밀값은 Git에 추가하지 않는다.

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

```powershell
cd backend
.\gradlew.bat bootRun
```

백엔드 기본 상태 확인 API는 `GET http://localhost:8080/api/v1/health`이며 `{"status":"UP"}`을 반환한다.

## Verification

저장소 루트에서 다음 명령으로 프론트엔드 lint·타입 검사·빌드와 백엔드 테스트·실행 JAR 빌드를 한 번에 수행한다.

```powershell
.\scripts\verify-all.ps1
```

같은 검증은 pull request와 `main` push에서 GitHub Actions로 자동 실행된다.

프론트엔드 배포 빌드는 대상에 따라 분리한다.

```powershell
cd frontend
pnpm build:vercel  # Vercel용 표준 Next.js .next 산출물
pnpm build         # OpenAI Sites용 Vinext dist 산출물
```
