package com.example

import android.app.Application
import com.example.data.engine.SafeFileEngine
import com.example.data.local.OrganizerDatabase
import com.example.data.local.SchedulePreferencesRepository
import com.example.data.repository.OrganizerRepository

class AppContainer(application: Application) {
    private val database = OrganizerDatabase.getInstance(application)
    val dao = database.organizerDao()
    val safeFileEngine = SafeFileEngine(application, dao)
    private val schedulePrefs = SchedulePreferencesRepository(application)
    val repository = OrganizerRepository(
        context = application,
        dao = dao,
        safeFileEngine = safeFileEngine,
        schedulePrefs = schedulePrefs
    )
}

class OrganizerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
