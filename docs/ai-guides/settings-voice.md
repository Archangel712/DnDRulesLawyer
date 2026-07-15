# Settings, Theme, and Voice Input Guide

## Scope

Use this guide for dark mode, official reload, speech-to-text, permissions, and app-level UI wiring.

## Key Files

- `MainActivity.kt`
- `presentation/settings/SettingsFragment.kt`
- `presentation/settings/AppThemePreferences.kt`
- `domain/voice/SpeechToTextManager.kt`
- `presentation/search/SearchFragment.kt`
- `presentation/favorites/FavoritesFragment.kt`
- `presentation/UIEntryPoint.kt`
- `AndroidManifest.xml`

## Current Spec

Settings supports:

- Dark mode preference through `AppThemePreferences`.
- Manual reload of default official resources.

Voice input supports:

- `RECORD_AUDIO` runtime permission.
- Android `SpeechRecognizer`.
- Fixed recognition language `en-US`.
- Search screen voice search.
- Favorites add-search voice input.
- Toast errors for denied permission, no result, unavailable recognizer, or recognition failure.

App wiring:

- `MainActivity` owns drawer navigation.
- `UIEntryPoint` constructs repository, adapters, detail intents, renderer, and speech manager.
- Tests can replace the repository through `UIEntryPoint.replaceRulesRepositoryForTests`.

## Implementation Rules

Do not create fragment-local repositories manually. Use `UIEntryPoint.rulesRepository`.

When adding UI dependencies that need test replacement, add construction to `UIEntryPoint`.

When changing permissions, update `AndroidManifest.xml`, requesting UI, and manual test notes.

When changing theme behavior, verify startup application of saved theme before `super.onCreate`.

## Tests

Relevant tests:

- `MainActivitySearchFlowTest` for drawer/settings reload behavior.
- Manual or instrumented testing for speech recognition and runtime permission behavior.
