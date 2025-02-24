package com.helgolabs.trego

import android.app.Application

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Do not initialize WorkManager or other production dependencies
    }
}