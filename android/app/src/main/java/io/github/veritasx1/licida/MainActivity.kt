package io.github.veritasx1.licida

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val cameraAllowed = mutableStateOf(false)
    private var askedOnce = false

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAllowed.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        cameraAllowed.value = granted()
        val studio = Studio(this)
        val initial = Reference.restore(this)
        if (!cameraAllowed.value) { askedOnce = true; askCamera.launch(Manifest.permission.CAMERA) }
        setContent {
            LiCidaApp(studio, initial, cameraAllowed.value, onAskCamera = ::requestCamera, onDrawMode = ::drawMode)
        }
    }

    override fun onResume() {
        super.onResume()
        cameraAllowed.value = granted()  // allowed meanwhile in the system settings
    }

    /** Keys first to LiCida (card 15: keyboard, controller, remote while drawing); the rest as usual. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = KeyHub.dispatch(event) || super.dispatchKeyEvent(event)

    private fun granted() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Ask again – or, once Android stops asking, open LiCida's page in the settings (the user decides there). */
    private fun requestCamera() {
        if (!askedOnce || shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            askedOnce = true
            askCamera.launch(Manifest.permission.CAMERA)
        } else {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    /** Drawing: the picture stays as it is (rotation locked, handbook p. 6) and the screen stays on. */
    private fun drawMode(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
