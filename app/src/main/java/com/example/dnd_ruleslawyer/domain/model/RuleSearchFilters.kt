package com.example.dnd_ruleslawyer.domain.model

data class RuleSearchFilters(
    val resourceTypes: Set<ResourceType> = emptySet(),
    val sources: Set<RuleSource> = emptySet(),
    val favoritesOnly: Boolean = false
) {
    fun matches(resource: RuleResource): Boolean {
        val typeMatches = resourceTypes.isEmpty() || resource.type in resourceTypes
        val sourceMatches = sources.isEmpty() || resource.source in sources
        val favoriteMatches = !favoritesOnly || resource.isFavorite

        return typeMatches && sourceMatches && favoriteMatches
    }
}
