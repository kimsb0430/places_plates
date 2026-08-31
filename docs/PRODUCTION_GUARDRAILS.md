# Places & Plates 운영 도메인·비용 가드레일

기준일: 2026-08-31

이 문서는 C42의 운영 도메인, Google Cloud·Maps 예산과 할당량, Supabase·Vercel 사용량 알림을 설정하고 확인하는 기준이다. 비밀키, 결제 계정 ID, 알림 수신 주소와 실제 사용량 수치는 저장소에 기록하지 않는다. 저장소가 검증하는 목표 상태는 `config/production-guardrails.json`이 단일 기준이다.

## 1. 운영 기준표

| 영역 | 운영 기준 | 경고·제한 |
|---|---|---|
| 프런트 도메인 | `https://placesplates.vercel.app` | Vercel 관리 HTTPS, 운영 스모크 대상 |
| API 도메인 | `https://places-plates-api-481849639838.asia-northeast3.run.app` | Cloud Run 관리 HTTPS, 다른 자동 생성 URL은 운영 링크에 사용하지 않음 |
| Google Cloud | 프로젝트 `placesplates`, 서울 `asia-northeast3` | 월 JPY 500 알림 예산, 실제 50%·80%·100%, 예측 100% |
| Cloud Run | `places-plates-api` | 최소 0, 최대 1 인스턴스 |
| Maps JavaScript | 사용자가 지도 불러오기를 선택할 때만 호출 | 월 9,000 load 목표, 공급자 한도 분당 30,000·조정 불가 |
| Places API (New) | 관리자 장소 검색의 Text Search만 사용 | 월 1,000 request 목표, 현재 공급자 한도 일 75,000·분 600 |
| Supabase | Free 플랜 | 유료 초과 사용 없음, DB·Storage·Egress·MAU를 주 1회 확인 |
| Vercel | Hobby 플랜 | 유료 초과 사용 없음, Web·Email 기본 알림과 주 1회 Usage 확인 |

Google Cloud 예산은 알림일 뿐 지출 상한이 아니다. 결제 자동 해제나 프로젝트 자동 중지는 API와 지도를 함께 중단하므로 설정하지 않는다. Supabase Free와 Vercel Hobby는 초과 요금 대신 제한 또는 서비스 중단 가능성이 있으므로 알림을 무시하지 않는다.

## 2. 운영 도메인 고정

### 2.1 현재 v1 도메인

1. Vercel Production Domain이 `placesplates.vercel.app`인지 확인한다.
2. Cloud Run 서비스 URL이 숫자 프로젝트 ID를 포함한 `places-plates-api-481849639838.asia-northeast3.run.app`인지 확인한다.
3. Cloud Run `FRONTEND_ORIGINS`는 `https://placesplates.vercel.app` 하나만 허용한다. 와일드카드와 Preview 도메인은 넣지 않는다.
4. Vercel `NEXT_PUBLIC_API_BASE_URL`은 위 Cloud Run API 도메인을 사용한다.
5. GitHub Repository Variables에 다음 두 값을 등록한다.

```text
PRODUCTION_FRONTEND_URL=https://placesplates.vercel.app
PRODUCTION_API_URL=https://places-plates-api-481849639838.asia-northeast3.run.app
```

6. `main` 검증 뒤 Production smoke가 두 도메인의 동일 커밋을 확인하는지 본다.

### 2.2 사용자 도메인을 구매한 뒤 전환

현재 v1은 공급자 관리 도메인으로 출시할 수 있으며 별도 도메인 구매는 비용이 발생하므로 보류한다. 도메인을 구매하면 다음 항목을 한 번에 변경한다.

