package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.ExamRepository
import com.example.utils.WebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExamAssessorApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: ExamRepository
        private set

    lateinit var webServer: WebServer
        private set

    lateinit var supabaseService: com.example.data.remote.SupabaseService
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = ExamRepository(this, database.examDao())
        webServer = WebServer(this, database.examDao(), port = 8080)
        supabaseService = com.example.data.remote.SupabaseService(this)

        // Seed initial demo data in background
        CoroutineScope(Dispatchers.IO).launch {
            repository.seedInitialDataIfEmpty()
        }
    }
}
