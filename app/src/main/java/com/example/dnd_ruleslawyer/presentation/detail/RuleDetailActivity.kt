package com.example.dnd_ruleslawyer.presentation.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.dnd_ruleslawyer.MainActivity
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.core.json.string
import com.example.dnd_ruleslawyer.databinding.ActivityRuleDetailBinding
import com.example.dnd_ruleslawyer.domain.model.ResourceType
import com.example.dnd_ruleslawyer.domain.model.RuleSource
import com.example.dnd_ruleslawyer.presentation.UIEntryPoint
import com.example.dnd_ruleslawyer.presentation.loading.LoadingOverlayController
import com.example.dnd_ruleslawyer.presentation.utils.rawJsonObject
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RuleDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRuleDetailBinding
    private lateinit var loadingOverlayController: LoadingOverlayController
    private val density by lazy { resources.displayMetrics.density }
    private var currentDetailScale = 0f

    private val htmlRenderer by lazy {
        UIEntryPoint.createRuleDetailRenderer()
    }

    private val repository by lazy {
        UIEntryPoint.rulesRepository(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadingOverlayController = LoadingOverlayController(
            root = binding.detailLoadingOverlay.root,
            flavorTextView = binding.detailLoadingOverlay.loadingFlavorText,
            flavorTexts = resources.getStringArray(R.array.loading_flavor_texts).toList()
        )

        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)

        if (ruleId == null) {
            finish()
            return
        }

        loadRuleDetail(ruleId)
    }

    private fun loadRuleDetail(ruleId: String) {
        showLoadingOverlay()
        lifecycleScope.launch {
            val resource = repository.getRuleResource(ruleId)
            configureUserOwnedResourceActions(ruleId, resource?.source)

            val detail = runCatching {
                repository.getRuleDetail(ruleId)
            }.getOrNull()

            configureDetailWebView(isRemoteHomebrewery = false)

            if (detail?.resource?.source == RuleSource.HOMEBREWERY) {
                val url = detail.rawJsonObject()
                    ?.string("homebrewery_url")
                    ?.normalizeHomebreweryUrl()

                if (!url.isNullOrBlank()) {
                    configureDetailWebView(isRemoteHomebrewery = true)
                    binding.detailWebView.visibility = View.INVISIBLE
                    binding.detailWebView.loadUrl(url)
                    return@launch
                }
            }

            val html = if (detail != null) {
                htmlRenderer.render(detail)
            } else {
                htmlRenderer.renderMissing(resource?.name ?: "Unknown rule")
            }

            binding.detailWebView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private fun configureUserOwnedResourceActions(ruleId: String, source: RuleSource?) {
        val canEdit = source == RuleSource.CUSTOM
        val canDelete = source == RuleSource.CUSTOM || source == RuleSource.HOMEBREWERY

        if (!canDelete) {
            binding.customResourceActionsContainer.visibility = View.GONE
            binding.editResourceButton.setOnClickListener(null)
            binding.deleteResourceButton.setOnClickListener(null)
            return
        }

        binding.customResourceActionsContainer.visibility = View.VISIBLE
        binding.editResourceButton.visibility = if (canEdit) View.VISIBLE else View.GONE
        updateDeleteButtonSpacing(canEdit)

        if (canEdit) {
            binding.editResourceButton.setOnClickListener {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_EDIT_RESOURCE_ID, ruleId)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
                finish()
            }
        } else {
            binding.editResourceButton.setOnClickListener(null)
        }

        binding.deleteResourceButton.setOnClickListener {
            showDeleteConfirmation(ruleId)
        }
    }

    private fun updateDeleteButtonSpacing(hasEditButton: Boolean) {
        val layoutParams = binding.deleteResourceButton.layoutParams as? LinearLayout.LayoutParams ?: return
        layoutParams.marginStart = if (hasEditButton) {
            resources.getDimensionPixelSize(R.dimen.resource_action_spacing)
        } else {
            0
        }
        binding.deleteResourceButton.layoutParams = layoutParams
    }

    private fun showDeleteConfirmation(ruleId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_resource_confirm_title)
            .setMessage(R.string.delete_resource_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_resource) { _, _ ->
                deleteUserOwnedResource(ruleId)
            }
            .show()
    }

    private fun deleteUserOwnedResource(ruleId: String) {
        binding.deleteResourceButton.isEnabled = false

        lifecycleScope.launch {
            val deleted = runCatching {
                repository.deleteLocalRule(ruleId)
            }.getOrDefault(false)

            if (deleted) {
                returnToSearch()
            } else {
                binding.deleteResourceButton.isEnabled = true
                Toast.makeText(
                    this@RuleDetailActivity,
                    R.string.delete_resource_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun returnToSearch() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_SHOW_SEARCH, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureDetailWebView(isRemoteHomebrewery: Boolean) {
        with(binding.detailWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = isRemoteHomebrewery
            databaseEnabled = isRemoteHomebrewery
            loadsImagesAutomatically = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            useWideViewPort = true
            loadWithOverviewMode = !isRemoteHomebrewery

            if (isRemoteHomebrewery) {
                userAgentString = "$userAgentString DnDRulesLawyerWebView"
            }
        }
        binding.detailWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.detailWebView.visibility = if (isRemoteHomebrewery) View.INVISIBLE else View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isRemoteHomebrewery) {
                    applyHomebreweryReaderMode(view)
                    return
                }

                if (view == null) {
                    hideLoadingOverlay()
                    return
                }

                view.postDelayed({
                    view.let { fitContentWidth(it) { hideLoadingOverlay() } }
                }, 100)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                if (request?.isForMainFrame == true) {
                    val message = error?.description?.toString().orEmpty()
                    view?.loadDataWithBaseURL(
                        null,
                        htmlRenderer.renderMissing("Could not load Homebrewery page\n$message"),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    binding.detailWebView.visibility = View.VISIBLE
                    hideLoadingOverlay()
                }
            }

            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                currentDetailScale = newScale

                if (!isRemoteHomebrewery && newScale > MAX_DETAIL_SCALE) {
                    view?.post { view.zoomBy(MAX_DETAIL_SCALE / newScale) }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return !isRemoteHomebrewery && handleLinkedResourceUrl(url)
            }
        }
    }

    private fun handleLinkedResourceUrl(url: String): Boolean {
        val resourceId = RuleDetailLink.resourceIdFromUri(url) ?: return false

        lifecycleScope.launch {
            val canOpen = ensureLinkedResourceAvailable(resourceId)

            if (canOpen) {
                startActivity(UIEntryPoint.createRuleDetailIntent(this@RuleDetailActivity, resourceId))
            } else {
                Toast.makeText(
                    this@RuleDetailActivity,
                    R.string.linked_resource_missing,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return true
    }

    private suspend fun ensureLinkedResourceAvailable(resourceId: String): Boolean {
        if (repository.getRuleResource(resourceId) != null) return true

        val officialType = officialResourceTypeFromId(resourceId) ?: return false

        runCatching {
            repository.syncOfficialResources(officialType)
        }

        return repository.getRuleResource(resourceId) != null
    }

    private fun officialResourceTypeFromId(resourceId: String): ResourceType? {
        val parts = resourceId.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != "official") return null

        return ResourceType.fromEndpoint(parts[1])
    }

    private fun applyHomebreweryReaderMode(webView: WebView?, attempt: Int = 0) {
        val view = webView ?: run {
            binding.detailWebView.visibility = View.VISIBLE
            hideLoadingOverlay()
            return
        }

        val delay = if (attempt == 0) {
            HOMEBREWERY_READER_INITIAL_DELAY_MS
        } else {
            HOMEBREWERY_READER_RETRY_DELAY_MS
        }

        view.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            view.evaluateJavascript(HomebreweryReaderScript.EXTRACT_RENDERED_CONTENT) { result ->
                if (isFinishing || isDestroyed) return@evaluateJavascript

                if (result?.contains("ok") == true || attempt >= HOMEBREWERY_READER_MAX_ATTEMPTS) {
                    binding.detailWebView.visibility = View.VISIBLE
                    hideLoadingOverlay()
                } else {
                    applyHomebreweryReaderMode(view, attempt + 1)
                }
            }
        }, delay)
    }

    private fun fitContentWidth(webView: WebView, onComplete: () -> Unit) {
        webView.post {
            webView.evaluateJavascript("document.documentElement.scrollWidth") { widthStr ->
                val contentWidthCss = widthStr?.toFloatOrNull()
                val availableWidthPx = (webView.width - webView.paddingLeft - webView.paddingRight).toFloat()

                if (contentWidthCss == null || contentWidthCss <= 0f || availableWidthPx <= 0f) {
                    onComplete()
                    return@evaluateJavascript
                }

                // Use current scale if known, otherwise fallback to density
                val currentScale = if (currentDetailScale > 0) currentDetailScale else density

                // Target scale to fit width, capped at density (100% zoom)
                val targetScale = (availableWidthPx / contentWidthCss).coerceAtMost(density)

                val zoomFactor = targetScale / currentScale

                if (zoomFactor.isFinite() && zoomFactor > 0f && Math.abs(zoomFactor - 1f) > 0.01f) {
                    webView.zoomBy(zoomFactor)
                }
                onComplete()
            }
        }
    }

    private fun showLoadingOverlay() {
        loadingOverlayController.show()
    }

    private fun hideLoadingOverlay() {
        loadingOverlayController.hide()
    }

    override fun onDestroy() {
        if (::loadingOverlayController.isInitialized) {
            loadingOverlayController.destroy()
        }
        super.onDestroy()
    }

    private fun String.normalizeHomebreweryUrl(): String {
        val trimmed = trim()
        if (trimmed.isBlank()) return trimmed

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = withScheme.toUri()
        val host = uri.host.orEmpty()

        return if (host == "homebrewery.naturalcrit.com" && uri.path.orEmpty().startsWith("/share/")) {
            uri.toString()
        } else {
            withScheme
        }
    }

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
        private const val MAX_DETAIL_SCALE = 8.0f
        private const val HOMEBREWERY_READER_INITIAL_DELAY_MS = 800L
        private const val HOMEBREWERY_READER_RETRY_DELAY_MS = 500L
        private const val HOMEBREWERY_READER_MAX_ATTEMPTS = 24
    }
}
