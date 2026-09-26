package dev.mdwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.mdwriter.ui.theme.DesignGallery
import dev.mdwriter.ui.theme.MdWriterTheme
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T02 and T20 count this line in logcat to prove the activity was NOT recreated. Keep the tag and wording.
        Log.d(TAG) { "onCreate restored=${savedInstanceState != null}" }
        enableEdgeToEdge()
        setContent {
            MdWriterTheme {
                if (BuildConfig.DEBUG) DesignGallery() else LaunchPlaceholder()
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

// T05 replaces this with the editor host.
@Composable
private fun LaunchPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(WriterTheme.colors.bg), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.app_name),
            style = WriterTheme.typography.body,
            color = WriterTheme.colors.text,
        )
    }
}
