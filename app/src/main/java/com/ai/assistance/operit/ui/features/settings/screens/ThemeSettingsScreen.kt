package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.components.ColorSelectionItem
import com.ai.assistance.operit.ui.features.settings.components.MediaTypeOption
import com.ai.assistance.operit.ui.features.settings.components.ThemeModeOption
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.launch

// Add utility function to calculate the luminance of a color
private fun calculateLuminance(color: Color): Float {
    return 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager(context) }
    val scope = rememberCoroutineScope()

    // Collect theme settings
    val themeMode =
            preferencesManager.themeMode.collectAsState(
                            initial = UserPreferencesManager.THEME_MODE_LIGHT
                    )
                    .value
    val useSystemTheme = preferencesManager.useSystemTheme.collectAsState(initial = true).value
    val customPrimaryColor =
            preferencesManager.customPrimaryColor.collectAsState(initial = null).value
    val customSecondaryColor =
            preferencesManager.customSecondaryColor.collectAsState(initial = null).value
    val useCustomColors = preferencesManager.useCustomColors.collectAsState(initial = false).value

    // Collect background image settings
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val backgroundImageOpacity =
            preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f).value

    // Collect background media type and video settings
    val backgroundMediaType =
            preferencesManager.backgroundMediaType.collectAsState(
                            initial = UserPreferencesManager.MEDIA_TYPE_IMAGE
                    )
                    .value
    val videoBackgroundMuted =
            preferencesManager.videoBackgroundMuted.collectAsState(initial = true).value
    val videoBackgroundLoop =
            preferencesManager.videoBackgroundLoop.collectAsState(initial = true).value

    // Default color definitions
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()

    // Mutable state
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var primaryColorInput by remember { mutableStateOf(customPrimaryColor ?: defaultPrimaryColor) }
    var secondaryColorInput by remember {
        mutableStateOf(customSecondaryColor ?: defaultSecondaryColor)
    }
    var useCustomColorsInput by remember { mutableStateOf(useCustomColors) }

    // Background image state
    var useBackgroundImageInput by remember { mutableStateOf(useBackgroundImage) }
    var backgroundImageUriInput by remember { mutableStateOf(backgroundImageUri) }
    var backgroundImageOpacityInput by remember { mutableStateOf(backgroundImageOpacity) }

    // Background media type and video settings state
    var backgroundMediaTypeInput by remember { mutableStateOf(backgroundMediaType) }
    var videoBackgroundMutedInput by remember { mutableStateOf(videoBackgroundMuted) }
    var videoBackgroundLoopInput by remember { mutableStateOf(videoBackgroundLoop) }

    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // Video player state
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
                // Add stricter memory limits
                .setLoadControl(
                        DefaultLoadControl.Builder()
                                .setBufferDurationsMs(
                                        5000, // Minimum buffer time reduced to 5 seconds
                                        10000, // Maximum buffer time reduced to 10 seconds
                                        500, // Minimum buffer for playback
                                        1000 // Minimum buffer for playback after rebuffering
                                )
                                .setTargetBufferBytes(5 * 1024 * 1024) // Limit buffer to 5MB
                                .setPrioritizeTimeOverSizeThresholds(true)
                                .build()
                )
                .build()
                .apply {
                    // Set loop playback
                    repeatMode = Player.REPEAT_MODE_ALL
                    // Set mute
                    volume = if (videoBackgroundMutedInput) 0f else 1f
                    playWhenReady = true

                    // If there's a background video URI, load it
                    if (backgroundImageUriInput != null &&
                                    backgroundMediaTypeInput ==
                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                    ) {
                        try {
                            val mediaItem = MediaItem.fromUri(Uri.parse(backgroundImageUriInput))
                            setMediaItem(mediaItem)
                            prepare()
                        } catch (e: Exception) {
                            Log.e("ThemeSettings", "Video loading error", e)
                        }
                    }
                }
    }

    // Free ExoPlayer resources when component is destroyed
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.e("ThemeSettings", "ExoPlayer release error", e)
            }
        }
    }

    // Handle video URI changes
    LaunchedEffect(backgroundImageUriInput, backgroundMediaTypeInput) {
        if (backgroundImageUriInput != null &&
                        backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO
        ) {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(backgroundImageUriInput)))
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                Log.e("ThemeSettings", "更新视频来源错误", e)
            }
        }
    }

    // Handle video settings changes - add error handling
    LaunchedEffect(videoBackgroundMutedInput, videoBackgroundLoopInput) {
        try {
            exoPlayer.volume = if (videoBackgroundMutedInput) 0f else 1f
            exoPlayer.repeatMode =
                    if (videoBackgroundLoopInput) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        } catch (e: Exception) {
            Log.e("ThemeSettings", "更新视频设置错误", e)
        }
    }

    // Image crop launcher
    val cropImageLauncher =
            rememberLauncherForActivityResult(CropImageContract()) { result ->
                if (result.isSuccessful) {
                    val croppedUri = result.uriContent
                    if (croppedUri != null) {
                        scope.launch {
                            val internalUri =
                                    FileUtils.copyFileToInternalStorage(context, croppedUri)
                            if (internalUri != null) {
                                backgroundImageUriInput = internalUri.toString()
                                backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_IMAGE
                                preferencesManager.saveThemeSettings(
                                        backgroundImageUri = internalUri.toString(),
                                        backgroundMediaType =
                                                UserPreferencesManager.MEDIA_TYPE_IMAGE
                                )
                                showSaveSuccessMessage = true
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_image_saved),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_copy_failed),
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    }
                } else if (result.error != null) {
                    Toast.makeText(
                                    context,
                                    context.getString(
                                            R.string.theme_image_crop_failed,
                                            result.error!!.message
                                    ),
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }

    // Launch image crop function
    fun launchImageCrop(uri: Uri) {
        // Use safe way to get system colors
        var primaryColor: Int
        var onPrimaryColor: Int
        var surfaceColor: Int
        var statusBarColor: Int

        // Check system dark theme
        val isNightMode =
                context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        try {
            // Try to use theme colors - this is a fallback option
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            primaryColor = typedValue.data

            // Try to get system status bar color (API 23+)
            try {
                context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
                statusBarColor = typedValue.data
            } catch (e: Exception) {
                // If unable to get, use theme color
                statusBarColor = primaryColor
            }

            context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            surfaceColor = typedValue.data

            onPrimaryColor =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } catch (e: Exception) {
            // Use fallback colors
            primaryColor = if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt() // Purple
            statusBarColor =
                    if (isNightMode) 0xFF7B1FA2.toInt() else 0xFF3700B3.toInt() // Dark purple
            surfaceColor =
                    if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            onPrimaryColor =
                    if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        }

        val cropOptions =
                CropImageContractOptions(
                        uri,
                        CropImageOptions().apply {
                            guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                            outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                            outputCompressQuality = 90
                            fixAspectRatio = false
                            cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                            activityTitle = context.getString(R.string.theme_crop_image)

                            // Set theme colors
                            toolbarColor = primaryColor
                            toolbarBackButtonColor = onPrimaryColor
                            toolbarTitleColor = onPrimaryColor
                            activityBackgroundColor = surfaceColor
                            backgroundColor = surfaceColor

                            // Status bar color
                            statusBarColor = statusBarColor

                            // Use light/dark theme
                            activityMenuIconColor = onPrimaryColor

                            // Improve user experience
                            showCropOverlay = true
                            showProgressBar = true
                            multiTouchEnabled = true
                            autoZoomEnabled = true
                        }
                )
        cropImageLauncher.launch(cropOptions)
    }

    // Image/video picker launcher
    val mediaPickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                if (uri != null) {
                    // Check if it's a video file
                    val isVideo = FileUtils.isVideoFile(context, uri)

                    if (isVideo) {
                        // Video file check size
                        val isVideoSizeAcceptable =
                                FileUtils.checkVideoSize(context, uri, 30) // Limit to 30MB

                        if (!isVideoSizeAcceptable) {
                            // Video too large, show warning
                            Toast.makeText(
                                            context,
                                            context.getString(R.string.theme_video_too_large),
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                            return@rememberLauncherForActivityResult
                        }

                        // Video file size acceptable, directly save
                        scope.launch {
                            val internalUri = FileUtils.copyFileToInternalStorage(context, uri)

                            if (internalUri != null) {
                                backgroundImageUriInput = internalUri.toString()
                                backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_VIDEO
                                preferencesManager.saveThemeSettings(
                                        backgroundImageUri = internalUri.toString(),
                                        backgroundMediaType =
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                )
                                showSaveSuccessMessage = true
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_video_saved),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            } else {
                                Toast.makeText(
                                                context,
                                                context.getString(R.string.theme_copy_failed),
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    } else {
                        // Image file first launch crop
                        launchImageCrop(uri)
                    }
                }
            }

    // Migrate existing background image if needed (on first load)
    LaunchedEffect(Unit) {
        // Check if we have a background image URI that starts with content://
        backgroundImageUri?.let { uriString ->
            if (uriString.startsWith("content://")) {
                try {
                    // Try to copy to internal storage
                    val uri = Uri.parse(uriString)
                    scope.launch {
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri)
                        if (internalUri != null) {
                            // Update the URI in preferences
                            preferencesManager.saveThemeSettings(
                                    backgroundImageUri = internalUri.toString()
                            )
                            // Update the local state
                            backgroundImageUriInput = internalUri.toString()
                            Toast.makeText(context, context.getString(R.string.theme_bg_migrated_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ThemeSettings", "Error migrating background image", e)
                    // If migration fails, disable background image to prevent
                    // crashes
                    scope.launch {
                        preferencesManager.saveThemeSettings(useBackgroundImage = false)
                        useBackgroundImageInput = false
                        Toast.makeText(context, context.getString(R.string.theme_bg_migration_failed_toast), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // When settings change, update local state
    LaunchedEffect(
            themeMode,
            useSystemTheme,
            customPrimaryColor,
            customSecondaryColor,
            useCustomColors,
            useBackgroundImage,
            backgroundImageUri,
            backgroundImageOpacity,
            backgroundMediaType,
            videoBackgroundMuted,
            videoBackgroundLoop
    ) {
        themeModeInput = themeMode
        useSystemThemeInput = useSystemTheme
        if (customPrimaryColor != null) primaryColorInput = customPrimaryColor
        if (customSecondaryColor != null) secondaryColorInput = customSecondaryColor
        useCustomColorsInput = useCustomColors
        useBackgroundImageInput = useBackgroundImage
        backgroundImageUriInput = backgroundImageUri
        backgroundImageOpacityInput = backgroundImageOpacity
        backgroundMediaTypeInput = backgroundMediaType
        videoBackgroundMutedInput = videoBackgroundMuted
        videoBackgroundLoopInput = videoBackgroundLoop
    }

    // Get background image state to check if we need opaque cards
    val hasBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value

    // Color surface modifier based on whether background image is used
    val cardModifier =
            if (hasBackgroundImage) {
                // Make cards fully opaque when background image is used
                CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f)
                )
            } else {
                CardDefaults.cardColors()
            }

    // Add a scroll state that we can control
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        // ======= SECTION 1: THEME MODE =======
        ThemeSectionTitle(
                title = stringResource(id = R.string.theme_title_mode),
                icon = Icons.Default.Brightness4
        )

        // System theme settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_system_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Follow system theme
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_follow_system),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_follow_system_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useSystemThemeInput,
                            onCheckedChange = {
                                useSystemThemeInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(useSystemTheme = it)
                                    showSaveSuccessMessage = true
                                }
                            }
                    )
                }

                // Only show theme selection when not following system
                if (!useSystemThemeInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                            text = stringResource(id = R.string.theme_select),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Theme mode selection
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                                title = stringResource(id = R.string.theme_light),
                                selected =
                                        themeModeInput == UserPreferencesManager.THEME_MODE_LIGHT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
                                    scope.launch {
                                        preferencesManager.saveThemeSettings(
                                                themeMode = UserPreferencesManager.THEME_MODE_LIGHT
                                        )
                                        showSaveSuccessMessage = true
                                    }
                                }
                        )

                        ThemeModeOption(
                                title = stringResource(id = R.string.theme_dark),
                                selected = themeModeInput == UserPreferencesManager.THEME_MODE_DARK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    themeModeInput = UserPreferencesManager.THEME_MODE_DARK
                                    scope.launch {
                                        preferencesManager.saveThemeSettings(
                                                themeMode = UserPreferencesManager.THEME_MODE_DARK
                                        )
                                        showSaveSuccessMessage = true
                                    }
                                }
                        )
                    }
                }
            }
        }

        // ======= SECTION 2: COLOR CUSTOMIZATION =======
        ThemeSectionTitle(
                title = stringResource(id = R.string.theme_title_color),
                icon = Icons.Default.ColorLens
        )

        // Custom color settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_custom_color),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Whether to use custom colors
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_color),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_custom_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useCustomColorsInput,
                            onCheckedChange = {
                                useCustomColorsInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(useCustomColors = it)
                                    showSaveSuccessMessage = true
                                }
                            }
                    )
                }

                // Only show color selection when custom colors are enabled
                if (useCustomColorsInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                            text = stringResource(id = R.string.theme_select_color),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Color selection
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Primary color selection
                        ColorSelectionItem(
                                title = stringResource(id = R.string.theme_primary_color),
                                color = Color(primaryColorInput),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentColorPickerMode = "primary"
                                    showColorPicker = true
                                }
                        )

                        // Secondary color selection
                        ColorSelectionItem(
                                title = stringResource(id = R.string.theme_secondary_color),
                                color = Color(secondaryColorInput),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentColorPickerMode = "secondary"
                                    showColorPicker = true
                                }
                        )
                    }

                    // Add a color preview section to show how colors will look
                    Text(
                            text = stringResource(id = R.string.theme_preview),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Create a mini-preview of how the selected colors will look
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        // Primary color demo
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            val primaryColor = Color(primaryColorInput)
                            val onPrimaryColor = getTextColorForBackground(primaryColor)

                            // Primary button preview
                            Surface(
                                    modifier =
                                            Modifier.weight(1f).height(40.dp).padding(end = 8.dp),
                                    color = primaryColor,
                                    shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            stringResource(id = R.string.theme_primary_button),
                                            color = onPrimaryColor,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Secondary button preview
                            val secondaryColor = Color(secondaryColorInput)
                            val onSecondaryColor = getTextColorForBackground(secondaryColor)

                            Surface(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    color = secondaryColor,
                                    shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            stringResource(id = R.string.theme_secondary_button),
                                            color = onSecondaryColor,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Text(
                                text = stringResource(id = R.string.theme_contrast_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Save custom colors button
                    Button(
                            onClick = {
                                scope.launch {
                                    preferencesManager.saveThemeSettings(
                                            customPrimaryColor = primaryColorInput,
                                            customSecondaryColor = secondaryColorInput
                                    )
                                    showSaveSuccessMessage = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) { Text(stringResource(id = R.string.theme_save_colors)) }
                }
            }
        }

        // ======= SECTION 3: BACKGROUND CUSTOMIZATION =======
        ThemeSectionTitle(
                title = stringResource(id = R.string.theme_title_background),
                icon = Icons.Default.Image
        )

        // Background media settings
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(id = R.string.theme_bg_media),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // Whether to use background image
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(id = R.string.theme_use_custom_bg),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = stringResource(id = R.string.theme_custom_bg_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useBackgroundImageInput,
                            onCheckedChange = {
                                useBackgroundImageInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(useBackgroundImage = it)
                                    showSaveSuccessMessage = true
                                }
                            }
                    )
                }

                // Only show image selection when background image is enabled
                if (useBackgroundImageInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Media type selection
                    Text(
                            text = stringResource(id = R.string.theme_media_type),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaTypeOption(
                                title = stringResource(id = R.string.theme_media_image),
                                icon = Icons.Default.Image,
                                selected =
                                        backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_IMAGE,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    backgroundMediaTypeInput =
                                            UserPreferencesManager.MEDIA_TYPE_IMAGE
                                    if (backgroundImageUriInput != null) {
                                        // If there's already a background, save the media type
                                        scope.launch {
                                            preferencesManager.saveThemeSettings(
                                                    backgroundMediaType =
                                                            UserPreferencesManager.MEDIA_TYPE_IMAGE
                                            )
                                            showSaveSuccessMessage = true
                                        }
                                    }
                                }
                        )

                        MediaTypeOption(
                                title = stringResource(id = R.string.theme_media_video),
                                icon = Icons.Default.Videocam,
                                selected =
                                        backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    backgroundMediaTypeInput =
                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                                    if (backgroundImageUriInput != null) {
                                        // If there's already a background, save the media type
                                        scope.launch {
                                            preferencesManager.saveThemeSettings(
                                                    backgroundMediaType =
                                                            UserPreferencesManager.MEDIA_TYPE_VIDEO
                                            )
                                            showSaveSuccessMessage = true
                                        }
                                    }
                                }
                        )
                    }

                    // Current selected media preview
                    if (backgroundImageUriInput != null) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(200.dp)
                                                .padding(bottom = 16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(Color.Black.copy(alpha = 0.1f))
                        ) {
                            if (backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_IMAGE
                            ) {
                                // Image preview
                                Image(
                                        painter =
                                                rememberAsyncImagePainter(
                                                        Uri.parse(backgroundImageUriInput)
                                                ),
                                        contentDescription = "背景图片预览",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                )

                                // Crop button
                                IconButton(
                                        onClick = {
                                            backgroundImageUriInput?.let {
                                                launchImageCrop(Uri.parse(it))
                                            }
                                        },
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f),
                                                                CircleShape
                                                        )
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Crop,
                                            contentDescription = "重新裁剪",
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                // Video preview
                                // Capture the background color from the Composable context
                                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                                // Determine if it's a light theme
                                val isLightTheme =
                                        calculateLuminance(MaterialTheme.colorScheme.background) >
                                                0.5f

                                AndroidView(
                                        factory = { ctx ->
                                            StyledPlayerView(ctx).apply {
                                                player = exoPlayer
                                                useController = false
                                                layoutParams =
                                                        ViewGroup.LayoutParams(
                                                                MATCH_PARENT,
                                                                MATCH_PARENT
                                                        )
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                // Use the captured background color
                                                setBackgroundColor(backgroundColor)
                                                // Create a semi-transparent overlay on the player
                                                // itself for opacity control
                                                foreground =
                                                        android.graphics.drawable.ColorDrawable(
                                                                android.graphics.Color.argb(
                                                                        ((1f -
                                                                                        backgroundImageOpacityInput) *
                                                                                        255)
                                                                                .toInt(),
                                                                        // Use white for light
                                                                        // theme, black for dark
                                                                        // theme
                                                                        if (isLightTheme) 255
                                                                        else 0,
                                                                        if (isLightTheme) 255
                                                                        else 0,
                                                                        if (isLightTheme) 255 else 0
                                                                )
                                                        )
                                            }
                                        },
                                        update = { view ->
                                            // Update the foreground transparency when opacity
                                            // changes
                                            view.foreground =
                                                    android.graphics.drawable.ColorDrawable(
                                                            android.graphics.Color.argb(
                                                                    ((1f -
                                                                                    backgroundImageOpacityInput) *
                                                                                    255)
                                                                            .toInt(),
                                                                    // Use white for light theme,
                                                                    // black for dark theme
                                                                    if (isLightTheme) 255 else 0,
                                                                    if (isLightTheme) 255 else 0,
                                                                    if (isLightTheme) 255 else 0
                                                            )
                                                    )
                                        },
                                        modifier = Modifier.fillMaxSize()
                                )

                                // Video control buttons
                                Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    // Mute button
                                    IconButton(
                                            onClick = {
                                                videoBackgroundMutedInput =
                                                        !videoBackgroundMutedInput
                                                scope.launch {
                                                    preferencesManager.saveThemeSettings(
                                                            videoBackgroundMuted =
                                                                    videoBackgroundMutedInput
                                                    )
                                                    showSaveSuccessMessage = true
                                                }
                                            },
                                            modifier =
                                                    Modifier.padding(end = 8.dp)
                                                            .background(
                                                                    MaterialTheme.colorScheme
                                                                            .surface.copy(
                                                                            alpha = 0.7f
                                                                    ),
                                                                    CircleShape
                                                            )
                                    ) {
                                        Icon(
                                                imageVector =
                                                        if (videoBackgroundMutedInput)
                                                                Icons.Default.VolumeOff
                                                        else Icons.Default.VolumeUp,
                                                contentDescription =
                                                        if (videoBackgroundMutedInput)
                                                                stringResource(
                                                                        id = R.string.theme_unmute
                                                                )
                                                        else
                                                                stringResource(
                                                                        id = R.string.theme_mute
                                                                ),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Loop button
                                    IconButton(
                                            onClick = {
                                                videoBackgroundLoopInput = !videoBackgroundLoopInput
                                                scope.launch {
                                                    preferencesManager.saveThemeSettings(
                                                            videoBackgroundLoop =
                                                                    videoBackgroundLoopInput
                                                    )
                                                    showSaveSuccessMessage = true

                                                    // Show Toast notification about status change
                                                    Toast.makeText(
                                                                    context,
                                                                    if (videoBackgroundLoopInput)
                                                                            context.getString(
                                                                                    R.string
                                                                                            .theme_loop_enabled
                                                                            )
                                                                    else
                                                                            context.getString(
                                                                                    R.string
                                                                                            .theme_loop_disabled
                                                                            ),
                                                                    Toast.LENGTH_SHORT
                                                            )
                                                            .show()
                                                }
                                            },
                                            modifier =
                                                    Modifier.background(
                                                            // Show different background color based
                                                            // on loop state
                                                            if (videoBackgroundLoopInput)
                                                                    MaterialTheme.colorScheme
                                                                            .primary.copy(
                                                                            alpha = 0.3f
                                                                    )
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .surface.copy(
                                                                            alpha = 0.7f
                                                                    ),
                                                            CircleShape
                                                    )
                                    ) {
                                        Icon(
                                                if (videoBackgroundLoopInput) Icons.Default.Loop
                                                else Icons.Outlined.Loop,
                                                contentDescription =
                                                        if (videoBackgroundLoopInput)
                                                                stringResource(
                                                                        id = R.string.theme_loop_on
                                                                )
                                                        else
                                                                stringResource(
                                                                        id = R.string.theme_loop_off
                                                                ),
                                                tint =
                                                        if (videoBackgroundLoopInput)
                                                                MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(150.dp)
                                                .padding(bottom = 16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                            Text(
                                    text = stringResource(id = R.string.theme_no_bg_selected),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                            onClick = {
                                if (backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                ) {
                                    mediaPickerLauncher.launch("video/*")
                                } else {
                                    mediaPickerLauncher.launch("image/*")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                                if (backgroundMediaTypeInput ==
                                                UserPreferencesManager.MEDIA_TYPE_VIDEO
                                )
                                        stringResource(id = R.string.theme_select_video)
                                else stringResource(id = R.string.theme_select_image)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opacity adjustment
                    Text(
                            text =
                                    stringResource(
                                            id = R.string.theme_bg_opacity,
                                            (backgroundImageOpacityInput * 100).toInt()
                                    ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Remember last saved value for debounce save operation
                    var lastSavedOpacity by remember { mutableStateOf(backgroundImageOpacityInput) }

                    // Use rememberUpdatedState to stabilize callback function
                    val currentScope = rememberCoroutineScope()

                    // Create a simpler disableScrollWhileDragging state
                    var isDragging by remember { mutableStateOf(false) }

                    // Custom interaction source, helps us monitor drag state
                    val interactionSource = remember { MutableInteractionSource() }

                    // Monitor drag state
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            when (interaction) {
                                is DragInteraction.Start -> isDragging = true
                                is DragInteraction.Stop -> isDragging = false
                                is DragInteraction.Cancel -> isDragging = false
                            }
                        }
                    }

                    // If in drag state, temporarily lock scrolling
                    if (isDragging) {
                        DisposableEffect(Unit) {
                            val previousScrollValue = scrollState.value
                            onDispose {
                                // Nothing to do on dispose
                            }
                        }
                    }

                    // Create a fixed update callback and finish callback
                    val updateOpacity = remember {
                        { value: Float -> backgroundImageOpacityInput = value }
                    }

                    val onValueChangeFinished = remember {
                        {
                            if (kotlin.math.abs(lastSavedOpacity - backgroundImageOpacityInput) >
                                            0.01f
                            ) {
                                currentScope.launch {
                                    preferencesManager.saveThemeSettings(
                                            backgroundImageOpacity = backgroundImageOpacityInput
                                    )
                                    lastSavedOpacity = backgroundImageOpacityInput
                                    showSaveSuccessMessage = true
                                }
                            }
                        }
                    }

                    // Use Box to wrap slider, solve drag issue
                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp)) {
                        Slider(
                                value = backgroundImageOpacityInput,
                                onValueChange = updateOpacity,
                                onValueChangeFinished = onValueChangeFinished,
                                valueRange = 0.1f..1f,
                                interactionSource = interactionSource,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor =
                                                        MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                        )
                        )
                    }

                    // Add a gap, ensure slider below has enough space
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Reset button
        OutlinedButton(
                onClick = {
                    scope.launch {
                        preferencesManager.resetThemeSettings()
                        // Reset local state after reset
                        themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
                        useSystemThemeInput = true
                        primaryColorInput = defaultPrimaryColor
                        secondaryColorInput = defaultSecondaryColor
                        useCustomColorsInput = false
                        useBackgroundImageInput = false
                        backgroundImageUriInput = null
                        backgroundImageOpacityInput = 0.3f
                        backgroundMediaTypeInput = UserPreferencesManager.MEDIA_TYPE_IMAGE
                        videoBackgroundMutedInput = true
                        videoBackgroundLoopInput = true
                        showSaveSuccessMessage = true
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) { Text(stringResource(id = R.string.theme_reset)) }

        // Show save success message
        if (showSaveSuccessMessage) {
            LaunchedEffect(key1 = showSaveSuccessMessage) {
                kotlinx.coroutines.delay(2000)
                showSaveSuccessMessage = false
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                        text = stringResource(id = R.string.theme_saved),
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Color picker dialog
        if (showColorPicker) {
            ColorPickerDialog(
                    showColorPicker = showColorPicker,
                    currentColorPickerMode = currentColorPickerMode,
                    primaryColorInput = primaryColorInput,
                    secondaryColorInput = secondaryColorInput,
                    onColorSelected = { primaryColor, secondaryColor ->
                        primaryColor?.let { primaryColorInput = it }
                        secondaryColor?.let { secondaryColorInput = it }

                        // Save the colors
                        scope.launch {
                            if (currentColorPickerMode == "primary" && primaryColor != null) {
                                preferencesManager.saveThemeSettings(
                                        customPrimaryColor = primaryColor
                                )
                            } else if (currentColorPickerMode == "secondary" &&
                                            secondaryColor != null
                            ) {
                                preferencesManager.saveThemeSettings(
                                        customSecondaryColor = secondaryColor
                                )
                            }
                            showSaveSuccessMessage = true
                        }
                    },
                    onDismiss = { showColorPicker = false }
            )
        }
    }
}

@Composable
private fun ThemeSectionTitle(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )
    }
    Divider(modifier = Modifier.padding(bottom = 8.dp))
}
