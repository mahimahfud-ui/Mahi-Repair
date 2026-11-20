package com.example.data.repository

import android.content.Context
import android.util.AtomicFile
import com.example.security.AppUpdateManifestVerifier
import com.example.security.VerifiedAppUpdateRelease
import com.example.security.VerifiedContentPackage
import com.example.security.VerifiedRemoteConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Private, staged activation of signed JSON/image resources. The active pointer changes atomically. */
class AppUpdateContentInstaller(context: Context) {
    private val root = File(context.applicationContext.filesDir, "app-update-content")
    private val releasesDir = File(root, "releases")
    private val stagingDir = File(root, "staging")
    private val pointer = AtomicFile(File(root, "active-release.json"))
    private val activeConfig = MutableStateFlow(readActiveRemoteConfig())
    val activeRemoteConfigFlow: StateFlow<VerifiedRemoteConfig?> = activeConfig

    init {
        require(releasesDir.mkdirs() || releasesDir.isDirectory) { "Could not create update release storage." }
        require(stagingDir.mkdirs() || stagingDir.isDirectory) { "Could not create update staging storage." }
    }

    @Synchronized
    fun activate(release: VerifiedAppUpdateRelease, downloadedPackages: Map<String, File>) {
        val api = android.os.Build.VERSION.SDK_INT
        require(api in release.remoteConfig.minAndroidApi..release.remoteConfig.maxAndroidApi) {
            "This release is not compatible with this Android version."
        }
        require(downloadedPackages.keys == release.packages.map { it.assetId }.toSet()) {
            "Downloaded package set does not match the signed manifest."
        }
        val previousId = readPointer()?.optString("releaseId")?.takeIf { it.isNotBlank() }
        val stage = File(stagingDir, release.releaseId)
        val finalDir = File(releasesDir, release.releaseId)
        deleteTree(stage)
        require(!finalDir.exists()) { "This content release is already installed. Check for updates again." }
        try {
            val packageRoot = File(stage, "packages")
            require(packageRoot.mkdirs() || packageRoot.isDirectory) { "Could not stage content packages." }
            previousId?.let { id ->
                val oldPackages = File(File(releasesDir, id), "packages")
                if (oldPackages.isDirectory) copyTree(oldPackages, packageRoot)
            }
            release.packages.forEach { pkg ->
                val archive = downloadedPackages[pkg.assetId]
                    ?: throw IllegalStateException("A declared content package was not downloaded.")
                val destination = File(packageRoot, pkg.assetId)
                deleteTree(destination)
                require(destination.mkdirs() || destination.isDirectory) { "Could not create package staging folder." }
                ContentPackageExtractor.extract(archive, destination, pkg)
            }
            File(stage, "remote-config.json").writeText(release.remoteConfig.rawJson, Charsets.UTF_8)
            File(stage, "release.json").writeText(
                JSONObject()
                    .put("releaseId", release.releaseId)
                    .put("releaseNumber", release.releaseNumber)
                    .put("channel", release.channel)
                    .put("previousReleaseId", previousId ?: JSONObject.NULL)
                    .put("activatedAt", System.currentTimeMillis()).toString(),
                Charsets.UTF_8
            )
            require(stage.renameTo(finalDir)) { "Could not finalize verified content release." }
            writePointer(JSONObject()
                .put("releaseId", release.releaseId)
                .put("previousReleaseId", previousId ?: JSONObject.NULL))
            activeConfig.value = release.remoteConfig
            pruneOldReleases(setOf(release.releaseId, previousId))
        } catch (error: Exception) {
            deleteTree(stage)
            if (finalDir.exists() && readPointer()?.optString("releaseId") != release.releaseId) deleteTree(finalDir)
            throw error
        }
    }

    @Synchronized
    fun rollback(): Boolean {
        val current = readPointer() ?: return false
        val currentId = current.optString("releaseId").takeIf { it.isNotBlank() } ?: return false
        val previousId = current.optString("previousReleaseId").takeIf { it.isNotBlank() } ?: return false
        if (!File(releasesDir, previousId).isDirectory) return false
        writePointer(JSONObject().put("releaseId", previousId).put("previousReleaseId", currentId))
        activeConfig.value = readRemoteConfig(File(releasesDir, previousId))
        return true
    }

    fun contentFile(packageId: String, relativePath: String): File? {
        if (!packageId.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")) ||
            !ContentPackageExtractor.isSafeRelativePath(relativePath)) return null
        val active = activeReleaseDirectory() ?: return null
        val packageRoot = File(File(active, "packages"), packageId).canonicalFile
        val file = File(packageRoot, relativePath).canonicalFile
        if (!file.path.startsWith(packageRoot.path + File.separator) || !file.isFile) return null
        return file
    }

