package com.example.myapplication.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a product link, preferring the Taobao app for Taobao/Tmall pages.
 *
 * A Taobao listing opened in the browser lands on a mobile web page that asks
 * the person to log in again and hides half the listing behind "open in app";
 * handing the same URL to the installed app skips all of that. Every other
 * host, and a phone without Taobao, goes to the browser as before.
 */
object OpenLink {

    private const val TAOBAO_PACKAGE = "com.taobao.taobao"

    private val taobaoHosts = listOf("taobao.com", "tmall.com", "tmall.hk", "1688.com")

    fun isTaobao(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        return taobaoHosts.any { host == it || host.endsWith(".$it") }
    }

    fun open(context: Context, url: String) {
        if (url.isBlank()) return
        val uri = Uri.parse(url)
        if (isTaobao(url)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(TAOBAO_PACKAGE))
                return
            } catch (_: ActivityNotFoundException) {
                // App not installed — fall through to the browser.
            }
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // Nothing can open this URL; a silent no-op beats a crash on tap.
        }
    }
}
