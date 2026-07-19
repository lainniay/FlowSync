# FlowSync Design System

This file is the shared visual context for developers and AI coding agents working on the FlowSync
frontend. It defines the intended product atmosphere, tokens, component patterns, interaction rules,
and implementation boundaries. It is not imported by the application at runtime.

FlowSync is an internal project and task collaboration product built with Vue 3 and Element Plus. The
interface should feel dependable, efficient, and calm. It is a working application, not a marketing
site.

## 1. Visual Theme and Atmosphere

### Direction

- Light enterprise productivity interface.
- Structured and information-dense without feeling cramped.
- White working surfaces on a cool gray-blue page background.
- One restrained blue accent for navigation, primary actions, links, and focus.
- Thin borders provide structure; shadows are rare and subtle.
- Content and status are visually stronger than decoration.

### Personality

- Clear, dependable, practical, collaborative.
- Neutral enough for long working sessions.
- Friendly through plain language and spacing, not illustrations or playful decoration.
- ADMIN screens should feel controlled and operational.
- USER screens should feel task-oriented and collaborative.

### Avoid

- Decorative gradients, glass effects, large hero sections, floating blobs, and marketing layouts.
- Dark mode in the current milestone.
- Oversized headings, excessive empty space, and card grids used where a table is clearer.
- Multiple competing accent colors.
- Animation that delays navigation or data access.

## 2. Color System

Runtime CSS variables belong in `src/assets/main.css`. Business modules should consume semantic
variables rather than introducing new raw colors.

### Brand and interaction

| Token | Value | Usage |
| --- | --- | --- |
| `--fs-color-primary` | `#2563eb` | Primary buttons, selected navigation, links, focus |
| `--fs-color-primary-hover` | `#1d4ed8` | Primary hover state |
| `--fs-color-primary-soft` | `#eff6ff` | Selected menu and quiet highlighted surfaces |

### Neutral surfaces and text

| Token | Value | Usage |
| --- | --- | --- |
| `--fs-color-page` | `#f4f7fb` | Application content background |
| `--fs-color-surface` | `#ffffff` | Tables, filters, forms, dialogs |
| `--fs-color-surface-muted` | `#f8fafc` | Secondary sections and table headers |
| `--fs-color-border` | `#dbe3ee` | Panels and regular separators |
| `--fs-color-border-strong` | `#c7d2e0` | Emphasized dividers and active boundaries |
| `--fs-color-text` | `#1f2937` | Titles and main content |
| `--fs-color-text-secondary` | `#64748b` | Descriptions, timestamps, supporting text |
| `--fs-color-text-disabled` | `#94a3b8` | Disabled and unavailable content |

### Semantic states

| Token | Value | Usage |
| --- | --- | --- |
| `--fs-color-success` | `#16a34a` | Completed and successful states |
| `--fs-color-warning` | `#d97706` | Pending, caution, approaching deadline |
| `--fs-color-danger` | `#dc2626` | Failure, deletion, blocked and destructive actions |
| `--fs-color-info` | `#475569` | Neutral informational states |

Color never carries meaning alone. Status text, an icon, or an accessible label must remain visible.
Project archival is represented by `archivedAt`, not by inventing another `ProjectStatus` color.

## 3. Typography

### Font family

Use locally available system fonts. Do not add a web-font download for the foundation milestone.

```text
Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
"Segoe UI", "Microsoft YaHei", sans-serif
```

### Scale

| Role | Size | Weight | Line height |
| --- | --- | --- | --- |
| Login product title | 40–56px responsive | 700 | 1.1 |
| Page title | 24px | 650–700 | 1.35 |
| Section title | 18px | 600 | 1.4 |
| Card metric | 24–32px | 700 | 1.2 |
| Body and table | 14px | 400 | 1.55 |
| Label and button | 14px | 500 | 1.4 |
| Supporting text | 13px | 400 | 1.5 |
| Compact metadata | 12px | 400 | 1.4 |

Use size and weight to create hierarchy. Do not use uppercase English labels or excessive letter
spacing as decoration. Chinese interface copy should be short, direct, and action-oriented.

## 4. Spacing, Radius, Border, and Elevation

### Spacing scale

Use a 4px base scale:

