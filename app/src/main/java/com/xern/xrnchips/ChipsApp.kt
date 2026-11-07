package com.xern.xrnchips

import android.app.Application
import com.xern.xrnchips.data.Sounds
import com.xern.xrnchips.data.Store

class ChipsApp : Application() {
    lateinit var store: Store
        private set
    lateinit var sounds: Sounds
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(filesDir)
        sounds = Sounds(this)
    }
}
