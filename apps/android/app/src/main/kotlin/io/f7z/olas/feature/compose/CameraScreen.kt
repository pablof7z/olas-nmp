package io.f7z.olas.feature.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.f7z.olas.ui.theme.OlasColors
import java.io.File
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onCapture: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraContent(onCapture = onCapture)
    } else {
        CameraPermissionDenied(onCapture = onCapture)
    }
}

// MARK: - Camera content (permission granted)

@Composable
private fun CameraContent(onCapture: (Uri) -> Unit) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing     by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val imageCapture   = remember { ImageCapture.Builder().build() }
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }
    val previewView    = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val libraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onCapture(it) } }

    // Re-bind camera when lensFacing changes.
    DisposableEffect(lensFacing) {
        val future = ProcessCameraProvider.getInstance(context)
        // Captured on the listener thread once the provider resolves; read in
        // onDispose to unbind without a blocking future.get() on the main thread.
        var boundProvider: ProcessCameraProvider? = null
        future.addListener({
            val provider = future.get()
            boundProvider = provider
            val preview  = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }, executor)
        onDispose {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Bottom controls overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Library
            IconButton(onClick = {
                libraryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Icon(
                    imageVector        = Icons.Default.PhotoLibrary,
                    contentDescription = "Library",
                    tint               = Color.White,
                    modifier           = Modifier.size(30.dp),
                )
            }

            // Shutter (guard against a double-tap firing two captures)
            var capturing by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (capturing) Color.Gray else Color.White)
                    .clickable(enabled = !capturing) {
                        capturing = true
                        takePhoto(context, imageCapture, executor) { uri ->
                            onCapture(uri)
                        }
                    },
            )

            // Lens toggle
            IconButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else CameraSelector.LENS_FACING_BACK
            }) {
                Icon(
                    imageVector        = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch camera",
                    tint               = Color.White,
                    modifier           = Modifier.size(30.dp),
                )
            }
        }
    }
}

// MARK: - Permission denied state

@Composable
private fun CameraPermissionDenied(onCapture: (Uri) -> Unit) {
    val context = LocalContext.current
    val libraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onCapture(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background)
            .padding(32.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.CameraAlt,
            contentDescription = null,
            tint               = OlasColors.Text2,
            modifier           = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text       = "Camera Access Needed",
            fontSize   = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OlasColors.Text1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Enable camera permission in Settings to take photos.",
            fontSize  = 14.sp,
            color     = OlasColors.Text2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        // Open Settings so a denied user can re-grant camera (parity with iOS).
        Button(
            onClick = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                )
                runCatching { context.startActivity(intent) }
            },
            colors   = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Text1,
                contentColor   = OlasColors.Background,
            ),
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("Open Settings", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                libraryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            colors   = ButtonDefaults.buttonColors(
                containerColor = OlasColors.Surface2,
                contentColor   = OlasColors.Text1,
            ),
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("Choose from Library", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

// MARK: - Capture helper

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onCapture: (Uri) -> Unit,
) {
    val photoFile = File(context.cacheDir, "olas_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onCapture(Uri.fromFile(photoFile))
            }
            override fun onError(exception: ImageCaptureException) {
                // Capture failed — user can tap shutter again; no-op.
            }
        },
    )
}
