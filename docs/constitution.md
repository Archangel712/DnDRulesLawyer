# DnD Rules Lawyer Constitution

This document is the first file future agents and contributors must read before changing code.

## Project Purpose

DnD Rules Lawyer is an Android Kotlin app for browsing, searching, saving, and creating D&D rule resources. It combines official API-backed resources, local Room caching, favorites, custom user-created rules, and Homebrewery links into one searchable rules reference.

## Core Architecture

The app is a single Android app module using Kotlin, XML layouts, View Binding, Room, Retrofit, Gson, Coroutines, Material Components, and Espresso/JUnit tests.

Keep the existing layer boundaries:

- `domain`: pure app models and logic. No Android UI dependencies except currently isolated voice integration.
- `data`: API, Room, repository, DTO/entity mapping.
- `presentation`: Activities, Fragments, adapters, HTML rendering, Android UI wiring.
- `presentation/UIEntryPoint.kt`: central UI-facing construction and test replacement point.
- `data/repository/RulesRepository.kt`: main behavioral contract for rule search, sync, details, favorites, and user-owned resources.

Do not introduce a new dependency injection framework, reactive stack, navigation framework, or database abstraction unless explicitly approved.

## Development Rules

1. Prefer extending the existing repository contract over bypassing it from UI code.
2. Keep official, custom, and Homebrewery ownership rules consistent with `RuleSource`.
3. Preserve stable resource IDs:
   - Official: `official:<endpoint>:<index>`
   - Custom: `custom:<endpoint>:<slug>-<timestamp>`
   - Homebrewery: `homebrewery:<endpoint>:<slug>-<timestamp>`
4. Use `ResourceType.endpoint` for API endpoints and resource IDs.
5. Put network DTO parsing in remote mappers or repository enrichment code, not fragments.
6. Put Room schema changes in entities/DAOs/database and update tests.
7. Do not make UI code depend directly on Retrofit or Room.
8. Use structured Gson/JsonObject helpers from `core/json` instead of ad hoc string parsing.
9. Keep View Binding lifecycle-safe: clear fragment bindings in `onDestroyView`.
10. Any new user-facing string belongs in `res/values/strings.xml`.

## Data Ownership Rules

Official resources are synced from the D&D API and may be favorited, cached, and refreshed. Sync must preserve existing favorite state.

Custom resources are user-owned, editable, deletable, and stored locally with structured `RuleDetail` data when possible.

Homebrewery resources are user-owned links. They are deletable, rendered remotely in WebView reader mode, and currently not editable through the manual custom-resource form.

## UI Rules

The app uses classic Android Views and XML layouts. Add UI in XML unless the existing screen is already constructing small dynamic controls in Kotlin.

Keep drawer navigation centered in `MainActivity`. Feature screens should remain fragments unless they need a separate activity like `RuleDetailActivity`.

Search and favorites use RecyclerView adapters. Detail pages render HTML into WebView using `RuleDetailHtmlRenderer`.

## Testing Rules

Use the existing test pyramid in `docs/teststrategie-meilenstein-4.md`.

For most changes, add or update local unit tests in `app/src/test`. Use fakes from `testing/RepositoryTestFactory.kt` for repository behavior.

Use instrumented tests only for Android-specific behavior: Room, Espresso UI flows, permissions, or WebView-sensitive behavior.

Before considering a change complete, run the narrowest meaningful tests. Common commands:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` requires an emulator or connected device.

## Documentation Map

Feature guides live under `docs/ai-guides/`.

These documents, especially the feature guides, are living documents. If a future request significantly changes a feature's behavior, architecture, or specification, ask for approval to update the relevant feature guide as part of that work.

Read the relevant guide before modifying a feature:

- Search, sync, and favorites: `docs/ai-guides/search-sync-favorites.md`
- Detail rendering, links, and WebView: `docs/ai-guides/detail-rendering.md`
- Custom resources and Homebrewery: `docs/ai-guides/custom-resources.md`
- Data, mapping, and persistence: `docs/ai-guides/data-persistence.md`
- Settings, theme, and voice input: `docs/ai-guides/settings-voice.md`
