package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.utils.CompressionResult
import com.example.utils.FileManager
import com.example.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ExamRepository(
    private val context: Context,
    private val examDao: ExamDao
) {
    fun getAllBatches(): Flow<List<ExamBatch>> = examDao.getAllBatches()

    suspend fun getBatchById(batchId: Long): ExamBatch? = examDao.getBatchById(batchId)

    suspend fun createBatch(batch: ExamBatch): Long = examDao.insertBatch(batch)

    suspend fun updateBatch(batch: ExamBatch) = examDao.updateBatch(batch)

    suspend fun deleteBatch(batch: ExamBatch) {
        withContext(Dispatchers.IO) {
            val batchDir = FileManager.getBatchDir(context, batch)
            if (batchDir.exists()) {
                batchDir.deleteRecursively()
            }
            examDao.deleteBatch(batch)
        }
    }

    fun getCandidatesForBatch(batchId: Long): Flow<List<Candidate>> =
        examDao.getCandidatesForBatch(batchId)

    suspend fun getCandidatesListForBatch(batchId: Long): List<Candidate> =
        examDao.getCandidatesListForBatch(batchId)

    suspend fun getAllPhotosForBatch(batchId: Long): List<StudentPhoto> =
        examDao.getAllPhotosForBatch(batchId)

    suspend fun getCandidateById(candidateId: Long): Candidate? =
        examDao.getCandidateById(candidateId)

    suspend fun saveCandidate(candidate: Candidate, batch: ExamBatch): Long {
        return withContext(Dispatchers.IO) {
            val folderName = candidate.folderName.ifBlank {
                FileManager.sanitizeFolderName("${candidate.rollNo}_${candidate.name}")
            }
            val candidateToSave = candidate.copy(folderName = folderName, updatedAt = System.currentTimeMillis())
            val id = if (candidate.id == 0L) {
                examDao.insertCandidate(candidateToSave)
            } else {
                examDao.updateCandidate(candidateToSave)
                candidate.id
            }
            val savedCandidate = candidateToSave.copy(id = id)
            val candidateDir = FileManager.getCandidateDir(context, batch, savedCandidate)
            FileManager.writeCandidateSummary(candidateDir, savedCandidate, batch)
            id
        }
    }

    suspend fun deleteCandidate(candidate: Candidate, batch: ExamBatch) {
        withContext(Dispatchers.IO) {
            val candidateDir = FileManager.getCandidateDir(context, batch, candidate)
            if (candidateDir.exists()) {
                candidateDir.deleteRecursively()
            }
            examDao.deleteCandidate(candidate)
        }
    }

    fun getPhotosForCandidate(candidateId: Long): Flow<List<StudentPhoto>> =
        examDao.getPhotosForCandidate(candidateId)

    suspend fun processAndSavePhoto(
        bitmap: Bitmap,
        candidate: Candidate,
        batch: ExamBatch,
        photoType: String,
        title: String,
        format: String = "JPG",
        targetMaxKb: Int = 50,
        enhanceDocument: Boolean = false
    ): CompressionResult = withContext(Dispatchers.IO) {
        val candidateDir = FileManager.getCandidateDir(context, batch, candidate)
        val ext = if (format.equals("PNG", true)) "png" else "jpg"

        val filePrefix = when (photoType) {
            "CNIC" -> "01_CNIC_Verification"
            "ATTENDANCE" -> "02_Exam_Attendance"
            "THEORY" -> "03_Theory_Paper"
            "PRACTICAL" -> "04_Practical_Paper"
            else -> "05_Evidence_${System.currentTimeMillis()}"
        }

        val targetFile = File(candidateDir, "${filePrefix}_${candidate.rollNo}.$ext")

        val result = ImageCompressor.compressToTargetSize(
            source = bitmap,
            targetFile = targetFile,
            targetMaxKb = targetMaxKb,
            format = format,
            enhanceForDocument = enhanceDocument
        )

        // Update database record for this photo
        val existingPhoto = examDao.getPhotoByType(candidate.id, photoType)
        if (existingPhoto != null) {
            val updated = existingPhoto.copy(
                filePath = result.file.absolutePath,
                fileSizeBytes = result.fileSizeBytes,
                fileSizeKb = result.fileSizeKb,
                width = result.width,
                height = result.height,
                format = result.format,
                capturedAt = System.currentTimeMillis()
            )
            examDao.updatePhoto(updated)
        } else {
            val newPhoto = StudentPhoto(
                candidateId = candidate.id,
                batchId = batch.id,
                photoType = photoType,
                title = title,
                filePath = result.file.absolutePath,
                fileSizeBytes = result.fileSizeBytes,
                fileSizeKb = result.fileSizeKb,
                width = result.width,
                height = result.height,
                format = result.format,
                capturedAt = System.currentTimeMillis()
            )
            examDao.insertPhoto(newPhoto)
        }

        FileManager.writeCandidateSummary(candidateDir, candidate, batch)

        result
    }

    suspend fun deletePhoto(photo: StudentPhoto) = withContext(Dispatchers.IO) {
        val file = File(photo.filePath)
        if (file.exists()) {
            file.delete()
        }
        examDao.deletePhoto(photo)
    }

    /**
     * Seeds initial demo data with the real-world assessor exam structure
     * (matching the NAVTTC Sports Trainer exam images provided by user).
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val existing = examDao.getBatchById(1L)
        if (existing == null) {
            val sampleBatch = ExamBatch(
                id = 1L,
                title = "NAVTTC Assessment Session - DevCastle Center",
                trade = "Sports Trainer",
                centerName = "DevCastle Vocational Training Institute Faisalabad",
                assessorName = "Kousar Akbar",
                examDate = "02/10/2026",
                targetMaxKb = 50,
                defaultFormat = "JPG"
            )
            val batchId = examDao.insertBatch(sampleBatch)

            // Candidate from uploaded photos
            val candidate1 = Candidate(
                id = 1L,
                batchId = batchId,
                rollNo = "1199917",
                name = "Manahal Naseer",
                cnic = "33102-3917230-0",
                attendanceStatus = "PRESENT",
                theoryMarks = "18 / 20",
                practicalMarks = "64 / 80",
                remarks = "ID verified, Practical Handball prevention program completed",
                folderName = "1199917_Manahal_Naseer"
            )
            val c1Id = examDao.insertCandidate(candidate1)
            val c1Dir = FileManager.getCandidateDir(context, sampleBatch, candidate1)
            FileManager.writeCandidateSummary(c1Dir, candidate1, sampleBatch)

            // Create initial placeholder clear sample images (< 50KB) for demo testing
            createSampleCompressedPhoto(
                candidate1, sampleBatch, "CNIC", "Candidate CNIC Verification",
                "CNIC & Student ID Verified\nRoll: 1199917\nManahal Naseer", Color.rgb(240, 248, 255)
            )
            createSampleCompressedPhoto(
                candidate1, sampleBatch, "ATTENDANCE", "Exam Hall Live Attendance",
                "Candidate Sitting in Exam Hall\nSports Trainer Assessment Session\nDevCastle Center", Color.rgb(245, 250, 245)
            )
            createSampleCompressedPhoto(
                candidate1, sampleBatch, "THEORY", "Theory Paper & Signatures",
                "NAVTTC Section A Theory\nMarks: 18/20\nCandidate & Examiner Signed", Color.rgb(255, 253, 240)
            )
            createSampleCompressedPhoto(
                candidate1, sampleBatch, "PRACTICAL", "Practical Paper (Section B)",
                "Handball Injury Prevention Program\nSection B Marks: 64/80\nPrincipal & Assessor Stamped", Color.rgb(255, 248, 248)
            )

            // Add candidate 2 for quick testing
            val candidate2 = Candidate(
                id = 2L,
                batchId = batchId,
                rollNo = "1199918",
                name = "Ahmad Raza",
                cnic = "33100-8451290-3",
                attendanceStatus = "PRESENT",
                theoryMarks = "16 / 20",
                practicalMarks = "70 / 80",
                remarks = "Practical session completed",
                folderName = "1199918_Ahmad_Raza"
            )
            val c2Id = examDao.insertCandidate(candidate2)
            val c2Dir = FileManager.getCandidateDir(context, sampleBatch, candidate2)
            FileManager.writeCandidateSummary(c2Dir, candidate2, sampleBatch)

            createSampleCompressedPhoto(
                candidate2, sampleBatch, "CNIC", "Candidate CNIC Verification",
                "Ahmad Raza (Roll: 1199918)\nCNIC 33100-8451290-3\nVerified", Color.rgb(240, 248, 255)
            )
        }
    }

    private suspend fun createSampleCompressedPhoto(
        candidate: Candidate,
        batch: ExamBatch,
        type: String,
        title: String,
        label: String,
        bgColor: Int
    ) {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val borderPaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, borderPaint)

        val headerPaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("EXAM VERIFICATION RECORD", width / 2f, 100f, headerPaint)

        val subPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Government of Pakistan / NAVTTC", width / 2f, 145f, subPaint)

        val textPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        val lines = label.split("\n")
        var y = 380f
        for (line in lines) {
            canvas.drawText(line, width / 2f, y, textPaint)
            y += 48f
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(22, 163, 74)
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("[ COMPRESSED < 50 KB FOR PORTAL UPLOAD ]", width / 2f, height - 80f, footerPaint)

        processAndSavePhoto(
            bitmap = bitmap,
            candidate = candidate,
            batch = batch,
            photoType = type,
            title = title,
            format = "JPG",
            targetMaxKb = 50,
            enhanceDocument = false
        )
    }
}
