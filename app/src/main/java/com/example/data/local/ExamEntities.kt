package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exam_batches")
data class ExamBatch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val trade: String,
    val centerName: String,
    val assessorName: String,
    val examDate: String,
    val targetMaxKb: Int = 50,
    val defaultFormat: String = "JPG",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "candidates",
    foreignKeys = [
        ForeignKey(
            entity = ExamBatch::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("batchId"), Index("rollNo")]
)
data class Candidate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val batchId: Long,
    val rollNo: String,
    val name: String,
    val cnic: String,
    val attendanceStatus: String = "PRESENT", // PRESENT, ABSENT, PENDING
    val theoryMarks: String = "",
    val practicalMarks: String = "",
    val remarks: String = "",
    val folderName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "student_photos",
    foreignKeys = [
        ForeignKey(
            entity = Candidate::class,
            parentColumns = ["id"],
            childColumns = ["candidateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("candidateId")]
)
data class StudentPhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val candidateId: Long,
    val batchId: Long,
    val photoType: String, // CNIC, ATTENDANCE, THEORY, PRACTICAL, OTHER
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val fileSizeKb: Float,
    val width: Int = 0,
    val height: Int = 0,
    val format: String = "JPG",
    val capturedAt: Long = System.currentTimeMillis()
)
