package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.Candidate
import com.example.data.local.ExamBatch
import com.example.ui.components.AttendanceBadge
import com.example.ui.dialogs.AddBatchDialog
import com.example.ui.dialogs.AddCandidateDialog
import com.example.ui.theme.AssessorBlue
import com.example.ui.theme.AssessorBlueLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.viewmodel.ExamViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExamViewModel,
    modifier: Modifier = Modifier
) {
    val activeBatch by viewModel.activeBatch.collectAsState()
    val allBatches by viewModel.allBatches.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val filteredCandidates by viewModel.filteredCandidates.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val isSupabaseConfigured by viewModel.isSupabaseConfigured.collectAsState()

    var showBatchMenu by remember { mutableStateOf(false) }
    var showAddBatchDialog by remember { mutableStateOf(false) }
    var showAddCandidateDialog by remember { mutableStateOf(false) }
    var showSupabaseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Exam Assessor",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = activeBatch?.trade ?: "Real-time Attendance & Sub-50KB Compressor",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Batch switcher
                    IconButton(
                        onClick = { showBatchMenu = true },
                        modifier = Modifier.testTag("btn_switch_batch")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch Session")
                    }
                    DropdownMenu(
                        expanded = showBatchMenu,
                        onDismissRequest = { showBatchMenu = false }
                    ) {
                        allBatches.forEach { b ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(b.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${b.trade} • ${b.examDate}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectBatch(b.id)
                                    showBatchMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = AssessorBlue)
                                    Spacer(Modifier.width(8.dp))
                                    Text("New Exam Session", color = AssessorBlue, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                showBatchMenu = false
                                showAddBatchDialog = true
                            }
                        )
                    }

                    // Supabase Cloud Sync Icon
                    IconButton(
                        onClick = { showSupabaseDialog = true },
                        modifier = Modifier.testTag("btn_supabase_cloud")
                    ) {
                        Box {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Supabase Cloud Database",
                                tint = if (isSupabaseConfigured) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSupabaseConfigured) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7))
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    // Direct PC Transfer Icon
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.PC_TRANSFER) },
                        modifier = Modifier.testTag("btn_pc_transfer_top")
                    ) {
                        Box {
                            Icon(
                                Icons.Default.Computer,
                                contentDescription = "PC Transfer",
                                tint = if (isServerRunning) SuccessGreen else MaterialTheme.colorScheme.onSurface
                            )
                            if (isServerRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCandidateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log Student") },
                containerColor = AssessorBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_student")
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Exam Header Card
            item {
                activeBatch?.let { batch ->
                    ExamInfoBanner(
                        batch = batch,
                        totalCandidates = candidates.size,
                        presentCount = candidates.count { it.attendanceStatus == "PRESENT" },
                        onShareBatchZip = { viewModel.shareBatchZip() },
                        onOpenPcTransfer = { viewModel.navigateTo(Screen.PC_TRANSFER) },
                        onOpenSupabase = { showSupabaseDialog = true },
                        isServerRunning = isServerRunning,
                        isSupabaseConfigured = isSupabaseConfigured
                    )
                }
            }

            // Search & Filter Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Search by Roll No, Name, or CNIC...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_candidates_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterStatus == "ALL",
                            onClick = { viewModel.filterStatus.value = "ALL" },
                            label = { Text("All (${candidates.size})") },
                            modifier = Modifier.testTag("filter_all")
                        )
                        FilterChip(
                            selected = filterStatus == "PRESENT",
                            onClick = { viewModel.filterStatus.value = "PRESENT" },
                            label = { Text("Present (${candidates.count { it.attendanceStatus == "PRESENT" }})") },
                            modifier = Modifier.testTag("filter_present")
                        )
                        FilterChip(
                            selected = filterStatus == "ABSENT",
                            onClick = { viewModel.filterStatus.value = "ABSENT" },
                            label = { Text("Absent (${candidates.count { it.attendanceStatus == "ABSENT" }})") },
                            modifier = Modifier.testTag("filter_absent")
                        )
                    }
                }
            }

            // Candidates List
            if (filteredCandidates.isEmpty()) {
                item {
                    EmptyCandidateView(onAddClick = { showAddCandidateDialog = true })
                }
            } else {
                items(filteredCandidates, key = { it.id }) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        onCardClick = { viewModel.selectCandidate(candidate) },
                        onToggleAttendance = { viewModel.toggleCandidateAttendance(candidate) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(64.dp))
            }
        }
    }

    // Dialogs
    if (showAddBatchDialog) {
        AddBatchDialog(
            onDismiss = { showAddBatchDialog = false },
            onConfirm = { title, trade, center, assessor, date, targetKb, format ->
                viewModel.createBatch(title, trade, center, assessor, date, targetKb, format)
                showAddBatchDialog = false
            }
        )
    }

    if (showAddCandidateDialog) {
        AddCandidateDialog(
            onDismiss = { showAddCandidateDialog = false },
            onConfirm = { roll, name, cnic, status, theory, practical, remarks ->
                viewModel.saveCandidate(
                    rollNo = roll,
                    name = name,
                    cnic = cnic,
                    status = status,
                    theoryMarks = theory,
                    practicalMarks = practical,
                    remarks = remarks
                )
                showAddCandidateDialog = false
            }
        )
    }

    if (showSupabaseDialog) {
        com.example.ui.dialogs.SupabaseSyncDialog(
            viewModel = viewModel,
            onDismiss = { showSupabaseDialog = false }
        )
    }
}

