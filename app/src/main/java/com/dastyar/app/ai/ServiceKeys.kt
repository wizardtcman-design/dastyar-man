package com.dastyar.app.ai

import android.content.Context

/**
 * Stores the credentials of every AI service independently, only in this app's
 * private preferences on the device. Nothing is compiled into the APK, written
 * to the source tree or placed in any public file.
 *
 * Each service keeps its own state, so one service being broken never marks
 * another as disconnected:
 *   - OpenRouter    -> chat / text features
 *   - Cloudflare AI -> image generation and editing
 *   - Pollinations  -> image fallback (key optional)
 */
object ServiceKeys {
    private const val PREFS = "dastyar_services"

    // OpenRouter
    private const val OR_KEY = "or_key"
    private const val OR_MODEL = "or_text_model"
    private const val OR_IMAGE_MODEL = "or_image_model"
    private const val OR_SUPPORTS_IMG = "or_supports_img"
    private const val OR_SUPPORTS_EDIT = "or_supports_edit"
    private const val OR_STATE = "or_state"

    // Cloudflare
    private const val CF_ACCOUNT = "cf_account"
    private const val CF_TOKEN = "cf_token"
    private const val CF_MODEL = "cf_model"
    private const val CF_STATE = "cf_state"

    // Pollinations
    private const val PO_KEY = "po_key"
    private const val PO_STATE = "po_state"

    /** A short, honest state per service. */
    enum class State { NOT_SET, CONNECTED, BAD_KEY, NO_CREDIT, FORBIDDEN, RATE_LIMIT, CAPACITY, MODEL, NETWORK, PROVIDER, ERROR }

    @Volatile private var orKey: String? = null
    @Volatile private var orTextModel: String = AiProviders.openRouter.textModel
    @Volatile private var orImageModel: String = AiProviders.openRouter.imageModel
    @Volatile private var orSupportsImg: Boolean = true
    @Volatile private var orSupportsEdit: Boolean = true
    @Volatile private var orState: State = State.NOT_SET

    @Volatile private var cfAccount: String? = null
    @Volatile private var cfToken: String? = null
    @Volatile private var cfModel: String = CloudflareClient.DEFAULT_MODEL.id
    @Volatile private var cfState: State = State.NOT_SET

    @Volatile private var poKey: String? = null
    @Volatile private var poState: State = State.NOT_SET

    // ---------------------------------------------------------- OpenRouter

    fun openRouterKey(): String? = orKey
    fun openRouterReady(): Boolean = orState == State.CONNECTED && !orKey.isNullOrBlank()
    fun openRouterState(): State = orState
    fun openRouterTextModel(): String = orTextModel
    fun openRouterImageModel(): String = orImageModel
    fun openRouterSupportsImage(): Boolean = orSupportsImg && orImageModel.isNotBlank()
    fun openRouterSupportsEdit(): Boolean = orSupportsEdit

    fun saveOpenRouter(ctx: Context, key: String, state: State = State.CONNECTED,
                       imageModel: String? = null, supportsImg: Boolean? = null,
                       supportsEdit: Boolean? = null) {
        val clean = key.trim()
        if (clean.isBlank()) return
        orKey = clean
        orState = state
        imageModel?.let { orImageModel = it }
        supportsImg?.let { orSupportsImg = it }
        supportsEdit?.let { orSupportsEdit = it }
        val e = prefs(ctx).edit()
            .putString(OR_KEY, clean)
            .putString(OR_STATE, state.name)
            .putString(OR_MODEL, orTextModel)
            .putString(OR_IMAGE_MODEL, orImageModel)
            .putBoolean(OR_SUPPORTS_IMG, orSupportsImg)
            .putBoolean(OR_SUPPORTS_EDIT, orSupportsEdit)
        e.apply()
    }

    /** Updates only the discovered capabilities of OpenRouter. */
    fun updateOpenRouterCaps(ctx: Context, imageModel: String, supportsImg: Boolean, supportsEdit: Boolean) {
        orImageModel = imageModel
        orSupportsImg = supportsImg
        orSupportsEdit = supportsEdit
        orState = State.CONNECTED
        prefs(ctx).edit()
            .putString(OR_IMAGE_MODEL, imageModel)
            .putBoolean(OR_SUPPORTS_IMG, supportsImg)
            .putBoolean(OR_SUPPORTS_EDIT, supportsEdit)
            .putString(OR_STATE, State.CONNECTED.name)
            .apply()
    }

    fun setOpenRouterState(ctx: Context, state: State) {
        orState = state
        prefs(ctx).edit().putString(OR_STATE, state.name).apply()
    }

