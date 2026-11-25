package com.owledit.core.nativebridge

/**
 * Direct JNI declarations bound to `libowledit_core.so` (`rust/owledit-ffi`).
 * Every symbol signature here MUST match `Java_com_owledit_core_nativebridge_RustCoreJni_*` exactly.
 */
object RustCoreJni {
    @Volatile
    private var loaded = false

    @Volatile
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("owledit_core")
            loaded = true
        } catch (t: Throwable) {
            loaded = false
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    fun isLoaded(): Boolean = loaded
    fun loadErrorMessage(): String? = loadError

    @JvmStatic
    external fun nativeCoreBanner(): String

    @JvmStatic
    external fun nativeOpenSession(
        xmlBytes: ByteArray,
        keyHex: String,
        ivHex: String,
        extraDictJson: String,
    ): String

    @JvmStatic
    external fun nativeOpenSessionFromFile(
        xmlPath: String,
        keyHex: String,
        ivHex: String,
        extraDictJson: String,
    ): String

    @JvmStatic
    external fun nativeCloseSession(sessionId: Long)

    @JvmStatic
    external fun nativeGetDashboard(sessionId: Long): String

    @JvmStatic
    external fun nativeQueryEntries(
        sessionId: Long,
        search: String,
        category: String,
        onlyModified: Boolean,
        offset: Int,
        limit: Int,
    ): String

    @JvmStatic
    external fun nativeInspectEntry(
        sessionId: Long,
        key: String,
    ): String

    @JvmStatic
    external fun nativeSetEncryptInt(
        sessionId: Long,
        keyName: String,
        value: Int,
    ): String

    @JvmStatic
    external fun nativeUpdateHero(
        sessionId: Long,
        bodyName: String,
        hasUnlocked: Boolean,
        unlocked: Boolean,
        hasLevel: Boolean,
        level: Int,
        hasSecondSkill: Boolean,
        secondSkill: Boolean,
        unlockAllSkills: Boolean,
        unlockAllStyles: Boolean,
    ): String

    @JvmStatic
    external fun nativePatchNrbfEntry(
        sessionId: Long,
        containerKey: String,
        entryKey: String,
        entryValue: String,
        defaultKindJson: String,
    ): String

    @JvmStatic
    external fun nativeSetAllNrbfValues(
        sessionId: Long,
        containerKey: String,
        uniformValue: String,
    ): String

    @JvmStatic
    external fun nativeSetRawEntry(
        sessionId: Long,
        key: String,
        value: String,
        encrypt: Boolean,
    ): String

    @JvmStatic
    external fun nativeApplyPreset(
        sessionId: Long,
        presetId: String,
        extraSkinsJson: String,
        extraItemsJson: String,
    ): String

    @JvmStatic
    external fun nativeComputeDiff(sessionId: Long): String

    @JvmStatic
    external fun nativeCommitSessionToFile(
        sessionId: Long,
        outPath: String,
    ): String

    @JvmStatic
    external fun nativeSanitizeXmlFile(
        inPath: String,
        outPath: String,
        extraPatternsJson: String,
        removeLocalDeviceId: Boolean,
    ): String

    @JvmStatic
    external fun nativeCreateBackupFromStaging(
        stagingDir: String,
        outputArchive: String,
        packageName: String,
        profileId: String,
        uid: String,
        note: String,
        deviceInfo: String,
        epochMs: Long,
    ): String

    @JvmStatic
    external fun nativeInspectBackup(archivePath: String): String

    @JvmStatic
    external fun nativeVerifyAndExtractBackup(
        archivePath: String,
        stagingDir: String,
        selectedPathsJson: String,
    ): String

    @JvmStatic
    external fun nativeInspectSsaidFile(
        ssaidPath: String,
        packageFilter: String,
    ): String

    @JvmStatic
    external fun nativeGenerateRandomSsaid(): String

    @JvmStatic
    external fun nativeUpdateSsaidFileVerified(
        inPath: String,
        targetPackage: String,
        newSsaid: String,
        outPath: String,
    ): String

    @JvmStatic
    external fun nativeBeautifyJson(input: String): String

    @JvmStatic
    external fun nativeMinifyJson(input: String): String

    @JvmStatic
    external fun nativeValidateContent(content: String, format: String): String
}
