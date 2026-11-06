AI Usage & Data Development Rules — POSE CRM
Owner: Nhi — Data module
Applies to: Claude (claude.ai), ChatGPT Web, GPT Codex and any AI-assisted changes involving POSE CRM data.
Baseline: The team's latest approved Data FINAL package, repository state, database schema and Data Dictionary. Do not assume a file, table, column or requirement exists until verified.
Status: Project rules and working agreement; implementation details must be checked against the actual repository and database before changes.

1. Purpose and scope
AI is a supporting tool, not an autonomous project owner. The responsible member makes design decisions, reviews modifications, runs/approves tests and controls Git integration.

Data scope includes:
- Reviewing use cases (UCs) for data requirements.
- Maintaining the approved database schema, relationships, constraints and Data Dictionary.
- Preparing and enriching realistic CSV/SQL seed data for existing CRM tables.
- Implementing approved database scripts, validations, migration changes and tests.
- Checking data integrity and compatibility with existing backend queries/APIs.
- Documenting assumptions, changes, test evidence and unresolved risks.
Default priority: Reuse and enrich existing approved tables and data. Do not create new tables, alter columns/constraints or modify teammates' frontend/backend code merely to make seeding easier. Propose such changes separately and obtain approval first.
2. AI tools and responsibilities
2.1 Claude (claude.ai) + ChatGPT Web — Planning, Design and Analysis
Used together to:
- Brainstorm and align data design decisions with Nhi.
- Analyze UCs, schema structure, data quality and seed-data coverage.
- Identify compatibility, privacy and integrity risks.
- Develop step-by-step implementation and verification plans.
- Produce concise English prompts for GPT Codex after the plan has been agreed.
- Explain questions, trade-offs and final decisions in Vietnamese.
Inputs: UC requirements, approved CSV/SQL data, schema documentation, Data Dictionary, relevant project rules, error logs and test results. Share only the necessary files and redact secrets or sensitive personal data.

