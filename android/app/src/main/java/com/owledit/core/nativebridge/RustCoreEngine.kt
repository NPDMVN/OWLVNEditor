package com.owledit.core.nativebridge

import com.owledit.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RustCoreException(message: String) : Exception(message)

/**
 * Type-safe Kotlin wrapper around [RustCoreJni] with unified JSON envelope decoding.
 */
class RustCoreEngine {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun isAvailable(): Boolean = RustCoreJni.isLoaded()

    fun coreBanner(): String {
        if (!RustCoreJni.isLoaded()) {
            return "RustCore unavailable: ${RustCoreJni.loadErrorMessage()}"
        }
        return runCatching { RustCoreJni.nativeCoreBanner() }
            .getOrElse { "RustCore error: ${it.message}" }
    }

    private inline fun <reified T> decodeEnvelope(raw: String): T {
        val env = json.decodeFromString<RustEnvelope<T>>(raw)
        if (!env.ok || env.data == null) {
            throw RustCoreException(env.error ?: "Lỗi lõi Rust không xác định")
        }
        return env.data
    }

    private fun ensureLoaded() {
        if (!RustCoreJni.isLoaded()) {
            throw RustCoreException(
                "Không thể nạp thư viện lõi libowledit_core.so: ${RustCoreJni.loadErrorMessage()}"
            )
        }
    }

