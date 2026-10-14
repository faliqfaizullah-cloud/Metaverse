package com.metaverse.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metaverse.gallery.ui.HomeScreen
import com.metaverse.gallery.ui.OnboardingScreen
import com.metaverse.gallery.ui.theme.MetaverseTheme
import androidx.compose.foundation.background
import com.metaverse.gallery.ui.theme.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MetaverseTheme {
                var crash by remember { mutableStateOf(readCrash()) }
                if (crash != null) CrashScreen(crash!!) { clearCrash(); crash = null }
                else MetaverseApp()
            }
        }
    }

    private fun readCrash(): String? =
        try { java.io.File(filesDir, "crash.txt").takeIf { it.exists() }?.readText()?.take(5000) } catch (_: Throwable) { null }

    private fun clearCrash() { try { java.io.File(filesDir, "crash.txt").delete() } catch (_: Throwable) {} }
}

@Composable
private fun CrashScreen(trace: String, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()
            .padding(16.dp)
    ) {
        androidx.compose.material3.Text("The app crashed last time", color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp)
        androidx.compose.material3.Text("Send a screenshot of this to Claude.", color = androidx.compose.ui.graphics.Color(0xFF999999), fontSize = 13.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.text.selection.SelectionContainer(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                trace, color = androidx.compose.ui.graphics.Color(0xFFFF8A80), fontSize = 9.sp, lineHeight = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            )
        }
        androidx.compose.material3.Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Text("Dismiss and open app")
        }
    }
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun MetaverseApp(vm: MuseumViewModel = viewModel()) {
    val ctx = LocalContext.current
    var entered by rememberSaveable { mutableStateOf(false) }

    fun hasAccess(): Boolean {
        val p = mediaPermissions()
        return p.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasAccess()) vm.startObserving()
        entered = true
    }

    LaunchedEffect(entered) {
        if (entered && hasAccess()) vm.startObserving()
    }

    val items by vm.items.collectAsState()

    Crossfade(
        targetState = entered,
        modifier = Modifier.fillMaxSize().background(Ink),
        label = "root"
    ) { inside ->
        if (!inside) {
            OnboardingScreen(
                photos = items.take(5).map { it.uri },
                onEnter = {
                    if (hasAccess()) { vm.startObserving(); entered = true }
                    else launcher.launch(mediaPermissions())
                }
            )
        } else {
            HomeScreen(vm)
        }
    }
}