@Composable
fun ExamInfoBanner(
    batch: ExamBatch,
    totalCandidates: Int,
    presentCount: Int,
    onShareBatchZip: () -> Unit,
    onOpenPcTransfer: () -> Unit,
    onOpenSupabase: () -> Unit,
    isServerRunning: Boolean,
    isSupabaseConfigured: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = AssessorBlue
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("exam_info_banner")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Official Session",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = Color(0xFF22C55E).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "< 50 KB Auto-Compressor",
                        color = Color(0xFF86EFAC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column {
                Text(
                    text = batch.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${batch.centerName} • ${batch.examDate}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                Text(
                    text = "Assessor: ${batch.assessorName} | Trade: ${batch.trade}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }

            // Real-Time Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$totalCandidates",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Candidates",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$presentCount",
                            color = Color(0xFF86EFAC),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Present",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${totalCandidates - presentCount}",
                            color = Color(0xFFFCA5A5),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Absent",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Quick Transfer & Cloud actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPcTransfer,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("btn_share_pc_card")
                ) {
                    Icon(
                        if (isServerRunning) Icons.Default.Wifi else Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isServerRunning) "PC ON" else "Share PC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onOpenSupabase,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.1f).testTag("btn_supabase_sync_banner")
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isSupabaseConfigured) "Supabase ✓" else "Supabase",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onShareBatchZip,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("btn_export_zip_card")
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Export ZIP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CandidateCard(
    candidate: Candidate,
    onCardClick: () -> Unit,
    onToggleAttendance: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("candidate_card_${candidate.rollNo}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Student Icon badge
            Surface(
                shape = CircleShape,
                color = AssessorBlue.copy(alpha = 0.1f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = AssessorBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = candidate.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Roll: ${candidate.rollNo} • CNIC: ${candidate.cnic}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = candidate.folderName.ifBlank { "${candidate.rollNo}_${candidate.name}" },
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            // Attendance Toggle and arrow
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AttendanceBadge(
                    status = candidate.attendanceStatus,
                    onClick = onToggleAttendance
                )

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyCandidateView(onAddClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No candidates found",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Log students to take compressed photos and verify attendance in real-time.",
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            OutlinedButton(
                onClick = onAddClick,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Log First Student")
            }
        }
    }
}
