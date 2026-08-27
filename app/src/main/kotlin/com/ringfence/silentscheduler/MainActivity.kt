package com.ringfence.silentscheduler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import com.ringfence.silentscheduler.core.theme.RingfenceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RingfenceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScaffoldPlaceholder()
                }
            }
        }
    }
}

/**
 * Temporary placeholder for Build Order step 1 (project scaffold only).
 * Replaced by the real navigation host starting at step 2 (DND permission explainer).
 */
@Composable
private fun ScaffoldPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Ringfence: Silent Scheduler — scaffold OK")
    }
}
