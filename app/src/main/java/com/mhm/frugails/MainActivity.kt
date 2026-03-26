package com.mhm.frugails

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            GammaStimApp(viewModel)
        }
    }
}

@Composable
fun GammaStimApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val themeStr by viewModel.theme.collectAsState()

    // Simplifed Theme Colors logic (Black/White only)
    val bgColor = if (themeStr == "White") Color.White else Color.Black
    val contentColor = if (themeStr == "White") Color.Black else Color.White

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor, contentColor = contentColor) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") { MainScreen(viewModel, navController, contentColor) }
            composable("options") { OptionsScreen(viewModel, navController, contentColor) }
            composable("info") { InfoScreen(viewModel, navController, contentColor) }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, navController: androidx.navigation.NavController, textColor: Color) {
    val context = LocalContext.current
    val isRunning by StimulationService.isRunning.collectAsState()
    val remainingSeconds by StimulationService.remainingTime.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()
    val timerMinutes by viewModel.timerMinutes.collectAsState()
    val mode by viewModel.stimulationMode.collectAsState()
    val playDing by viewModel.playDing.collectAsState()
    val slowerFlicker by viewModel.slowerFlicker.collectAsState()

    val totalSeconds = timerMinutes * 60
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    val rainbowColorsList = listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
    val goldenColor = Color(0xFFFFD700)

    // Dynamic brush for instruction text (Moves when not running)
    val instructionBrush = if (isRunning) {
        SolidColor(textColor)
    } else {
        Brush.linearGradient(
            colors = rainbowColorsList,
            start = Offset(rotation * 4, 0f),
            end = Offset(rotation * 4 + 800f, 0f)
        )
    }

    // Standard Grey Rounded Box for all themes
    val standardBoxModifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.DarkGray.copy(alpha = 0.3f)).padding(16.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 100.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {

            // --- TOP SECTION (Title + Description) ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FRUGAILS", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = textColor, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = standardBoxModifier) {
                    Text(Translations.getString(currentLang, "main_desc"), fontSize = 14.sp, textAlign = TextAlign.Center, color = textColor, lineHeight = 20.sp)
                }
            }

            // --- CENTER SECTION (Button) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clickable {
                    if (isRunning) {
                        context.startService(Intent(context, StimulationService::class.java).apply { action = StimulationService.ACTION_STOP })
                    } else {
                        val intent = Intent(context, StimulationService::class.java).apply {
                            action = StimulationService.ACTION_START
                            putExtra(StimulationService.EXTRA_MINUTES, timerMinutes)
                            putExtra(StimulationService.EXTRA_AUDIO_ONLY, mode == "Audio Only")
                            putExtra(StimulationService.EXTRA_FLASHLIGHT_ONLY, mode == "Flashlight Only")
                            putExtra(StimulationService.EXTRA_PLAY_DING, playDing)
                            putExtra(StimulationService.EXTRA_SLOWER_FLICKER, slowerFlicker)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                        else context.startService(intent)
                    }
                }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2

                    // Inactive base ring (track)
                    drawArc(color = Color.DarkGray, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                    if (isRunning) {
                        val sweep = progress * 360f

                        // Active Progress Ring (Animated Rainbow)
                        rotate(rotation) {
                            drawArc(
                                brush = Brush.sweepGradient(rainbowColorsList),
                                startAngle = -90f - rotation,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Moving Tracking Dot (Now Golden!)
                        val dotAngleRads = Math.toRadians((-90f + sweep).toDouble())
                        val dotX = center.x + radius * cos(dotAngleRads).toFloat()
                        val dotY = center.y + radius * sin(dotAngleRads).toFloat()
                        drawCircle(color = goldenColor, radius = 10.dp.toPx(), center = Offset(dotX, dotY))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRunning) {
                        val mins = remainingSeconds / 60
                        val secs = remainingSeconds % 60
                        Text(String.format("%02d:%02d", mins, secs), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(Translations.getString(currentLang, "stop"), fontSize = 18.sp, color = textColor)
                    } else {
                        Text(Translations.getString(currentLang, "start"), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("$timerMinutes min", fontSize = 18.sp, color = textColor)
                    }
                }
            }

            // --- BOTTOM SECTION (Instructions + Disclaimer) ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = standardBoxModifier) {
                    Text(
                        text = Translations.getString(currentLang, "main_inst"),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = if (isRunning) textColor else Color.Unspecified,
                        style = androidx.compose.ui.text.TextStyle(brush = instructionBrush)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = Translations.getString(currentLang, "main_disc"),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
            }
        }

        // Navigation Icons
        Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { navController.navigate("options") }) { Icon(Icons.Default.Settings, contentDescription = "Options", modifier = Modifier.size(36.dp), tint = textColor) }
            IconButton(onClick = { navController.navigate("info") }) { Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(36.dp), tint = textColor) }
        }
    }
}

