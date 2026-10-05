package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    // Batches
    @Query("SELECT * FROM exam_batches ORDER BY createdAt DESC")
    fun getAllBatches(): Flow<List<ExamBatch>>

    @Query("SELECT * FROM exam_batches WHERE id = :batchId")
    suspend fun getBatchById(batchId: Long): ExamBatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: ExamBatch): Long

    @Update
    suspend fun updateBatch(batch: ExamBatch)

    @Delete
    suspend fun deleteBatch(batch: ExamBatch)

    // Candidates
    @Query("SELECT * FROM candidates WHERE batchId = :batchId ORDER BY rollNo ASC")
    fun getCandidatesForBatch(batchId: Long): Flow<List<Candidate>>

    @Query("SELECT * FROM candidates WHERE id = :candidateId")
    suspend fun getCandidateById(candidateId: Long): Candidate?

    @Query("SELECT * FROM candidates WHERE batchId = :batchId")
    suspend fun getCandidatesListForBatch(batchId: Long): List<Candidate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidate(candidate: Candidate): Long

    @Update
    suspend fun updateCandidate(candidate: Candidate)

    @Delete
    suspend fun deleteCandidate(candidate: Candidate)

    // Photos
    @Query("SELECT * FROM student_photos WHERE candidateId = :candidateId ORDER BY capturedAt ASC")
    fun getPhotosForCandidate(candidateId: Long): Flow<List<StudentPhoto>>

    @Query("SELECT * FROM student_photos WHERE candidateId = :candidateId")
    suspend fun getPhotosListForCandidate(candidateId: Long): List<StudentPhoto>

    @Query("SELECT * FROM student_photos WHERE batchId = :batchId")
    suspend fun getAllPhotosForBatch(batchId: Long): List<StudentPhoto>

    @Query("SELECT * FROM student_photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: Long): StudentPhoto?

    @Query("SELECT * FROM student_photos WHERE candidateId = :candidateId AND photoType = :photoType LIMIT 1")
    suspend fun getPhotoByType(candidateId: Long, photoType: String): StudentPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: StudentPhoto): Long

    @Update
    suspend fun updatePhoto(photo: StudentPhoto)

    @Delete
    suspend fun deletePhoto(photo: StudentPhoto)

    @Query("DELETE FROM student_photos WHERE candidateId = :candidateId AND photoType = :photoType")
    suspend fun deletePhotoByType(candidateId: Long, photoType: String)
}
