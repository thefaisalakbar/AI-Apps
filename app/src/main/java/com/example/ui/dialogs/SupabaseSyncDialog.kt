package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.SyncProgress
import com.example.ui.theme.AssessorBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.viewmodel.ExamViewModel

@Composable
fun SupabaseSyncDialog(
    viewModel: ExamViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.supabaseConfig.collectAsState()
    val isConfigured by viewModel.isSupabaseConfigured.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val testStatus by viewModel.testConnectionStatus.collectAsState()

    var selectedTab by remember { mutableIntStateOf(if (isConfigured) 0 else 1) }

    var urlInput by remember { mutableStateOf(config.url) }
    var keyInput by remember { mutableStateOf(config.apiKey) }
    var bucketInput by remember { mutableStateOf(config.bucketName) }

    fun copySql() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val sql = com.example.data.remote.SupabaseService.getRecommendedSqlSchema(bucketInput)
        clipboard.setPrimaryClip(ClipData.newPlainText("Supabase Schema SQL", sql))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF3ECF8E).copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Supabase Cloud Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isConfigured) "Connected to Supabase" else "Offline-first • Ready to connect",
                        fontSize = 12.sp,
                        color = if (isConfigured) SuccessGreen else Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info Box explaining where data is saved right now
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = AssessorBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Current Storage: Safe on Device (Offline)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AssessorBlue
                            )
                        }
                        Text(
                            text = "All student records, attendance, and sub-50KB photos are persistently saved on your phone in the Room SQLite database and student folders. Connecting your Supabase account syncs everything to the cloud!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Cloud Sync", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("API Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("SQL Schema", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Cloud Sync Tab
                        if (!isConfigured) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Supabase credentials not configured yet.",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Button(
                                    onClick = { selectedTab = 1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = AssessorBlue)
                                ) {
                                    Text("Enter Supabase URL & Key")
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SuccessGreenContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen)
                                        Column {
                                            Text("Supabase Project Connected", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen)
                                            Text(config.url, fontSize = 11.sp, color = SuccessGreen.copy(alpha = 0.8f))
                                        }
                                    }
                                }

                                // Sync Action
                                Button(
                                    onClick = { viewModel.syncCurrentBatchToSupabase() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    modifier = Modifier.fillMaxWidth().testTag("btn_sync_now_supabase")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sync Current Exam to Supabase Now", fontWeight = FontWeight.Bold)
                                }

                                // Sync Progress Display
                                syncProgress?.let { prog ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            when (prog) {
                                                is SyncProgress.Progress -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Syncing to Supabase...", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                        Text("${prog.current}/${prog.total}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AssessorBlue)
                                                    }
                                                    LinearProgressIndicator(
                                                        progress = {
                                                            if (prog.total > 0) prog.current.toFloat() / prog.total.toFloat() else 0f
                                                        },
                                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                                    )
                                                    Text(prog.message, fontSize = 11.sp, color = Color.Gray)
                                                }
                                                is SyncProgress.Success -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                                        Text(prog.message, color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                }
                                                is SyncProgress.Error -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                                        Text(prog.error, color = Color.Red, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // API Settings Tab
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Supabase Project URL") },
                                placeholder = { Text("https://your-project.supabase.co") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("supabase_url_input")
                            )

                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                label = { Text("Supabase Anon / Service Key") },
                                placeholder = { Text("eyJhbGciOiJIUzI1...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("supabase_key_input")
                            )

                            OutlinedTextField(
                                value = bucketInput,
                                onValueChange = { bucketInput = it },
                                label = { Text("Storage Bucket Name") },
                                placeholder = { Text("exam-photos") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveSupabaseConfig(urlInput, keyInput, bucketInput)
                                        viewModel.testSupabaseConnection()
                                    },
                                    modifier = Modifier.weight(1f).testTag("btn_save_supabase_cfg")
                                ) {
                                    Text("Save & Test")
                                }
                            }

                            testStatus?.let { status ->
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (status.startsWith("Connected") || status.contains("verified", ignoreCase = true)) SuccessGreen else Color.Red
                                )
                            }
                        }
                    }

                    2 -> {
                        // SQL Schema Tab
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Run this in your Supabase SQL Editor once:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = com.example.data.remote.SupabaseService.getRecommendedSqlSchema(bucketInput),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8),
                                        maxLines = 14
                                    )
                                }
                            }

                            Button(
                                onClick = { copySql() },
                                colors = ButtonDefaults.buttonColors(containerColor = AssessorBlue),
                                modifier = Modifier.fillMaxWidth().testTag("btn_copy_sql")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy SQL Schema to Clipboard")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