@Composable
fun OptionsScreen(viewModel: MainViewModel, navController: androidx.navigation.NavController, textColor: Color) {
    val currentLang by viewModel.currentLanguage.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val timerMinutes by viewModel.timerMinutes.collectAsState()
    val playDing by viewModel.playDing.collectAsState()
    val mode by viewModel.stimulationMode.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val slowerFlicker by viewModel.slowerFlicker.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(Translations.getString(currentLang, "options"), fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        item {
            Text("Language / Idioma", fontWeight = FontWeight.Bold)
            val langs = listOf("English" to "EN", "Español" to "ES", "日本語" to "JA", "Français" to "FR", "中文" to "ZH", "Deutsch" to "DE", "Italiano" to "IT", "Tiếng Việt" to "VI")
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    langs.take(4).forEach { (lang, code) ->
                        Button(onClick = { viewModel.setLanguage(lang) }, colors = ButtonDefaults.buttonColors(containerColor = if (currentLang == lang) Color(0xFF4CAF50) else Color.DarkGray), modifier = Modifier.weight(1f).padding(horizontal = 4.dp), contentPadding = PaddingValues(0.dp)) {
                            Text(code, color = Color.White)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    langs.drop(4).forEach { (lang, code) ->
                        Button(onClick = { viewModel.setLanguage(lang) }, colors = ButtonDefaults.buttonColors(containerColor = if (currentLang == lang) Color(0xFF4CAF50) else Color.DarkGray), modifier = Modifier.weight(1f).padding(horizontal = 4.dp), contentPadding = PaddingValues(0.dp)) {
                            Text(code, color = Color.White)
                        }
                    }
                }
            }
        }

        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = keepScreenOn, onCheckedChange = { viewModel.setKeepScreenOn(it) }); Text(Translations.getString(currentLang, "screen_on")) } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = slowerFlicker, onCheckedChange = { viewModel.setSlowerFlicker(it) }); Text(Translations.getString(currentLang, "slower_flicker")) } }

        item {
            Text(Translations.getString(currentLang, "theme"), fontWeight = FontWeight.Bold)
            // Theme selector simplified to just Black/White
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("Black", "White").forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == t, onClick = { viewModel.setTheme(t) })
                        Text(Translations.getString(currentLang, t.lowercase()), fontSize = 16.sp)
                    }
                }
            }
        }

        item {
            Text("${Translations.getString(currentLang, "timer_duration")}: $timerMinutes", fontWeight = FontWeight.Bold)
            Slider(value = timerMinutes.toFloat(), onValueChange = { viewModel.setTimer(it.toInt()) }, valueRange = 1f..55f, steps = 54)
        }

        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = playDing, onCheckedChange = { viewModel.setPlayDing(it) }); Text(Translations.getString(currentLang, "play_ding")) } }

        item {
            Text(Translations.getString(currentLang, "mode"), fontWeight = FontWeight.Bold)
            Column { listOf("Both", "Audio Only", "Flashlight Only").forEach { m -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = mode == m, onClick = { viewModel.setStimulationMode(m) }); Text(m) } } }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { navController.popBackStack() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.fillMaxWidth()) {
                Text(Translations.getString(currentLang, "back"), color = Color.White)
            }
        }
    }
}