1. Vercel Project > Settings > Domains에서 새 HTTPS 도메인을 연결하고 DNS·인증서가 정상인지 확인한다.
2. Cloud Run `FRONTEND_ORIGINS`, Vercel `NEXT_PUBLIC_API_BASE_URL`, Google Maps 브라우저 키 HTTP referrer를 새 도메인으로 바꾼다.
3. GitHub의 `PRODUCTION_FRONTEND_URL`·`PRODUCTION_API_URL`과 `config/production-guardrails.json`을 같은 PR에서 갱신한다.
4. 로그인·업로드·지도·공개 사진·Production smoke를 새 도메인에서 통과시킨다.
5. 세션과 CORS가 안정된 뒤에만 이전 도메인을 운영 안내에서 제거한다.

## 3. Google Cloud 월 예산 알림

Google Cloud Console의 **결제 > 예산 및 알림 > 예산 만들기**에서 다음과 같이 설정한다.

| 항목 | 값 |
|---|---|
| 이름 | `Places Plates` |
| 기간 | 월간 |
| 범위 | 프로젝트 `placesplates`만 |
| 금액 | JPY 500 고정 |
| 실제 지출 임계값 | 50%, 80%, 100% |
| 예측 지출 임계값 | 100% |
| 수신자 | 결제 관리자·사용자 기본 이메일, 실제 수신 가능한지 확인 |
| 자동 조치 | 없음, 결제 자동 해제 금지 |

Cloud SDK가 설치된 관리 PC에서는 결제 계정 ID를 저장소 밖에서 확인한 뒤 아래와 동등한 설정을 사용할 수 있다. `<BILLING_ACCOUNT_ID>`는 문서나 보고서에 남기지 않는다.

```powershell
gcloud billing budgets create `
  --billing-account=<BILLING_ACCOUNT_ID> `
  --display-name="Places Plates" `
  --budget-amount=500JPY `
  --calendar-period=month `
  --filter-projects=projects/481849639838 `
  --threshold-rule=percent=0.50,basis=current-spend `
  --threshold-rule=percent=0.80,basis=current-spend `
  --threshold-rule=percent=1.00,basis=current-spend `
  --threshold-rule=percent=1.00,basis=forecasted-spend
```

생성 후 예산 상세 화면에서 프로젝트 범위, 네 임계값, 이메일 수신 설정을 다시 확인한다. 예산 설정 화면 캡처에는 결제 계정 ID와 개인 이메일이 보이지 않게 한다.

## 4. Cloud Run 비용 제한

`backend/cloudbuild.yaml`은 배포 때 다음 값을 유지한다.

```text
--min=0
--max=1
```

Cloud Run > `places-plates-api` > 새 버전 수정 및 배포 > 자동 확장에서 최소 인스턴스 0, 최대 인스턴스 1인지 확인한다. 최소 0은 유휴 시간 비용을 줄이고, 최대 1은 트래픽·버그로 인한 동시 인스턴스 급증을 제한한다. 실제 커뮤니티 트래픽이 늘면 오류율·응답 지연과 비용 근거를 기록한 별도 PR로 최대값을 높인다.

주 1회 Cloud Run Metrics에서 요청 수, 컨테이너 인스턴스 수, 컨테이너 CPU·메모리와 4xx·5xx를 확인한다. 최대 인스턴스에 반복적으로 도달해 5xx가 발생하면 비용을 무시하고 즉시 확장하지 말고 먼저 비정상 요청과 캐시·DB 병목을 조사한다.

## 5. Google Maps·Places 가드레일

### 5.1 API 키 제한

브라우저 키:

- 애플리케이션 제한: 웹사이트
- 허용 referrer: `https://placesplates.vercel.app/*`
- API 제한: `Maps JavaScript API`만
- 키 값은 Vercel의 `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY`에만 둔다. 브라우저 공개 키이지만 저장소에는 기록하지 않는다.

서버 키:

- API 제한: `Places API (New)`만
- Cloud Run Secret Manager의 `GOOGLE_PLACES_API_KEY`로만 주입한다.
- 브라우저·Vercel·GitHub 변수와 저장소에 넣지 않는다.

### 5.2 사용량 목표와 공급자 할당량

Google Maps Platform > Quotas에서 확인한 현재 공급자 할당량과 별도의 월 운영 목표는 다음과 같다.

