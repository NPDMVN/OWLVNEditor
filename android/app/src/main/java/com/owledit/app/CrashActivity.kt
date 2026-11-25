package com.owledit.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owledit.app.ui.theme.OwlEditorTheme

class CrashActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TRACE = "extra_crash_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "Không có thông tin lỗi."

        setContent {
            OwlEditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "OWLVNEditor encountered an unexpected error / gặp sự cố",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Copy the log below for troubleshooting or relaunch OWLVNEditor.\nSao chép log bên dưới để kiểm tra hoặc mở lại ứng dụng.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            Text(
                                text = trace,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("OWLVNEditor Crash", trace))
                                    Toast.makeText(this@CrashActivity, "Log copied / Đã sao chép log", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Copy Log")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val restart = Intent(this@CrashActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    startActivity(restart)
                                    finish()
                                }
                            ) {
                                Text("Relaunch OWLVNEditor")
                            }
                        }
                    }
                }
            }
        }
    }
}
