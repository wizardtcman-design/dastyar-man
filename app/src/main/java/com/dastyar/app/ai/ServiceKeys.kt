package com.dastyar.app.ai

import android.content.Context

/**
 * Stores the credentials of every AI service independently, only in this app's
 * private preferences on the device. Nothing is compiled into the APK, written
 * to the source tree or placed in any public file.
 *
 * Each service keeps its own state, so one service being broken never marks
 * another as disconnected:
 *   - Text provider -> chat / text features; 1xAI, OpenRouter, Cloudflare,
 *                      Atria or Groq, chosen by the user, each with its
 *                      own key and model
 *   - Cloudflare AI -> image generation and editing
 *   - Pollinations  -> image fallback (key optional)
 */
object ServiceKeys {
    private const val PREFS = "dastyar_services"

    /** Which provider serves chat/text. One of [AiProviders.textProviders]. */
    private const val TEXT_PROVIDER = "text_provider"

    // OpenRouter
    private const val OR_KEY = "or_key"
    private const val OR_MODEL = "or_text_model"
    private const val OR_IMAGE_MODEL = "or_image_model"
    private const val OR_SUPPORTS_IMG = "or_supports_img"
    private const val OR_SUPPORTS_EDIT = "or_supports_edit"
    private const val OR_STATE = "or_state"

    // 1xAI (1xai.ir)
    private const val XAI_KEY = "xai_key"
    private const val XAI_MODEL = "xai_text_model"
    private const val XAI_STATE = "xai_state"
    // Chosen 1xAI image model for "generate image"; empty = automatic
    // (Cloudflare -> Pollinations -> OpenRouter), the pre-existing behaviour.
    private const val XAI_IMAGE_MODEL = "xai_image_model"

    // Atria (Atria Dawn Preview)
    private const val AT_KEY = "at_key"
    private const val AT_MODEL = "at_text_model"
    private const val AT_STATE = "at_state"

    // Groq (GPT-OSS 120B / 20B)
    private const val GQ_KEY = "gq_key"
    private const val GQ_MODEL = "gq_text_model"
    private const val GQ_STATE = "gq_state"

    // (CodeCraft, kie.ai and Aion Labs were removed; their key slots are gone.)

    // Cloudflare (image account)
    private const val CF_ACCOUNT = "cf_account"
    private const val CF_TOKEN = "cf_token"
    private const val CF_MODEL = "cf_model"
    private const val CF_STATE = "cf_state"

    // Cloudflare (text account) -- a separate account for the text model, so the
    // image account's settings and daily quota are never touched.
    private const val CFT_ACCOUNT = "cft_account"
    private const val CFT_TOKEN = "cft_token"
    private const val CFT_MODEL = "cft_text_model"
    private const val CFT_STATE = "cft_state"

    // Pollinations
    private const val PO_KEY = "po_key"
    private const val PO_STATE = "po_state"

    /** A short, honest state per service. */
    enum class State { NOT_SET, CONNECTED, BAD_KEY, NO_CREDIT, FORBIDDEN, RATE_LIMIT, CAPACITY, MODEL, NETWORK, PROVIDER, ERROR }

    @Volatile private var textProviderId: String = AiProviders.openRouter.id

    @Volatile private var orKey: String? = null
    @Volatile private var orTextModel: String = AiProviders.openRouter.textModel
    @Volatile private var orImageModel: String = AiProviders.openRouter.imageModel
    @Volatile private var orSupportsImg: Boolean = true
    @Volatile private var orSupportsEdit: Boolean = true
    @Volatile private var orState: State = State.NOT_SET

    @Volatile private var xaiKey: String? = null
    @Volatile private var xaiTextModel: String = AiProviders.oneXai.textModel
    @Volatile private var xaiState: State = State.NOT_SET
    @Volatile private var xaiImageModel: String = ""