Outputs: Agreed decisions, affected-file list, design notes, acceptance criteria, implementation plans, Codex prompts and review checklists.
2.2 GPT Codex — Implementation and Verification
Codex must:
- Read project rules first, then inspect only relevant files and their dependencies.
- Confirm the existing schema and naming conventions rather than inventing tables/columns.
- Make minimal, approved, task-scoped changes to SQL/CSV/scripts/tests.
- Follow code formatting, data-cleaning and safety rules in this file.
- Run relevant tests in a safe local/test environment when available.
- Report exact changed files, what changed, tests run/not run, results and remaining risks in Vietnamese.
For small, well-scoped tasks, Nhi may request Codex directly. For multi-file, complex or risky work, plan with Claude/ChatGPT Web first and then hand an English prompt to Codex.
2.3 Nhi — Responsible member
Nhi approves scope, reviews diffs and data samples, checks actual output, decides on migration/seed execution and personally authorizes Git operations. No AI response or generated artifact constitutes approval to change production data.
3. Mandatory working sequence
1. Understand: Identify the exact UC, approved baseline, target environment, relevant files/tables and expected result.
2. Inspect: Read actual schema, Data Dictionary, constraints, current seed scripts and relevant queries; report unknowns instead of guessing.
3. Plan: Describe affected tables/files, proposed record counts, relations, quality checks, risks and rollback/recovery approach. For structural or destructive changes, stop for Nhi's approval.
4. Implement: Make only authorized changes in small, readable units. Preserve existing conventions and compatibility.
5. Validate: Test syntax, idempotency, keys, relationships, encoding, content quality and relevant UC/API behavior; use a test database when possible.
6. Review: Provide concise diff summary, test evidence, sample data checks and issues requiring a decision.
7. Git: Do not commit, push, merge, rebase, reset, force-push or switch branches unless explicitly instructed. Prefer not to run commands that modify Git state. Nhi handles integration.
4. Clean Code and SQL style — non-negotiable
- Readable, maintainable code: Clear responsibility per file/function/procedure/script; separate schema, migrations, seed data and validation where the repository's conventions allow.
- Never collapse an entire SQL statement, function, script or code block into one long line. Use consistent indentation, line breaks and whitespace. Multi-column INSERT, complex JOIN, CASE, WHERE, CTE and procedural logic must be formatted across lines.
- Avoid monolithic blocks: Split logically distinct operations into named, focused units; avoid duplicated queries/logic and unnecessary abstraction.
- Clear, consistent names: Table, column, constraint, variable and script names must describe their business meaning. Follow the approved project's convention (prefer consistent snake_case for new identifiers only when compatible). Do not rename existing identifiers for style alone.
- Comments are required when useful: At minimum, explain the business purpose of non-obvious scripts, complex logic, transformations, data assumptions, important constraints and safety guards. Write concise, accurate Vietnamese comments (or follow the team's documented convention). Do not add noisy comments that merely repeat obvious SQL. Update comments when code changes.
- Explicit SQL: Prefer named columns in INSERT; avoid SELECT * in maintained queries when an explicit column list is safer. Use parameterized queries for application inputs; never concatenate user input into SQL.
- Minimal diffs: Do not reformat unrelated code, silently replace existing logic, overwrite files or create duplicate modules.
- Reproducible scripts: Use consistent ordering, declared dependencies and explicit prerequisites. Avoid hidden environment assumptions and hardcoded secrets.
5. Naming, Vietnamese text and data cleanliness
Distinguish technical identifiers from displayed business data:
Category	Required standard
SQL identifiers (tables, columns, constraints, scripts)	Use the existing schema's convention; new identifiers should be consistent, meaningful and usually ASCII without spaces/diacritics if allowed by project rules. Do not break existing references by renaming.
Person/organization names and addresses stored for display	Preserve full, natural Vietnamese diacritics when the real/sample value is Vietnamese (e.g., Nguyễn Thị Minh Anh, not Nguyen Thi Minh Anh), proper capitalization and meaningful words. Do not fabricate claims about real people.
Search keys/slugs	If a UC requires accent-insensitive search, implement a separate normalized search field/index or query strategy without overwriting the original displayed value.
Email, phone, IDs, codes, tax numbers, URLs	Follow their own documented formats and business constraints; do not add Vietnamese diacritics or spaces where invalid.
CSV	UTF-8 (prefer UTF-8 with appropriate tool compatibility), consistent delimiter, escaped quotes, headers matching mapping, no malformed rows.
SQL text	Use Unicode-aware types/collations and PostgreSQL-compatible handling according to the actual schema; test Vietnamese round-trip read/write.


5.1 Cleaning rules
- Remove unintended leading/trailing spaces, repeated whitespace, hidden control characters and encoding artifacts (e.g., replacement character �) after inspecting their cause.
- Detect and fix obvious typos, malformed casing, invalid dates, impossible amounts, broken references and inconsistent status/category values according to business rules.
- Do not blanket-strip punctuation or symbols. Keep legitimate characters in names, addresses, emails, product names, codes, currencies and descriptions (such as &, -, /, +, @, #) when meaningful. Do not remove accents.
- No placeholder or junk values such as asdf, ???, test123, meaningless repeated strings or random symbols in approved demo data, unless they are explicitly labeled negative-test fixtures in a separate test dataset.
- Use NULL only when the column and business meaning allow it; do not use arbitrary 0, N/A, empty strings or fake dates as substitutes without a documented rule.
- Keep units, currencies, timestamps, time zones, codes and enumeration values consistent and documented.
- Store only necessary synthetic or approved data; do not expose real customer personal information or credentials in seeds or AI prompts.
5.2 Data must look like a coherent CRM system
- Records must be plausible, varied and linked to the actual UCs rather than generated purely for volume.
- Maintain sensible business flows and timelines across related entities (for example, customer → opportunity → sale, only if these entities exist in the actual schema).
- Cover regular cases, boundary cases and distinct valid statuses/categories without contradictory states.
- Document the target record count by existing table, the reason for the count and the UC it supports. Scale gradually to avoid performance and review problems.
- Prefer deterministic, reproducible generation using fixed seeds/stable test identifiers and clear ownership/prefix conventions when appropriate.
- Never invent new business rules or columns to accommodate synthetic data without approval.
6. Database integrity and safe seeding
- Respect PK, FK, UNIQUE, NOT NULL, CHECK, DEFAULT and all applicable business constraints.
- Respect parent-child insert order and maintain referential integrity; no orphan rows.
- Ensure uniqueness where required; detect duplicates by the correct business key, not just a display name.
- Make seed scripts idempotent when possible: rerunning should not silently double the dataset. Choose ON CONFLICT/upsert or explicit existence checks only after verifying keys and desired behavior.
- Clearly separate demo/test seed data from actual operational or production records.
- Use transactions and safe failure behavior where appropriate; never assume a script is automatically reversible.
- Do not execute DROP, TRUNCATE, broad DELETE, mass UPDATE, destructive migrations or production resets without explicit scope and approval. Any risky operation needs a reviewed backup/recovery plan.
- Preserve compatibility with current API queries, application expectations, constraints and migration history.
- Check execution plans/performance on large data additions when relevant; avoid unnecessary huge seeds.
7. Tests and acceptance criteria
Before reporting completion, verify what is applicable:
- SQL and script syntax pass on the actual project-supported PostgreSQL version/environment.
- All intended rows load; actual record counts match the approved plan.
- No unintended duplicate business keys, missing required fields or orphan relationships.
- Vietnamese Unicode displays correctly after CSV import, database write, query and API read.
- Names, descriptions, statuses, dates, amounts and codes follow documented standards.
- Re-running seed scripts is safe and yields the expected dataset (idempotency or explicitly documented non-idempotent behavior).
- Existing UC queries and relevant integration/API tests still work, when available.
- No unrelated file changes and no unapproved Git state changes.
If a test cannot run, state Not run, explain why and show the exact manual verification commands/steps; never claim tests passed without executing them.
8. File handoff and efficient AI use
Send only what the task needs:
- Planning (Claude/ChatGPT Web): UC/acceptance criteria + Data Dictionary/schema summary + relevant CSV samples + errors (redacted).
- Implementation (Codex): agreed plan/prompt + project rules + exact repository file paths; Codex reads necessary source/dependencies from the workspace.
- Verification: seed/test commands + results + SQL assertions/data samples + diff summary.
To save tokens, point to exact paths, specify the expected change, avoid re-reading unrelated files, request short status reports and use direct Codex tasks for simple fixes. Never sacrifice validation, safety or clarity just to reduce tokens.
9. Required Codex completion report (in Vietnamese)
1. Phạm vi: UC/task; approved constraints.
2. Đã kiểm tra: exact file paths, tables and assumptions.
3. Đã chỉnh: exact files and short explanation for each.
4. Chất lượng dữ liệu: added/changed row counts, format/Unicode checks, duplicate and FK checks.
5. Tests: actual commands and PASS/FAIL/NOT RUN with evidence.
6. Rủi ro/cần duyệt: unresolved concerns and next decisions.
7. Git: state clearly “Không tự commit/push/merge/chuyển nhánh.”
10. Reusable English prompt for GPT Codex
Task: [Exact Data UC / seed / validation request]
Files in scope: [Exact paths]
Current baseline: [Approved FINAL package / branch / schema version, verified]
Expected output: [Acceptance criteria and target tables/record counts]

Read this AI_USAGE_DATA.md and relevant project rules before editing. Inspect the actual schema, keys, constraints and related code first. Do not assume missing files or columns exist.

Implement only the approved Data scope using clean, modular, clearly formatted code. Do not write monolithic or one-line SQL blocks. Use meaningful names and concise comments for business logic and non-obvious operations. Preserve existing schema and functionality unless I expressly authorize changes.

Enrich data with realistic, coherent, diverse records that satisfy the existing UC and database constraints. Keep Vietnamese display names and text fully accented and correctly encoded; remove only accidental junk characters or invalid formatting, not legitimate punctuation. Keep technical identifiers and codes compliant with the existing schema. Make seed scripts safely repeatable when possible.

Run relevant validation/tests locally if available. Do not perform destructive database operations. Do not modify unrelated modules. Never commit, push, merge, rebase, reset or switch Git branches.

Report findings, exact changed files, inserted/updated row counts, executed tests, results and remaining risks in Vietnamese. For any schema, destructive or cross-module change, stop and ask for approval before editing.

11. Final review checklist
- [ ] Existing FINAL baseline and UC verified.
- [ ] Affected schema/files and business rules identified.
- [ ] Names and technical identifiers are meaningful and consistent.
- [ ] SQL/code is clean, multi-line, modular and properly commented.
- [ ] Vietnamese names/descriptions keep accurate diacritics; encoding checked.
- [ ] No junk characters, broken formats, accidental spaces or invalid placeholders.
- [ ] Data is realistic, diverse and linked correctly to existing tables.
- [ ] PK/FK/UNIQUE/NOT NULL/CHECK constraints validated.
- [ ] Seed rerun behavior verified; no unintended duplicates.
- [ ] Relevant tests run or clearly marked Not run.
- [ ] No unapproved schema changes, destructive SQL or unrelated edits.
- [ ] No AI-initiated Git commits/pushes/merges/branch switches.
- [ ] Nhi reviews the final diff and explicitly approves integration.