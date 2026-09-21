package hu.wukki.tv.parity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.TimeZone

/** Explicitly launched only by reference capture tooling; absent from release builds. */
class ParityReferenceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration =
            Configuration(newBase.resources.configuration).apply {
                densityDpi = 160
                fontScale = 1f
            }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Budapest"))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        val scenario = intent.getStringExtra("scenario") ?: "channels"
        setContent { ParityReferenceScreen(scenario, androidLayout = true) }
    }
}