    private fun readActiveRemoteConfig(): VerifiedRemoteConfig? =
        activeReleaseDirectory()?.let(::readRemoteConfig)
    private fun readRemoteConfig(directory: File): VerifiedRemoteConfig? = runCatching {
        val file = File(directory, "remote-config.json")
        if (!file.isFile || file.length() > MAX_CONFIG_BYTES) return null
        AppUpdateManifestVerifier.verifyRemoteConfig(JSONObject(file.readText(Charsets.UTF_8)))
    }.getOrNull()
    private fun activeReleaseDirectory(): File? {
        val id = readPointer()?.optString("releaseId")?.takeIf { it.isNotBlank() } ?: return null
        return File(releasesDir, id).takeIf { it.isDirectory }
    }
    private fun readPointer(): JSONObject? = runCatching {
        val file = pointer.baseFile
        if (!file.exists()) null else FileInputStream(file).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }.getOrNull()
    private fun writePointer(value: JSONObject) {
        val output = pointer.startWrite()
        try {
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
            pointer.finishWrite(output)
        } catch (error: Exception) {
            pointer.failWrite(output)
            throw error
        }
    }
    private fun pruneOldReleases(keep: Set<String?>) {
        releasesDir.listFiles()?.filter { it.isDirectory && it.name !in keep }?.forEach(::deleteTree)
    }
    private fun copyTree(source: File, destination: File) {
        source.walkTopDown().forEach { file ->
            require(!Files.isSymbolicLink(file.toPath())) { "Symbolic links are not allowed in cached content." }
            val target = File(destination, file.relativeTo(source).path)
            if (file.isDirectory) {
                require(target.mkdirs() || target.isDirectory) { "Could not copy previous content cache." }
            } else {
                require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) {
                    "Could not create previous content cache directory."
                }
                file.copyTo(target, overwrite = true)
            }
        }
    }
    private fun deleteTree(file: File) {
        if (!file.exists()) return
        file.walkBottomUp().forEach { require(it.delete() || !it.exists()) { "Could not clean update staging data." } }
    }
    private companion object { const val MAX_CONFIG_BYTES = 256L * 1024L }
}

/** Extracts only exact manifest-declared data files after validating every byte. */
object ContentPackageExtractor {
    fun extract(archive: File, destination: File, pkg: VerifiedContentPackage) {
        require(archive.isFile && archive.length() == pkg.sizeBytes) { "Content ZIP size does not match its descriptor." }
        val expected = pkg.entries.associateBy { it.path }
        require(expected.size == pkg.entries.size) { "Duplicate declared content paths are not allowed." }
        val seen = mutableSetOf<String>()
        var expandedTotal = 0L
        java.util.zip.ZipInputStream(FileInputStream(archive).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                require(!entry.isDirectory) { "Directory entries are not allowed in content packages." }
                val name = entry.name
                require(isSafeRelativePath(name)) { "Content ZIP contains an unsafe resource path." }
                val descriptor = expected[name] ?: throw IllegalArgumentException("Content ZIP contains an undeclared resource.")
                require(seen.add(name)) { "Content ZIP contains a duplicate resource." }
                val outputFile = File(destination, name)
                val rootPath = destination.canonicalFile
                val targetPath = outputFile.canonicalFile
                require(targetPath.path.startsWith(rootPath.path + File.separator)) {
                    "Content ZIP resource escaped its staging directory."
                }
                require(outputFile.parentFile?.mkdirs() == true || outputFile.parentFile?.isDirectory == true) {
                    "Could not create content resource directory."
                }
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                FileOutputStream(outputFile).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        expandedTotal += count
                        require(bytes <= descriptor.sizeBytes && expandedTotal <= MAX_EXPANDED_PACKAGE_BYTES) {
                            "Content ZIP expanded beyond signed size limits."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
                require(bytes == descriptor.sizeBytes) { "Content resource size did not match its descriptor." }
                val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                require(actual == descriptor.sha256) { "Content resource SHA-256 verification failed." }
                input.closeEntry()
            }
        }
        require(seen == expected.keys) { "Content ZIP is missing one or more declared resources." }
    }

    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > 240 || path.startsWith('/') || path.contains('\\')) return false
        if (!path.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._/-]*$"))) return false
        if (path.split('/').any { it.isEmpty() || it == "." || it == ".." }) return false
        val lower = path.lowercase()
        return lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }
    private const val MAX_EXPANDED_PACKAGE_BYTES = 250L * 1024L * 1024L
}
