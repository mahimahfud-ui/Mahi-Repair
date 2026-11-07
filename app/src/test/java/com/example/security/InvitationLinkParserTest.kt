package com.example.security

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvitationLinkParserTest {
    @Test
    fun acceptsOnlyAnAppOwnedInvitationWithAWellFormedTokenHash() {
        val tokenHash = "a".repeat(64)
        val parsed = InvitationLinkParser.parse(
            Uri.parse("mahirepair://invite?token_hash=$tokenHash&type=invite")
        )
        assertEquals(tokenHash, parsed)
    }

    @Test
    fun rejectsUnexpectedSchemesAndHosts() {
        val tokenHash = "b".repeat(64)
        assertNull(InvitationLinkParser.parse(Uri.parse("https://invite?token_hash=$tokenHash")))
        assertNull(InvitationLinkParser.parse(Uri.parse("mahirepair://other?token_hash=$tokenHash")))
    }

    @Test
    fun rejectsMissingOrMalformedTokenHashes() {
        assertNull(InvitationLinkParser.parse(Uri.parse("mahirepair://invite")))
        assertNull(InvitationLinkParser.parse(Uri.parse("mahirepair://invite?token_hash=short")))
        assertNull(InvitationLinkParser.parse(Uri.parse("mahirepair://invite?token_hash=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc%2F")))
    }
}