@Composable
fun ResearchLink(text: String, url: String, textColor: Color) {
    val splitIndex = text.indexOf(":") + 1
    if (splitIndex > 0) {
        val linkText = text.substring(0, splitIndex)
        val bodyText = text.substring(splitIndex)

        val annotatedString = buildAnnotatedString {
            val link = LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(style = SpanStyle(color = Color(0xFF64B5F6), textDecoration = TextDecoration.Underline, fontSize = 14.sp))
            )
            withLink(link) { append(linkText) }
            withStyle(style = SpanStyle(color = textColor, fontSize = 14.sp)) { append(bodyText) }
        }
        Text(text = annotatedString)
    } else {
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
fun InfoScreen(viewModel: MainViewModel, navController: androidx.navigation.NavController, textColor: Color) {
    val scrollState = rememberScrollState()
    val currentLang by viewModel.currentLanguage.collectAsState()

    val urls = listOf(
        "https://www.pnas.org/doi/10.1073/pnas.2529565123", "https://academic.oup.com/sleep/article/48/3/zsae299/7928860", "https://www.biorxiv.org/content/10.1101/2025.03.14.643227v1.full-text",
        "https://www.pnas.org/doi/10.1073/pnas.2419364122", "https://picower.mit.edu/news/study-reveals-ways-which-40hz-sensory-stimulation-may-preserve-brains-white-matter",
        "https://www.eurekalert.org/news-releases/1035324", "https://www.withpower.com/trial/phase-parkinson-disease-1-2022-eb496"
    )

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        // --- Top Bar with Title and Tiny Back Button ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Translations.getString(currentLang, "info_title"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = textColor, modifier = Modifier.weight(1f))
            Button(
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(Translations.getString(currentLang, "back"), color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Instructions Section
        Text(Translations.getString(currentLang, "info_inst_title"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(Translations.getString(currentLang, "info_about"), fontSize = 14.sp, color = textColor)
        Spacer(modifier = Modifier.height(16.dp))
        Text(Translations.getString(currentLang, "info_use_title"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(Translations.getString(currentLang, "info_setup"), fontSize = 14.sp, color = textColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(Translations.getString(currentLang, "info_duration"), fontSize = 14.sp, color = textColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(Translations.getString(currentLang, "info_opt"), fontSize = 14.sp, color = textColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(Translations.getString(currentLang, "info_disc"), fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // How it works
        Text(Translations.getString(currentLang, "info_mech_title"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(Translations.getString(currentLang, "info_mech_body"), fontSize = 14.sp, color = textColor)
        Spacer(modifier = Modifier.height(24.dp))

        // Other Diseases
        Text(Translations.getString(currentLang, "info_dis_title"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(Translations.getString(currentLang, "info_dis_body"), fontSize = 14.sp, color = textColor)
        Spacer(modifier = Modifier.height(24.dp))

        // Clickable Research
        Text(Translations.getString(currentLang, "info_res_title"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(8.dp))
        for (i in 1..7) {
            ResearchLink(text = "• " + Translations.getString(currentLang, "res_$i"), url = urls[i-1], textColor = textColor)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Email + Clickable GitHub Link
        Text("FAQ: Dr. Manuel Herrador (mherrador@ujaen.es)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
        val githubPrefix = Translations.getString(currentLang, "github_link")
        val githubUrl = "https://github.com/PacifAIst/FRUGAILS"
        val annotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)) { append(githubPrefix) }
            val link = LinkAnnotation.Url(
                url = githubUrl,
                styles = TextLinkStyles(style = SpanStyle(color = Color(0xFF64B5F6), textDecoration = TextDecoration.Underline, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            )
            withLink(link) { append(githubUrl) }
        }
        Text(text = annotatedString)

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController.popBackStack() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.fillMaxWidth()) {
            Text(Translations.getString(currentLang, "back"), color = Color.White, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}