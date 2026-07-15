# Search, Sync, and Favorites Guide

## Scope

Use this guide for search ranking, filters, initial official sync, reload behavior, favorites, and search/favorites UI flows.

## Key Files

- `presentation/search/SearchFragment.kt`
- `presentation/search/SearchFilterGroup.kt`
- `presentation/search/RuleResourceAdapter.kt`
- `presentation/favorites/FavoritesFragment.kt`
- `presentation/favorites/CompactRuleResourceAdapter.kt`
- `presentation/settings/SettingsFragment.kt`
- `data/repository/DefaultRulesRepository.kt`
- `domain/model/RuleSearchFilters.kt`

## Current Spec

Search starts from cached local resources. `SearchFragment` syncs default official resource types on first load if missing.

Search behavior:

- Trim the query.
- Empty search in UI shows no results.
- Repository empty query may return all filtered resources for internal use.
- Type aliases like `spell`, `spells`, `class`, and endpoint names should work.
- Whole phrase name matches outrank loose matches.
- Type matches outrank broad matches.
- Fuzzy matching supports small typos for longer terms.
- Results sort favorites first, then name, through DAO ordering.

Filters are represented as `SearchFilterGroup`, then converted into `RuleSearchFilters`.

Favorites behavior:

- Favorites are stored on `RuleResource`.
- Official sync must keep favorite state.
- Search can toggle favorites inline.
- Favorites screen can remove favorites or search for non-favorites to add.
- Favorites screen applies the same type filter groups as search.

Settings reload:

- Reloads all `ResourceType.syncByDefault` official resources.
- Shows success only if all reloads succeed.
- Does not clear custom or Homebrewery resources.

## Implementation Rules

Do not put search ranking in fragments. Ranking belongs in the repository or a domain helper extracted from it.

Do not duplicate filter meaning in multiple screens. Extend `SearchFilterGroup` and `RuleSearchFilters`.

If adding source filters or favorites-only UI, update `RuleSearchFilters.matches` tests.

## Tests

Relevant tests:

- `RuleSearchFiltersTest`
- `DefaultRulesRepositorySearchTest`
- `DefaultRulesRepositorySyncAndDetailsTest`
- `DefaultRulesRepositoryCustomRulesTest`
- `MainActivitySearchFlowTest`
