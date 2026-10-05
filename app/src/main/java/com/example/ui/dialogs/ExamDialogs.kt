package com.example.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.Candidate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddBatchDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        trade: String,
        center: String,
        assessor: String,
        date: String,
        targetKb: Int,
        format: String
    ) -> Unit
) {
    val today = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()) }
    var title by remember { mutableStateOf("NAVTTC Assessment Session") }
    var trade by remember { mutableStateOf("Sports Trainer") }
    var center by remember { mutableStateOf("DevCastle Vocational Training Institute") }
    var assessor by remember { mutableStateOf("Kousar Akbar") }
    var date by remember { mutableStateOf(today) }
    var targetKb by remember { mutableStateOf(50) }
    var format by remember { mutableStateOf("JPG") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Exam Batch",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam Session Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_title_input")
                )
                OutlinedTextField(
                    value = trade,
                    onValueChange = { trade = it },
                    label = { Text("Trade / Course") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_trade_input")
                )
                OutlinedTextField(
                    value = center,
                    onValueChange = { center = it },
                    label = { Text("Examination Center") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_center_input")
                )
                OutlinedTextField(
                    value = assessor,
                    onValueChange = { assessor = it },
                    label = { Text("Assessor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_assessor_input")
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Exam Date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_date_input")
                )

                Text(
                    text = "Compression Format (Strict < 50 KB):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == "JPG",
                        onClick = { format = "JPG" },
                        label = { Text("JPG (Recommended)") }
                    )
                    FilterChip(
                        selected = format == "PNG",
                        onClick = { format = "PNG" },
                        label = { Text("PNG") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, trade, center, assessor, date, targetKb, format)
                    }
                },
                modifier = Modifier.testTag("submit_create_batch")
            ) {
                Text("Create Session")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AddCandidateDialog(
    initialCandidate: Candidate? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        rollNo: String,
        name: String,
        cnic: String,
        status: String,
        theoryMarks: String,
        practicalMarks: String,
        remarks: String
    ) -> Unit
) {
    var rollNo by remember { mutableStateOf(initialCandidate?.rollNo ?: "") }
    var name by remember { mutableStateOf(initialCandidate?.name ?: "") }
    var cnic by remember { mutableStateOf(initialCandidate?.cnic ?: "") }
    var status by remember { mutableStateOf(initialCandidate?.attendanceStatus ?: "PRESENT") }
    var theoryMarks by remember { mutableStateOf(initialCandidate?.theoryMarks ?: "") }
    var practicalMarks by remember { mutableStateOf(initialCandidate?.practicalMarks ?: "") }
    var remarks by remember { mutableStateOf(initialCandidate?.remarks ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCandidate == null) "Log Student Attendance" else "Edit Candidate Details",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = rollNo,
                    onValueChange = { rollNo = it },
                    label = { Text("Roll Number (e.g. 1199917)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("candidate_roll_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Candidate Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("candidate_name_input")
                )
                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it },
                    label = { Text("CNIC / National ID (e.g. 33102-3917230-0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("candidate_cnic_input")
                )

                Text(
                    text = "Attendance Status:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == "PRESENT",
                        onClick = { status = "PRESENT" },
                        label = { Text("Present") },
                        modifier = Modifier.testTag("chip_status_present")
                    )
                    FilterChip(
                        selected = status == "ABSENT",
                        onClick = { status = "ABSENT" },
                        label = { Text("Absent") },
                        modifier = Modifier.testTag("chip_status_absent")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = theoryMarks,
                        onValueChange = { theoryMarks = it },
                        label = { Text("Theory (/20)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("theory_marks_input")
                    )
                    OutlinedTextField(
                        value = practicalMarks,
                        onValueChange = { practicalMarks = it },
                        label = { Text("Practical (/80)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("practical_marks_input")
                    )
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rollNo.isNotBlank() && name.isNotBlank()) {
                        onConfirm(rollNo, name, cnic, status, theoryMarks, practicalMarks, remarks)
                    }
                },
                modifier = Modifier.testTag("submit_candidate_btn")
            ) {
                Text(if (initialCandidate == null) "Log Student" else "Save Changes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
