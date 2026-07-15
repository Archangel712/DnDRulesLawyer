# Custom Resources and Homebrewery Guide

## Scope

Use this guide for creating, editing, deleting, linking, and rendering user-owned resources.

## Key Files

- `presentation/create/CreateResourceFragment.kt`
- `presentation/create/CustomResourceDraft.kt`
- `presentation/create/CustomResourceDetailFactory.kt`
- `presentation/detail/RuleDetailActivity.kt`
- `data/repository/DefaultRulesRepository.kt`
- `domain/model/RuleSource.kt`

## Current Spec

The create screen supports two modes:

- Homebrewery link mode.
- Manual custom resource mode.

Manual custom resources currently have structured forms for spells, monsters, and classes. Other types use generic sections.

Homebrewery resources store:

- Source `HOMEBREWERY`.
- Share URL in raw JSON under `homebrewery_url`.
- A simple section containing the URL.
- No manual edit support.

Custom resources store:

- Source `CUSTOM`.
- Stable local ID with timestamp.
- Structured raw JSON when the renderer can use it.
- Generic `RuleSection` data when no specialized structure exists.

Edit/delete behavior:

- `CUSTOM` resources are editable and deletable.
- `HOMEBREWERY` resources are deletable but not currently editable.
- Official resources are neither editable nor deletable.
- Detail delete returns to search.

Reference linking:

- Spell school, damage type, classes, and subclasses can be linked to existing resources.
- Linked references should preserve `resourceId` where possible.
- Official URL references convert to `official:<endpoint>:<index>` IDs.

## Implementation Rules

When adding a new manual custom form:

1. Add draft models in `CustomResourceDraft.kt`.
2. Extend `CreateResourceFragment` input visibility, validation, build, and populate logic.
3. Extend `CustomResourceDetailFactory`.
4. Reuse renderer-compatible JSON field names when possible.
5. Add tests in `CustomResourceDetailFactoryTest`.
6. Add renderer tests if the new structure has custom detail output.

Avoid storing renderer-only text if structured JSON can preserve intent better.
