package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ExamAssessorApp
import com.example.data.local.Candidate
import com.example.data.local.ExamBatch
import com.example.data.local.StudentPhoto
import com.example.utils.FileManager
import com.example.utils.ImageCompressor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class ShareFile(val file: File, val mimeType: String, val title: String) : UiEvent
}

enum class Screen {
    HOME,
    CANDIDATE_DETAIL,
    PHOTO_CAPTURE,
    PC_TRANSFER,
    SETTINGS
}

data class CaptureState(
    val candidate: Candidate? = null,
    val photoType: String = "CNIC",
    val photoTitle: String = "Candidate CNIC Verification",
    val previewBitmap: Bitmap? = null,
    val originalSizeBytes: Long = 0,
    val targetMaxKb: Int = 50,
    val format: String = "JPG",
    val enhanceDocument: Boolean = false,
    val compressedTempFile: File? = null,
    val compressedSizeKb: Float = 0f,
    val isCompressing: Boolean = false
)

class ExamViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ExamAssessorApp
    private val repository = app.repository
    val webServer = app.webServer
    private val supabaseService = app.supabaseService

    // Supabase state
    private val _supabaseConfig = MutableStateFlow(supabaseService.getConfig())
    val supabaseConfig: StateFlow<com.example.data.remote.SupabaseConfig> = _supabaseConfig.asStateFlow()

    private val _isSupabaseConfigured = MutableStateFlow(supabaseService.isConfigured())
    val isSupabaseConfigured: StateFlow<Boolean> = _isSupabaseConfigured.asStateFlow()

    private val _syncProgress = MutableStateFlow<com.example.data.remote.SyncProgress?>(null)
    val syncProgress: StateFlow<com.example.data.remote.SyncProgress?> = _syncProgress.asStateFlow()

    private val _testConnectionStatus = MutableStateFlow<String?>(null)
    val testConnectionStatus: StateFlow<String?> = _testConnectionStatus.asStateFlow()

    // Navigation state
    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Screen navigation stack
    private val screenStack = mutableListOf<Screen>()

    // Selected Batch
    private val _selectedBatchId = MutableStateFlow<Long?>(null)
    val selectedBatchId: StateFlow<Long?> = _selectedBatchId.asStateFlow()

    // All batches
    val allBatches: StateFlow<List<ExamBatch>> = repository.getAllBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Batch
    val activeBatch: StateFlow<ExamBatch?> = combine(allBatches, _selectedBatchId) { batches, selectedId ->
        if (batches.isEmpty()) return@combine null
        if (selectedId != null) {
            batches.find { it.id == selectedId } ?: batches.first()
        } else {
            batches.first()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Candidates in active batch
    val candidates: StateFlow<List<Candidate>> = activeBatch.flatMapLatest { batch ->
        if (batch == null) flowOf(emptyList())
        else repository.getCandidatesForBatch(batch.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query & attendance filter
    val searchQuery = MutableStateFlow("")
    val filterStatus = MutableStateFlow("ALL") // ALL, PRESENT, ABSENT

    val filteredCandidates: StateFlow<List<Candidate>> = combine(
        candidates,
        searchQuery,
        filterStatus
    ) { list, query, filter ->
        list.filter { c ->
            val matchesQuery = query.isBlank() ||
                    c.name.contains(query, ignoreCase = true) ||
                    c.rollNo.contains(query, ignoreCase = true) ||
                    c.cnic.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                "PRESENT" -> c.attendanceStatus == "PRESENT"
                "ABSENT" -> c.attendanceStatus == "ABSENT"
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Candidate for Detail
    private val _selectedCandidateId = MutableStateFlow<Long?>(null)
    val selectedCandidateId: StateFlow<Long?> = _selectedCandidateId.asStateFlow()

    val selectedCandidate: StateFlow<Candidate?> = combine(candidates, _selectedCandidateId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Photos for selected candidate
    val candidatePhotos: StateFlow<List<StudentPhoto>> = _selectedCandidateId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getPhotosForCandidate(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Photo capture / compression state
    private val _captureState = MutableStateFlow(CaptureState())
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    // Web Server Status
    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    // One-time UI events
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            return true
        }
        return false
    }

    fun selectBatch(batchId: Long) {
        _selectedBatchId.value = batchId
    }

    fun selectCandidate(candidate: Candidate) {
        _selectedCandidateId.value = candidate.id
        navigateTo(Screen.CANDIDATE_DETAIL)
    }

    fun createBatch(
        title: String,
        trade: String,
        centerName: String,
        assessorName: String,
        examDate: String,
        targetMaxKb: Int = 50,
        defaultFormat: String = "JPG"
    ) {
        viewModelScope.launch {
            val batch = ExamBatch(
                title = title.trim(),
                trade = trade.trim(),
                centerName = centerName.trim(),
                assessorName = assessorName.trim(),
                examDate = examDate.trim(),
                targetMaxKb = targetMaxKb,
                defaultFormat = defaultFormat
            )
            val id = repository.createBatch(batch)
            _selectedBatchId.value = id
            _uiEvents.emit(UiEvent.ShowToast("Exam batch created successfully"))
        }
    }

    fun saveCandidate(
        id: Long = 0,
        rollNo: String,
        name: String,
        cnic: String,
        status: String = "PRESENT",
        theoryMarks: String = "",
        practicalMarks: String = "",
        remarks: String = ""
    ) {
        val batch = activeBatch.value ?: return
        viewModelScope.launch {
            val candidate = Candidate(
                id = id,
                batchId = batch.id,
                rollNo = rollNo.trim(),
                name = name.trim(),
                cnic = cnic.trim(),
                attendanceStatus = status,
                theoryMarks = theoryMarks.trim(),
                practicalMarks = practicalMarks.trim(),
                remarks = remarks.trim()
            )
            val savedId = repository.saveCandidate(candidate, batch)
            _uiEvents.emit(UiEvent.ShowToast("Candidate ${candidate.name} saved"))
            if (_selectedCandidateId.value == id && id != 0L) {
                // refreshed
            }
        }
    }

    fun toggleCandidateAttendance(candidate: Candidate) {
        val batch = activeBatch.value ?: return
        val newStatus = if (candidate.attendanceStatus == "PRESENT") "ABSENT" else "PRESENT"
        viewModelScope.launch {
            val updated = candidate.copy(attendanceStatus = newStatus)
            repository.saveCandidate(updated, batch)
            _uiEvents.emit(UiEvent.ShowToast("Marked ${candidate.name} as $newStatus"))
        }
    }

    fun deleteCandidate(candidate: Candidate) {
        val batch = activeBatch.value ?: return
        viewModelScope.launch {
            repository.deleteCandidate(candidate, batch)
            _uiEvents.emit(UiEvent.ShowToast("Candidate ${candidate.name} deleted"))
            if (_selectedCandidateId.value == candidate.id) {
                navigateBack()
            }
        }
    }

    // Photo capture setup
    fun initiatePhotoCapture(
        candidate: Candidate,
        photoType: String,
        photoTitle: String
    ) {
        val batch = activeBatch.value
        _captureState.value = CaptureState(
            candidate = candidate,
            photoType = photoType,
            photoTitle = photoTitle,
            targetMaxKb = batch?.targetMaxKb ?: 50,
            format = batch?.defaultFormat ?: "JPG",
            enhanceDocument = (photoType == "THEORY" || photoType == "PRACTICAL")
        )
        navigateTo(Screen.PHOTO_CAPTURE)
    }

    fun onImageSelectedForCapture(uri: Uri) {
        viewModelScope.launch {
            _captureState.value = _captureState.value.copy(isCompressing = true)
            val bitmap = ImageCompressor.loadBitmapFromUri(app, uri)
            if (bitmap != null) {
                // Calculate original size
                val stream = app.contentResolver.openInputStream(uri)
                val originalBytes = stream?.available()?.toLong() ?: 0L
                stream?.close()

                _captureState.value = _captureState.value.copy(
                    previewBitmap = bitmap,
                    originalSizeBytes = originalBytes
                )
                recomputeCompression()
            } else {
                _captureState.value = _captureState.value.copy(isCompressing = false)
                _uiEvents.emit(UiEvent.ShowToast("Failed to load image"))
            }
        }
    }

    fun onBitmapCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _captureState.value = _captureState.value.copy(
                previewBitmap = bitmap,
                originalSizeBytes = (bitmap.width * bitmap.height * 4).toLong(),
                isCompressing = true
            )
            recomputeCompression()
        }
    }

    fun updateCompressionSettings(
        targetMaxKb: Int? = null,
        format: String? = null,
        enhanceDocument: Boolean? = null
    ) {
        val current = _captureState.value
        _captureState.value = current.copy(
            targetMaxKb = targetMaxKb ?: current.targetMaxKb,
            format = format ?: current.format,
            enhanceDocument = enhanceDocument ?: current.enhanceDocument
        )
        if (_captureState.value.previewBitmap != null) {
            recomputeCompression()
        }
    }

    private fun recomputeCompression() {
        val state = _captureState.value
        val bitmap = state.previewBitmap ?: return

        viewModelScope.launch {
            _captureState.value = _captureState.value.copy(isCompressing = true)
            val tempFile = File(app.cacheDir, "preview_compressed.${if (state.format.equals("PNG", true)) "png" else "jpg"}")
            val result = ImageCompressor.compressToTargetSize(
                source = bitmap,
                targetFile = tempFile,
                targetMaxKb = state.targetMaxKb,
                format = state.format,
                enhanceForDocument = state.enhanceDocument
            )
            _captureState.value = _captureState.value.copy(
                compressedTempFile = result.file,
                compressedSizeKb = result.fileSizeKb,
                isCompressing = false
            )
        }
    }

    fun confirmAndSavePhoto() {
        val state = _captureState.value
        val bitmap = state.previewBitmap ?: return
        val candidate = state.candidate ?: return
        val batch = activeBatch.value ?: return

        viewModelScope.launch {
            val result = repository.processAndSavePhoto(
                bitmap = bitmap,
                candidate = candidate,
                batch = batch,
                photoType = state.photoType,
                title = state.photoTitle,
                format = state.format,
                targetMaxKb = state.targetMaxKb,
                enhanceDocument = state.enhanceDocument
            )

            _uiEvents.emit(UiEvent.ShowToast("Photo saved: ${String.format(java.util.Locale.US, "%.1f KB", result.fileSizeKb)} (<50KB)"))
            navigateBack()
        }
    }

    fun deletePhoto(photo: StudentPhoto) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            _uiEvents.emit(UiEvent.ShowToast("Photo deleted"))
        }
    }

    // Web Server Control
    fun toggleWebServer() {
        if (_isServerRunning.value) {
            webServer.stop()
            _isServerRunning.value = false
            _serverUrl.value = null
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("PC Transfer server stopped"))
            }
        } else {
            webServer.start(
                onStarted = { url ->
                    _isServerRunning.value = true
                    _serverUrl.value = url
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("PC Transfer active at $url"))
                    }
                },
                onError = { err ->
                    _isServerRunning.value = false
                    _serverUrl.value = null
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Server error: $err"))
                    }
                }
            )
        }
    }

    // ZIP & Share
    fun shareBatchZip() {
        val batch = activeBatch.value ?: return
        viewModelScope.launch {
            val zipFile = FileManager.createBatchZip(app, batch)
            if (zipFile != null && zipFile.exists()) {
                _uiEvents.emit(UiEvent.ShareFile(zipFile, "application/zip", "Share Exam Batch ${batch.title}"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("No candidate files or photos found to export"))
            }
        }
    }

    fun shareCandidateZip(candidate: Candidate) {
        val batch = activeBatch.value ?: return
        viewModelScope.launch {
            val zipFile = FileManager.createCandidateZip(app, batch, candidate)
            if (zipFile != null && zipFile.exists()) {
                _uiEvents.emit(UiEvent.ShareFile(zipFile, "application/zip", "Share Candidate ${candidate.rollNo} ${candidate.name}"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("No photos found for ${candidate.name}"))
            }
        }
    }

    fun exportToDocuments() {
        val batch = activeBatch.value ?: return
        viewModelScope.launch {
            val result = FileManager.copyBatchToDocuments(app, batch)
            result.onSuccess { dir ->
                _uiEvents.emit(UiEvent.ShowToast("Saved to Documents/${dir.name} for USB PC Transfer"))
            }.onFailure { e ->
                _uiEvents.emit(UiEvent.ShowToast("Export failed: ${e.localizedMessage}"))
            }
        }
    }

    // Supabase Cloud Sync
    fun saveSupabaseConfig(url: String, apiKey: String, bucket: String) {
        supabaseService.saveConfig(url, apiKey, bucket)
        _supabaseConfig.value = supabaseService.getConfig()
        _isSupabaseConfigured.value = supabaseService.isConfigured()
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("Supabase credentials saved"))
        }
    }

    fun testSupabaseConnection() {
        viewModelScope.launch {
            _testConnectionStatus.value = "Testing connection..."
            val result = supabaseService.testConnection()
            result.onSuccess { msg ->
                _testConnectionStatus.value = msg
            }.onFailure { e ->
                _testConnectionStatus.value = "Connection failed: ${e.localizedMessage}"
            }
        }
    }

    fun syncCurrentBatchToSupabase() {
        val batch = activeBatch.value ?: return
        if (!supabaseService.isConfigured()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Please enter Supabase URL and API Key first"))
            }
            return
        }

        viewModelScope.launch {
            val candidateList = repository.getCandidatesListForBatch(batch.id)
            val allPhotos = repository.getAllPhotosForBatch(batch.id)
            val totalSteps = 1 + candidateList.size + allPhotos.size
            var currentStep = 0

            _syncProgress.value = com.example.data.remote.SyncProgress.Progress(
                current = currentStep,
                total = totalSteps,
                message = "Syncing exam session metadata..."
            )

            // 1. Sync batch
            val batchResult = supabaseService.syncBatch(batch)
            if (batchResult.isFailure) {
                _syncProgress.value = com.example.data.remote.SyncProgress.Error(
                    batchResult.exceptionOrNull()?.localizedMessage ?: "Failed to sync exam session"
                )
                return@launch
            }
            currentStep++

            // 2. Sync candidates
            for (c in candidateList) {
                _syncProgress.value = com.example.data.remote.SyncProgress.Progress(
                    current = currentStep,
                    total = totalSteps,
                    message = "Syncing candidate: ${c.name} (${c.rollNo})..."
                )
                val candResult = supabaseService.syncCandidate(c)
                if (candResult.isFailure) {
                    _syncProgress.value = com.example.data.remote.SyncProgress.Error(
                        candResult.exceptionOrNull()?.localizedMessage ?: "Failed to sync candidate ${c.name}"
                    )
                    return@launch
                }
                currentStep++
            }

            // 3. Upload sub-50KB photos to Supabase Storage
            for (photo in allPhotos) {
                val candidate = candidateList.find { it.id == photo.candidateId }
                val rollNo = candidate?.rollNo ?: "unknown"
                _syncProgress.value = com.example.data.remote.SyncProgress.Progress(
                    current = currentStep,
                    total = totalSteps,
                    message = "Uploading photo: ${photo.title} (${photo.fileSizeKb} KB)..."
                )

                val file = java.io.File(photo.filePath)
                val photoResult = supabaseService.uploadPhoto(photo, file, rollNo)
                if (photoResult.isFailure) {
                    _syncProgress.value = com.example.data.remote.SyncProgress.Error(
                        photoResult.exceptionOrNull()?.localizedMessage ?: "Failed to upload photo"
                    )
                    return@launch
                }
                currentStep++
            }

            _syncProgress.value = com.example.data.remote.SyncProgress.Success(
                "Synced! ${candidateList.size} candidates & ${allPhotos.size} photos uploaded to Supabase."
            )
            _uiEvents.emit(UiEvent.ShowToast("Supabase sync completed successfully!"))
        }
    }
}
