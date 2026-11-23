package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.engine.ParsedQrisResult
import com.example.engine.QrisParser
import com.example.model.CellAddress
import com.example.model.QrisInsertMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrisScannerScreen(
    activeCell: CellAddress,
    qrisInsertMode: QrisInsertMode,
    autoAdvanceRow: Boolean,
    onSelectMode: (QrisInsertMode) -> Unit,
    onToggleAutoAdvance: (Boolean) -> Unit,
    onQrisDetected: (ParsedQrisResult) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var continuousBatch by remember { mutableStateOf(false) }
    var showManualPasteDialog by remember { mutableStateOf(false) }
    var cameraControlRef by remember { mutableStateOf<Camera?>(null) }
    val processingLock = remember { AtomicBoolean(false) }
    var lastScanTimestamp by remember { mutableStateOf(0L) }

    val offlineScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            offlineScanner.close()
        }
    }

    fun handleRawQr(raw: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTimestamp < 1500L) return
        if (!processingLock.compareAndSet(false, true)) return
        lastScanTimestamp = now

        val parsed = QrisParser.parse(raw)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onQrisDetected(parsed)

        if (continuousBatch) {
            scope.launch {
                val targetLabel = parsed.merchantName ?: raw.take(24)
                snackbarHostState.showSnackbar("Masuk ke ${activeCell.toA1()}: $targetLabel")
                kotlinx.coroutines.delay(1400L)
                processingLock.set(false)
            }
        } else {
            onBack()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                offlineScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val firstRaw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (firstRaw != null) {
                            handleRawQr(firstRaw)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Tidak ditemukan kode QR / QRIS pada gambar.")
                            }
                        }
                    }
                    .addOnFailureListener {
                        scope.launch {
                            snackbarHostState.showSnackbar("Gagal membaca gambar QRIS.")
                        }
                    }
            } catch (_: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Gagal membuka gambar dari galeri.")
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Black
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                CameraPreviewWithQrAnalyzer(
                    scanner = offlineScanner,
                    onCameraReady = { cam -> cameraControlRef = cam },
                    onQrFound = { raw -> handleRawQr(raw) },
                    modifier = Modifier.fillMaxSize()
                )
                ScannerViewfinderOverlay(modifier = Modifier.fillMaxSize())
            } else {
                CameraPermissionRationale(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickFromGallery = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onManualPaste = { showManualPasteDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp)
                )
            }

            // Top HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("scanner_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sel Aktif: ${activeCell.toA1()}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasCameraPermission) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.62f),
                            shape = CircleShape
                        ) {
                            IconButton(
                                onClick = {
                                    torchEnabled = !torchEnabled
                                    cameraControlRef?.cameraControl?.enableTorch(torchEnabled)
                                },
                                modifier = Modifier.testTag("scanner_torch_button")
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                    contentDescription = "Senter",
                                    tint = if (torchEnabled) Color(0xFFFDE047) else Color.White
                                )
                            }
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("scanner_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = "Scan dari Galeri",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Bottom Control HUD
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Format Input ke Sel ${activeCell.toA1()}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = qrisInsertMode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { showManualPasteDialog = true },
                            modifier = Modifier.testTag("scanner_manual_paste_button")
                        ) {
                            Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tempel")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QrisInsertMode.entries.forEach { mode ->
                            FilterChip(
                                selected = qrisInsertMode == mode,
                                onClick = { onSelectMode(mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("qris_mode_${mode.name}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = autoAdvanceRow,
                                onCheckedChange = onToggleAutoAdvance,
                                modifier = Modifier.testTag("auto_advance_switch")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Turun sel bawah",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = continuousBatch,
                                onCheckedChange = { continuousBatch = it },
                                modifier = Modifier.testTag("continuous_scan_switch")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan beruntun",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualPasteDialog) {
        var rawInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualPasteDialog = false },
            title = { Text("Tempel String Kode QRIS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tempel data EMVCo QRIS (diawali 000201...) atau teks QR untuk langsung diproses dan dimasukkan ke sel ${activeCell.toA1()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        label = { Text("Payload QR / QRIS") },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_qris_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = rawInput.isNotBlank(),
                    onClick = {
                        showManualPasteDialog = false
                        val parsed = QrisParser.parse(rawInput)
                        onQrisDetected(parsed)
                        if (!continuousBatch) onBack()
                    },
                    modifier = Modifier.testTag("manual_qris_submit")
                ) {
                    Text("Masukkan ke ${activeCell.toA1()}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualPasteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun CameraPreviewWithQrAnalyzer(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCameraReady: (Camera) -> Unit,
    onQrFound: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (raw != null) onQrFound(raw)
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
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(cam)
                } catch (_: Exception) {
                    // Camera unavailable on device/emulator
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
private fun ScannerViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boxSize = size.minDimension * 0.64f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f - 40.dp.toPx()

        drawRect(color = Color.Black.copy(alpha = 0.50f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            blendMode = BlendMode.Clear
        )
        drawRoundRect(
            color = Color(0xFF5CE6A0),
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    onPickFromGallery: () -> Unit,
    onManualPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.camera_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(stringResource(R.string.camera_permission_grant))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Pilih Gambar QR")
            }
            OutlinedButton(
                onClick = onManualPaste,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Tempel Teks")
            }
        }
    }
}
