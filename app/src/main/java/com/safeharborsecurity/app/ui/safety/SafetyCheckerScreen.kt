package com.safeharborsecurity.app.ui.safety

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.safeharborsecurity.app.data.repository.AiImageAnalysis
import com.safeharborsecurity.app.data.repository.SafetyVerdict
import com.safeharborsecurity.app.ui.components.VerdictIcon
import com.safeharborsecurity.app.ui.components.toVerdict
import com.safeharborsecurity.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCheckerScreen(
    onNavigateBack: () -> Unit,
    onOpenSafeHarborWithContext: (String) -> Unit,
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToVoicemailScanner: () -> Unit = {},
    onNavigateToRoomScanner: () -> Unit = {},
    onNavigateToAppChecker: () -> Unit = {},
    onNavigateToTrackerScanner: () -> Unit = {},
    onNavigateToListeningShield: () -> Unit = {},
    viewModel: SafetyCheckerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showUrlInput by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var pastedText by remember { mutableStateOf("") }
    var showCameraPermissionGuide by remember { mutableStateOf(false) }

    val photoUri = remember {
        val photoFile = File(context.cacheDir, "safety_check_photo.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            viewModel.onImageSelected(photoUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showCameraPermissionGuide = false
            cameraLauncher.launch(photoUri)
        } else {
            showCameraPermissionGuide = true
        }
    }

    // Primary: PickVisualMedia (modern photo picker, works on Android 11+ natively, backported)
    val pickMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        Log.d("SafetyChecker", "PickVisualMedia result: uri=$uri scheme=${uri?.scheme} authority=${uri?.authority}")
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.d("SafetyChecker", "takePersistable not supported for $uri: ${e.message}")
            }
            // Verify we can actually read this URI before proceeding
            val canRead = try {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (e: Exception) {
                Log.e("SafetyChecker", "Cannot read URI $uri: ${e.message}")
                false
            }
            if (canRead) {
                viewModel.onImageSelected(uri)
            } else {
                Toast.makeText(context, "Could not open that photo. It may be stored in the cloud — try downloading it first.", Toast.LENGTH_LONG).show()
            }
        }
        // null = user cancelled picker, no error needed
    }

    // Fallback: GetContent (used only if PickVisualMedia is unavailable)
    val galleryFallbackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        Log.d("SafetyChecker", "GetContent fallback result: uri=$uri")
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            val canRead = try {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) { false }
            if (canRead) {
                viewModel.onImageSelected(uri)
            } else {
                Toast.makeText(context, "Could not open that photo. Please try downloading it to your device first.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Smart gallery launch — try PickVisualMedia first, fall back to GetContent
    val launchGallery = remember(pickMediaLauncher, galleryFallbackLauncher) {
        {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                galleryFallbackLauncher.launch("image/*")
            }
        }
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = { Text("Is This Safe?", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Show verdict if we have one
            state.verdict?.let { verdict ->
                VerdictCard(
                    verdict = verdict,
                    qrResult = state.qrResult,
                    onDismiss = { viewModel.clearVerdict() },
                    onShareWithFamily = { summary ->
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Safe Companion check result:\n\n$summary")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share result"))
                    }
                )
            }

            // Show error with retry button
            state.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⚠️", fontSize = 24.sp)
                            Text(error, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                        Button(
                            onClick = { viewModel.retryLastCheck() },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Try Again", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Show analyzing indicator
            if (state.isAnalyzing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = NavyBlue, strokeWidth = 3.dp)
                        Text(
                            state.analyzingMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NavyBlue
                        )
                    }
                }
            }

            // Image preview — show when user selected an image but hasn't checked yet
            state.selectedImageUri?.let { uri ->
                ImagePreviewCard(
                    imageUri = uri,
                    onCheck = { viewModel.analyzeSelectedImage() },
                    onCancel = { viewModel.clearSelectedImage() },
                    isAnalyzing = state.isAnalyzing
                )
            }

            // Only show option cards when no image preview and no verdict
            if (state.selectedImageUri == null && state.verdict == null) {
                // Header
                Text("What would you like to check?", style = MaterialTheme.typography.headlineSmall, color = NavyBlue, fontWeight = FontWeight.Bold)
                Text(
                    "Choose how you want to share something with Safe Companion. We'll check if it's safe for you.",
                    style = MaterialTheme.typography.bodyLarge, color = TextSecondary, lineHeight = 24.sp
                )
                Spacer(Modifier.height(4.dp))

                // Camera permission guidance
                if (showCameraPermissionGuide) {
                    CameraPermissionGuidanceCard(
                        context = context,
                        onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onUseGallery = { launchGallery() },
                        onDismiss = { showCameraPermissionGuide = false }
                    )
                }

                // Option cards
                SafetyOptionCard("📷", "Take a Photo", "Use your camera to photograph something you want to check", Icons.Default.CameraAlt) {
                    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasCameraPermission) {
                        cameraLauncher.launch(photoUri)
                    } else {
                        showCameraPermissionGuide = true
                    }
                }

                SafetyOptionCard("🖼️", "Choose from Gallery", "Pick a photo or screenshot you already have", Icons.Default.PhotoLibrary) {
                    launchGallery()
                }

                SafetyOptionCard("📸", "Take a Screenshot", "Capture what's on your screen right now", Icons.Default.Screenshot) {
                    Toast.makeText(context, "Press the Power + Volume Down buttons together to take a screenshot, then come back and choose \"Choose from Gallery\"", Toast.LENGTH_LONG).show()
                }

                SafetyOptionCard("📱", "Scan a QR Code", "Point your camera at a QR code to check if it's safe", Icons.Default.QrCodeScanner) {
                    onNavigateToQrScanner()
                }

                SafetyOptionCard("📞", "Check a Voicemail", "Find out if a voicemail is a scam", Icons.Default.Voicemail) {
                    onNavigateToVoicemailScanner()
                }

                SafetyOptionCard("🔍", "Scan This Room", "Check for hidden cameras and surveillance devices", Icons.Default.Search) {
                    onNavigateToRoomScanner()
                }

                SafetyOptionCard("📡", "Scan for Trackers Nearby", "Check for AirTags, Tiles, SmartTags, and other tracking devices", Icons.Default.Bluetooth) {
                    onNavigateToTrackerScanner()
                }

                SafetyOptionCard("🎤", "Find Listening Apps", "See which apps have used your microphone recently", Icons.Default.Mic) {
                    onNavigateToListeningShield()
                }

                SafetyOptionCard("📱", "Check an App on My Phone", "Find out if an app is safe and how to remove it", Icons.Default.Apps) {
                    onNavigateToAppChecker()
                }

                // Check from Clipboard
                SafetyOptionCard("📋", "Check from Clipboard", "Check text or a link you've already copied", Icons.Default.ContentPaste) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(context).toString()
                        if (text.isNotBlank()) {
                            if (text.contains("http://") || text.contains("https://") || text.contains("www.")) {
                                viewModel.analyzeUrl(text.trim())
                            } else {
                                viewModel.analyzeText(text)
                            }
                        } else {
                            Toast.makeText(context, "Your clipboard is empty. Copy some text first, then try again.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Your clipboard is empty. Copy some text first, then try again.", Toast.LENGTH_LONG).show()
                    }
                }

                // Enter a Web Address — in-line analysis
                if (!showUrlInput) {
                    SafetyOptionCard("🔗", "Enter a Web Address", "Type or paste a website link to check if it's safe", Icons.Default.Link) {
                        showUrlInput = true
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🔗", fontSize = 28.sp)
                                Text("Enter a Web Address", style = MaterialTheme.typography.titleMedium, color = NavyBlue, fontWeight = FontWeight.Bold)
                            }

                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("Website address") },
                                placeholder = { Text("e.g. www.example.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        if (urlText.isNotBlank()) {
                                            focusManager.clearFocus()
                                            viewModel.analyzeUrl(urlText.trim())
                                            showUrlInput = false
                                            urlText = ""
                                        }
                                    }
                                )
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showUrlInput = false; urlText = "" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        if (urlText.isNotBlank()) {
                                            focusManager.clearFocus()
                                            viewModel.analyzeUrl(urlText.trim())
                                            showUrlInput = false
                                            urlText = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = urlText.isNotBlank() && !state.isAnalyzing
                                ) {
                                    Text("Check it", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Paste an Email or Text — in-line analysis
                if (!showTextInput) {
                    SafetyOptionCard("✉️", "Paste an Email or Text", "Paste a message, email, or text you received to check it", Icons.Default.Email) {
                        showTextInput = true
                    }
                } else {
                    ExpandedInputCard(
                        emoji = "✉️",
                        title = "Paste an Email or Text",
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        label = "Paste the message here",
                        placeholder = "Copy the email or text message, then paste it here...",
                        onCancel = { showTextInput = false; pastedText = "" },
                        onSubmit = {
                            if (pastedText.isNotBlank()) {
                                focusManager.clearFocus()
                                viewModel.analyzeText(pastedText)
                                showTextInput = false
                                pastedText = ""
                            }
                        },
                        isLoading = state.isAnalyzing
                    )
                }

                // Recent check history
                if (state.history.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Recent Checks", style = MaterialTheme.typography.headlineSmall, color = NavyBlue)
                    state.history.take(5).forEach { result ->
                        val emoji = when (result.verdict) {
                            "SAFE" -> "🟢"
                            "SUSPICIOUS" -> "🟡"
                            "DANGEROUS" -> "🔴"
                            else -> "⚪"
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 24.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.verdict, style = MaterialTheme.typography.labelLarge, color = NavyBlue)
                                    Text(
                                        result.contentPreview.take(80) + if (result.contentPreview.length > 80) "..." else "",
                                        style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2
                                    )
                                }
                                Text(result.contentType, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Extra space at bottom for keyboard
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun ImagePreviewCard(
    imageUri: Uri,
    onCheck: () -> Unit,
    onCancel: () -> Unit,
    isAnalyzing: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Photo to Check",
                style = MaterialTheme.typography.titleMedium,
                color = NavyBlue,
                fontWeight = FontWeight.Bold
            )

            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "Photo to check",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )

            Button(
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                shape = RoundedCornerShape(12.dp),
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Checking...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Check This Photo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Choose a different photo")
            }
        }
    }
}