    @Volatile private var atKey: String? = null
    @Volatile private var atTextModel: String = AiProviders.atria.textModel
    @Volatile private var atState: State = State.NOT_SET

    @Volatile private var gqKey: String? = null
    @Volatile private var gqTextModel: String = AiProviders.groq.textModel
    @Volatile private var gqState: State = State.NOT_SET

    @Volatile private var cfAccount: String? = null
    @Volatile private var cfToken: String? = null
    @Volatile private var cfModel: String = CloudflareClient.DEFAULT_MODEL.id
    @Volatile private var cfState: State = State.NOT_SET

    @Volatile private var cftAccount: String? = null
    @Volatile private var cftToken: String? = null
    @Volatile private var cftModel: String = AiProviders.cloudflareText.textModel
    @Volatile private var cftState: State = State.NOT_SET

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

    /**
     * Remembers the text model that actually answered. Used when the configured
     * model is unaffordable on a free account and a free model took over, so the
     * app keeps using the one that demonstrably works.
     */
    fun setOpenRouterTextModel(ctx: Context, model: String) {
        if (model.isBlank()) return
        orTextModel = model
        prefs(ctx).edit().putString(OR_MODEL, model).apply()
    }

    fun clearOpenRouter(ctx: Context) {
        orKey = null
        orState = State.NOT_SET
        prefs(ctx).edit()
            .remove(OR_KEY).remove(OR_STATE).remove(OR_MODEL)
            .remove(OR_IMAGE_MODEL).remove(OR_SUPPORTS_IMG).remove(OR_SUPPORTS_EDIT)
            .apply()
    }

    // --------------------------------------------------------------- 1xAI

    fun oneXaiKey(): String? = xaiKey
    fun oneXaiTextModel(): String = xaiTextModel
    fun oneXaiState(): State = xaiState
    fun oneXaiReady(): Boolean = xaiState == State.CONNECTED && !xaiKey.isNullOrBlank()

    /**
     * The 1xAI image model chosen for the "generate image" box, or empty for the
     * automatic Cloudflare -> Pollinations route. Only these two documented 1xAI
     * image models are ever stored, so a stale or text model id can never leak
     * into the image endpoint.
     */
    fun oneXaiImageModel(): String = xaiImageModel

    fun setOneXaiImageModel(ctx: Context, model: String) {
        xaiImageModel = model.takeIf { m -> OneXaiImage.models.any { it.first == m } }.orEmpty()
        prefs(ctx).edit().putString(XAI_IMAGE_MODEL, xaiImageModel).apply()
    }

    fun saveOneXai(ctx: Context, key: String, state: State = State.CONNECTED,
                   textModel: String? = null) {
        val clean = key.trim()
        if (clean.isBlank()) return
        xaiKey = clean
        xaiState = state
        textModel?.takeIf { it.isNotBlank() }?.let { xaiTextModel = it }
        prefs(ctx).edit()
            .putString(XAI_KEY, clean)
            .putString(XAI_MODEL, xaiTextModel)
            .putString(XAI_STATE, state.name)
            .apply()
    }

    fun setOneXaiState(ctx: Context, state: State) {
        xaiState = state
        prefs(ctx).edit().putString(XAI_STATE, state.name).apply()
    }

    fun setOneXaiTextModel(ctx: Context, model: String) {
        if (model.isBlank()) return
        xaiTextModel = model
        prefs(ctx).edit().putString(XAI_MODEL, model).apply()
    }

    fun clearOneXai(ctx: Context) {
        xaiKey = null
        xaiState = State.NOT_SET
        prefs(ctx).edit().remove(XAI_KEY).remove(XAI_MODEL).remove(XAI_STATE).apply()
    }

    // --------------------------------------------------------------- Atria

    fun atriaKey(): String? = atKey
    fun atriaTextModel(): String = atTextModel
    fun atriaState(): State = atState
    fun atriaReady(): Boolean = atState == State.CONNECTED && !atKey.isNullOrBlank()

