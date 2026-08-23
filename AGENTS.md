# Repository working rules

## Source-of-truth documents

- `PLACES_AND_PLATES_PRODUCT_DESIGN.html` defines the product and UX decisions.
- `PROJECT_STRUCTURE.md` defines the frontend and Spring Boot backend structure.
- `DEVELOPMENT_SCHEDULE.md` defines delivery order and commit boundaries.
- When a requirement, architecture decision, schedule, UI rule, or development convention changes, update every affected document in the same task and commit.

## Code comments

- Write method/function-level comments, Javadoc, docstrings, and explanatory TODO/FIXME text in Japanese.
- Do not add comments to self-explanatory code merely to satisfy the rule.
- Identifiers, API names, library names, protocol terms, and official error messages may remain in their original language.

## Commit boundary and subject

- Keep one coherent intention in each commit.
- Start every commit subject with the actual commit date in `YYYY/MM/DD` format.
- Use `YYYY/MM/DD <type>: <English> | <한국어> | <日本語>`.
- Keep the three summaries concise and semantically equivalent.
- Never commit secrets, actual private photos, raw EXIF data, API keys, or production credentials.
- Before every commit, run `scripts/check-secrets.ps1` against tracked and untracked commit candidates.
- Before deployment, scan the generated public artifact with `scripts/check-public-artifact.ps1`.

Example:

```text
2026/08/23 docs: add initial mockup and roadmap | 초기 목업과 개발 일정을 추가 | 初期モックアップと開発計画を追加
```

## Branch and pull request workflow

- Do not develop directly on `main`.
- Update `main`, then create a short-lived `codex/<scope>` branch for each work unit.
- Keep implementation, tests, affected documentation, and the daily report on that branch.
- Run the available frontend and backend verification before publication.
- Open the pull request as Draft when verification or review is still pending; mark it ready only after local verification succeeds.
- Push the work branch and open one pull request targeting `main`.
- The repository owner reviews and merges the pull request.
- Use **Rebase and merge** so the validated multilingual commit subject is preserved and no merge commit is added.
- Do not push review fixes directly to `main`; add them to the open branch and PR.
- After the owner merges, update local `main` and run the available verification again before starting the next branch.
- A work unit is complete only after the merged `main` CI and the available deployment smoke check pass.
- If a post-merge check fails, create a new `codex/post-merge-fix-<scope>` branch; never repair `main` directly.

## Deployment safety

- Keep production credentials only in the hosting provider or GitHub Environment secrets.
- Build and verify the exact artifact that will be deployed; do not deploy an unverified local directory.
- Confirm the target project, environment, branch, and public URL before deployment.
- After deployment, verify HTTPS, the main page, critical API health, and the absence of secrets in the public artifact.
- Record the deployment result, URL, verification, rollback point, and unresolved risk in the daily report.
- Follow `docs/DEPLOYMENT_CHECKLIST.md`; add a host-specific smoke test when the production hosting provider is selected.

## Daily development record

- At the end of a day with repository activity, create or update `docs/daily/YYYY-MM-DD.md` from `docs/daily/TEMPLATE.md`.
- Record the goal, completed work, important files, verification, decisions, risks, and next task.
- Never include secrets, private photo metadata, or raw sensitive logs.
