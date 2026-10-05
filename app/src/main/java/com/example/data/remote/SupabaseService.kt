package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.Candidate
import com.example.data.local.ExamBatch
import com.example.data.local.StudentPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class SupabaseConfig(
    val url: String,
    val apiKey: String,
    val bucketName: String = "exam-photos"
)

sealed interface SyncProgress {
    data class Progress(val current: Int, val total: Int, val message: String) : SyncProgress
    data class Success(val message: String) : SyncProgress
    data class Error(val error: String) : SyncProgress
}

class SupabaseService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_config", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getConfig(): SupabaseConfig {
        val url = prefs.getString("supabase_url", "") ?: ""
        val apiKey = prefs.getString("supabase_api_key", "") ?: ""
        val bucket = prefs.getString("supabase_bucket", "exam-photos") ?: "exam-photos"
        return SupabaseConfig(url.trim().removeSuffix("/"), apiKey.trim(), bucket.trim())
    }

    fun saveConfig(url: String, apiKey: String, bucketName: String = "exam-photos") {
        prefs.edit()
            .putString("supabase_url", url.trim().removeSuffix("/"))
            .putString("supabase_api_key", apiKey.trim())
            .putString("supabase_bucket", bucketName.trim().ifBlank { "exam-photos" })
            .apply()
    }

    fun isConfigured(): Boolean {
        val config = getConfig()
        return config.url.isNotBlank() && config.apiKey.isNotBlank()
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (config.url.isBlank() || config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase URL and API Key are required."))
        }

        try {
            // Check health or rest endpoint
            val request = Request.Builder()
                .url("${config.url}/rest/v1/")
                .addHeader("apikey", config.apiKey)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 200 || response.code == 404) {
                    Result.success("Connection to Supabase project verified!")
                } else {
                    Result.failure(Exception("Supabase responded with code ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncBatch(batch: ExamBatch): Result<Unit> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (!isConfigured()) return@withContext Result.failure(Exception("Supabase is not configured."))

        try {
            val json = JSONObject().apply {
                put("id", batch.id)
                put("title", batch.title)
                put("trade", batch.trade)
                put("center_name", batch.centerName)
                put("assessor_name", batch.assessorName)
                put("exam_date", batch.examDate)
                put("target_max_kb", batch.targetMaxKb)
                put("default_format", batch.defaultFormat)
                put("created_at", batch.createdAt)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${config.url}/rest/v1/exam_batches")
                .addHeader("apikey", config.apiKey)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val err = response.body?.string() ?: response.message
                    Result.failure(Exception("Failed to sync batch: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCandidate(candidate: Candidate): Result<Unit> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (!isConfigured()) return@withContext Result.failure(Exception("Supabase is not configured."))

        try {
            val json = JSONObject().apply {
                put("id", candidate.id)
                put("batch_id", candidate.batchId)
                put("roll_no", candidate.rollNo)
                put("name", candidate.name)
                put("cnic", candidate.cnic)
                put("attendance_status", candidate.attendanceStatus)
                put("theory_marks", candidate.theoryMarks)
                put("practical_marks", candidate.practicalMarks)
                put("remarks", candidate.remarks)
                put("folder_name", candidate.folderName)
                put("updated_at", candidate.updatedAt)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${config.url}/rest/v1/candidates")
                .addHeader("apikey", config.apiKey)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val err = response.body?.string() ?: response.message
                    Result.failure(Exception("Failed to sync candidate: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPhoto(
        photo: StudentPhoto,
        file: File,
        rollNo: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (!isConfigured()) return@withContext Result.failure(Exception("Supabase is not configured."))
        if (!file.exists()) return@withContext Result.failure(Exception("Photo file does not exist locally."))

        try {
            val mimeType = if (photo.format.equals("PNG", true)) "image/png" else "image/jpeg"
            val remotePath = "${photo.batchId}/$rollNo/${file.name}"

            // 1. Upload file to Supabase Storage
            val uploadUrl = "${config.url}/storage/v1/object/${config.bucketName}/$remotePath"
            val fileBody = file.asRequestBody(mimeType.toMediaType())

            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", config.apiKey)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("x-upsert", "true")
                .post(fileBody)
                .build()

            var publicUrl = "${config.url}/storage/v1/object/public/${config.bucketName}/$remotePath"

            client.newCall(uploadRequest).execute().use { response ->
                if (!response.isSuccessful && response.code != 200 && response.code != 201) {
                    val err = response.body?.string() ?: response.message
                    // If bucket not found, record clear warning
                    if (response.code == 404 || err.contains("Bucket not found")) {
                        return@withContext Result.failure(Exception("Storage bucket '${config.bucketName}' not found. Please create it in your Supabase Storage dashboard."))
                    }
                }
            }

            // 2. Upsert photo metadata into 'student_photos' table
            val photoJson = JSONObject().apply {
                put("id", photo.id)
                put("candidate_id", photo.candidateId)
                put("batch_id", photo.batchId)
                put("photo_type", photo.photoType)
                put("title", photo.title)
                put("file_path", publicUrl)
                put("file_size_bytes", photo.fileSizeBytes)
                put("file_size_kb", photo.fileSizeKb)
                put("width", photo.width)
                put("height", photo.height)
                put("format", photo.format)
                put("captured_at", photo.capturedAt)
            }

            val metaRequest = Request.Builder()
                .url("${config.url}/rest/v1/student_photos")
                .addHeader("apikey", config.apiKey)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(photoJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(metaRequest).execute().use { response ->
                // metadata upserted
            }

            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun getRecommendedSqlSchema(bucketName: String = "exam-photos"): String {
            return """
-- 1. Create table for Exam Batches / Sessions
create table if not exists exam_batches (
    id bigint primary key,
    title text not null,
    trade text not null,
    center_name text not null,
    assessor_name text not null,
    exam_date text not null,
    target_max_kb int default 50,
    default_format text default 'JPG',
    created_at bigint not null
);

-- 2. Create table for Candidates
create table if not exists candidates (
    id bigint primary key,
    batch_id bigint references exam_batches(id) on delete cascade,
    roll_no text not null,
    name text not null,
    cnic text not null,
    attendance_status text default 'PRESENT',
    theory_marks text,
    practical_marks text,
    remarks text,
    folder_name text,
    updated_at bigint not null
);

-- 3. Create table for Compressed Student Photos (<50KB)
create table if not exists student_photos (
    id bigint primary key,
    candidate_id bigint references candidates(id) on delete cascade,
    batch_id bigint references exam_batches(id) on delete cascade,
    photo_type text not null,
    title text not null,
    file_path text not null,
    file_size_bytes bigint,
    file_size_kb real,
    width int,
    height int,
    format text default 'JPG',
    captured_at bigint not null
);

-- 4. Enable Row Level Security (RLS) or public access
alter table exam_batches enable row level security;
alter table candidates enable row level security;
alter table student_photos enable row level security;

-- Allow anon read/write policy for assessors
create policy "Allow all assessor operations on batches" on exam_batches for all using (true) with check (true);
create policy "Allow all assessor operations on candidates" on candidates for all using (true) with check (true);
create policy "Allow all assessor operations on photos" on student_photos for all using (true) with check (true);

-- 5. Storage Bucket for photos:
-- In Supabase Dashboard -> Storage -> Create new bucket named: '$bucketName' (set to Public)
            """.trimIndent()
        }
    }
}
