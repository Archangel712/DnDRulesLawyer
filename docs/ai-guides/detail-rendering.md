# Detail Rendering, Links, and WebView Guide

## Scope

Use this guide for rule detail pages, HTML output, linked references, Homebrewery reader mode, and WebView behavior.

## Key Files

- `presentation/detail/RuleDetailActivity.kt`
- `presentation/detail/RuleDetailHtmlRenderer.kt`
- `presentation/detail/RuleDetailLink.kt`
- `presentation/detail/ClassProgressionTableConfig.kt`
- `presentation/detail/HomebreweryReaderScript.kt`
- `app/src/main/assets/homebrewery-lite.css`
- `presentation/utils/PresentationUtils.kt`

## Current Spec

`RuleDetailActivity` loads a resource by ID, configures edit/delete actions based on source, gets detail from the repository, and renders it in a WebView.

Rendering behavior:

- Official/custom structured details render through `RuleDetailHtmlRenderer`.
- For both official and custom structured resources, the goal is to match how that resource type is represented in the 5e 2014 Player's Handbook as closely as the available data allows.
- Missing details render a safe missing-detail document.
- Homebrewery resources load a remote URL and then apply reader mode JavaScript.
- Local rendered HTML uses `file:///android_asset/` so the CSS asset can load.
- WebView supports zoom and fits local generated content to width.
- Local generated detail pages intercept app resource links.

Link behavior:

- Internal links use `dndruleslawyer://resource?id=<resourceId>`.
- Official references can be resolved from `id`, API `url`, or fallback endpoint plus `index`.
- Opening a linked official resource may sync that resource type first if it is missing locally.

Security and correctness:

- Escape user/API text before inserting into HTML.
- Only use raw HTML where it was produced by renderer/link helpers.
- Keep generated HTML compatible with `homebrewery-lite.css`.

## Implementation Rules

When adding specialized rendering for a `ResourceType`:

1. Add a branch in `RuleDetailHtmlRenderer.render`.
2. Prefer raw JSON helpers from `core/json`.
3. Fall back gracefully when fields are absent.
4. Escape all visible text.
5. Add renderer tests for important fields and escaping.
6. Add repository enrichment only if the renderer needs extra remote data.

For custom resources that share a type with official resources, reuse the same PHB-like rendering path whenever possible. Add custom-only rendering only when the custom data model cannot reasonably produce the same structure.

## Tests

Relevant tests:

- `RuleDetailHtmlRendererTest`
- `RemoteRuleDetailMapperTest`
- `DefaultRulesRepositorySyncAndDetailsTest`
- Espresso coverage only when navigation/WebView flow changes.
