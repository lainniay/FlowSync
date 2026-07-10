# Repository Guidelines

## Project Structure & Module Organization

FlowSync is a small monorepo with separate applications:

- `backend/`: Spring Boot and Maven Wrapper. Java sources live in `src/main/java/hgc/flowsync`, configuration in `src/main/resources`, and JUnit tests in `src/test/java`.
- `frontend/`: Vue 3, TypeScript, and Vite. Put pages in `src/views`, routing in `src/router`, shared infrastructure in `src/shared`, assets in `src/assets`, and tests in `src/__tests__`.
- `docs/`: requirements and architecture notes.
- `compose.yaml`, `mise.toml`, and `.env.example`: local infrastructure, tool versions, tasks, and configuration template.

Keep backend code grouped by business feature under `hgc.flowsync`; do not create global controller/service/mapper hierarchies or speculative empty modules.

## Build, Test, and Development Commands

Run commands from the repository root:

- `mise install`: install the project Java, Node.js, and pnpm versions.
- `cp .env.example .env`: create local configuration; never commit `.env`.
- `mise run db`: start MySQL 8.4 through Docker Compose.
- `mise run backend`: run Spring Boot with the `dev` profile on port 8080.
- `mise run frontend`: install locked dependencies and start Vite on port 8081.
- `mise run test:backend`: run the backend JUnit suite; start MySQL first.
- `mise run test:frontend`: run Vitest once.
- `cd frontend && pnpm lint && pnpm build`: lint, type-check, and produce a frontend build.
- `cd backend && ./mvnw verify`: perform the full backend Maven verification.

## Coding Style & Naming Conventions

Use Java 21 conventions: package names lowercase, classes `PascalCase`, methods and fields `camelCase`, and tabs matching the generated backend sources. Vue and TypeScript use two spaces, single quotes, and extensionless imports. Name Vue components `PascalCase.vue` and composables `useThing.ts`. Run frontend linting before submitting; ESLint and Oxlint may apply fixes.

## Testing Guidelines

Backend tests use JUnit 5 and Spring Boot Test; name classes `*Tests` and methods after observable behavior. Frontend tests use Vitest and Vue Test Utils; name files `*.spec.ts`. Add the smallest regression test for changed behavior. No coverage threshold is currently enforced.

## Commit & Pull Request Guidelines

The repository has no commit history yet. Use concise Conventional Commit messages such as `feat(auth): add login endpoint` or `chore: update mise tools`. Pull requests should describe the change, list verification commands, link related issues, and include screenshots for visible UI changes. Keep secrets, generated output, IDE settings, and local database data out of Git.