    fun saveAtria(ctx: Context, key: String, state: State = State.CONNECTED,
                  textModel: String? = null) {
        val clean = key.trim()
        if (clean.isBlank()) return
        atKey = clean
        atState = state
        textModel?.takeIf { it.isNotBlank() }?.let { atTextModel = it }
        prefs(ctx).edit()
            .putString(AT_KEY, clean)
            .putString(AT_MODEL, atTextModel)
            .putString(AT_STATE, state.name)
            .apply()
    }

    fun setAtriaState(ctx: Context, state: State) {
        atState = state
        prefs(ctx).edit().putString(AT_STATE, state.name).apply()
    }

    fun setAtriaTextModel(ctx: Context, model: String) {
        if (model.isBlank()) return
        atTextModel = model
        prefs(ctx).edit().putString(AT_MODEL, model).apply()
    }

    fun clearAtria(ctx: Context) {
        atKey = null
        atState = State.NOT_SET
        prefs(ctx).edit().remove(AT_KEY).remove(AT_MODEL).remove(AT_STATE).apply()
    }

    // ----------------------------------------------------------------- Groq

    fun groqKey(): String? = gqKey
    fun groqTextModel(): String = gqTextModel
    fun groqState(): State = gqState
    fun groqReady(): Boolean = gqState == State.CONNECTED && !gqKey.isNullOrBlank()

    fun saveGroq(ctx: Context, key: String, state: State = State.CONNECTED,
                  textModel: String? = null) {
        val clean = key.trim()
        if (clean.isBlank()) return
        gqKey = clean
        gqState = state
        textModel?.takeIf { it.isNotBlank() }?.let { gqTextModel = it }
        prefs(ctx).edit()
            .putString(GQ_KEY, clean)
            .putString(GQ_MODEL, gqTextModel)
            .putString(GQ_STATE, state.name)
            .apply()
    }

    fun setGroqState(ctx: Context, state: State) {
        gqState = state
        prefs(ctx).edit().putString(GQ_STATE, state.name).apply()
    }

    fun setGroqTextModel(ctx: Context, model: String) {
        if (model.isBlank()) return
        gqTextModel = model
        prefs(ctx).edit().putString(GQ_MODEL, model).apply()
    }

    fun clearGroq(ctx: Context) {
        gqKey = null
        gqState = State.NOT_SET
        prefs(ctx).edit().remove(GQ_KEY).remove(GQ_MODEL).remove(GQ_STATE).apply()
    }

    // ------------------------------------------------------ text provider

    /**
     * The provider that currently serves chat/text. Falls back to OpenRouter
     * when the stored id is unknown, so a removed provider never leaves the app
     * without a text service.
     */
    fun textProvider(): AiProvider = AiProviders.byId(textProviderId)

    fun textProviderId(): String = textProvider().id

    /**
     * Switches the chat/text provider. The key entered for the new provider is
     * used from now on; the other provider's key is kept so switching back does
     * not require re-entering it.
     */
    fun setTextProvider(ctx: Context, providerId: String) {
        val p = AiProviders.byId(providerId)
        textProviderId = p.id
        prefs(ctx).edit().putString(TEXT_PROVIDER, p.id).apply()
    }

    /** The key of any text provider, or empty when that provider has none. */
    fun keyFor(provider: AiProvider): String =
        when (provider.id) {
            AiProviders.oneXai.id -> xaiKey.orEmpty()
            AiProviders.atria.id -> atKey.orEmpty()
            AiProviders.groq.id -> gqKey.orEmpty()
            AiProviders.cloudflareText.id -> cftToken.orEmpty()
            else -> orKey.orEmpty()
        }

    /** The stored chat model of any text provider. */
    fun modelFor(provider: AiProvider): String =
        when (provider.id) {
            AiProviders.oneXai.id -> xaiTextModel
            AiProviders.atria.id -> atTextModel
            AiProviders.groq.id -> gqTextModel
            AiProviders.cloudflareText.id -> cftModel
            else -> orTextModel
        }

