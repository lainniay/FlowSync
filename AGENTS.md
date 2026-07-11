# Repository Guidelines

## Project Structure & Module Organization

FlowSync is a small monorepo with two applications:

- `backend/`: Spring Boot 3 and Maven Wrapper. Sources are under `src/main/java/hgc/flowsync`, configuration under `src/main/resources`, and tests under `src/test/java`.
- `frontend/`: Vue 3, TypeScript, and Vite. Use `src/views` for pages, `src/router` for routes, `src/shared` for shared infrastructure, `src/assets` for styles/assets, and `src/__tests__` for tests.
- `docs/`: requirements, architecture, and environment setup guidance.
- Root files: `compose.yaml` runs MySQL; `mise.toml` defines tools/tasks; `.env.example` documents local variables.

Keep backend code grouped by business feature under `hgc.flowsync`; do not create global controller/service/mapper hierarchies or speculative empty modules.

## Build, Test, and Development Commands

Run from the repository root. `mise install` provides Temurin 21, Node 24, and pnpm 11; Maven itself comes from the committed wrapper.

- `cp .env.example .env`: create local configuration; never commit `.env`.
- `mise run db`: start MySQL 8.4 through Docker Compose.
- `mise run backend`: run Spring Boot with the `dev` profile on port 8080.
- `mise run frontend`: install from `pnpm-lock.yaml` and start Vite on port 8081.
- `mise run test:backend` / `mise run test:frontend`: run JUnit or Vitest; start MySQL before backend tests.
- `cd backend && ./mvnw verify`: perform full backend verification.
- `cd frontend && pnpm lint && pnpm build`: apply lint fixes, type-check, and build production assets.

## Coding Style & Naming Conventions

Java uses lowercase packages, `PascalCase` classes, `camelCase` members, and tabs matching the generated sources. Vue/TypeScript/CSS follow `.editorconfig`: UTF-8, LF, two spaces, final newline, and 100-column guidance. Use single quotes and extensionless imports. Name Vue components `PascalCase.vue` and composables `useThing.ts`. ESLint and Oxlint enforce frontend rules; inspect their automatic fixes.

## Testing Guidelines

Backend tests use JUnit 5 and Spring Boot Test; name classes `*Tests` and methods after observable behavior. Frontend tests use Vitest and Vue Test Utils; name files `*.spec.ts`. Add the smallest regression test for changed behavior. No coverage threshold is enforced; every PR must still run both existing suites and the relevant build.

## Commit & Pull Request Guidelines

History establishes concise Conventional Commits (`chore: initialize FlowSync project`). Continue with messages such as `feat(auth): add login endpoint` or `fix(frontend): handle expired session`. Pull requests must describe scope, list verification commands, link issues, and include screenshots for visible UI changes. Keep secrets, generated output, IDE settings, and local database data out of Git; update `.env.example` when adding required configuration.
