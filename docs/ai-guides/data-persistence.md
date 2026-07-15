# Data, Mapping, and Persistence Guide

## Scope

Use this guide for changes to API access, Room storage, mappers, repository behavior, resource IDs, or cached details.

## Key Files

- `data/repository/RulesRepository.kt`
- `data/repository/DefaultRulesRepository.kt`
- `data/repository/RepositoryProvider.kt`
- `data/remote/api/DndApiService.kt`
- `data/remote/mapper/*`
- `data/local/dao/*`
- `data/local/entity/*`
- `data/local/database/DndRulesDatabase.kt`
- `domain/model/*`

## Current Spec

The repository is the only normal app entry point for rule data.

It must support:

- Searching local cached resources.
- Syncing official resources by `ResourceType`.
- Preserving favorites across official sync.
- Loading and caching remote details on first detail open.
- Returning cached detail on later opens.
- Adding, editing, and deleting local user-owned resources.
- Rejecting edits/deletes for resources that are not user-owned.
- Exposing favorite reads and updates.

Official list sync stores lightweight `RuleResource` rows. Detail loading fetches full JSON only when needed.

Class, subclass, race, and subrace details are enriched in `DefaultRulesRepository` before mapping so the renderer can show progression tables, feature details, traits, and subtraits.

## Implementation Rules

When adding a resource type:

1. Add it to `ResourceType`.
2. Confirm the API endpoint.
3. Add search filter grouping only if it belongs in visible filters.
4. Add remote detail rendering if generic output is insufficient.
5. Add mapper/repository tests for ID, sync, detail cache, or enrichment changes.

When changing Room entities:

1. Update `DndRulesDatabase.version`.
2. Prefer a migration if user data must be preserved.
3. Only use destructive migration intentionally and document the tradeoff.
4. Update DAO instrumented tests when query behavior changes.

## Tests

Relevant tests:

- `DefaultRulesRepositorySearchTest`
- `DefaultRulesRepositorySyncAndDetailsTest`
- `DefaultRulesRepositoryCustomRulesTest`
- `LocalRuleMapperTest`
- `LocalRuleDetailMapperTest`
- `RemoteRuleMapperTest`
- `RemoteRuleDetailMapperTest`
- `RuleDaoInstrumentedTest`
