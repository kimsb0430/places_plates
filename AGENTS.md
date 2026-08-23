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

Example:

```text
2026/08/23 docs: add initial mockup and roadmap | 초기 목업과 개발 일정을 추가 | 初期モックアップと開発計画を追加
```

## Branch and pull request workflow

- Do not develop directly on `main`.
- Update `main`, then create a short-lived `codex/<scope>` branch for each work unit.
- Keep implementation, tests, affected documentation, and the daily report on that branch.
- Run the available frontend and backend verification before publication.
- Push the work branch and open one pull request targeting `main`.
- The repository owner reviews and merges the pull request.
- Use **Rebase and merge** so the validated multilingual commit subject is preserved and no merge commit is added.
- Do not push review fixes directly to `main`; add them to the open branch and PR.
- After the owner merges, update local `main` and run the available verification again before starting the next branch.

## Daily development record

- At the end of a day with repository activity, create or update `docs/daily/YYYY-MM-DD.md` from `docs/daily/TEMPLATE.md`.
- Record the goal, completed work, important files, verification, decisions, risks, and next task.
- Never include secrets, private photo metadata, or raw sensitive logs.
