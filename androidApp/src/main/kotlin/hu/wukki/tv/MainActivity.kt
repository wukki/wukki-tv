package hu.wukki.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import hu.wukki.tv.ui.app.WukkiApp
import hu.wukki.tv.ui.components.WukkiColorScheme
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.navigation.DashboardSection

class MainActivity : ComponentActivity() {
    private var appBackAction: (() -> Boolean)? = null
    private var playbackController: AndroidPlaybackController? = null
    private var liveSectionActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (appBackAction?.invoke() == true) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        LocalStore.install(applicationContext)
        DeviceInfoProvider.install(applicationContext)
        AndroidRefreshScheduler.sync(applicationContext, LocalStore.load().settings ?: AppSettings())

        setContent {
            val player = remember { AndroidPlaybackController(applicationContext).also { playbackController = it } }
            MaterialTheme(colorScheme = WukkiColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = WukkiColors.background,
                    contentColor = WukkiColors.textPrimary
                ) {
                    WukkiApp(
                        playbackController = player,
                        videoHost = player::VideoSurface,
                        playbackEngineLabel = "Media3 / ExoPlayer",
                        onActiveSectionChange = { section ->
                            liveSectionActive = section == DashboardSection.LIVE
                            setKeepScreenOn(liveSectionActive)
                        },
                        androidSettingsNavigation = true,
                        onPlatformBackActionChange = { action -> appBackAction = action }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        setKeepScreenOn(false)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        playbackController?.resumeAfterBackground()
        setKeepScreenOn(liveSectionActive)
    }

    override fun onStop() {
        playbackController?.pauseForBackground()
        setKeepScreenOn(false)
        super.onStop()
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