| Token | Value | Typical usage |
| --- | --- | --- |
| `--fs-space-1` | 4px | Tight icon or metadata gap |
| `--fs-space-2` | 8px | Related controls |
| `--fs-space-3` | 12px | Compact row padding |
| `--fs-space-4` | 16px | Form and panel padding |
| `--fs-space-5` | 20px | Standard content panel padding |
| `--fs-space-6` | 24px | Page content gap |
| `--fs-space-8` | 32px | Major section separation |

Do not introduce arbitrary spacing such as 17px or 19px when a token already fits.

### Radius

| Token | Value | Usage |
| --- | --- | --- |
| `--fs-radius-sm` | 4px | Tags and compact controls |
| `--fs-radius-md` | 8px | Filters, tables, forms, dialogs |
| `--fs-radius-lg` | 12px | Login card and major empty state |

Do not use pill shapes for regular buttons or panels. Status tags may use their Element Plus default
shape.

### Borders and shadows

- Standard panels: 1px solid `--fs-color-border`.
- Table sections may use borders instead of separate cards.
- Standard business panels have no shadow.
- Dialogs and dropdown overlays use the restrained Element Plus default elevation.
- The login card may use one subtle shadow; no colored glow.

## 5. Application Layout

### Desktop shell

- Persistent left sidebar: 216–232px.
- Top bar: 56–64px.
- Main content: flexible width, scrollable, with 24px outer padding.
- Comfortable maximum content width may be used for forms; data tables should use the available width.
- Navigation remains visually stable while page content loads.

### Page composition

Use this order:

```text
.page
  .page-header
    title and description
    page-level primary action
  .filter-panel       optional
  .content-panel
    table, detail, form, or feedback state
```

- Page title describes the current resource.
- The main action sits at the right of the page header.
- Filters stay above their table and remain visible during table loading.
- Breadcrumbs are used for three or more navigation levels, such as project to task detail.
- Tabs are used for equally important sections within one resource, such as project overview, tasks,
  members, invitations, and summaries.

### Responsive behavior

- The primary target is laptop and desktop use.
- Below 720px, page headers and filter actions may wrap vertically.
- Tables may scroll horizontally rather than hiding required columns.
- The current milestone does not require a separate mobile navigation system.

## 6. Element Plus Component Rules

FlowSync continues to use explicit component imports and the matching component CSS imports. Do not
install a second UI library or create replacement base controls.

### Buttons

- One primary button per page section or dialog.
- Secondary actions use default or text buttons.
- Destructive actions use `type="danger"` and a confirmation step.
- Loading buttons are disabled against duplicate submission.
- Button copy uses a verb: “创建项目”, “重新加载”, “确认导入”.

### Forms

- Filter forms may be inline and wrap when space is limited.
- Dialog and editing forms use top labels unless horizontal alignment materially improves scanning.
- Required fields use Element Plus validation rules.
- Server field errors should be associated with their field when Problem Details includes a field path.
- Nullable fields are submitted as `null`; complete PUT forms include every editable field.
- Failed submissions retain user input.

### Data tables

- Use tables for comparable business records; do not replace them with decorative card grids.
- Headers use a quiet muted surface and medium-weight text.
- The operation column stays on the right and uses compact text buttons.
- Important identity columns, such as project name or task title, may link to detail pages.
- Pagination sits below the table, aligned right, with total count visible.
- API page numbers are zero-based; Element Plus displayed page numbers are one-based.

### Dialogs and confirmations

- Use a dialog for focused create/edit forms that do not need a shareable URL.
- Use a full page for complex review flows such as the editable AI task plan.
- Archive, restore, deactivate, password reset, removal, and permanent deletion require confirmation.
- Permanent deletion copy must say that the action cannot be undone.

### Tags and status

- Tags are for short roles, priority, status, or archived state.
- Keep the raw API enum in data; map it to an explicit Chinese label for display.
- ADMIN and USER are system roles, not project roles.
- Do not display `ARCHIVED` as a project status because it is not in the API contract.

## 7. Reusable Page Patterns

### Dashboard

- Five or six statistic cards at most before wrapping.
- Metric value is the strongest element; label and context are secondary.
- Task status distribution and recent activity appear below the metrics.
- Cards use consistent height and restrained borders.

### Standard list page

```text
Page header and main action
Filter panel: search, enum filters, query, reset
Content panel: error or table
Empty state when a successful response has no items
Pagination
```

