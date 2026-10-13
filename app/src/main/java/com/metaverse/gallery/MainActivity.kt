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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metaverse.gallery.ui.MuseumScreen
import com.metaverse.gallery.ui.OnboardingScreen
import com.metaverse.gallery.ui.theme.MetaverseTheme
import androidx.compose.foundation.background
import com.metaverse.gallery.ui.theme.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MetaverseTheme { MetaverseApp() } }
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
            MuseumScreen(vm)
        }
    }
}
