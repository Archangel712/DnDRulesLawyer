package com.example.dnd_ruleslawyer.presentation.detail

import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.presentation.utils.escapeHtml
import com.google.gson.JsonObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RuleDetailLink {
    const val SCHEME = "dndruleslawyer"
    private const val HOST_RESOURCE = "resource"

    fun hrefForResourceId(resourceId: String): String =
        "$SCHEME://$HOST_RESOURCE?id=${resourceId.urlEncode()}"

    fun hrefForOfficialReference(reference: JsonObject, fallbackEndpoint: String? = null): String? {
        reference.stringValue("id")
            ?.takeIf { id -> id.contains(":") }
            ?.let { id -> return hrefForResourceId(id) }

        val endpointAndIndex = endpointAndIndexFromUrl(reference.stringValue("url"))
            ?: fallbackEndpoint?.let { endpoint ->
                reference.stringValue("index")?.let { index -> endpoint to index }
            }
            ?: return null

        val (endpoint, index) = endpointAndIndex
        ResourceType.fromEndpoint(endpoint) ?: return null

        return hrefForResourceId("official:$endpoint:$index")
    }

    fun linkHtml(text: String, href: String?): String {
        val escapedText = text.escapeHtml()
        if (href.isNullOrBlank()) return escapedText

        return """<a class="resource-link" href="${href.escapeHtml()}">$escapedText</a>"""
    }

    fun linkedReferenceHtml(reference: JsonObject, fallbackEndpoint: String? = null): String {
        val name = reference.stringValue("name") ?: reference.stringValue("index") ?: return ""
        return linkHtml(name, hrefForOfficialReference(reference, fallbackEndpoint))
    }

    fun linkedReferencesHtml(references: List<JsonObject>, fallbackEndpoint: String? = null): String =
        references
            .mapNotNull { reference ->
                linkedReferenceHtml(reference, fallbackEndpoint).takeIf { html -> html.isNotBlank() }
            }
            .joinToString(", ")

    fun resourceIdFromUri(uri: String): String? {
        val prefix = "$SCHEME://$HOST_RESOURCE?"
        if (!uri.startsWith(prefix)) return null

        return uri
            .removePrefix(prefix)
            .split("&")
            .mapNotNull { parameter ->
                val parts = parameter.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .firstOrNull { (key) -> key == "id" }
            ?.second
            ?.urlDecode()
            ?.takeIf { id -> id.isNotBlank() }
    }

    private fun endpointAndIndexFromUrl(url: String?): Pair<String, String>? {
        if (url.isNullOrBlank()) return null

        val segments = url
            .trim()
            .split("/")
            .filter { segment -> segment.isNotBlank() }

        val apiIndex = segments.indexOf("api")
        if (apiIndex < 0) return null

        val endpointIndex = if (segments.getOrNull(apiIndex + 1) == "2014") {
            apiIndex + 2
        } else {
            apiIndex + 1
        }

        val endpoint = segments.getOrNull(endpointIndex) ?: return null
        val index = segments.getOrNull(endpointIndex + 1) ?: return null

        return endpoint to index
    }

    private fun JsonObject.stringValue(name: String): String? =
        get(name)
            ?.takeIf { value -> !value.isJsonNull && value.isJsonPrimitive }
            ?.asString

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun String.urlDecode(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}