    /** True when a specific text provider is connected and verified. */
    fun isReady(provider: AiProvider): Boolean =
        when (provider.id) {
            AiProviders.oneXai.id -> oneXaiReady()
            AiProviders.atria.id -> atriaReady()
            AiProviders.groq.id -> groqReady()
            AiProviders.cloudflareText.id -> cloudflareTextReady()
            else -> openRouterReady()
        }

    /** The key of the selected text provider, or empty when none is set. */
    fun textKey(): String = keyFor(textProvider())

    /** The stored chat model of the selected text provider. */
    fun textModel(): String = modelFor(textProvider())

    /** State of the selected text provider. */
    fun textState(): State =
        when (textProvider().id) {
            AiProviders.oneXai.id -> xaiState
            AiProviders.atria.id -> atState
            AiProviders.groq.id -> gqState
            AiProviders.cloudflareText.id -> cftState
            else -> orState
        }

    /** True when the selected text provider is connected and verified. */
    fun textReady(): Boolean = isReady(textProvider())

    /**
     * The text providers the user has actually configured and verified, in the
     * order shown to the user. Only these appear in the in-chat model chooser, so
     * a removed or unconfigured provider is never offered.
     */
    fun readyTextProviders(): List<AiProvider> =
        AiProviders.textProviders.filter { isReady(it) }

    /** Saves a key + model for whichever provider serves text right now. */
    fun saveTextProvider(ctx: Context, key: String, model: String, state: State = State.CONNECTED) {
        when (textProvider().id) {
            AiProviders.oneXai.id -> saveOneXai(ctx, key, state, model)
            AiProviders.atria.id -> saveAtria(ctx, key, state, model)
            AiProviders.groq.id -> saveGroq(ctx, key, state, model)
            // Cloudflare text needs an account as well as a token; when one is
            // already stored, saving a new key keeps it. The dedicated
            // saveCloudflareText call is used by the screens so both are set.
            AiProviders.cloudflareText.id ->
                saveCloudflareText(ctx, cftAccount.orEmpty(), key, model, state)
            else -> saveOpenRouter(ctx, key, state)
        }
    }

    fun setTextProviderState(ctx: Context, state: State) {
        when (textProvider().id) {
            AiProviders.oneXai.id -> setOneXaiState(ctx, state)
            AiProviders.atria.id -> setAtriaState(ctx, state)
            AiProviders.groq.id -> setGroqState(ctx, state)
            AiProviders.cloudflareText.id -> setCloudflareTextState(ctx, state)
            else -> setOpenRouterState(ctx, state)
        }
    }

    fun setTextModel(ctx: Context, model: String) {
        when (textProvider().id) {
            AiProviders.oneXai.id -> setOneXaiTextModel(ctx, model)
            AiProviders.atria.id -> setAtriaTextModel(ctx, model)
            AiProviders.groq.id -> setGroqTextModel(ctx, model)
            AiProviders.cloudflareText.id -> setCloudflareTextModel(ctx, model)
            else -> setOpenRouterTextModel(ctx, model)
        }
    }

    /** Clears the key of whichever provider serves text right now. */
    fun clearTextProvider(ctx: Context) {
        when (textProvider().id) {
            AiProviders.oneXai.id -> clearOneXai(ctx)
            AiProviders.atria.id -> clearAtria(ctx)
            AiProviders.groq.id -> clearGroq(ctx)
            AiProviders.cloudflareText.id -> clearCloudflareText(ctx)
            else -> clearOpenRouter(ctx)
        }
    }

    // ------------------------------------------- per-conversation model

    private const val CHAT_MODEL_PREFIX = "chat_model_"

    @Volatile private var conversationModels: Map<String, String> = emptyMap()

