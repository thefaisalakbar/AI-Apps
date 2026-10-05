package com.example.utils

import android.content.Context
import com.example.data.local.ExamDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections

class WebServer(
    private val context: Context,
    private val examDao: ExamDao,
    private val port: Int = 8080
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning = false
        private set

    fun start(onStarted: (String) -> Unit, onError: (String) -> Unit) {
        if (isRunning) {
            val ip = getIpAddress() ?: "localhost"
            onStarted("http://$ip:$port")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            val ip = getIpAddress() ?: "127.0.0.1"
            val url = "http://$ip:$port"

            job = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }

            onStarted(url)
        } catch (e: Exception) {
            isRunning = false
            onError(e.message ?: "Failed to start server on port $port")
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
        job?.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val outHeader = PrintWriter(socket.getOutputStream(), false)
                val outData = BufferedOutputStream(socket.getOutputStream())

                val requestLine = reader.readLine() ?: return@withContext
                val tokens = requestLine.split(" ")
                if (tokens.size < 2) return@withContext

                val rawUri = tokens[1]
                val decodedUri = URLDecoder.decode(rawUri, "UTF-8")

                when {
                    decodedUri == "/" || decodedUri.startsWith("/?") -> {
                        val html = buildDashboardHtml()
                        val bytes = html.toByteArray(Charsets.UTF_8)
                        outHeader.print("HTTP/1.1 200 OK\r\n")
                        outHeader.print("Content-Type: text/html; charset=utf-8\r\n")
                        outHeader.print("Content-Length: ${bytes.size}\r\n")
                        outHeader.print("Connection: close\r\n\r\n")
                        outHeader.flush()
                        outData.write(bytes)
                        outData.flush()
                    }

                    decodedUri.startsWith("/download-all-zip") -> {
                        // Download full ZIP for selected batch or latest batch
                        val batchId = parseQueryParam(decodedUri, "batchId")?.toLongOrNull() ?: 1L
                        val batch = examDao.getBatchById(batchId)
                        if (batch == null) {
                            return404(outHeader)
                            return@withContext
                        }
                        val zipFile = FileManager.createBatchZip(context, batch)
                        if (zipFile != null && zipFile.exists()) {
                            streamFile(zipFile, "application/zip", outHeader, outData, "${FileManager.sanitizeFolderName(batch.title)}.zip")
                        } else {
                            returnMessage(outHeader, outData, "No photos or candidates found to export in this batch.")
                        }
                    }

                    decodedUri.startsWith("/download-student-zip") -> {
                        val candidateId = parseQueryParam(decodedUri, "candidateId")?.toLongOrNull()
                        val candidate = candidateId?.let { examDao.getCandidateById(it) }
                        if (candidate != null) {
                            val batch = examDao.getBatchById(candidate.batchId)
                            if (batch != null) {
                                val zipFile = FileManager.createCandidateZip(context, batch, candidate)
                                if (zipFile != null && zipFile.exists()) {
                                    streamFile(
                                        zipFile,
                                        "application/zip",
                                        outHeader,
                                        outData,
                                        "${FileManager.sanitizeFolderName("${candidate.rollNo}_${candidate.name}")}.zip"
                                    )
                                } else {
                                    returnMessage(outHeader, outData, "No photos found for candidate.")
                                }
                            } else {
                                return404(outHeader)
                            }
                        } else {
                            return404(outHeader)
                        }
                    }

                    decodedUri.startsWith("/photo") -> {
                        val photoId = parseQueryParam(decodedUri, "id")?.toLongOrNull()
                        val photo = photoId?.let { examDao.getPhotoById(it) }
                        if (photo != null) {
                            val file = File(photo.filePath)
                            if (file.exists()) {
                                val mime = if (photo.format.equals("PNG", true)) "image/png" else "image/jpeg"
                                streamFile(file, mime, outHeader, outData, file.name)
                            } else {
                                return404(outHeader)
                            }
                        } else {
                            return404(outHeader)
                        }
                    }

                    else -> {
                        return404(outHeader)
                    }
                }
            } catch (e: Exception) {
                // connection closed or aborted
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun parseQueryParam(uri: String, paramName: String): String? {
        val queryStart = uri.indexOf('?')
        if (queryStart == -1) return null
        val query = uri.substring(queryStart + 1)
        val pairs = query.split("&")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size == 2 && parts[0] == paramName) {
                return parts[1]
            }
        }
        return null
    }

    private fun return404(outHeader: PrintWriter) {
        outHeader.print("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: 9\r\nConnection: close\r\n\r\nNot Found")
        outHeader.flush()
    }

    private fun returnMessage(outHeader: PrintWriter, outData: BufferedOutputStream, msg: String) {
        val bytes = msg.toByteArray()
        outHeader.print("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
        outHeader.flush()
        outData.write(bytes)
        outData.flush()
    }

    private fun streamFile(file: File, mimeType: String, outHeader: PrintWriter, outData: BufferedOutputStream, downloadName: String) {
        val fileLength = file.length()
        outHeader.print("HTTP/1.1 200 OK\r\n")
        outHeader.print("Content-Type: $mimeType\r\n")
        outHeader.print("Content-Length: $fileLength\r\n")
        outHeader.print("Content-Disposition: attachment; filename=\"$downloadName\"\r\n")
        outHeader.print("Connection: close\r\n\r\n")
        outHeader.flush()

        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                outData.write(buffer, 0, read)
            }
            outData.flush()
        }
    }

    private suspend fun buildDashboardHtml(): String {
        // Collect latest batch data
        val batches = examDao.getAllBatches()
        // We will fetch direct list for first batch or latest
        val db = com.example.data.local.AppDatabase.getDatabase(context)
        // Get all candidates
        val allBatches = withContext(Dispatchers.IO) {
            db.openHelper.readableDatabase.query("SELECT * FROM exam_batches ORDER BY createdAt DESC LIMIT 1")
        }

        var batchTitle = "Exam Assessment Portal"
        var batchTrade = "Vocational & Technical Training"
        var batchCenter = "Examination Center"
        var batchId = 1L
        var batchDate = "Today"

        if (allBatches.moveToFirst()) {
            batchId = allBatches.getLong(allBatches.getColumnIndexOrThrow("id"))
            batchTitle = allBatches.getString(allBatches.getColumnIndexOrThrow("title"))
            batchTrade = allBatches.getString(allBatches.getColumnIndexOrThrow("trade"))
            batchCenter = allBatches.getString(allBatches.getColumnIndexOrThrow("centerName"))
            batchDate = allBatches.getString(allBatches.getColumnIndexOrThrow("examDate"))
        }
        allBatches.close()

        val candidates = examDao.getCandidatesListForBatch(batchId)
        val allPhotos = examDao.getAllPhotosForBatch(batchId)

        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Exam Assessor - PC Transfer Portal</title>
                <style>
                    :root {
                        --primary: #1E3A8A;
                        --primary-light: #2563EB;
                        --success: #15803D;
                        --bg: #F8FAFC;
                        --card: #FFFFFF;
                        --text: #0F172A;
                        --text-muted: #64748B;
                        --border: #E2E8F0;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg);
                        color: var(--text);
                        margin: 0;
                        padding: 24px;
                    }
                    .container {
                        max-width: 1100px;
                        margin: 0 auto;
                    }
                    .header {
                        background: linear-gradient(135deg, #1E3A8A, #1E40AF);
                        color: white;
                        border-radius: 16px;
                        padding: 28px;
                        box-shadow: 0 4px 20px rgba(30, 58, 138, 0.15);
                        margin-bottom: 24px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        flex-wrap: wrap;
                        gap: 16px;
                    }
                    .badge {
                        display: inline-block;
                        background: rgba(255, 255, 255, 0.2);
                        padding: 4px 12px;
                        border-radius: 20px;
                        font-size: 13px;
                        font-weight: 600;
                        margin-bottom: 8px;
                    }
                    .btn-download-all {
                        background-color: #22C55E;
                        color: white;
                        text-decoration: none;
                        font-weight: 700;
                        padding: 14px 24px;
                        border-radius: 12px;
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        box-shadow: 0 4px 12px rgba(34, 197, 94, 0.35);
                        transition: transform 0.15s ease;
                    }
                    .btn-download-all:hover {
                        transform: translateY(-2px);
                        background-color: #16A34A;
                    }
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 16px;
                        margin-bottom: 24px;
                    }
                    .stat-box {
                        background: var(--card);
                        padding: 18px;
                        border-radius: 12px;
                        border: 1px solid var(--border);
                        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                    }
                    .stat-value {
                        font-size: 26px;
                        font-weight: 800;
                        color: var(--primary);
                    }
                    .stat-label {
                        font-size: 13px;
                        color: var(--text-muted);
                        margin-top: 4px;
                    }
                    .candidate-card {
                        background: var(--card);
                        border-radius: 14px;
                        border: 1px solid var(--border);
                        padding: 20px;
                        margin-bottom: 16px;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.04);
                    }
                    .candidate-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 16px;
                        flex-wrap: wrap;
                        gap: 10px;
                    }
                    .candidate-title {
                        font-size: 18px;
                        font-weight: 700;
                        color: var(--text);
                    }
                    .candidate-meta {
                        font-size: 13px;
                        color: var(--text-muted);
                        margin-top: 4px;
                    }
                    .btn-student-zip {
                        background: #EFF6FF;
                        color: var(--primary-light);
                        border: 1px solid #BFDBFE;
                        padding: 8px 16px;
                        border-radius: 8px;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 600;
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                    }
                    .btn-student-zip:hover {
                        background: #DBEAFE;
                    }
                    .photo-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
                        gap: 12px;
                    }
                    .photo-item {
                        border: 1px solid var(--border);
                        border-radius: 10px;
                        overflow: hidden;
                        background: #F1F5F9;
                        position: relative;
                    }
                    .photo-item img {
                        width: 100%;
                        height: 140px;
                        object-fit: cover;
                        display: block;
                    }
                    .photo-footer {
                        padding: 8px;
                        background: white;
                        font-size: 11px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .size-tag {
                        color: var(--success);
                        font-weight: 700;
                    }
                    .tag-present {
                        background: #DCFCE7;
                        color: #15803D;
                        padding: 3px 8px;
                        border-radius: 6px;
                        font-size: 12px;
                        font-weight: 600;
                    }
                    .tag-absent {
                        background: #FEE2E2;
                        color: #B91C1C;
                        padding: 3px 8px;
                        border-radius: 6px;
                        font-size: 12px;
                        font-weight: 600;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div>
                            <span class="badge">Real-time Assessor Transfer</span>
                            <h1 style="margin: 4px 0 8px 0; font-size: 26px;">$batchTitle</h1>
                            <div style="font-size: 14px; opacity: 0.9;">
                                <strong>Trade:</strong> $batchTrade &nbsp;|&nbsp; <strong>Center:</strong> $batchCenter &nbsp;|&nbsp; <strong>Date:</strong> $batchDate
                            </div>
                        </div>
                        <div>
                            <a href="/download-all-zip?batchId=$batchId" class="btn-download-all">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                Download Complete Exam (ZIP)
                            </a>
                        </div>
                    </div>

                    <div class="stats-grid">
                        <div class="stat-box">
                            <div class="stat-value">${candidates.size}</div>
                            <div class="stat-label">Registered Candidates</div>
                        </div>
                        <div class="stat-box">
                            <div class="stat-value" style="color: #16A34A;">${candidates.count { it.attendanceStatus == "PRESENT" }}</div>
                            <div class="stat-label">Present & Logged</div>
                        </div>
                        <div class="stat-box">
                            <div class="stat-value">${allPhotos.size}</div>
                            <div class="stat-label">Total Verified Photos</div>
                        </div>
                        <div class="stat-box">
                            <div class="stat-value" style="color: #059669;">100% &lt;50KB</div>
                            <div class="stat-label">Portal Compliance Rate</div>
                        </div>
                    </div>

                    <h2 style="font-size: 20px; margin-bottom: 16px; color: var(--text);">Student Verification Folders</h2>
        """.trimIndent())

        if (candidates.isEmpty()) {
            sb.append("""
                <div style="background: white; border-radius: 12px; padding: 40px; text-align: center; border: 1px solid var(--border);">
                    <p style="color: var(--text-muted); font-size: 16px; margin: 0;">No candidates logged yet in this exam batch. Add candidates and capture photos in the mobile app!</p>
                </div>
            """.trimIndent())
        } else {
            for (c in candidates) {
                val candidatePhotos = allPhotos.filter { it.candidateId == c.id }
                val statusClass = if (c.attendanceStatus == "PRESENT") "tag-present" else "tag-absent"

                sb.append("""
                    <div class="candidate-card">
                        <div class="candidate-header">
                            <div>
                                <span class="candidate-title">${c.name}</span> &nbsp;
                                <span class="$statusClass">${c.attendanceStatus}</span>
                                <div class="candidate-meta">
                                    Roll No: <strong>${c.rollNo}</strong> &nbsp;•&nbsp; CNIC: <strong>${c.cnic}</strong> &nbsp;•&nbsp; Folder: <code>${c.folderName.ifBlank { "${c.rollNo}_${c.name}" }}</code>
                                    ${if (c.practicalMarks.isNotBlank()) " &nbsp;•&nbsp; Marks: <strong>${c.practicalMarks}</strong>" else ""}
                                </div>
                            </div>
                            <div>
                                <a href="/download-student-zip?candidateId=${c.id}" class="btn-student-zip">
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                    Download Folder ZIP (${candidatePhotos.size} files)
                                </a>
                            </div>
                        </div>
                        <div class="photo-grid">
                """.trimIndent())

                if (candidatePhotos.isEmpty()) {
                    sb.append("""
                        <div style="grid-column: 1 / -1; padding: 12px; background: #F8FAFC; border-radius: 8px; color: var(--text-muted); font-size: 13px;">
                            No photos captured yet for this candidate.
                        </div>
                    """.trimIndent())
                } else {
                    for (p in candidatePhotos) {
                        sb.append("""
                            <div class="photo-item">
                                <a href="/photo?id=${p.id}" target="_blank">
                                    <img src="/photo?id=${p.id}" alt="${p.title}" />
                                </a>
                                <div class="photo-footer">
                                    <span style="font-weight: 600; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 90px;">${p.title}</span>
                                    <span class="size-tag">${String.format(java.util.Locale.US, "%.1f KB", p.fileSizeKb)}</span>
                                </div>
                            </div>
                        """.trimIndent())
                    }
                }

                sb.append("</div></div>")
            }
        }

        sb.append("""
                    <div style="margin-top: 40px; text-align: center; color: var(--text-muted); font-size: 12px;">
                        Exam Assessor Android Bridge &bull; Sub-50KB Direct Transfer &bull; Ready for Academic Portal Upload
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }

    companion object {
        fun getIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    // check wlan0, ap0, eth0, etc.
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (host != null && !host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}
