package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.ui.components.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.ui.components.SyncedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.hasCameraHardware
import com.example.ui.components.tvFocusHighlight
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasCamera = remember(context) { context.hasCameraHardware() }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var handledCode by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasCamera) {
        if (hasCamera && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val barcodeScanner = remember(hasCamera) {
        if (!hasCamera) null
        else {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(options)
        }
    }

    val cameraController = remember(hasCamera) {
        if (!hasCamera) null else LifecycleCameraController(context)
    }

    DisposableEffect(cameraController, barcodeScanner) {
        onDispose {
            cameraController?.unbind()
            barcodeScanner?.close()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = { Text(stringResource(R.string.qr_title)) },
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    if (hasCamera && hasPermission && cameraController != null) {
                        IconButton(
                            onClick = {
                                torchEnabled = !torchEnabled
                                cameraController.enableTorch(torchEnabled)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .tvFocusHighlight(cornerRadius = 14.dp)
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) AppIcons.FlashOn else AppIcons.FlashOff,
                                contentDescription = stringResource(R.string.qr_title)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!hasCamera) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.NoPhotography,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = stringResource(R.string.qr_no_camera_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.qr_no_camera_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .tvFocusHighlight(cornerRadius = 20.dp)
                            .testTag("qr_no_camera_back_button")
                    ) {
                        Text(stringResource(R.string.action_go_back))
                    }
                }
            }
        } else if (!hasPermission || cameraController == null || barcodeScanner == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = stringResource(R.string.qr_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.qr_permission_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .tvFocusHighlight(cornerRadius = 20.dp)
                            .testTag("grant_camera_permission_button")
                    ) {
                        Text(stringResource(R.string.action_grant_camera))
                    }
                }
            }
        } else {
            val frameColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            val executor = ContextCompat.getMainExecutor(ctx)
                            cameraController.setImageAnalysisAnalyzer(
                                executor,
                                MlKitAnalyzer(
                                    listOf(barcodeScanner),
                                    COORDINATE_SYSTEM_VIEW_REFERENCED,
                                    executor
                                ) { result ->
                                    if (handledCode) return@MlKitAnalyzer
                                    val codes = result?.getValue(barcodeScanner).orEmpty()
                                    val value = codes.firstNotNullOfOrNull { it.rawValue }
                                    if (!value.isNullOrBlank()) {
                                        handledCode = true
                                        onQrScanned(value)
                                    }
                                }
                            )
                            cameraController.bindToLifecycle(lifecycleOwner)
                            controller = cameraController
                        }
                    }
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = size.minDimension * 0.64f
                    val topLeft = Offset(
                        x = (size.width - boxSize) / 2f,
                        y = (size.height - boxSize) / 2f
                    )
                    drawRect(color = Color.Black.copy(alpha = 0.45f))
                    drawRoundRect(
                        color = frameColor,
                        topLeft = topLeft,
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.qr_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