| API·요청 | 공급자 할당량 | 월 운영 목표 |
|---|---:|---:|
| Maps JavaScript API 2D map loads | 분당 30,000, 조정 불가 | 9,000 |
| Places API (New) Text Search | 일 75,000·분당 600 | 1,000 |

Maps JavaScript의 `Map loads per minute`는 Console에서 조정 불가로 표시되므로 임의의 낮은 한도를 설정했다고 기록하지 않는다. Places `SearchTextRequest`는 일반적으로 조정 가능하지만 현재 무료 평가판 계정에서는 할당량 수정 작업이 비활성화되어 있다. 결제 계정 상태가 바뀌어 수정할 수 있게 되면 일 1,000·분당 10부터 적용하고 별도 운영 기록을 남긴다. 공급자 한도는 월 사용량을 보장하지 않으므로 지연 로딩, 관리자 전용 검색, 월 누계와 ¥500 예산 경고가 실제 비용 가드레일이다.

Google Cloud Monitoring에서 두 API 사용량 또는 quota exceeded 오류 알림을 만들고 이메일 알림 채널을 연결한다. 50%에서 원인을 조사하고, 80%에서 불필요한 지도 테스트와 반복 검색을 중지하며, 100%에서는 지도·검색 기능을 일시 제한한 뒤 키 유출과 비정상 트래픽을 확인한다.

## 6. Supabase 사용량 관리

Free 플랜은 요금이 청구되지 않으며 Pro의 Spend Cap을 설정하는 대상이 아니다. 제공량을 계속 초과하면 제한될 수 있으므로 Organization > Usage에서 프로젝트를 선택하고 매주 다음을 확인한다.

- Database Size
- Storage Size
- Egress
- Monthly Active Users

50%는 추세 확인, 80%는 불필요한 사진·전송과 오래된 파생본 조사, 100%는 새 업로드를 일시 중지하고 제한 또는 Pro 전환을 결정하는 기준이다. Pro로 전환하는 경우 Cost Control에서 Spend Cap을 켠 상태로 시작하고 Compute·Custom Domain처럼 Spend Cap 대상이 아닌 항목을 별도로 확인한다. 결제 플랜 변경은 별도 승인 없이 수행하지 않는다.

Free 프로젝트는 활동이 적으면 일시 중지될 수 있다. 중지 알림을 받으면 데이터를 삭제하거나 새 프로젝트를 만들기 전에 `docs/OPERATIONS_RUNBOOK.md`의 백업 상태를 확인한다.

## 7. Vercel 사용량 관리

Hobby 플랜은 유료 초과 사용을 구매할 수 없고 포함량을 넘으면 프로젝트가 제한될 수 있다. Dashboard > Usage에서 `placesplates` 프로젝트와 최근 30일을 선택해 매주 다음을 확인한다.

- Edge Requests와 Fast Data Transfer
- Function Invocations·Duration·Active CPU
- Image Optimization 사용량
- 빌드와 배포 횟수

My Notifications에서 Web·Email 알림을 유지한다. Hobby는 Pro의 임의 금액 Spend Management 대상이 아니므로 저장소의 50%·80%·100% 값은 운영자가 Usage 지표를 검토하는 기준이다. 80%에서 불필요한 Preview 배포를 줄이고, 100%에서는 공급자 제한을 우회하지 말고 다음 기간 또는 명시적인 Pro 전환을 판단한다.

## 8. 적용·검증 순서

1. `config/production-guardrails.json`과 실제 도메인을 대조한다.
2. Google Cloud 월 예산과 네 임계값, 알림 수신자를 확인한다.
3. Cloud Run 최소 0·최대 1 인스턴스를 적용한다.
4. Maps 브라우저 키 referrer·API 제한과 서버 키 API 제한을 확인한다.
5. Maps·Places 공급자 할당량의 조정 가능 여부와 월 누계 관찰 화면을 확인한다.
6. Supabase Free, Vercel Hobby와 각 사용량 알림 수신 상태를 확인한다.
7. GitHub 운영 URL 변수를 등록하고 Production smoke를 수동 실행한다.
8. 비밀값을 제거한 결과와 확인 시각을 `docs/daily/YYYY-MM-DD.md`에 남긴다.

