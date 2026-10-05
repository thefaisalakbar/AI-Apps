package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.Candidate
import com.example.data.local.StudentPhoto
import com.example.ui.components.AttendanceBadge
import com.example.ui.components.SizeBadge
import com.example.ui.dialogs.AddCandidateDialog
import com.example.ui.theme.AssessorBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.viewmodel.ExamViewModel
import java.io.File

data class RequiredPhotoSlot(
    val type: String,
    val title: String,
    val description: String
)

val REQUIRED_SLOTS = listOf(
    RequiredPhotoSlot("CNIC", "1. Candidate with CNIC", "Face + Original ID card verification"),
    RequiredPhotoSlot("ATTENDANCE", "2. Exam Hall Attendance", "Candidate sitting & writing exam in hall"),
    RequiredPhotoSlot("THEORY", "3. Theory Paper", "Section A paper with signatures & roll no"),
    RequiredPhotoSlot("PRACTICAL", "4. Practical Paper", "Section B task sheet & examiner stamps")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidateDetailScreen(
    viewModel: ExamViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val candidate by viewModel.selectedCandidate.collectAsState()
    val photos by viewModel.candidatePhotos.collectAsState()
    val batch by viewModel.activeBatch.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var fullScreenPhoto by remember { mutableStateOf<StudentPhoto?>(null) }

    if (candidate == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Candidate not found")
        }
        return
    }

    val activeCandidate = candidate!!
    val totalSizeKb = photos.sumOf { it.fileSizeKb.toDouble() }.toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeCandidate.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Roll: ${activeCandidate.rollNo} • ${photos.size} Photos",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }, modifier = Modifier.testTag("btn_back_detail")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.testTag("btn_edit_candidate")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Candidate")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.testTag("btn_delete_candidate")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Candidate", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.shareCandidateZip(activeCandidate) },
                        modifier = Modifier.weight(1f).testTag("btn_share_candidate_zip")
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Folder (ZIP)")
                    }

                    Button(
                        onClick = {
                            viewModel.initiatePhotoCapture(
                                candidate = activeCandidate,
                                photoType = "OTHER",
                                photoTitle = "Extra Exam Evidence"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AssessorBlue),
                        modifier = Modifier.weight(1f).testTag("btn_add_extra_photo")
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ Extra Photo")
                    }
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Student Profile Card
            item(span = { GridItemSpan(2) }) {
                CandidateProfileCard(
                    candidate = activeCandidate,
                    folderName = activeCandidate.folderName.ifBlank { "${activeCandidate.rollNo}_${activeCandidate.name}" },
                    totalSizeKb = totalSizeKb,
                    onToggleAttendance = { viewModel.toggleCandidateAttendance(activeCandidate) }
                )
            }

            // Photos Header
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mandatory Academic Photos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "All images automatically compressed to < 50 KB",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreenContainer
                    ) {
                        Text(
                            text = "${photos.size}/4 Completed",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Required photo slots (CNIC, ATTENDANCE, THEORY, PRACTICAL)
            items(REQUIRED_SLOTS, key = { it.type }) { slot ->
                val photo = photos.find { it.photoType == slot.type }
                PhotoSlotCard(
                    slot = slot,
                    photo = photo,
                    onCapture = {
                        viewModel.initiatePhotoCapture(
                            candidate = activeCandidate,
                            photoType = slot.type,
                            photoTitle = slot.title
                        )
                    },
                    onView = { photo?.let { fullScreenPhoto = it } },
                    onDelete = { photo?.let { viewModel.deletePhoto(it) } }
                )
            }

            // Any extra photos taken
            val extraPhotos = photos.filter { p -> REQUIRED_SLOTS.none { it.type == p.photoType } }
            if (extraPhotos.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Additional Evidence Photos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(extraPhotos, key = { it.id }) { photo ->
                    PhotoSlotCard(
                        slot = RequiredPhotoSlot(photo.photoType, photo.title, "Evidence Document"),
                        photo = photo,
                        onCapture = {
                            viewModel.initiatePhotoCapture(
                                candidate = activeCandidate,
                                photoType = photo.photoType,
                                photoTitle = photo.title
                            )
                        },
                        onView = { fullScreenPhoto = photo },
                        onDelete = { viewModel.deletePhoto(photo) }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Full screen photo viewer
    fullScreenPhoto?.let { photo ->
        FullScreenImageDialog(
            photo = photo,
            onDismiss = { fullScreenPhoto = null },
            onShare = {
                val file = File(photo.filePath)
                if (file.exists()) {
                    val mime = if (photo.format.equals("PNG", true)) "image/png" else "image/jpeg"
                    viewModel.webServer.let {
                        com.example.utils.FileManager.shareFile(
                            viewModel.getApplication(),
                            file,
                            mime,
                            "Share ${photo.title}"
                        )
                    }
                }
            }
        )
    }

    // Edit Candidate Dialog
    if (showEditDialog) {
        AddCandidateDialog(
            initialCandidate = activeCandidate,
            onDismiss = { showEditDialog = false },
            onConfirm = { roll, name, cnic, status, theory, practical, remarks ->
                viewModel.saveCandidate(
                    id = activeCandidate.id,
                    rollNo = roll,
                    name = name,
                    cnic = cnic,
                    status = status,
                    theoryMarks = theory,
                    practicalMarks = practical,
                    remarks = remarks
                )
                showEditDialog = false
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Candidate?") },
            text = { Text("This will permanently remove ${activeCandidate.name} and all captured photos from this session.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCandidate(activeCandidate)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CandidateProfileCard(
    candidate: Candidate,
    folderName: String,
    totalSizeKb: Float,
    onToggleAttendance: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("candidate_profile_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = candidate.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Roll: ${candidate.rollNo} • CNIC: ${candidate.cnic}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AttendanceBadge(
                    status = candidate.attendanceStatus,
                    onClick = onToggleAttendance
                )
            }

            // Evaluation row if available
            if (candidate.theoryMarks.isNotBlank() || candidate.practicalMarks.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        if (candidate.theoryMarks.isNotBlank()) {
                            Text(
                                text = "Theory: ${candidate.theoryMarks}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (candidate.practicalMarks.isNotBlank()) {
                            Text(
                                text = "Practical: ${candidate.practicalMarks}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Folder destination tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = AssessorBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = folderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AssessorBlue
                    )
                }

                Text(
                    text = "Total: ${String.format(java.util.Locale.US, "%.1f KB", totalSizeKb)}",
                    fontSize = 11.sp,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PhotoSlotCard(
    slot: RequiredPhotoSlot,
    photo: StudentPhoto?,
    onCapture: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("photo_slot_${slot.type.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = slot.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )

            // Image Preview or Empty Placeholder
            if (photo != null && File(photo.filePath).exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onView() }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(photo.filePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = slot.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Zoom icon overlay
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "View Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Verified badge on bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        SizeBadge(sizeKb = photo.fileSizeKb)
                    }
                }

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCapture,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retake", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                // Empty photo slot
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onCapture() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Capture Photo",
                            tint = AssessorBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Take Photo",
                            color = AssessorBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "< 50 KB Auto",
                            color = SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = slot.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(
    photo: StudentPhoto,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = photo.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f KB", photo.fileSizeKb)} • ${photo.width}x${photo.height} • ${photo.format}",
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share Photo")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(photo.filePath))
                            .build(),
                        contentDescription = photo.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
