package com.remodex.android.ui.scanner

import android.Manifest
import android.util.Log
import android.util.Base64
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.remodex.android.service.CodexPairingQRPayload
import com.remodex.android.ui.main.ContentViewModel
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: ContentViewModel,
    onPaired: () -> Unit,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var processingQR by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check camera permission
    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        hasCameraPermission = permission == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            // Permission request view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Remodex needs camera access to scan the pairing QR code from your Mac.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    // Request permission at runtime
                    val activity = context as? android.app.Activity ?: return@Button
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.CAMERA), 100
                    )
                }) {
                    Text("Grant Camera Access")
                }
            }
        } else {
            // Camera preview with ML Kit barcode scanning
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val barcodeScanner = BarcodeScanning.getClient()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            @androidx.annotation.OptIn(ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !processingQR) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            if (barcode.valueType == Barcode.TYPE_TEXT) {
                                                val raw = barcode.rawValue ?: continue
                                                processQRPayload(raw, json, viewModel, onPaired) {
                                                    processingQR = it
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("QRScanner", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scan overlay
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Frame guide
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent)
                ) {
                    // Corner guides would go here
                }
            }

            // Instruction text
            Text(
                "Align QR code in frame",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            // Error message
            errorMessage?.let { err ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .padding(horizontal = 32.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Red.copy(alpha = 0.8f)
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Back button
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (hasCameraPermission) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun processQRPayload(
    raw: String,
    json: Json,
    viewModel: ContentViewModel,
    onPaired: () -> Unit,
    setProcessing: (Boolean) -> Unit
) {
    try {
        setProcessing(true)
        // Try base64 decode first
        val decoded = try {
            String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) { raw }

        val payload = json.decodeFromString(CodexPairingQRPayload.serializer(), decoded)
        if (!payload.isValid) {
            Log.w("QRScanner", "Invalid QR payload")
            setProcessing(false)
            return
        }

        viewModel.connectFromQR(payload)
        onPaired()
    } catch (e: Exception) {
        Log.e("QRScanner", "Failed to parse QR: ${e.message}")
        setProcessing(false)
    }
}
