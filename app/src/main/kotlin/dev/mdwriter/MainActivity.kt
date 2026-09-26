package dev.mdwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.mdwriter.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T02 and T20 count this line in logcat to prove the activity was NOT recreated. Keep the tag and wording.
        Log.d(TAG) { "onCreate restored=${savedInstanceState != null}" }
        enableEdgeToEdge()
        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "mdwriter", color = Color(0xFF1A1A1A))
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