    fun clearOpenRouter(ctx: Context) {
        orKey = null
        orState = State.NOT_SET
        prefs(ctx).edit()
            .remove(OR_KEY).remove(OR_STATE).remove(OR_MODEL)
            .remove(OR_IMAGE_MODEL).remove(OR_SUPPORTS_IMG).remove(OR_SUPPORTS_EDIT)
            .apply()
    }

    // ---------------------------------------------------------- Cloudflare

    fun cloudflareAccount(): String = cfAccount.orEmpty()
    fun cloudflareToken(): String = cfToken.orEmpty()
    fun cloudflareModel(): String = cfModel
    fun cloudflareState(): State = cfState
    fun cloudflareReady(): Boolean =
        cfState == State.CONNECTED && !cfAccount.isNullOrBlank() && !cfToken.isNullOrBlank()
    fun cloudflareConfigured(): Boolean =
        !cfAccount.isNullOrBlank() && !cfToken.isNullOrBlank()

    fun saveCloudflare(ctx: Context, account: String, token: String,
                       model: String = cfModel, state: State = State.CONNECTED) {
        val a = account.trim()
        val t = token.trim()
        if (a.isBlank() || t.isBlank()) return
        cfAccount = a
        cfToken = t
        cfModel = model
        cfState = state
        prefs(ctx).edit()
            .putString(CF_ACCOUNT, a)
            .putString(CF_TOKEN, t)
            .putString(CF_MODEL, model)
            .putString(CF_STATE, state.name)
            .apply()
    }

    fun setCloudflareState(ctx: Context, state: State) {
        cfState = state
        prefs(ctx).edit().putString(CF_STATE, state.name).apply()
    }

    fun setCloudflareModel(ctx: Context, model: String) {
        cfModel = model
        prefs(ctx).edit().putString(CF_MODEL, model).apply()
    }

    fun clearCloudflare(ctx: Context) {
        cfAccount = null
        cfToken = null
        cfState = State.NOT_SET
        prefs(ctx).edit()
            .remove(CF_ACCOUNT).remove(CF_TOKEN).remove(CF_STATE)
            .apply()
    }

    // -------------------------------------------------------- Pollinations

    fun pollinationsKey(): String? = poKey
    fun pollinationsState(): State = poState
    fun setPollinations(ctx: Context, key: String?, state: State) {
        poKey = key?.trim()?.ifBlank { null }
        poState = state
        prefs(ctx).edit()
            .putString(PO_KEY, poKey)
            .putString(PO_STATE, state.name)
            .apply()
    }

    fun clearPollinations(ctx: Context) {
        poKey = null
        poState = State.NOT_SET
        prefs(ctx).edit().remove(PO_KEY).remove(PO_STATE).apply()
    }

    // ----------------------------------------------------------------- load

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun stateOf(v: String?): State =
        runCatching { State.valueOf(v ?: "") }.getOrDefault(State.NOT_SET)

    /** Restores every service and its capabilities; called once at startup. */
    fun load(ctx: Context) {
        val p = prefs(ctx)

        orKey = p.getString(OR_KEY, null)?.trim()?.ifBlank { null }
        orTextModel = p.getString(OR_MODEL, null)?.ifBlank { null } ?: AiProviders.openRouter.textModel
        orImageModel = p.getString(OR_IMAGE_MODEL, null)?.ifBlank { null }
            ?: AiProviders.openRouter.imageModel
        orSupportsImg = p.getBoolean(OR_SUPPORTS_IMG, true)
        orSupportsEdit = p.getBoolean(OR_SUPPORTS_EDIT, true)
        orState = if (orKey == null) State.NOT_SET else stateOf(p.getString(OR_STATE, null))

        cfAccount = p.getString(CF_ACCOUNT, null)?.trim()?.ifBlank { null }
        cfToken = p.getString(CF_TOKEN, null)?.trim()?.ifBlank { null }
        cfModel = p.getString(CF_MODEL, null)?.ifBlank { null } ?: CloudflareClient.DEFAULT_MODEL.id
        cfState = if (cfAccount == null || cfToken == null) State.NOT_SET
        else stateOf(p.getString(CF_STATE, null))

        poKey = p.getString(PO_KEY, null)?.trim()?.ifBlank { null }
        poState = stateOf(p.getString(PO_STATE, null))
    }

    /** True when at least one real service is connected, so the gate can close. */
    fun anyConnected(): Boolean = openRouterReady() || cloudflareReady() || poState == State.CONNECTED
}
