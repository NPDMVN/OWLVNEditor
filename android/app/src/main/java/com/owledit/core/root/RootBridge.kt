package com.owledit.core.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.owledit.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Root & Emulator Bridge designed for rooted Android devices (Magisk, KernelSU, APatch)
 * and rooted PC Android emulators (LDPlayer, MuMu, BlueStacks, NoxPlayer, MEmu).
 *
 * Uses the bundled native Rust `owledit-helper` binary in persistent JSON-Lines daemon mode
 * over `su` for millisecond-latency, safe atomic writes with UID/GID/mode & SELinux preservation.
 */
class RootBridge(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val daemonMutex = Mutex()
    private var suArgsCache: List<String>? = null
    private var helperBinaryPath: String? = null

    private var daemonProc: Process? = null
    private var daemonWriter: BufferedWriter? = null
    private var daemonReader: BufferedReader? = null

    companion object {
        const val SSAID_SYSTEM_PATH = "/data/system/users/0/settings_ssaid.xml"
        const val SSAID_BACKUP_FILENAME = "settings_ssaid.orig.bak"
    }

    fun stagingDir(): File = File(context.cacheDir, "owledit_staging").apply { mkdirs() }
    fun rollbackDir(): File = File(context.filesDir, "rollbacks").apply { mkdirs() }
    fun ssaidBackupFile(): File = File(context.filesDir, SSAID_BACKUP_FILENAME)

    fun defaultBackupDir(): File {
        val sdcardDir = File("/sdcard/OWLVNEditor/backups")
        return try {
            if (sdcardDir.exists() || sdcardDir.mkdirs()) {
                if (sdcardDir.canWrite()) sdcardDir else appExternalBackupDir()
            } else {
                appExternalBackupDir()
            }
        } catch (_: Throwable) {
            appExternalBackupDir()
        }
    }

    private fun appExternalBackupDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "backups").apply { mkdirs() }
    }

    fun playerPrefsPathFor(packageName: String): String =
        "/data/data/$packageName/shared_prefs/$packageName.v2.playerprefs.xml"

    fun packageDataDir(packageName: String): String = "/data/data/$packageName"

    /**
     * Extracts the ABI-matched `owledit-helper` binary from APK assets to app-private `files/bin/`
     * with executable `0755` permission.
     */
    fun ensureHelperExtracted(): Result<File> = runCatching {
        val abi = selectHelperAssetSuffix()
        val assetPath = "bin/owledit-helper-$abi"
        val outDir = File(context.filesDir, "bin").apply { mkdirs() }
        val outFile = File(outDir, "owledit-helper")
        val versionMarker = File(outDir, "helper.abi")
        val expectedTag = "$abi:${com.owledit.app.BuildConfig.VERSION_CODE}"

        if (!outFile.exists() || outFile.length() == 0L || versionMarker.readTextOrEmpty() != expectedTag) {
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setReadable(true, false)
            outFile.setExecutable(true, false)
            versionMarker.writeText(expectedTag)
        } else {
            outFile.setExecutable(true, false)
        }
        helperBinaryPath = outFile.absolutePath
        outFile
    }

    private fun selectHelperAssetSuffix(): String {
        for (abi in Build.SUPPORTED_ABIS) {
            when (abi.lowercase()) {
                "arm64-v8a" -> return "aarch64"
                "armeabi-v7a", "armeabi" -> return "armv7"
                "x86_64" -> return "x86_64"
                "x86" -> return "x86"
            }
        }
        return "aarch64"
    }

    /**
     * Probes Root access, SuVariant (Magisk/KSU/APatch/Generic), EmulatorType (LDPlayer/MuMu/etc.),
     * SELinux status, native helper ping, and installed Otherworld Legends packages.
     */
    suspend fun probeEnvironment(
        knownPackages: List<String>,
        coreBanner: String,
    ): RootEnvironmentInfo = withContext(Dispatchers.IO) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val emulator = detectEmulatorType()

        // 1. Probe root variant
        val (suVariant, suCmd) = detectSuVariant()
        suArgsCache = suCmd

        val rootGranted = suCmd != null
        val selinuxMode = if (rootGranted) {
            execRootShell("getenforce 2>/dev/null || echo Unknown").stdout.trim().ifBlank { "Unknown" }
        } else {
            "Unknown"
        }

        // 2. Extract and ping Rust root helper
        var helperReady = false
        var helperBanner = "Chưa khởi chạy"
        if (rootGranted) {
            val helperRes = ensureHelperExtracted()
            if (helperRes.isSuccess) {
                val ping = helperPing()
                if (ping.isSuccess) {
                    val p = ping.getOrThrow()
                    helperReady = true
                    helperBanner = "owledit-helper v${p.version} (${p.arch}, uid=${p.uid})"
                } else {
                    helperBanner = "Lỗi helper: ${ping.exceptionOrNull()?.message}"
                }
            } else {
                helperBanner = "Lỗi giải nén helper: ${helperRes.exceptionOrNull()?.message}"
            }
        }

        // 3. Detect installed OWL packages via PackageManager + root fallback
        val installed = detectInstalledPackages(knownPackages, rootGranted)

        val diag = buildString {
            if (!rootGranted) {
                append("Không có quyền Root. Bạn vẫn có thể mở file XML thủ công (chế độ Offline/Test) hoặc cấp quyền Root cho OWLEditor.")
                if (emulator.isEmulator) {
                    append("\nGợi ý trên ${emulator.label}: Mở Cài đặt giả lập → Khác → Bật Quyền Root rồi khởi động lại giả lập.")
                }
            } else {
                append("Root sẵn sàng (${suVariant.label}) • SELinux: $selinuxMode")
                if (installed.isEmpty()) {
                    append("\nChưa thấy Otherworld Legends cài trên máy. Kiểm tra lại gói game hoặc chọn thủ công.")
                }
            }
        }

        RootEnvironmentInfo(
            rootGranted = rootGranted,
            suVariant = suVariant,
            emulatorType = emulator,
            abi = abi,
            androidVersion = androidVer,
            selinuxMode = selinuxMode,
            helperReady = helperReady,
            helperBanner = helperBanner,
            coreBanner = coreBanner,
            installedPackages = installed,
            diagnosticMessage = diag,
        )
    }

    private fun detectEmulatorType(): EmulatorType {
        val fingerprint = listOf(
            Build.BRAND,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.HARDWARE,
            Build.FINGERPRINT,
        ).joinToString(" ").lowercase()

        val propText = runCatching {
            val p = ProcessBuilder("getprop").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(2, TimeUnit.SECONDS)
            out.lowercase()
        }.getOrDefault("")

        val combined = "$fingerprint\n$propText"
        return when {
            combined.contains("ldplayer") || combined.contains("leidian") ||
                File("/system/bin/ldinit").exists() || File("/dev/ld_vbox").exists() -> EmulatorType.LDPLAYER
            combined.contains("mumu") || combined.contains("nemu") || combined.contains("netease") ||
                File("/system/bin/nemuVM").exists() -> EmulatorType.MUMU
            combined.contains("bluestacks") || combined.contains("bst") ||
                File("/data/bst").exists() -> EmulatorType.BLUESTACKS
            combined.contains("nox") || File("/system/bin/nox-prop").exists() -> EmulatorType.NOX
            combined.contains("memu") || combined.contains("microvirt") -> EmulatorType.MEMU
            Build.SUPPORTED_ABIS.any { it == "x86_64" || it == "x86" } ||
                combined.contains("vbox") || combined.contains("goldfish") || combined.contains("ranchu") ->
                EmulatorType.GENERIC_EMULATOR
            else -> EmulatorType.REAL_DEVICE
        }
    }

    private fun detectSuVariant(): Pair<SuVariant, List<String>?> {
        // Test Magisk / KSU / APatch mount-master flag first (preferred to see real /data/data mount namespace)
        val candidates = listOf(
            listOf("su", "-mm", "-c"),
            listOf("su", "-c"),
            listOf("/system/xbin/su", "-c"),
            listOf("/system/bin/su", "-c"),
        )
        for (cmd in candidates) {
            val res = runRawCommand(cmd + "id", timeoutSec = 5)
            if (res.ok && res.stdout.contains("uid=0")) {
                val verRes = runRawCommand(cmd + "su -v 2>/dev/null || su -V 2>/dev/null || echo generic", timeoutSec = 4)
                val v = verRes.stdout.lowercase()
                val variant = when {
                    v.contains("magisk") -> SuVariant.MAGISK
                    v.contains("kernelsu") || v.contains("ksu") -> SuVariant.KERNELSU
                    v.contains("apatch") -> SuVariant.APATCH
                    else -> SuVariant.GENERIC
                }
                return variant to cmd
            }
        }
        return SuVariant.NONE to null
    }

    private suspend fun detectInstalledPackages(
        knownPackages: List<String>,
        rootGranted: Boolean,
    ): List<InstalledGamePackage> {
        val pm = context.packageManager
        val found = linkedMapOf<String, InstalledGamePackage>()

        for (pkg in knownPackages) {
            try {
                val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                val label = pi.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                found[pkg] = InstalledGamePackage(
                    packageName = pkg,
                    appLabel = label,
                    versionName = pi.versionName ?: "?",
                    saveExists = false,
                )
            } catch (_: Throwable) {
                // Not installed or hidden by package visibility
            }
        }

        if (rootGranted) {
            // Also scan via `pm list packages` in root to catch regional variants even if hidden
            val pmList = execRootShell("pm list packages | grep -E 'chillyroom\\.zhmr|chillyroom\\.otherworld|tencent\\.tmgp\\.zhmr' || true")
            if (pmList.ok) {
                pmList.stdout.lineSequence()
                    .map { it.trim().removePrefix("package:") }
                    .filter { it.isNotBlank() }
                    .forEach { pkg ->
                        found.putIfAbsent(
                            pkg,
                            InstalledGamePackage(
                                packageName = pkg,
                                appLabel = "Otherworld Legends",
                                versionName = "installed",
                                saveExists = false,
                            )
                        )
                    }
            }

            // Stat each found package's save file
            return found.values.map { item ->
                val st = helperStat(playerPrefsPathFor(item.packageName), hash = false).getOrNull()
                item.copy(
                    saveExists = st?.exists == true,
                    saveSizeBytes = st?.size ?: 0L,
                )
            }.sortedWith(compareByDescending<InstalledGamePackage> { it.saveExists }.thenBy { it.packageName })
        }

        return found.values.toList()
    }

    // ==================== Persistent Helper Daemon ====================

    private fun ensureDaemonLocked(): Boolean {
        val existing = daemonProc
        if (existing != null && existing.isAlive && daemonWriter != null && daemonReader != null) {
            return true
        }
        closeDaemonLocked()
        val helperPath = helperBinaryPath ?: ensureHelperExtracted().getOrNull()?.absolutePath ?: return false
        val suCmd = suArgsCache ?: detectSuVariant().second ?: return false

        return try {
            val baseCmd = suCmd.dropLast(1) // drop "-c"
            val proc = ProcessBuilder(baseCmd + listOf("-c", "'$helperPath' serve"))
                .redirectErrorStream(false)
                .start()
            daemonProc = proc
            daemonWriter = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
            daemonReader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
            true
        } catch (_: Throwable) {
            closeDaemonLocked()
            false
        }
    }

    private fun closeDaemonLocked() {
        runCatching { daemonWriter?.close() }
        runCatching { daemonReader?.close() }
        runCatching { daemonProc?.destroy() }
        daemonWriter = null
        daemonReader = null
        daemonProc = null
    }

    private suspend inline fun <reified T> sendHelperRequest(
        requestJson: String,
        oneShotArgs: String,
    ): Result<T> = withContext(Dispatchers.IO) {
        daemonMutex.withLock {
            // Try fast persistent daemon first
            val rawLine: String? = if (ensureDaemonLocked()) {
                try {
                    daemonWriter!!.write(requestJson)
                    daemonWriter!!.newLine()
                    daemonWriter!!.flush()
                    daemonReader!!.readLine()
                } catch (_: Throwable) {
                    closeDaemonLocked()
                    null
                }
            } else {
                null
            }

            val responseText = rawLine ?: run {
                val helperPath = helperBinaryPath
                    ?: ensureHelperExtracted().getOrElse { return@withContext Result.failure(it) }.absolutePath
                val res = execRootShell("'$helperPath' $oneShotArgs")
                if (!res.ok && res.stdout.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("owledit-helper failed (${res.exitCode}): ${res.stderr}")
                    )
                }
                res.stdout.lineSequence().lastOrNull { it.trim().startsWith("{") } ?: res.stdout
            }

            runCatching {
                val env = json.decodeFromString<HelperEnvelope<T>>(responseText.trim())
                if (!env.ok || env.data == null) {
                    throw IllegalStateException(env.error ?: "Helper returned error")
                }
                env.data
            }
        }
    }

    suspend fun helperPing(): Result<HelperPingInfo> {
        val req = buildJsonObject { put("cmd", "ping") }.toString()
        return sendHelperRequest(req, "ping")
    }

    suspend fun helperStat(path: String, hash: Boolean = false): Result<HelperStatInfo> {
        val req = buildJsonObject {
            put("cmd", "stat")
            put("path", path)
            put("hash", hash)
        }.toString()
        val hashFlag = if (hash) " --hash" else ""
        return sendHelperRequest(req, "stat ${shellQuote(path)}$hashFlag")
    }

    suspend fun helperCopy(src: String, dst: String, modeOctal: String? = null): Result<HelperStatInfo> {
        val req = buildJsonObject {
            put("cmd", "copy")
            put("src", src)
            put("dst", dst)
            if (modeOctal != null) put("mode_octal", modeOctal)
        }.toString()
        val modeArg = if (modeOctal != null) " $modeOctal" else ""
        return sendHelperRequest(req, "copy ${shellQuote(src)} ${shellQuote(dst)}$modeArg")
    }

    suspend fun helperAtomicWrite(
        src: String,
        dst: String,
        modeOctal: String,
        uid: Int,
        gid: Int,
    ): Result<HelperAtomicWriteResult> {
        val req = buildJsonObject {
            put("cmd", "atomic-write")
            put("src", src)
            put("dst", dst)
            put("mode_octal", modeOctal)
            put("uid", uid)
            put("gid", gid)
        }.toString()
        return sendHelperRequest(
            req,
            "atomic-write ${shellQuote(src)} ${shellQuote(dst)} $modeOctal $uid $gid"
        )
    }

    // ==================== High-level Game Save Operations ====================

    suspend fun forceStopGame(packageName: String): ShellResult = withContext(Dispatchers.IO) {
        execRootShell("am force-stop ${shellQuote(packageName)}")
    }

    suspend fun launchGame(packageName: String): ShellResult = withContext(Dispatchers.IO) {
        execRootShell("monkey -p ${shellQuote(packageName)} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
    }

    /**
     * Copies `/data/data/<pkg>/shared_prefs/<pkg>.v2.playerprefs.xml` into app staging dir
     * with world-readable temp permissions so `owledit-core` can open it directly via JNI.
     */
    suspend fun pullGameSaveToStaging(packageName: String): Result<File> = withContext(Dispatchers.IO) {
        val remotePath = playerPrefsPathFor(packageName)
        val stat = helperStat(remotePath, hash = false).getOrElse {
            return@withContext Result.failure(IllegalStateException("Không thể kiểm tra file save: ${it.message}"))
        }
        if (!stat.exists) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Không tìm thấy file save tại $remotePath. Hãy mở game ít nhất 1 lần để tạo dữ liệu."
                )
            )
        }
        val stagedFile = File(stagingDir(), "$packageName.v2.playerprefs.xml")
        helperCopy(remotePath, stagedFile.absolutePath, "0644").getOrElse {
            return@withContext Result.failure(IllegalStateException("Không thể đọc file save: ${it.message}"))
        }
        Result.success(stagedFile)
    }

    /**
     * 7-Step Safe Atomic Write Transaction back to `/data/data/<pkg>/shared_prefs/...`:
     * 1. Force-stop game (if enabled)
     * 2. Stat target (or parent dir) for original UID/GID/mode
     * 3. Auto-rollback snapshot of original save (if enabled)
     * 4. Atomic write + fsync + chown + chmod + SHA-256 read-back inside Rust `owledit-helper`
     * 5. Restore SELinux context (`restorecon`)
     */
    suspend fun pushStagedSaveAtomic(
        packageName: String,
        stagedFile: File,
        forceStop: Boolean,
        createRollbackSnapshot: Boolean,
    ): Result<HelperAtomicWriteResult> = withContext(Dispatchers.IO) {
        val targetPath = playerPrefsPathFor(packageName)

        if (forceStop) {
            forceStopGame(packageName)
        }

        val currentStat = helperStat(targetPath, hash = false).getOrNull()
        val (uid, gid, mode) = if (currentStat != null && currentStat.exists) {
            Triple(currentStat.uid, currentStat.gid, currentStat.modeOctal.ifBlank { "0660" })
        } else {
            val dirStat = helperStat(packageDataDir(packageName), hash = false).getOrNull()
            Triple(dirStat?.uid ?: 0, dirStat?.gid ?: 0, "0660")
        }

        if (createRollbackSnapshot && currentStat?.exists == true) {
            val rbFile = File(
                rollbackDir(),
                "rollback_${packageName}_${System.currentTimeMillis()}.xml"
            )
            helperCopy(targetPath, rbFile.absolutePath, "0644")
            pruneOldRollbacks(maxKeep = 12)
        }

        // Make sure staged file is readable by root helper
        stagedFile.setReadable(true, false)

        val writeRes = helperAtomicWrite(
            src = stagedFile.absolutePath,
            dst = targetPath,
            modeOctal = mode,
            uid = uid,
            gid = gid,
        ).getOrElse {
            return@withContext Result.failure(IllegalStateException("Ghi nguyên tử thất bại: ${it.message}"))
        }

        // Best-effort SELinux context restoration (crucial on Android 10–15 enforcing ROMs)
        execRootShell("restorecon -F ${shellQuote(targetPath)} 2>/dev/null || true")

        Result.success(writeRes)
    }

    fun listRollbacks(): List<RollbackRecord> {
        val dir = rollbackDir()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            ?.map { f ->
                val pkg = f.name
                    .removePrefix("rollback_")
                    .substringBeforeLast("_", missingDelimiterValue = "unknown")
                RollbackRecord(
                    filePath = f.absolutePath,
                    fileName = f.name,
                    packageName = pkg,
                    createdAtMs = f.lastModified(),
                    sizeBytes = f.length(),
                )
            }
            ?.sortedByDescending { it.createdAtMs }
            ?: emptyList()
    }

    private fun pruneOldRollbacks(maxKeep: Int) {
        val files = rollbackDir().listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size > maxKeep) {
            files.drop(maxKeep).forEach { runCatching { it.delete() } }
        }
    }

    // ==================== SSAID Operations ====================

    suspend fun pullSsaidToStaging(): Result<File> = withContext(Dispatchers.IO) {
        val st = helperStat(SSAID_SYSTEM_PATH, hash = false).getOrElse {
            return@withContext Result.failure(IllegalStateException("Không thể kiểm tra settings_ssaid.xml: ${it.message}"))
        }
        if (!st.exists) {
            return@withContext Result.failure(
                IllegalStateException("Không tìm thấy $SSAID_SYSTEM_PATH trên thiết bị này.")
            )
        }
        val staged = File(stagingDir(), "settings_ssaid.xml")
        helperCopy(SSAID_SYSTEM_PATH, staged.absolutePath, "0644").getOrElse {
            return@withContext Result.failure(IllegalStateException("Không thể đọc settings_ssaid.xml: ${it.message}"))
        }
        // Automatically save one-time pristine backup of original SSAID if not yet present
        val origBackup = ssaidBackupFile()
        if (!origBackup.exists()) {
            runCatching { staged.copyTo(origBackup, overwrite = false) }
        }
        Result.success(staged)
    }

    suspend fun pushSsaidAtomic(stagedSsaidFile: File): Result<HelperAtomicWriteResult> = withContext(Dispatchers.IO) {
        val st = helperStat(SSAID_SYSTEM_PATH, hash = false).getOrNull()
        val uid = st?.uid ?: 1000
        val gid = st?.gid ?: 1000
        val mode = st?.modeOctal?.ifBlank { "0600" } ?: "0600"

        stagedSsaidFile.setReadable(true, false)
        val res = helperAtomicWrite(
            src = stagedSsaidFile.absolutePath,
            dst = SSAID_SYSTEM_PATH,
            modeOctal = mode,
            uid = uid,
            gid = gid,
        ).getOrElse {
            return@withContext Result.failure(IllegalStateException("Ghi settings_ssaid.xml thất bại: ${it.message}"))
        }
        execRootShell("restorecon -F ${shellQuote(SSAID_SYSTEM_PATH)} 2>/dev/null || true")
        Result.success(res)
    }

    suspend fun restoreOriginalSsaid(): Result<HelperAtomicWriteResult> = withContext(Dispatchers.IO) {
        val bak = ssaidBackupFile()
        if (!bak.exists()) {
            return@withContext Result.failure(IllegalStateException("Chưa có bản sao lưu settings_ssaid.xml gốc."))
        }
        pushSsaidAtomic(bak)
    }

    suspend fun softReboot(): ShellResult = withContext(Dispatchers.IO) {
        execRootShell("setprop ctl.restart zygote || killall system_server")
    }

    // ==================== Shell Helpers ====================

    suspend fun execRootShell(command: String, timeoutSec: Long = 15): ShellResult =
        withContext(Dispatchers.IO) {
            val suCmd = suArgsCache ?: detectSuVariant().second
                ?: return@withContext ShellResult(-1, "", "Root su not available")
            runRawCommand(suCmd + command, timeoutSec)
        }

    private fun runRawCommand(cmd: List<String>, timeoutSec: Long = 10): ShellResult {
        return try {
            val proc = ProcessBuilder(cmd).start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val tOut = Thread {
                proc.inputStream.bufferedReader().use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        stdout.append(line).append('\n')
                    }
                }
            }
            val tErr = Thread {
                proc.errorStream.bufferedReader().use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        stderr.append(line).append('\n')
                    }
                }
            }
            tOut.start()
            tErr.start()
            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return ShellResult(-2, stdout.toString(), "Command timeout after ${timeoutSec}s")
            }
            tOut.join(1000)
            tErr.join(1000)
            ShellResult(proc.exitValue(), stdout.toString(), stderr.toString())
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }

    private fun shellQuote(s: String): String = "'${s.replace("'", "'\"'\"'")}'"

    private fun File.readTextOrEmpty(): String =
        if (exists()) runCatching { readText() }.getOrDefault("") else ""
}