    /**
     * The provider + model the user picked for one conversation (a chat
     * channel), or null when the conversation should use the app defaults.
     *
     * Both are stored together so a choice made in one conversation is fully
     * independent: switching a model in one chat never changes another, and a
     * provider that was later removed simply stops being offered.
     */
    fun conversationChoice(channel: String): Pair<AiProvider, String>? {
        val stored = conversationModels[channel] ?: return null
        val sep = stored.indexOf('\u001F')
        if (sep <= 0) return null
        val providerId = stored.substring(0, sep)
        val model = stored.substring(sep + 1)
        if (model.isBlank()) return null
        val provider = AiProviders.all.firstOrNull { it.id == providerId } ?: return null
        return provider to model
    }

    /**
     * The model of the conversation's own choice, or empty when the conversation
     * uses the default. Kept for screens that only need the model name.
     */
    fun conversationModel(channel: String): String =
        conversationChoice(channel)?.second.orEmpty()

    /** Remembers the provider + model for one conversation. */
    fun setConversationChoice(ctx: Context, channel: String, provider: AiProvider, model: String) {
        val key = CHAT_MODEL_PREFIX + channel
        val clean = model.trim()
        val map = conversationModels.toMutableMap()
        val e = prefs(ctx).edit()
        if (clean.isBlank()) {
            map.remove(channel)
            e.remove(key)
        } else {
            val value = provider.id + '\u001F' + clean
            map[channel] = value
            e.putString(key, value)
        }
        e.apply()
        conversationModels = map
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

    // ------------------------------------------------ Cloudflare text account

    /** The separate Cloudflare account used only for the text model. */
    fun cloudflareTextAccount(): String = cftAccount.orEmpty()
    fun cloudflareTextToken(): String = cftToken.orEmpty()
    fun cloudflareTextModel(): String = cftModel
    fun cloudflareTextState(): State = cftState
    fun cloudflareTextConfigured(): Boolean =
        !cftAccount.isNullOrBlank() && !cftToken.isNullOrBlank()
    fun cloudflareTextReady(): Boolean =
        cftState == State.CONNECTED && cloudflareTextConfigured()

    fun saveCloudflareText(
        ctx: Context,
        account: String,
        token: String,
        model: String = cftModel,
        state: State = State.CONNECTED
    ) {
        val a = account.trim()
        val t = token.trim()
        if (a.isBlank() || t.isBlank()) return
        cftAccount = a
        cftToken = t
        cftModel = model.ifBlank { AiProviders.cloudflareText.textModel }
        cftState = state
        prefs(ctx).edit()
            .putString(CFT_ACCOUNT, a)
            .putString(CFT_TOKEN, t)
            .putString(CFT_MODEL, cftModel)
            .putString(CFT_STATE, state.name)
            .apply()
    }

    fun setCloudflareTextState(ctx: Context, state: State) {
        cftState = state
        prefs(ctx).edit().putString(CFT_STATE, state.name).apply()
    }

    fun setCloudflareTextModel(ctx: Context, model: String) {
        if (model.isBlank()) return
        cftModel = model
        prefs(ctx).edit().putString(CFT_MODEL, model).apply()
    }

    fun clearCloudflareText(ctx: Context) {
        cftAccount = null
        cftToken = null
        cftState = State.NOT_SET
        prefs(ctx).edit()
            .remove(CFT_ACCOUNT).remove(CFT_TOKEN).remove(CFT_STATE)
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

    private fun prefs(ctx: Context) =
        com.dastyar.app.security.SecurePrefs.get(ctx, PREFS)

    private fun stateOf(v: String?): State =
        runCatching { State.valueOf(v ?: "") }.getOrDefault(State.NOT_SET)

    /** Restores every service and its capabilities; called once at startup. */
    fun load(ctx: Context) {
        // One-time move of any plaintext key written by an older version into
        // the encrypted store, before anything is read.
        com.dastyar.app.security.SecurePrefs.migrateFromPlain(
            ctx, PREFS,
            listOf(OR_KEY, XAI_KEY, AT_KEY, GQ_KEY,
                CF_ACCOUNT, CF_TOKEN, CFT_ACCOUNT, CFT_TOKEN, PO_KEY)
        )
        val p = prefs(ctx)

        orKey = p.getString(OR_KEY, null)?.trim()?.ifBlank { null }
        orTextModel = p.getString(OR_MODEL, null)?.ifBlank { null } ?: AiProviders.openRouter.textModel
        orImageModel = p.getString(OR_IMAGE_MODEL, null)?.ifBlank { null }
            ?: AiProviders.openRouter.imageModel
        orSupportsImg = p.getBoolean(OR_SUPPORTS_IMG, true)
        orSupportsEdit = p.getBoolean(OR_SUPPORTS_EDIT, true)
        orState = if (orKey == null) State.NOT_SET else stateOf(p.getString(OR_STATE, null))

        xaiKey = p.getString(XAI_KEY, null)?.trim()?.ifBlank { null }
        xaiTextModel = p.getString(XAI_MODEL, null)?.ifBlank { null } ?: AiProviders.oneXai.textModel
        xaiState = if (xaiKey == null) State.NOT_SET else stateOf(p.getString(XAI_STATE, null))
        xaiImageModel = p.getString(XAI_IMAGE_MODEL, null)
            ?.takeIf { m -> OneXaiImage.models.any { it.first == m } }.orEmpty()

        atKey = p.getString(AT_KEY, null)?.trim()?.ifBlank { null }
        atTextModel = p.getString(AT_MODEL, null)?.ifBlank { null } ?: AiProviders.atria.textModel
        atState = if (atKey == null) State.NOT_SET else stateOf(p.getString(AT_STATE, null))

        gqKey = p.getString(GQ_KEY, null)?.trim()?.ifBlank { null }
        gqTextModel = p.getString(GQ_MODEL, null)?.ifBlank { null } ?: AiProviders.groq.textModel
        gqState = if (gqKey == null) State.NOT_SET else stateOf(p.getString(GQ_STATE, null))

        textProviderId = AiProviders.byId(p.getString(TEXT_PROVIDER, null)).id

        // Per-conversation model choices (chat_model_<channel> = "<providerId>\u001F<model>").
        conversationModels = p.all
            .mapNotNull { (k, v) ->
                if (k.startsWith(CHAT_MODEL_PREFIX) && v is String)
                    k.removePrefix(CHAT_MODEL_PREFIX) to v else null
            }
            .toMap()

        cfAccount = p.getString(CF_ACCOUNT, null)?.trim()?.ifBlank { null }
        cfToken = p.getString(CF_TOKEN, null)?.trim()?.ifBlank { null }
        cfModel = p.getString(CF_MODEL, null)?.ifBlank { null } ?: CloudflareClient.DEFAULT_MODEL.id
        cfState = if (cfAccount == null || cfToken == null) State.NOT_SET
        else stateOf(p.getString(CF_STATE, null))

        cftAccount = p.getString(CFT_ACCOUNT, null)?.trim()?.ifBlank { null }
        cftToken = p.getString(CFT_TOKEN, null)?.trim()?.ifBlank { null }
        cftModel = p.getString(CFT_MODEL, null)?.ifBlank { null }
            ?: AiProviders.cloudflareText.textModel
        cftState = if (cftAccount == null || cftToken == null) State.NOT_SET
        else stateOf(p.getString(CFT_STATE, null))

        poKey = p.getString(PO_KEY, null)?.trim()?.ifBlank { null }
        poState = stateOf(p.getString(PO_STATE, null))
    }

    /** True when at least one real service is connected, so the gate can close. */
    fun anyConnected(): Boolean =
        textReady() || cloudflareReady() || cloudflareTextReady() || poState == State.CONNECTED
}