저장소 계약 검증:

```powershell
.\scripts\check-production-guardrails.ps1
```

운영 확인 결과에는 다음만 기록한다.

```text
확인 시각(UTC/JST):
프런트/API 도메인:
Google Cloud 예산 이름과 임계값 확인: PASS/FAIL
Cloud Run 최소/최대 인스턴스 확인: PASS/FAIL
Maps·Places 키 제한과 할당량 확인: PASS/FAIL
Supabase 플랜·사용량 알림 확인: PASS/FAIL
Vercel 플랜·Web·Email 알림 확인: PASS/FAIL
Production smoke URL과 결과:
남은 위험:
```

## 9. 공식 참고 문서

- [Google Cloud 예산과 알림](https://cloud.google.com/billing/docs/how-to/budgets)
- [gcloud billing budgets create](https://cloud.google.com/sdk/gcloud/reference/billing/budgets/create)
- [Maps JavaScript API 사용량과 과금](https://developers.google.com/maps/documentation/javascript/usage-and-billing)
- [Cloud Quotas 알림](https://cloud.google.com/docs/quotas/set-up-quota-alerts)
- [Supabase 비용 제어](https://supabase.com/docs/guides/platform/cost-control)
- [Supabase 사용량 관리](https://supabase.com/docs/guides/platform/manage-your-usage)
- [Vercel Hobby 플랜](https://vercel.com/docs/plans/hobby)
- [Vercel 사용량 관리](https://vercel.com/docs/pricing/manage-and-optimize-usage)
- [Vercel 알림](https://vercel.com/docs/notifications)

---

## 日本語要約

### 運用基準

- Frontendは`https://placesplates.vercel.app`、APIは`https://places-plates-api-481849639838.asia-northeast3.run.app`をv1のcanonical domainとする。Custom domain購入は費用が発生するため保留し、購入後にVercel domain、CORS、API URL、Maps referrer、GitHub Variablesを同時に切り替える。
- Google Cloudの月次alert budgetはJPY 500、実績50%・80%・100%、予測100%とする。Budgetは支出上限ではなく、自動billing解除は設定しない。
- Cloud Runはmin 0、max 1 instanceとする。拡張は遅延・error・費用の根拠を確認した別PRで行う。
- Maps JavaScriptは月9,000 map load、Places Text Searchは月1,000 requestを運用目標とする。Mapsのprovider limitは30,000回/分で変更不可、Placesは現在75,000回/日・600回/分だがfree trial accountでは編集actionが無効である。Provider quotaだけでは月間利用量を保証しないため、lazy load、admin限定search、Metricsの月次累計、JPY 500 budget alertで管理する。
- Browser keyは`https://placesplates.vercel.app/*`とMaps JavaScript APIだけに制限する。Server keyはPlaces API (New)だけに制限し、Cloud Run Secret Managerにのみ保存する。
- Supabase FreeとVercel Hobbyはpaid overageを使わない。両providerのUsageを毎週確認し、50%で傾向確認、80%で不要利用抑制、100%で機能制限または明示的なplan変更を判断する。

### 適用確認

1. `config/production-guardrails.json`とprovider consoleを照合する。
2. Google Cloud budget、Cloud Run instance、Maps・Places keyとquotaを確認する。
3. Supabase Free、Vercel Hobby、Web・Email通知を確認する。
4. GitHub `PRODUCTION_FRONTEND_URL`・`PRODUCTION_API_URL`を登録しProduction smokeを実行する。
5. Credential、billing account ID、個人email、実際の使用量を除いた結果だけをdaily reportへ記録する。

Repository contractは`.\scripts\check-production-guardrails.ps1`で検証する。
