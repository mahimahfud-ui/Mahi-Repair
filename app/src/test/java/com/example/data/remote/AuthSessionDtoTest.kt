package com.example.data.remote

import com.example.data.remote.dto.AuthSessionResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthSessionDtoTest {
    @Test
    fun parsesSupabaseMetadataContainingArraysAndNestedValues() {
        val json = """
            {
              "access_token": "access",
              "refresh_token": "refresh",
              "expires_in": 3600,
              "user": {
                "id": "0e4eac6e-6b36-4a72-9c8d-53daeb6b5c77",
                "email": "member@example.com",
                "user_metadata": { "display_name": "Mahi", "preferences": { "theme": "dark" } },
                "app_metadata": { "provider": "email", "providers": ["email"], "role": "member" }
              }
            }
        """.trimIndent()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val session = moshi.adapter(AuthSessionResponse::class.java).fromJson(json)

        assertEquals("member@example.com", session?.user?.email)
        assertEquals("member", session?.user?.appMetadata?.role)
    }
}