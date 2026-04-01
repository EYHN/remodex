package com.remodex.android.ui.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.remodex.android.service.CodexPairingQRPayload
import com.remodex.android.service.FlexibleExpiresAtSerializer
import com.remodex.android.service.PAIRING_QR_VERSION
import com.remodex.android.ui.main.ContentViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: ContentViewModel,
    onPaired: () -> Unit,
    onBack: (() -> Unit)?
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(cameraPermissionGranted(context)) }
    var isCheckingPermission by remember { mutableStateOf(true) }
    var isShowingManualPayloadSheet by rememberSaveable { mutableStateOf(false) }
    var manualPayloadText by rememberSaveable { mutableStateOf("") }
    var scannerError by rememberSaveable { mutableStateOf<String?>(null) }
    var manualPayloadError by rememberSaveable { mutableStateOf<String?>(null) }
    var bridgeUpdatePrompt by remember { mutableStateOf<ScannerBridgeUpdatePrompt?>(null) }
    var processingQR by remember { mutableStateOf(false) }
    var didCopyBridgeUpdateCommand by remember { mutableStateOf(false) }
    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true } }
    val manualSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun refreshCameraPermissionState() {
        hasCameraPermission = cameraPermissionGranted(context)
        isCheckingPermission = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshCameraPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshCameraPermissionState()
    }

    LaunchedEffect(didCopyBridgeUpdateCommand) {
        if (!didCopyBridgeUpdateCommand) return@LaunchedEffect
        delay(1500)
        didCopyBridgeUpdateCommand = false
    }

    fun submitPairingCode(
        code: String,
        isManualEntry: Boolean,
        resetScanLock: (() -> Unit)? = null
    ) {
        when (val result = validatePairingPayload(code, json)) {
            is ScannerPairingValidationResult.Success -> {
                processingQR = false
                manualPayloadText = ""
                scannerError = null
                manualPayloadError = null
                bridgeUpdatePrompt = null
                isShowingManualPayloadSheet = false
                viewModel.connectFromQR(result.payload)
                onPaired()
            }

            is ScannerPairingValidationResult.ScanError -> {
                processingQR = false
                if (isManualEntry) {
                    manualPayloadError = result.message
                } else {
                    scannerError = result.message
                }
                resetScanLock?.invoke()
            }

            is ScannerPairingValidationResult.BridgeUpdateRequired -> {
                processingQR = false
                manualPayloadText = ""
                manualPayloadError = null
                scannerError = null
                isShowingManualPayloadSheet = false
                bridgeUpdatePrompt = result.prompt
                resetScanLock?.invoke()
            }
        }
    }

    fun submitManualPayload() {
        if (processingQR) return

        val trimmed = manualPayloadText.trim()
        if (trimmed.isBlank()) {
            manualPayloadError = "Paste the pairing payload from Remodex on your Mac."
            return
        }

        processingQR = true
        submitPairingCode(trimmed, isManualEntry = true)
    }

    fun pastePayloadFromClipboard() {
        val clipboardText = clipboard.getText()?.text?.trim().orEmpty()
        if (clipboardText.isBlank()) {
            manualPayloadError = "Clipboard does not contain a pairing payload."
            return
        }

        manualPayloadError = null
        manualPayloadText = clipboardText
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isCheckingPermission -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }

            bridgeUpdatePrompt != null -> {
                BridgeUpdateRequiredView(
                    prompt = bridgeUpdatePrompt!!,
                    didCopyCommand = didCopyBridgeUpdateCommand,
                    onCopyCommand = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(bridgeUpdatePrompt!!.command))
                        didCopyBridgeUpdateCommand = true
                    },
                    onReset = {
                        bridgeUpdatePrompt = null
                        didCopyBridgeUpdateCommand = false
                    }
                )
            }

            hasCameraPermission -> {
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
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes: List<Barcode> ->
                                            for (barcode in barcodes) {
                                                if (barcode.valueType == Barcode.TYPE_TEXT) {
                                                    val raw = barcode.rawValue ?: continue
                                                    processingQR = true
                                                    submitPairingCode(
                                                        code = raw,
                                                        isManualEntry = false,
                                                        resetScanLock = { processingQR = false }
                                                    )
                                                    break
                                                }
                                            }
                                        }
                                        .addOnFailureListener { error: Exception ->
                                            Log.e("QRScanner", "Barcode scan failed", error)
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
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("QRScanner", "Camera bind failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ScannerOverlay(
                    onManualPairing = {
                        manualPayloadError = null
                        isShowingManualPayloadSheet = true
                    }
                )
            }

            else -> {
                CameraPermissionView(
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    onEnterPayloadInstead = {
                        manualPayloadError = null
                        isShowingManualPayloadSheet = true
                    }
                )
            }
        }

        if (onBack != null) {
            BackCircleButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                onClick = onBack
            )
        }
    }

    if (scannerError != null) {
        AlertDialog(
            onDismissRequest = { scannerError = null },
            title = { Text("Scan Error") },
            text = { Text(scannerError ?: "Invalid QR code") },
            confirmButton = {
                TextButton(onClick = { scannerError = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (manualPayloadError != null) {
        AlertDialog(
            onDismissRequest = { manualPayloadError = null },
            title = { Text("Pairing Error") },
            text = { Text(manualPayloadError ?: "Invalid pairing payload") },
            confirmButton = {
                TextButton(onClick = { manualPayloadError = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (isShowingManualPayloadSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                manualPayloadError = null
                isShowingManualPayloadSheet = false
            },
            sheetState = manualSheetState
        ) {
            ManualPairingSheet(
                payloadText = manualPayloadText,
                onPayloadTextChange = { manualPayloadText = it },
                onPasteFromClipboard = ::pastePayloadFromClipboard,
                onConnect = ::submitManualPayload,
                onDismiss = {
                    manualPayloadError = null
                    isShowingManualPayloadSheet = false
                },
                isConnectEnabled = manualPayloadText.trim().isNotEmpty() && !processingQR,
                isProcessing = processingQR
            )
        }
    }
}

@Composable
private fun BackCircleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ScannerOverlay(onManualPairing: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(250.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Transparent)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.6f))
            ) {}
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Scan QR code from Remodex CLI",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(20.dp))

        SecondaryScannerButton(
            text = "Enter Payload Instead",
            onClick = onManualPairing
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CameraPermissionView(
    onOpenSettings: () -> Unit,
    onEnterPayloadInstead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Camera access needed",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Open Settings and allow camera access to scan the pairing QR code.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0A84FF),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text("Open Settings", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SecondaryScannerButton(
            text = "Enter Payload Instead",
            onClick = onEnterPayloadInstead
        )
    }
}

@Composable
private fun SecondaryScannerButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BridgeUpdateRequiredView(
    prompt: ScannerBridgeUpdatePrompt,
    didCopyCommand: Boolean,
    onCopyCommand: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            prompt.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            prompt.message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Do these steps on your Mac",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(14.dp))

        BridgeUpdateStep(
            number = "1",
            title = "Update Remodex",
            detail = prompt.command,
            showsCopyButton = true,
            copyButtonLabel = if (didCopyCommand) "Copied" else "Copy Command",
            onCopyCommand = onCopyCommand
        )
        Spacer(modifier = Modifier.height(12.dp))
        BridgeUpdateStep(number = "2", title = "Start it again", detail = "Run remodex up")
        Spacer(modifier = Modifier.height(12.dp))
        BridgeUpdateStep(number = "3", title = "Make a new QR code", detail = "Use the new QR shown in the terminal")
        Spacer(modifier = Modifier.height(12.dp))
        BridgeUpdateStep(number = "4", title = "Come back here", detail = "Then scan the new QR code from the phone")

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Text("I Updated It", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BridgeUpdateStep(
    number: String,
    title: String,
    detail: String,
    showsCopyButton: Boolean = false,
    copyButtonLabel: String = "Copy Command",
    onCopyCommand: (() -> Unit)? = null
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            color = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = if (showsCopyButton) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = Color.White.copy(alpha = 0.82f)
                )
            }

            if (showsCopyButton && onCopyCommand != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCopyCommand) {
                    Icon(
                        imageVector = if (copyButtonLabel == "Copied") Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(copyButtonLabel, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ManualPairingSheet(
    payloadText: String,
    onPayloadTextChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
    isConnectEnabled: Boolean,
    isProcessing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text("Close")
            }

            Text(
                "Manual Pairing",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Pair with payload",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Paste the full JSON pairing payload from Remodex on your Mac. This uses the same secure pairing flow as the QR code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = payloadText,
            onValueChange = onPayloadTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(18.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Text
            ),
            placeholder = { Text("Paste base64 or JSON payload") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Tip: you can copy the payload from your terminal and paste it here directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onPasteFromClipboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("Paste from Clipboard", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onConnect,
            enabled = isConnectEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color.Black.copy(alpha = 0.4f),
                disabledContentColor = Color.White
            )
        ) {
            Text(
                if (isProcessing) "Connecting..." else "Connect",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun cameraPermissionGranted(context: android.content.Context): Boolean {
    val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    return permission == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private sealed interface ScannerPairingValidationResult {
    data class Success(val payload: CodexPairingQRPayload) : ScannerPairingValidationResult
    data class ScanError(val message: String) : ScannerPairingValidationResult
    data class BridgeUpdateRequired(val prompt: ScannerBridgeUpdatePrompt) : ScannerPairingValidationResult
}

private data class ScannerBridgeUpdatePrompt(
    val title: String,
    val message: String,
    val command: String
)

@Serializable
private data class PairingPayloadProbe(
    val relay: String? = null,
    val sessionId: String? = null,
    val macDeviceId: String? = null,
    val macIdentityPublicKey: String? = null,
    @Serializable(with = FlexibleExpiresAtSerializer::class)
    val expiresAt: String? = null,
    val v: Int? = null,
    @SerialName("pairingVersion") val pairingVersion: Int? = null
)

private fun validatePairingPayload(
    raw: String,
    json: Json
): ScannerPairingValidationResult {
    val decoded = try {
        decodePairingPayload(raw)
    } catch (_: Exception) {
        raw.trim()
    }

    return try {
        val payload = json.decodeFromString(CodexPairingQRPayload.serializer(), decoded)
        if (payload.v != PAIRING_QR_VERSION) {
            ScannerPairingValidationResult.BridgeUpdateRequired(
                makeBridgeUpdatePrompt(
                    "This QR code was generated by a different Remodex npm version. Update the package on your Mac to the latest release before scanning a new QR code."
                )
            )
        } else if (payload.relay.trim().isEmpty()) {
            ScannerPairingValidationResult.ScanError(
                "QR code is missing the relay URL. Re-generate the code from the bridge."
            )
        } else if (payload.sessionId.trim().isEmpty()) {
            ScannerPairingValidationResult.ScanError(
                "QR code is missing the session ID. Re-generate the code from the bridge."
            )
        } else if (payload.isExpired) {
            ScannerPairingValidationResult.ScanError(
                "This pairing QR code has expired. Generate a new one from the Mac bridge."
            )
        } else {
            ScannerPairingValidationResult.Success(payload)
        }
    } catch (_: Exception) {
        if (looksLikeRemodexPairingPayload(decoded, json)) {
            ScannerPairingValidationResult.BridgeUpdateRequired(
                makeBridgeUpdatePrompt(
                    "This QR code looks like it came from an older Remodex bridge. Update the npm package on your Mac to the latest release before scanning a new QR code."
                )
            )
        } else {
            ScannerPairingValidationResult.ScanError(
                "Not a valid secure pairing code. Make sure you're scanning a QR from the latest Remodex bridge."
            )
        }
    }
}

private fun looksLikeRemodexPairingPayload(raw: String, json: Json): Boolean {
    return try {
        val objectKeys = json.parseToJsonElement(raw).jsonObject.keys
        val pairingKeys = setOf(
            "relay",
            "sessionId",
            "macDeviceId",
            "macIdentityPublicKey",
            "expiresAt",
            "v",
            "pairingVersion"
        )
        objectKeys.any { it in pairingKeys }
    } catch (_: Exception) {
        false
    }
}

private fun makeBridgeUpdatePrompt(message: String): ScannerBridgeUpdatePrompt {
    return ScannerBridgeUpdatePrompt(
        title = "Update Remodex on your Mac before scanning",
        message = message,
        command = "npm install -g remodex@latest"
    )
}

private fun decodePairingPayload(raw: String): String {
    val normalized = raw.trim()
    if (normalized.isBlank()) {
        return normalized
    }

    val candidates = listOf(
        normalized,
        normalized.replace('-', '+').replace('_', '/')
    )

    for (candidate in candidates) {
        val padded = candidate.padEnd(candidate.length + ((4 - candidate.length % 4) % 4), '=')
        val decoded = tryDecodeBase64(padded, Base64.DEFAULT)
            ?: tryDecodeBase64(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        if (decoded != null) {
            return decoded
        }
    }

    return normalized
}

private fun tryDecodeBase64(raw: String, flags: Int): String? = try {
    String(Base64.decode(raw, flags), Charsets.UTF_8)
} catch (_: Exception) {
    null
}
