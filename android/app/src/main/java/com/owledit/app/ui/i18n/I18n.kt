package com.owledit.app.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.owledit.core.model.EmulatorType
import com.owledit.core.model.SuVariant

val LocalIsEnglish = staticCompositionLocalOf { false }

@Composable
fun tr(vi: String, en: String): String = if (LocalIsEnglish.current) en else vi

fun tr(isEn: Boolean, vi: String, en: String): String = if (isEn) en else vi

fun SuVariant.localizedLabel(isEn: Boolean): String = when (this) {
    SuVariant.MAGISK -> "Magisk (su -mm)"
    SuVariant.KERNELSU -> "KernelSU"
    SuVariant.APATCH -> "APatch"
    SuVariant.GENERIC -> if (isEn) "Root SU (Emulator / SuperSU)" else "Root SU (Giả lập / SuperSU)"
    SuVariant.NONE -> if (isEn) "Root Not Detected" else "Chưa phát hiện Root"
}

fun EmulatorType.localizedLabel(isEn: Boolean): String = when (this) {
    EmulatorType.LDPLAYER -> "LDPlayer (PC Emulator)"
    EmulatorType.MUMU -> "MuMu Player (PC Emulator)"
    EmulatorType.BLUESTACKS -> "BlueStacks (PC Emulator)"
    EmulatorType.NOX -> "NoxPlayer (PC Emulator)"
    EmulatorType.MEMU -> "MEmu Play (PC Emulator)"
    EmulatorType.GENERIC_EMULATOR -> if (isEn) "Android Emulator (x86/x86_64)" else "Giả lập Android (x86/x86_64)"
    EmulatorType.REAL_DEVICE -> if (isEn) "Physical Android Device" else "Thiết bị Android thật"
}
