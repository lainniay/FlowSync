# Repository Guidelines

## Project Structure & Module Organization

FlowSync is a small monorepo with two applications:

- `backend/`: Spring Boot 3 and Maven Wrapper. Sources are under `src/main/java/hgc/flowsync`, configuration under `src/main/resources`, and tests under `src/test/java`.
- `frontend/`: Vue 3, TypeScript, and Vite. Use `src/views` for pages, `src/router` for routes, `src/shared` for shared infrastructure, `src/assets` for styles/assets, and `src/__tests__` for tests.
- `docs/`: requirements, architecture, and environment setup guidance.
- Root files: `compose.yaml` runs MySQL; `mise.toml` defines tools/tasks; `.env.example` documents local variables.

Keep backend code grouped by business feature under `hgc.flowsync`; do not create global controller/service/mapper hierarchies or speculative empty modules.

## Domain Model & API Contracts

Treat `docs/relationship.md` as the source of truth for persisted models, fields, constraints, and relationships. Treat `docs/api.md` as the source of truth for the frontend/backend HTTP contract. `docs/FlowSync.pdf` provides the original requirements, but the two Markdown contracts contain the agreed security and modeling adjustments and take precedence when they differ from the PDF.

- Persist only the current core models: `User`, `Project`, `ProjectMember`, `ProjectInvitation`, `Task`, `TaskLog`, and `Summary`.
- `User.systemRole` distinguishes only `ADMIN` and `USER`. Project ownership is represented by `Project.ownerId`; project participation is represented by `ProjectMember`. Every owner must also be a project member.
- `ADMIN` accounts are for system administration only. They may inspect all projects and manage projects, owners, and members, but cannot be project owners, members, invitees, task assignees, or project-content authors. An administrator who participates in project work must use a separate `USER` account.
- Only `ADMIN` may add a user directly to `ProjectMember`. A project owner must create a `ProjectInvitation`; only the invitee may accept or reject it, and acceptance creates the member relationship transactionally.
- Use foreign keys and the constraints documented in `docs/relationship.md`. Do not add implicit cascading deletion of project or task history.
- Use Spring Security Session authentication with HttpOnly cookies and CSRF protection. Never accept `currentUserId`, `creatorId`, or `operatorId` from the client as proof of identity.
- Resource updates use `PUT` with every editable field present. A nullable field may be sent as `null`; omitted fields or `null` for non-nullable fields produce validation errors.
- Return resources directly and use RFC 9457 Problem Details for errors. JSON IDs are strings, JSON fields are camelCase, and API enums must match `docs/api.md` exactly.
- AI task plans are transient DTOs held and edited by the frontend. Do not persist `AiTaskPlan`, add `planId` state, or create an AI plan table; only imported `Task` records are persisted.
- Keep at least one active `ADMIN`; project owners and users assigned incomplete tasks cannot be deactivated. Password resets and deactivation must invalidate the affected user's sessions.
- Array-based member, invitation, and AI-import writes are transactional: validate all elements and roll back the whole request on any failure.
- Bootstrap the first administrator only when no active `ADMIN` exists, using `DEFAULT_ADMIN_USERNAME` and `DEFAULT_ADMIN_PASSWORD` from the environment.

When a model or endpoint changes, update both contracts where applicable before implementation, then keep backend DTOs and frontend TypeScript types aligned with them.

## Build, Test, and Development Commands

Run from the repository root. `mise install` provides Temurin 21, Node 24, and pnpm 11; Maven itself comes from the committed wrapper.

- `cp .env.example .env`: create local configuration; never commit `.env`.
- `mise run db`: start MySQL 8.4 through Docker Compose.
- `mise run backend`: run Spring Boot with the `dev` profile on port 8080.
- `mise run frontend`: install from `pnpm-lock.yaml` and start Vite on port 8081.
- `mise run test:backend` / `mise run test:frontend`: run JUnit or Vitest; start MySQL before backend tests.
- `mise run test:mock`: run the frontend MSW contract and state-transition tests without starting the backend.
- `cd backend && ./mvnw verify`: perform full backend verification.
- `cd frontend && pnpm lint && pnpm build`: apply lint fixes, type-check, and build production assets.

## Frontend Mock API

The frontend development Mock uses MSW. Keep handlers under `frontend/src/mocks/handlers`, shared
response and validation helpers in `frontend/src/mocks/utils.ts`, and resettable seed state in
`frontend/src/mocks/store.ts`. Mock behavior must follow `docs/api.md`; do not invent a second,
frontend-only contract.

- Parse JSON request bodies through the shared `readJson` helper. Malformed or non-object JSON must
  return `400` Problem Details instead of escaping as an MSW `500`.
- TypeScript request types do not provide runtime validation. Validate query values, enums, actual
  calendar dates, nullable fields, documented lengths, contact fields, and AI parameters before use.
- Validation and business-rule failures use Problem Details. Batch errors identify the failing path,
  such as `userIds[1]` or `items[2].title`.
- Validate every batch element before mutating Mock state. Member, invitation, and AI-import failures
  must leave the entire in-memory request unapplied.
- Contract tests must cover status, response fields, `Content-Type`, Problem Details, and rollback for
  changed behavior, not only successful status codes.
- The Mock session is intentionally in-memory and does not simulate a real HttpOnly cookie. Cookie,
  `withCredentials`, expiry, session invalidation, and database transactions remain backend integration
  test responsibilities.
- Mock startup remains development-only. Do not register MSW from the production application entry.

## Coding Style & Naming Conventions

Java uses lowercase packages, `PascalCase` classes, `camelCase` members, and tabs matching the generated sources. Vue/TypeScript/CSS follow `.editorconfig`: UTF-8, LF, two spaces, final newline, and 100-column guidance. Use single quotes and extensionless imports. Name Vue components `PascalCase.vue` and composables `useThing.ts`. ESLint and Oxlint enforce frontend rules; inspect their automatic fixes.

## Testing Guidelines

Backend tests use JUnit 5 and Spring Boot Test; name classes `*Tests` and methods after observable behavior. Frontend tests use Vitest and Vue Test Utils; name files `*.spec.ts`. Add the smallest regression test for changed behavior. No coverage threshold is enforced; every PR must still run both existing suites and the relevant build.

## Commit & Pull Request Guidelines

History establishes concise Conventional Commits (`chore: initialize FlowSync project`). Continue with messages such as `feat(auth): add login endpoint` or `fix(frontend): handle expired session`. Pull requests must describe scope, list verification commands, link issues, and include screenshots for visible UI changes. Keep secrets, generated output, IDE settings, and local database data out of Git; update `.env.example` when adding required configuration.
