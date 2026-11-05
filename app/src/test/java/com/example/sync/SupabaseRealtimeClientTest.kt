package com.example.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseRealtimeClientTest {
    @Test
    fun parsesOnlySupportedCatalogAndAccessEvents() {
        val parsed = SupabaseRealtimeClient.parseBroadcastMessage(
            """{"topic":"realtime:mahi-repair:member:member-id:device:device-id","event":"broadcast","payload":{"event":"catalog_changed","payload":{"event_id":"event-123"}}}"""
        )

        assertEquals("catalog_changed", parsed?.name)
        assertEquals("event-123", parsed?.eventId)
    }

    @Test
    fun ignoresUnrecognizedEventsAndNonBroadcastFrames() {
        val unknown = SupabaseRealtimeClient.parseBroadcastMessage(
            """{"event":"broadcast","payload":{"event":"private_recipe_payload","payload":{"event_id":"event-123"}}}"""
        )
        val notBroadcast = SupabaseRealtimeClient.parseBroadcastMessage(
            """{"event":"phx_reply","payload":{"status":"ok"}}"""
        )

        assertNull(unknown)
        assertNull(notBroadcast)
    }

    @Test
    fun acceptsMinimalEventWithoutAnEventId() {
        val parsed = SupabaseRealtimeClient.parseBroadcastMessage(
            """{"event":"broadcast","payload":{"event":"device_revoked","payload":{}}}"""
        )

        assertEquals("device_revoked", parsed?.name)
        assertNull(parsed?.eventId)
    }
}