    fun openSessionFromFile(
        xmlPath: String,
        keyHex: String = "",
        ivHex: String = "",
        extraDictJson: String = "",
    ): OpenSessionOut {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeOpenSessionFromFile(xmlPath, keyHex, ivHex, extraDictJson)
        )
    }

    fun openSessionFromBytes(
        xmlBytes: ByteArray,
        keyHex: String = "",
        ivHex: String = "",
        extraDictJson: String = "",
    ): OpenSessionOut {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeOpenSession(xmlBytes, keyHex, ivHex, extraDictJson)
        )
    }

    fun closeSession(sessionId: Long) {
        if (sessionId > 0L && RustCoreJni.isLoaded()) {
            runCatching { RustCoreJni.nativeCloseSession(sessionId) }
        }
    }

    fun getDashboard(sessionId: Long): DashboardSummary {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeGetDashboard(sessionId))
    }

    fun queryEntries(
        sessionId: Long,
        search: String = "",
        category: String = "all",
        onlyModified: Boolean = false,
        offset: Int = 0,
        limit: Int = 80,
    ): ExplorerPage {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeQueryEntries(sessionId, search, category, onlyModified, offset, limit)
        )
    }

    fun inspectEntry(sessionId: Long, key: String): EntryInspection {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeInspectEntry(sessionId, key))
    }

    fun setEncryptInt(sessionId: Long, keyName: String, value: Int): SetEncryptIntOut {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeSetEncryptInt(sessionId, keyName, value))
    }

    fun updateHero(
        sessionId: Long,
        bodyName: String,
        unlocked: Boolean? = null,
        level: Int? = null,
        secondSkill: Boolean? = null,
        unlockAllSkills: Boolean = false,
        unlockAllStyles: Boolean = false,
    ): Int {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeUpdateHero(
                sessionId = sessionId,
                bodyName = bodyName,
                hasUnlocked = unlocked != null,
                unlocked = unlocked ?: false,
                hasLevel = level != null,
                level = level ?: 1,
                hasSecondSkill = secondSkill != null,
                secondSkill = secondSkill ?: false,
                unlockAllSkills = unlockAllSkills,
                unlockAllStyles = unlockAllStyles,
            )
        )
    }

    fun patchNrbfEntry(
        sessionId: Long,
        containerKey: String,
        entryKey: String,
        entryValue: String,
        defaultKind: NrbfDictKindDto? = null,
    ): Int {
        ensureLoaded()
        val kindJson = defaultKind?.let { json.encodeToString(it) } ?: ""
        return decodeEnvelope(
            RustCoreJni.nativePatchNrbfEntry(sessionId, containerKey, entryKey, entryValue, kindJson)
        )
    }

    fun setAllNrbfValues(sessionId: Long, containerKey: String, uniformValue: String): Int {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeSetAllNrbfValues(sessionId, containerKey, uniformValue)
        )
    }

    fun setRawEntry(sessionId: Long, key: String, value: String, encrypt: Boolean): Int {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeSetRawEntry(sessionId, key, value, encrypt))
    }

    fun applyPreset(
        sessionId: Long,
        presetId: String,
        extraSkins: List<String> = emptyList(),
        extraItems: List<String> = emptyList(),
    ): ApplyPresetOut {
        ensureLoaded()
        val skinsJson = if (extraSkins.isEmpty()) "" else json.encodeToString(extraSkins)
        val itemsJson = if (extraItems.isEmpty()) "" else json.encodeToString(extraItems)
        return decodeEnvelope(
            RustCoreJni.nativeApplyPreset(sessionId, presetId, skinsJson, itemsJson)
        )
    }

    fun computeDiff(sessionId: Long): List<DiffEntry> {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeComputeDiff(sessionId))
    }

    fun commitSessionToFile(sessionId: Long, outPath: String): CommitOut {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeCommitSessionToFile(sessionId, outPath))
    }

    fun sanitizeXmlFile(
        inPath: String,
        outPath: String,
        extraPatterns: List<String> = emptyList(),
        removeLocalDeviceId: Boolean = true,
    ): SanitizeOut {
        ensureLoaded()
        val extraJson = if (extraPatterns.isEmpty()) "" else json.encodeToString(extraPatterns)
        return decodeEnvelope(
            RustCoreJni.nativeSanitizeXmlFile(inPath, outPath, extraJson, removeLocalDeviceId)
        )
    }

    fun createBackupFromStaging(
        stagingDir: String,
        outputArchive: String,
        packageName: String,
        profileId: String,
        uid: String?,
        note: String,
        deviceInfo: String,
        epochMs: Long = System.currentTimeMillis(),
    ): BackupManifest {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeCreateBackupFromStaging(
                stagingDir,
                outputArchive,
                packageName,
                profileId,
                uid.orEmpty(),
                note,
                deviceInfo,
                epochMs,
            )
        )
    }

    fun inspectBackup(archivePath: String): BackupInspectResult {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeInspectBackup(archivePath))
    }

    fun verifyAndExtractBackup(
        archivePath: String,
        stagingDir: String,
        selectedPaths: List<String> = emptyList(),
    ): List<ExtractedBackupFile> {
        ensureLoaded()
        val selJson = if (selectedPaths.isEmpty()) "" else json.encodeToString(selectedPaths)
        return decodeEnvelope(
            RustCoreJni.nativeVerifyAndExtractBackup(archivePath, stagingDir, selJson)
        )
    }

    fun inspectSsaidFile(ssaidPath: String, packageFilter: String = ""): SsaidInspection {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeInspectSsaidFile(ssaidPath, packageFilter))
    }

    fun generateRandomSsaid(): String {
        ensureLoaded()
        return RustCoreJni.nativeGenerateRandomSsaid()
    }

    fun updateSsaidFileVerified(
        inPath: String,
        targetPackage: String,
        newSsaid: String,
        outPath: String,
    ): SsaidUpdateOut {
        ensureLoaded()
        return decodeEnvelope(
            RustCoreJni.nativeUpdateSsaidFileVerified(inPath, targetPackage, newSsaid, outPath)
        )
    }

    fun beautifyJson(input: String): String {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeBeautifyJson(input))
    }

    fun minifyJson(input: String): String {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeMinifyJson(input))
    }

    fun validateContent(content: String, format: String): ValidationResult {
        ensureLoaded()
        return decodeEnvelope(RustCoreJni.nativeValidateContent(content, format))
    }
}
