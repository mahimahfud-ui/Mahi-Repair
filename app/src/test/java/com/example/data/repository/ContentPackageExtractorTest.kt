package com.example.data.repository

import com.example.security.VerifiedContentEntry
import com.example.security.VerifiedContentPackage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPackageExtractorTest {
    @Test
    fun extractsExactlyDeclaredResourcesAfterHashAndSizeVerification() {
        val bytes = """{"feature":"enabled"}""".toByteArray()
        val archiveBytes = zip("config/features.json" to bytes)
        val archive = File.createTempFile("mahi-update-", ".zip")
        val destination = createTempDir(prefix = "mahi-content-")
        try {
            archive.writeBytes(archiveBytes)
            ContentPackageExtractor.extract(archive, destination, packageDescriptor(archiveBytes, "config/features.json", bytes))
            assertEquals(String(bytes), File(destination, "config/features.json").readText())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test
    fun rejectsUndeclaredZipEntries() {
        val bytes = "ok".toByteArray()
        val archiveBytes = zip("allowed.json" to bytes, "extra.json" to bytes)
        val archive = File.createTempFile("mahi-update-", ".zip")
        val destination = createTempDir(prefix = "mahi-content-")
        try {
            archive.writeBytes(archiveBytes)
            val result = runCatching {
                ContentPackageExtractor.extract(archive, destination, packageDescriptor(archiveBytes, "allowed.json", bytes))
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("undeclared"))
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathTraversalBeforeWritingOutsideStagingDirectory() {
        val bytes = "escape".toByteArray()
        val archiveBytes = zip("../escape.json" to bytes)
        val archive = File.createTempFile("mahi-update-", ".zip")
        val destination = createTempDir(prefix = "mahi-content-")
        try {
            archive.writeBytes(archiveBytes)
            val pkg = VerifiedContentPackage(
                assetId = "safe-package",
                version = 1,
                storagePath = "channels/stable/packages/123e4567-e89b-12d3-a456-426614174000.zip",
                sha256 = digest(archiveBytes),
                sizeBytes = archiveBytes.size.toLong(),
                contentType = "application/zip",
                entries = emptyList()
            )
            val result = runCatching { ContentPackageExtractor.extract(archive, destination, pkg) }
            assertTrue(result.isFailure)
            assertFalse(File(destination.parentFile, "escape.json").exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test
    fun rejectsIncorrectInnerResourceDigest() {
        val bytes = "untrusted".toByteArray()
        val archiveBytes = zip("data.json" to bytes)
        val archive = File.createTempFile("mahi-update-", ".zip")
        val destination = createTempDir(prefix = "mahi-content-")
        try {
            archive.writeBytes(archiveBytes)
            val pkg = packageDescriptor(archiveBytes, "data.json", bytes).copy(
                entries = listOf(VerifiedContentEntry("data.json", "0".repeat(64), bytes.size.toLong()))
            )
            val result = runCatching { ContentPackageExtractor.extract(archive, destination, pkg) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("SHA-256"))
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    private fun packageDescriptor(archive: ByteArray, path: String, bytes: ByteArray) = VerifiedContentPackage(
        assetId = "repair-content",
        version = 2,
        storagePath = "channels/stable/packages/123e4567-e89b-12d3-a456-426614174000.zip",
        sha256 = digest(archive),
        sizeBytes = archive.size.toLong(),
        contentType = "application/zip",
        entries = listOf(VerifiedContentEntry(path, digest(bytes), bytes.size.toLong()))
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
