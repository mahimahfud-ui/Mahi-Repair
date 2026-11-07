package com.example.security

import android.net.Uri

object InvitationLinkParser {
    private val tokenHashPattern = Regex("^[A-Za-z0-9_-]{16,512}$")

    fun parse(uri: Uri?): String? {
        if (uri == null || uri.scheme != "mahirepair" || uri.host != "invite") return null
        val tokenHash = uri.getQueryParameter("token_hash")?.trim() ?: return null
        return tokenHash.takeIf { tokenHashPattern.matches(it) }
    }
}