This pattern is demonstrated by the project list and should be copied structurally by user, task, and
summary modules without creating a universal table abstraction.

### Detail page

- Identity and key state appear in the header.
- Read-only metadata uses a description list or clear labeled rows.
- Related collections use tabs or distinct sections.
- Unauthorized actions are absent; archived writes are visibly disabled when that context helps explain
  why the action is unavailable.

### Authentication page

- Centered, single-purpose login card.
- No application sidebar or top bar.
- Product name, short description, username, password, one primary action, and an inline error region.
- Authentication loading must not cause the login form to flash before redirecting an existing session.

## 8. Loading, Empty, Success, and Error States

### Loading

- Authentication initialization: root-level loading state.
- List loading: table or content-panel loading while filters remain usable where safe.
- Detail loading: skeleton or content-level loading.
- Submission loading: action button loading and duplicate submission disabled.

### Empty

- A successful empty response uses `el-empty` or an equally clear in-panel state.
- Filtered empty results explain that no records match and offer “重置筛选”.
- An initial empty state may show a create action only when the current user is authorized.
- Empty and error states never appear together.

### Success

- Reads render updated data without a success toast.
- Writes show one lightweight success message.
- After a write, re-query or reliably synchronize the affected resource.
- Never show success while leaving stale rows or counters on screen.

### Error

- `401`: clear authentication and navigate to login, except invalid login credentials handled on login.
- `403`: show the permission page or a clear resource-level forbidden state.
- `404`: show unavailable or invisible resource state without leaking its existence.
- `409`: retain current data and show the business conflict.
- `422`: prefer field-level feedback; otherwise show the Problem Details message near the form.
- Network and `5xx`: show a stable fallback message and a retry action.
- Global infrastructure must not emit a duplicate toast for an error already owned by the page.

## 9. Motion and Interaction

- No page-entry animation or decorative motion in the current milestone.
- Hover feedback is a color or background change, never scale or bounce.
- Use browser and Element Plus default timing for menus, dialogs, dropdowns, and tooltips.
- Loading indicators communicate real pending work and must stop in `finally` paths.
- Keyboard focus must remain visible.
- Dialog focus and Escape behavior should use Element Plus defaults unless a destructive confirmation is
  intentionally protected.

## 10. Accessibility and Content

- Maintain readable contrast between text, surfaces, borders, and status colors.
- Inputs have visible labels; placeholders do not replace labels.
- Icon-only actions require accessible labels or tooltips.
- Error regions that update after requests should be perceivable without relying only on color.
- Use real buttons for actions and real links or router links for navigation.
- Dates and enum labels are formatted consistently in Chinese UI text.
- Confirmation text states the resource and consequence, not only “确定吗？”.

## 11. Implementation Boundaries

The foundation maintainer owns:

- `src/main.ts`
- `src/App.vue`
- `src/router/index.ts`
- `src/layouts/AppLayout.vue`
- `src/assets/main.css`
- `src/shared/api/http.ts`
- `src/shared/api/csrf.ts`
- `src/shared/api/errors.ts`
- `src/shared/api/types.ts`
- `src/stores/auth.ts`

Module developers may add scoped styles within their pages and components, but they must not:

- replace the root shell or duplicate AppLayout;
- create another Axios instance, CSRF helper, authentication store, or Problem Details parser;
- override unrelated Element Plus internal classes globally;
- introduce module-specific global colors or spacing tokens;
- create universal table, form, or API abstractions without two proven consumers;
- treat frontend visibility checks as a replacement for backend authorization.

When a new public visual pattern is genuinely needed by multiple modules, discuss it in review and add
the smallest shared rule to this file and `src/assets/main.css`.

## 12. Design Review Checklist

- Does the page follow the shared shell and page composition?
- Are raw colors and arbitrary spacing avoided?
- Is Element Plus used before creating a custom control?
- Are ADMIN, USER, owner, member, assignee, and creator permissions kept distinct?
- Are loading, empty, success, and error states all defined?
- Do writes re-query or reliably synchronize the visible data?
- Are archived projects read-only except for restoration and permanent deletion?
- Is AI output visibly temporary and subject to authorized USER review?
- Are destructive consequences explicit?
- Does the page remain usable without decoration or animation?