@Composable
private fun VerdictCard(
    verdict: SafetyVerdict,
    qrResult: com.safeharborsecurity.app.data.model.QrAnalysisResult? = null,
    onDismiss: () -> Unit,
    onShareWithFamily: (String) -> Unit
) {
    val verdictEnum = verdict.verdict.toVerdict()
    val (bgColor, textColor) = when (verdict.verdict) {
        "SAFE" -> Pair(SafeGreenLight, SafeGreen)
        "SUSPICIOUS" -> Pair(WarningAmberLight, WarningAmber)
        "DANGEROUS" -> Pair(ScamRedLight, ScamRed)
        else -> Pair(Color.LightGray, TextPrimary)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VerdictIcon(verdict = verdictEnum, size = 48.dp, showLabel = false)
                Text(
                    verdict.verdict,
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(verdict.summary, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, lineHeight = 24.sp)

            if (verdict.details.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("What we found", style = MaterialTheme.typography.titleSmall, color = NavyBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(verdict.details, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            if (verdict.whatToDoNext.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("What to do next", style = MaterialTheme.typography.titleSmall, color = NavyBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(verdict.whatToDoNext, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            // AI Image Analysis card
            verdict.aiImageAnalysis?.let { ai ->
                AIImageAnalysisCard(ai)
            }

            // Text content found in image
            if (verdict.containsText && verdict.textContent.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Text found in image", style = MaterialTheme.typography.titleSmall, color = NavyBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(verdict.textContent, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            // Fix 32: QR action button — SAFE verdicts only
            if (verdict.verdict == "SAFE" && verdict.contentType == "QR_CODE" && qrResult != null) {
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Show decoded URL for URL-type QR codes
                if (qrResult.qrType == com.safeharborsecurity.app.data.model.QrType.URL) {
                    Text(
                        text = qrResult.rawValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val (buttonLabel, buttonIcon) = when (qrResult.qrType) {
                    com.safeharborsecurity.app.data.model.QrType.URL,
                    com.safeharborsecurity.app.data.model.QrType.APP_LINK -> "Open Website" to Icons.Default.OpenInNew
                    com.safeharborsecurity.app.data.model.QrType.PHONE -> "Call This Number" to Icons.Default.Phone
                    com.safeharborsecurity.app.data.model.QrType.EMAIL -> "Send Email" to Icons.Default.Email
                    com.safeharborsecurity.app.data.model.QrType.SMS -> "Send Message" to Icons.Default.Sms
                    com.safeharborsecurity.app.data.model.QrType.GEO -> "Open Map" to Icons.Default.Place
                    com.safeharborsecurity.app.data.model.QrType.WIFI -> "Copy WiFi Details" to Icons.Default.Wifi
                    com.safeharborsecurity.app.data.model.QrType.CONTACT -> "Save Contact" to Icons.Default.PersonAdd
                    com.safeharborsecurity.app.data.model.QrType.CALENDAR -> "Add to Calendar" to Icons.Default.DateRange
                    com.safeharborsecurity.app.data.model.QrType.TEXT -> "Copy Text" to Icons.Default.Share
                    else -> "Copy Content" to Icons.Default.Share
                }

                Button(
                    onClick = {
                        try {
                            when (qrResult.qrType) {
                                com.safeharborsecurity.app.data.model.QrType.URL,
                                com.safeharborsecurity.app.data.model.QrType.APP_LINK -> {
                                    val url = if (qrResult.rawValue.startsWith("http")) qrResult.rawValue
                                              else "https://${qrResult.rawValue}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                                com.safeharborsecurity.app.data.model.QrType.PHONE -> {
                                    val phone = qrResult.rawValue.removePrefix("tel:")
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                }
                                com.safeharborsecurity.app.data.model.QrType.EMAIL -> {
                                    val email = qrResult.rawValue.removePrefix("mailto:")
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                                }
                                com.safeharborsecurity.app.data.model.QrType.SMS -> {
                                    val sms = qrResult.rawValue.removePrefix("smsto:").removePrefix("sms:")
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$sms")))
                                }
                                com.safeharborsecurity.app.data.model.QrType.GEO -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qrResult.rawValue)))
                                }
                                else -> {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Content", qrResult.rawValue))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: ActivityNotFoundException) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No app found to open this")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(buttonIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(buttonLabel, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Check something else")
                }
                Button(
                    onClick = { onShareWithFamily("${verdict.verdict}: ${verdict.summary}\n\n${verdict.whatToDoNext}") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Share with family", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ExpandedInputCard(
    emoji: String,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(emoji, fontSize = 28.sp)
                Text(title, style = MaterialTheme.typography.titleMedium, color = NavyBlue, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                singleLine = false,
                maxLines = 10,
                shape = RoundedCornerShape(12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp),
                    enabled = value.isNotBlank() && !isLoading
                ) {
                    Text("Check it", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionGuidanceCard(
    context: Context,
    onRequestPermission: () -> Unit,
    onUseGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val activity = context as? android.app.Activity
    val shouldShowRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false

    val permanentlyDenied = !shouldShowRationale &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📷", fontSize = 48.sp)

            Text(
                "Camera Access Needed",
                style = MaterialTheme.typography.titleMedium,
                color = NavyBlue,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                if (permanentlyDenied)
                    "You previously declined camera access. To use the camera, you'll need to turn it on in your phone's settings."
                else
                    "Safe Companion needs your camera to photograph letters, screens, or anything you want to check for scams. The camera is only used when you tap this button.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            if (permanentlyDenied) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Settings", color = Color.White)
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Allow Camera Access", color = Color.White)
                }
            }

            OutlinedButton(
                onClick = onUseGallery,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use Gallery Instead")
            }

            TextButton(onClick = onDismiss) {
                Text("Not right now", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SafetyOptionCard(
    emoji: String,
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(emoji, fontSize = 36.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = NavyBlue, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 22.sp)
            }
            Icon(Icons.Default.ChevronRight, "Open", tint = WarmGold, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun AIImageAnalysisCard(ai: AiImageAnalysis) {
    val (bgColor, emoji, title) = if (ai.isLikelyAiGenerated) {
        Triple(WarningAmberLight, "⚠️", "This image may have been created by a computer")
    } else {
        Triple(SafeGreenLight, "✅", "This looks like a real photograph")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji, fontSize = 24.sp)
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            }

            if (ai.confidence > 0f) {
                Text(
                    "Confidence: ${(ai.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            if (ai.indicators.isNotEmpty()) {
                ai.indicators.forEach { indicator ->
                    Text(
                        "• $indicator",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (ai.explanation.isNotBlank()) {
                Text(ai.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}
