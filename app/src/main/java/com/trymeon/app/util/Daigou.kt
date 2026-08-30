package com.trymeon.app.util

import java.net.URLEncoder

/**
 * One-tap handoff to a forwarding agent's order page.
 *
 * What this does NOT do is create an account or pay on the user's behalf. No
 * agent publishes an order-placement API, so doing it "for" the user would mean
 * driving their signup form with their card details and accepting the agent's
 * terms as them — that is how an affiliate account gets terminated, and it puts
 * us in the middle of someone else's money.
 *
 * What works instead, and is nearly as short: every agent accepts a deep link
 * that opens their order form with the Taobao/1688 product already loaded and
 * our referral code attached. First order is tap → log in → confirm. Once
 * [preferredAgentId] is remembered, every order after that is a single tap.
 *
 * Templates are data, not code, precisely so a change in one agent's link
 * format is a config edit. Verify each against that agent's own affiliate docs
 * before shipping it — a wrong template silently loses the commission.
 */
object Daigou {

    /**
     * @param linkTemplate deep link to the agent's order page.
     *   `{url}` — URL-encoded product link. `{code}` — our referral code.
     */
    data class Provider(
        val id: String,
        val name: String,
        val linkTemplate: String,
        val referralCode: String = "",
        val note: String = ""
    ) {
        val configured: Boolean get() = linkTemplate.contains("{url}")
    }

    @Volatile
    private var providers: List<Provider> = emptyList()

    @Volatile
    var preferredAgentId: String = ""
        private set

    /**
     * Parse a configured provider list.
     *
     * Format, one provider per entry: `id|name|linkTemplate|referralCode`,
     * entries separated by `;;`. Kept as configuration rather than code so a
     * change in an agent's link format — which happens — is an edit to
     * local.properties, which is what the templates were separated out for in
     * the first place.
     */
    fun parse(config: String): List<Provider> = config
        .split(";;")
        .mapNotNull { entry ->
            val parts = entry.split("|").map(String::trim)
            if (parts.size < 3) return@mapNotNull null
            val (id, name, template) = parts
            // A template with no {url} cannot open a product page; a provider
            // that silently sends people to a homepage is worse than none.
            if (id.isEmpty() || name.isEmpty() || !template.contains("{url}")) return@mapNotNull null
            Provider(id, name, template, parts.getOrElse(3) { "" })
        }

    /** Configure at startup from settings. */
    fun init(config: String, preferredAgentId: String = "") = init(parse(config), preferredAgentId)

    /** Configure at startup from remote config or settings; never hard-code live codes. */
    fun init(providers: List<Provider>, preferredAgentId: String = "") {
        this.providers = providers
        this.preferredAgentId = preferredAgentId.ifBlank { providers.firstOrNull()?.id ?: "" }
    }

    fun providers(): List<Provider> = providers

    fun provider(id: String): Provider? = providers.firstOrNull { it.id == id }

    fun preferred(): Provider? = provider(preferredAgentId) ?: providers.firstOrNull()

    fun choose(id: String) {
        if (providers.any { it.id == id }) preferredAgentId = id
    }

    /**
     * Deep link that opens [productUrl] on the agent's order page, or null when
     * the agent has no usable template — better to hide the button than to send
     * someone to a broken page.
     */
    fun orderUrl(productUrl: String, agentId: String = preferredAgentId): String? {
        if (productUrl.isBlank()) return null
        val provider = provider(agentId) ?: return null
        if (!provider.configured) return null
        return provider.linkTemplate
            .replace("{url}", URLEncoder.encode(productUrl, "UTF-8"))
            .replace("{code}", URLEncoder.encode(provider.referralCode, "UTF-8"))
    }
}
