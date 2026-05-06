package com.safeharborsecurity.app.ui.safety

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.safeharborsecurity.app.data.model.QrAnalysisResult
import com.safeharborsecurity.app.ui.theme.*
import com.safeharborsecurity.app.util.QrCodeAnalyzer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit,
    onQrDetected: (QrAnalysisResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    var qrDetected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        if (hasCameraPermission) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Camera preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val analyzer = QrCodeAnalyzer { result ->
                                if (!qrDetected) {
                                    qrDetected = true
                                    onQrDetected(result)
                                }
                            }

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("QrScanner", "Camera binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Scan overlay
                ScanOverlay()

                // Instructions at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, "Scan", tint = Color.White, modifier = Modifier.size(32.dp))
                            Text(
                                "Point your camera at a QR code",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Safe Companion will check if it's safe",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // No camera permission
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.QrCodeScanner, "QR", tint = NavyBlue, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(24.dp))
                Text(
                    "Camera Permission Needed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NavyBlue,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Safe Companion needs your camera to scan QR codes. Please allow camera access.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Allow Camera", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ScanOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Semi-transparent overlay
        val cutoutSize = minOf(canvasWidth, canvasHeight) * 0.65f
        val cutoutLeft = (canvasWidth - cutoutSize) / 2f
        val cutoutTop = (canvasHeight - cutoutSize) / 2f - 60f
        val cutoutRect = Rect(cutoutLeft, cutoutTop, cutoutLeft + cutoutSize, cutoutTop + cutoutSize)

        // Draw semi-transparent background
        drawRect(Color.Black.copy(alpha = 0.5f))

        // Cut out the scan area
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(cutoutRect.left, cutoutRect.top),
            size = androidx.compose.ui.geometry.Size(cutoutSize, cutoutSize),
            cornerRadius = CornerRadius(16f, 16f),
            blendMode = BlendMode.Clear
        )

        // Draw corner brackets
        val bracketLength = 40f
        val bracketWidth = 4f
        val bracketColor = Color.White

        // Top-left
        drawLine(bracketColor, Offset(cutoutRect.left, cutoutRect.top + bracketLength), Offset(cutoutRect.left, cutoutRect.top), bracketWidth)
        drawLine(bracketColor, Offset(cutoutRect.left, cutoutRect.top), Offset(cutoutRect.left + bracketLength, cutoutRect.top), bracketWidth)

        // Top-right
        drawLine(bracketColor, Offset(cutoutRect.right - bracketLength, cutoutRect.top), Offset(cutoutRect.right, cutoutRect.top), bracketWidth)
        drawLine(bracketColor, Offset(cutoutRect.right, cutoutRect.top), Offset(cutoutRect.right, cutoutRect.top + bracketLength), bracketWidth)

        // Bottom-left
        drawLine(bracketColor, Offset(cutoutRect.left, cutoutRect.bottom - bracketLength), Offset(cutoutRect.left, cutoutRect.bottom), bracketWidth)
        drawLine(bracketColor, Offset(cutoutRect.left, cutoutRect.bottom), Offset(cutoutRect.left + bracketLength, cutoutRect.bottom), bracketWidth)

        // Bottom-right
        drawLine(bracketColor, Offset(cutoutRect.right - bracketLength, cutoutRect.bottom), Offset(cutoutRect.right, cutoutRect.bottom), bracketWidth)
        drawLine(bracketColor, Offset(cutoutRect.right, cutoutRect.bottom - bracketLength), Offset(cutoutRect.right, cutoutRect.bottom), bracketWidth)

        // Animated scan line
        val lineY = cutoutRect.top + (cutoutSize * scanLineY)
        drawLine(
            color = SafeGreen.copy(alpha = 0.8f),
            start = Offset(cutoutRect.left + 8f, lineY),
            end = Offset(cutoutRect.right - 8f, lineY),
            strokeWidth = 3f
        )
    }
}
