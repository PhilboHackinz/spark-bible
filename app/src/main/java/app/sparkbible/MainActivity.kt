package app.sparkbible

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.sparkbible.ui.SparkRoot

class MainActivity : ComponentActivity() {
    private val model: SparkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { SparkRoot(model) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == "daily") {
            model.openDaily()
            intent.removeExtra(EXTRA_OPEN)
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
