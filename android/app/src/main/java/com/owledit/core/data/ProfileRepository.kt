package com.owledit.core.data

import android.content.Context
import com.owledit.core.model.OwlCatalog
import com.owledit.core.model.OwlPresetsFile
import com.owledit.core.model.OwlProfile
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Loads Otherworld Legends profile, catalog, presets, and MD5 dictionary from bundled APK assets,
 * while supporting hot-import overrides stored in `filesDir/overrides/` without rebuilding the APK.
 */
class ProfileRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val overridesDir: File
        get() = File(context.filesDir, "overrides/otherworld_legends").apply { mkdirs() }

    fun loadProfile(): OwlProfile {
        val text = readAssetOrOverride(
            overrideName = "profile.json",
            assetPath = "profiles/otherworld_legends/profile.json",
        )
        return runCatching { json.decodeFromString<OwlProfile>(text) }.getOrDefault(OwlProfile())
    }

    fun loadPresets(): OwlPresetsFile {
        val text = readAssetOrOverride(
            overrideName = "owl_presets.json",
            assetPath = "profiles/otherworld_legends/presets/owl_presets.json",
        )
        return runCatching { json.decodeFromString<OwlPresetsFile>(text) }.getOrDefault(OwlPresetsFile())
    }

    fun loadCatalog(): OwlCatalog {
        val text = readAssetOrOverride(
            overrideName = "owl_vi_en.json",
            assetPath = "profiles/otherworld_legends/catalogs/owl_vi_en.json",
        )
        return runCatching { json.decodeFromString<OwlCatalog>(text) }.getOrDefault(OwlCatalog())
    }

    fun loadExtraDictionaryJson(): String {
        return readAssetOrOverride(
            overrideName = "keys_dictionary.json",
            assetPath = "profiles/otherworld_legends/keys_dictionary.json",
        )
    }

    fun hasOverrides(): Boolean =
        overridesDir.listFiles()?.any { it.isFile && it.name.endsWith(".json") } == true

    fun clearOverrides() {
        overridesDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /**
     * Imports either a standalone JSON (auto-classified as profile, presets, catalog, or keys dictionary)
     * or a ZIP bundle containing one or more of those JSON files.
     */
    fun importExternalFile(fileName: String, bytes: ByteArray): Result<String> = runCatching {
        val lower = fileName.lowercase()
        if (lower.endsWith(".zip") || (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())) {
            var count = 0
            val importedNames = mutableListOf<String>()
            ZipInputStream(bytes.inputStream()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                        val content = zis.readBytes().toString(Charsets.UTF_8)
                        val target = classifyJsonOverride(entry.name, content)
                        if (target != null) {
                            File(overridesDir, target).writeText(content)
                            importedNames += target
                            count++
                        }
                    }
                    zis.closeEntry()
                }
            }
            if (count == 0) {
                throw IllegalArgumentException("File ZIP không chứa profile/preset/catalog JSON hợp lệ.")
            }
            "Đã nhập $count tệp cấu hình ngoài: ${importedNames.joinToString(", ")}"
        } else {
            val content = bytes.toString(Charsets.UTF_8)
            val target = classifyJsonOverride(fileName, content)
                ?: throw IllegalArgumentException(
                    "Không nhận diện được cấu trúc JSON (hỗ trợ profile.json, owl_presets.json, owl_vi_en.json, keys_dictionary.json)."
                )
            File(overridesDir, target).writeText(content)
            "Đã nhập và kích hoạt cấu hình ngoài: $target"
        }
    }

    private fun classifyJsonOverride(name: String, content: String): String? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val lower = name.lowercase()
        return when {
            lower.contains("profile") || (trimmed.contains("\"packages\"") && trimmed.contains("\"crypto\"")) -> {
                json.decodeFromString<OwlProfile>(trimmed)
                "profile.json"
            }
            lower.contains("preset") || trimmed.contains("\"quickPresets\"") -> {
                json.decodeFromString<OwlPresetsFile>(trimmed)
                "owl_presets.json"
            }
            lower.contains("catalog") || lower.contains("vi_en") || trimmed.contains("\"currencies\"") -> {
                json.decodeFromString<OwlCatalog>(trimmed)
                "owl_vi_en.json"
            }
            lower.contains("dict") || lower.contains("keys") -> {
                "keys_dictionary.json"
            }
            else -> null
        }
    }

    private fun readAssetOrOverride(overrideName: String, assetPath: String): String {
        val overrideFile = File(overridesDir, overrideName)
        if (overrideFile.exists() && overrideFile.length() > 2) {
            runCatching { return overrideFile.readText() }
        }
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
}
