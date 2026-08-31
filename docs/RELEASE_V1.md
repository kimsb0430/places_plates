# Places & Plates v1 출시 기준

출시 대상: `v1.0.0`  
운영 Web: https://placesplates.vercel.app  
운영 API: https://places-plates-api-481849639838.asia-northeast3.run.app

이 문서는 C43의 최종 출시 승인, 태그 생성, GitHub Release와 ROLLBACK 기준이다. 비밀키·개인 이메일·결제 계정·실제 사진과 원본 메타데이터는 출시 증거에 포함하지 않는다.

## 1. 출시 불가 조건

다음 중 하나라도 충족하면 태그를 만들지 않는다.

- `main`의 Secret protection, Verify, Production smoke가 같은 커밋에서 성공하지 않음
- Vercel과 Cloud Run의 `X-Places-Plates-Commit`이 출시 커밋과 다름
- PostgreSQL Flyway가 V16보다 낮거나 공개 RLS 검증이 실패함
- 임시 원본·정제 마스터 경로나 Storage 키가 공개 응답에 포함됨
- 공개 사진에서 EXIF·XMP·IPTC가 검출되거나 워터마크 정책 `places-plates-corner-v1`이 아님
- 비로그인 사용자가 초안·비공개 기록·관리 사진에 접근할 수 있음
- 운영 백업·복구·ROLLBACK 절차와 현재 정상 복귀 지점이 확인되지 않음
- 지도 숫자·카테고리·공개 목록이 실제 공개 데이터와 일치하지 않음

## 2. 태그 전 최종 점검

1. `main`이 원격과 일치하고 작업 트리가 비어 있는지 확인한다.
2. `config/release.json`, 프런트·백엔드 버전이 모두 `1.0.0`인지 확인한다.
3. `scripts/verify-all.ps1`과 비밀정보 검사를 통과시킨다.
4. GitHub `main`의 Secret protection·Verify·Production smoke 성공 커밋 SHA를 기록한다.
5. `/`, `/posts`, `/map`, `/api/deployment`, `/api/v1/health`, 공개 목록·지도 API가 HTTPS 200인지 확인한다.
6. 관리자 로그인, 사진 업로드·정제, 초안 자동 저장, 게시, 목록·상세·지도 노출, 초안·공개 기록 삭제를 데스크톱 또는 모바일에서 한 번 수행한다.
7. 공개 대표 사진과 상세 사진이 같은 출처 프록시를 사용하고 메타데이터 제거·픽셀 워터마크·보호 헤더를 유지하는지 확인한다.
8. DB와 `sanitized/`·`variants/` Storage의 최신 백업 및 복원 훈련 증거, Vercel 이전 배포와 Cloud Run 정상 리비전의 ROLLBACK 지점을 확인한다.
9. Google Cloud 월 JPY 500 예산 경고, Cloud Run 0~1, Maps·Places 키 제한, Supabase Free, Vercel Hobby 알림을 확인한다.

저장소 출시 계약 검사:

```powershell
.\scripts\check-release-readiness.ps1
```

## 3. 병합 후 태그와 Release

C43 PR을 Rebase and merge한 뒤 새 작업을 시작하지 않은 상태에서 실행한다.

```powershell
git switch main
git pull --ff-only origin main
.\scripts\verify-all.ps1
git tag -a v1.0.0 -m "Places & Plates v1.0.0"
git push origin v1.0.0
```

태그 워크플로는 다음을 다시 확인한다.

- 태그 이름·프런트·백엔드 버전·출시 계약 일치
- 태그 커밋이 `origin/main`에 포함됨
- 같은 커밋의 Secret protection·Verify·Production smoke 성공
- 실제 운영 Web·API가 태그 커밋을 제공함
- GitHub Release notes가 `docs/releases/v1.0.0.md`와 일치함

검사가 모두 성공한 뒤에만 GitHub Release가 생성된다. 실패한 태그에 Release를 수동 생성하지 않는다.

## 4. 출시 후 확인과 ROLLBACK

- GitHub Release와 `v1.0.0` 태그가 같은 커밋인지 확인한다.
- Web·API HTTPS와 health, 관리자 로그인, 공개 기록·지도, 사진 확대를 다시 확인한다.
- 회귀가 있으면 새 태그를 덮어쓰지 않는다. Vercel 이전 정상 배포와 Cloud Run 이전 정상 리비전으로 되돌리고 원인을 수정한 새 패치 버전을 준비한다.
- 비호환 DB 변경은 코드만 되돌리지 않고 forward fix를 우선한다.
- 결과, 커밋, 태그, Release URL, 운영 스모크, ROLLBACK 지점과 남은 위험을 당일 보고서에 기록한다.

---

## 日本語

Release対象は`v1.0.0`で、Webはhttps://placesplates.vercel.app、APIはhttps://places-plates-api-481849639838.asia-northeast3.run.app とする。

Tag作成前に、同一`main` commitのSecret protection・Verify・Production smoke、V16 migration、PUBLIC RLS、private Storage path不存在、EXIF・XMP・IPTC除去、`places-plates-corner-v1` watermark、owner login、upload・sanitize・draft・publish・list・detail・map・delete、backup・restore・ROLLBACK point、cost guardrailを確認する。一項目でも失敗した場合はreleaseしない。

C43 PRをRebase and merge後、最新`main`で全verificationを再実行し、annotated tag `v1.0.0`をpushする。Tag workflowはtag・version・main包含・同一commit checks・production deploymentを再検証し、成功後にだけ`docs/releases/v1.0.0.md`からGitHub Releaseを作成する。

Release後の問題では既存tagを上書きせず、Vercel previous deploymentとCloud Run previous revisionへROLLBACKし、databaseはforward fixを優先する。
