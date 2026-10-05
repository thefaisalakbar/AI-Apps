package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.local.Candidate
import com.example.data.local.ExamBatch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileManager {

    fun getBatchesRootDir(context: Context): File {
        val dir = File(context.filesDir, "ExamBatches")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBatchDir(context: Context, batch: ExamBatch): File {
        val sanitized = sanitizeFolderName("${batch.id}_${batch.title}_${batch.trade}")
        val dir = File(getBatchesRootDir(context), sanitized)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCandidateDir(context: Context, batch: ExamBatch, candidate: Candidate): File {
        val batchDir = getBatchDir(context, batch)
        val folderName = if (candidate.folderName.isNotBlank()) {
            candidate.folderName
        } else {
            sanitizeFolderName("${candidate.rollNo}_${candidate.name}")
        }
        val dir = File(batchDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .trim()
            .replace("\\s+".toRegex(), "_")
    }

    fun writeCandidateSummary(
        candidateDir: File,
        candidate: Candidate,
        batch: ExamBatch
    ) {
        val summaryFile = File(candidateDir, "candidate_info.txt")
        val content = """
            ==================================================
            EXAM ATTENDANCE & VERIFICATION RECORD
            ==================================================
            Exam: ${batch.title}
            Trade: ${batch.trade}
            Exam Center: ${batch.centerName}
            Assessor: ${batch.assessorName}
            Exam Date: ${batch.examDate}
            --------------------------------------------------
            Candidate Roll No: ${candidate.rollNo}
            Candidate Name: ${candidate.name}
            CNIC: ${candidate.cnic}
            Attendance Status: ${candidate.attendanceStatus}
            Theory Marks: ${candidate.theoryMarks.ifBlank { "N/A" }}
            Practical Marks: ${candidate.practicalMarks.ifBlank { "N/A" }}
            Remarks: ${candidate.remarks.ifBlank { "Verified" }}
            Folder: ${candidateDir.name}
            Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
            ==================================================
        """.trimIndent()
        try {
            summaryFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createBatchZip(context: Context, batch: ExamBatch): File? {
        val batchDir = getBatchDir(context, batch)
        if (!batchDir.exists() || batchDir.listFiles().isNullOrEmpty()) {
            return null
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(exportDir, "${sanitizeFolderName(batch.title)}_$timeStamp.zip")

        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                zipDirectory(batchDir, batchDir, zos)
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createCandidateZip(context: Context, batch: ExamBatch, candidate: Candidate): File? {
        val candidateDir = getCandidateDir(context, batch, candidate)
        if (!candidateDir.exists()) return null

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val zipFile = File(exportDir, "${sanitizeFolderName("${candidate.rollNo}_${candidate.name}")}.zip")

        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                zipDirectory(candidateDir, candidateDir, zos)
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun zipDirectory(root: File, source: File, zos: ZipOutputStream) {
        val files = source.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(root, file, zos)
            } else {
                val relativePath = root.toURI().relativize(file.toURI()).path
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                BufferedInputStream(FileInputStream(file)).use { bis ->
                    bis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String = "Share Exam Files") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun copyBatchToDocuments(context: Context, batch: ExamBatch): Result<File> {
        return try {
            val sourceDir = getBatchDir(context, batch)
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetBase = File(publicDocs, "ExamAssessor")
            val targetBatchDir = File(targetBase, sourceDir.name)
            if (!targetBatchDir.exists()) targetBatchDir.mkdirs()

            sourceDir.copyRecursively(targetBatchDir, overwrite = true)
            Result.success(targetBatchDir)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
