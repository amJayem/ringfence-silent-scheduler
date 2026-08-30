package com.ringfence.silentscheduler

import android.app.Application
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RingfenceApp : Application() {

    @Inject
    lateinit var quickSilenceRepository: QuickSilenceRepository

    override fun onCreate() {
        super.onCreate()
        // "Always reverts... on next launch" (see onboarding copy): if the revert
        // alarm was lost (force-stop, Doze, reboot) while a Quick Silence session was
        // running, the phone would otherwise stay muted forever with nothing left to
        // notice. Checked once here so it's fixed the moment the process starts, not
        // only once the Dashboard happens to be opened.
        CoroutineScope(Dispatchers.Default).launch {
            quickSilenceRepository.reconcileIfExpired()
        }
    }
}
