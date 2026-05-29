package com.example

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

// Navigation destinations
enum class MTDestination {
    FILE_EXPLORER,
    TEXT_EDITOR,
    TERMINAL_SIMULATOR,
    EXTRACT_APK,
    COLOR_PICKER,
    REMOTE_MANAGEMENT,
    PLUGIN_MANAGER,
    SMALI_INSTRUCTIONS
}

// Model representing which Pane is selected in Explorer
enum class PaneSelect {
    LEFT,
    RIGHT
}

enum class WorkspaceMode {
    SANDBOX,
    EXTERNAL,
    VIRTUAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MTEditorApp(
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    val context = LocalContext.current
    val sandboxPath = remember { context.filesDir.absolutePath + "/MTProjects" }
    
    val vfs = remember {
        val v = VirtualFileSystem()
        v.customRootPath = sandboxPath
        v
    }
    
    var workspaceMode by remember { mutableStateOf(WorkspaceMode.SANDBOX) }
    
    // Theme & Info configurations
    var selectedTheme by remember { mutableStateOf(AppTheme.CLASSIC_DARK) }
    var appLanguage by remember { mutableStateOf(AppLanguage.ARABIC) }
    
    // VFS navigation state
    val leftPath = remember { mutableStateListOf<String>() }
    val rightPath = remember { mutableStateListOf("AndroidCSProjects") }
    var selectedPane by remember { mutableStateOf(PaneSelect.LEFT) }
    var vfsVersion by remember { mutableStateOf(0) }
    
    // File highlights for context operations
    var leftSelectedFile by remember { mutableStateOf<VirtualFile?>(null) }
    var rightSelectedFile by remember { mutableStateOf<VirtualFile?>(null) }

    // Async directory setup in IO background thread
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                ensureRealDirectorySetup(context, sandboxPath)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            try {
                ensureRealDirectorySetup(context, "/storage/emulated/0")
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    // Adapt paths when workspace mode changes dynamically
    LaunchedEffect(workspaceMode) {
        when (workspaceMode) {
            WorkspaceMode.SANDBOX -> {
                vfs.useRealFS = true
                vfs.customRootPath = sandboxPath
                leftPath.clear()
                rightPath.clear()
                rightPath.add("AndroidCSProjects")
            }
            WorkspaceMode.EXTERNAL -> {
                vfs.useRealFS = true
                vfs.customRootPath = null
                leftPath.clear()
                leftPath.addAll(listOf("storage", "emulated", "0"))
                rightPath.clear()
                rightPath.addAll(listOf("storage", "emulated", "0", "AndroidCSProjects"))
            }
            WorkspaceMode.VIRTUAL -> {
                vfs.useRealFS = false
                vfs.customRootPath = null
                leftPath.clear()
                leftPath.addAll(listOf("storage", "emulated", "0"))
                rightPath.clear()
                rightPath.addAll(listOf("storage", "emulated", "0", "AndroidCSProjects"))
            }
        }
        leftSelectedFile = null
        rightSelectedFile = null
        vfsVersion++
    }
    
    // Active Screen Destination
    var currentDestination by remember { mutableStateOf(MTDestination.FILE_EXPLORER) }
    
    // State of Text Editor
    var editingFile by remember { mutableStateOf<VirtualFile?>(null) }
    var editingFileParentPath by remember { mutableStateOf<List<String>>(emptyList()) }
    var editorTextBuffer by remember { mutableStateOf("") }
    
    // Drawer Control
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Setup Gemini API integration proxy matching DIRECT REST strategy
    val askGemini: suspend (String) -> String = { prompt ->
        GeminiWorker.generateContent(prompt)
    }

    // Terminal Emulator state
    val terminalState = remember { TerminalState(vfs, coroutineScope, askGemini) }

    // Synchronize App Settings changes to ThemeState
    ThemeState.currentTheme = selectedTheme
    ThemeState.currentLanguage = appLanguage

    // Back button routing: intercept back press when inside secondary utilities and route back.
    BackHandler(enabled = currentDestination != MTDestination.FILE_EXPLORER) {
        currentDestination = MTDestination.FILE_EXPLORER
    }

    // Custom Color Palette mimicking MT Manager aesthetics
    val backgroundBrush = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Brush.verticalGradient(listOf(Color(0xFF141A24), Color(0xFF10141C)))
        AppTheme.MIDNIGHT_BLACK -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF0A0A0A)))
        AppTheme.DEEP_BLUE -> Brush.verticalGradient(listOf(Color(0xFF070F1F), Color(0xFF10254C)))
    }
    
    val surfaceColor = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Color(0xFF1E2633)
        AppTheme.MIDNIGHT_BLACK -> Color(0xFF0F0F0F)
        AppTheme.DEEP_BLUE -> Color(0xFF172A45)
    }
    
    val headerColor = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Color(0xFF263238)
        AppTheme.MIDNIGHT_BLACK -> Color(0xFF141414)
        AppTheme.DEEP_BLUE -> Color(0xFF0B192E)
    }
    
    val dividerColor = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Color(0xFF2E3B4E)
        AppTheme.MIDNIGHT_BLACK -> Color(0xFF222222)
        AppTheme.DEEP_BLUE -> Color(0xFF1E3A5F)
    }
    
    val textColor = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Color(0xFFECEFF1)
        AppTheme.MIDNIGHT_BLACK -> Color(0xFFECEFF1)
        AppTheme.DEEP_BLUE -> Color(0xFFE6F1FF)
    }
    
    val secondaryText = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Color(0xFF90A4AE)
        AppTheme.MIDNIGHT_BLACK -> Color(0xFF888888)
        AppTheme.DEEP_BLUE -> Color(0xFF8892B0)
    }
    
    val accentColor = when (selectedTheme) {
        AppTheme.CLASSIC_DARK -> Color(0xFFFFA726) // Amber/Orange
        AppTheme.MIDNIGHT_BLACK -> Color(0xFF00FF66) // Electric Neon Lime
        AppTheme.DEEP_BLUE -> Color(0xFF64FFDA) // Cyber Aquamarine
    }
    
    val blueAccent = Color(0xFF29B6F6)
    val greenAccent = Color(0xFF66BB6A)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = surfaceColor,
                modifier = Modifier.width(310.dp)
            ) {
                DrawerHeaderView(
                    selectedTheme = selectedTheme,
                    onThemeChange = { selectedTheme = it },
                    language = appLanguage,
                    onLanguageToggle = {
                        appLanguage = if (appLanguage == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
                        Toast.makeText(context, if (appLanguage == AppLanguage.ARABIC) "تم تغيير لغة الواجهة للعربية" else "UI Switched to English", Toast.LENGTH_SHORT).show()
                    },
                    headerColor = headerColor
                )
                
                Divider(color = dividerColor)
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    item {
                        DrawerCategoryHeader(title = if (appLanguage == AppLanguage.ARABIC) "منطقة العمل النشطة" else "Active Workspace", textColor = textColor)
                    }
                    item {
                        DrawerStorageItem(
                            title = if (appLanguage == AppLanguage.ARABIC) "مساحة المشاريع الآمنة" else "Secure Sandbox Workspace",
                            subtitle = if (appLanguage == AppLanguage.ARABIC) "مساحة سريعة ومعزولة لملفات التعديل" else "High-speed isolated editing zone",
                            percentage = 0.15f,
                            icon = Icons.Default.Shield,
                            iconTint = Color(0xFF64FFDA),
                            textColor = textColor,
                            subColor = secondaryText,
                            isActive = workspaceMode == WorkspaceMode.SANDBOX,
                            onClick = {
                                workspaceMode = WorkspaceMode.SANDBOX
                                currentDestination = MTDestination.FILE_EXPLORER
                                coroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                    item {
                        DrawerStorageItem(
                            title = if (appLanguage == AppLanguage.ARABIC) "مساحة الهاتف العامة" else "Device Public Storage",
                            subtitle = "/storage/emulated/0",
                            percentage = 0.82f,
                            icon = Icons.Default.FolderSpecial,
                            iconTint = Color(0xFFFFB300),
                            textColor = textColor,
                            subColor = secondaryText,
                            isActive = workspaceMode == WorkspaceMode.EXTERNAL,
                            onClick = {
                                workspaceMode = WorkspaceMode.EXTERNAL
                                currentDestination = MTDestination.FILE_EXPLORER
                                coroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                    item {
                        DrawerStorageItem(
                            title = if (appLanguage == AppLanguage.ARABIC) "النظام الوهمي الافتراضي" else "Virtual In-Memory FS",
                            subtitle = if (appLanguage == AppLanguage.ARABIC) "محاكاة ذكية للتعديل السريع" else "Fast in-memory file simulation",
                            percentage = 0.05f,
                            icon = Icons.Default.CloudQueue,
                            iconTint = Color(0xFF29B6F6),
                            textColor = textColor,
                            subColor = secondaryText,
                            isActive = workspaceMode == WorkspaceMode.VIRTUAL,
                            onClick = {
                                workspaceMode = WorkspaceMode.VIRTUAL
                                currentDestination = MTDestination.FILE_EXPLORER
                                coroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        DrawerCategoryHeader(title = ThemeState.getTranslation("tools_section"), textColor = textColor)
                    }
                    
                    val tools = listOf(
                        Triple(MTDestination.PLUGIN_MANAGER, ThemeState.getTranslation("plugin_manager"), Icons.Default.Extension),
                        Triple(MTDestination.REMOTE_MANAGEMENT, ThemeState.getTranslation("remote_manage"), Icons.Default.SettingsEthernet),
                        Triple(MTDestination.COLOR_PICKER, ThemeState.getTranslation("color_picker"), Icons.Default.Palette),
                        Triple(MTDestination.EXTRACT_APK, ThemeState.getTranslation("extract_apk"), Icons.Default.SettingsSystemDaydream),
                        Triple(MTDestination.TEXT_EDITOR, ThemeState.getTranslation("text_editor"), Icons.Default.Description),
                        Triple(MTDestination.TERMINAL_SIMULATOR, ThemeState.getTranslation("terminal_sim"), Icons.Default.Terminal),
                        Triple(MTDestination.SMALI_INSTRUCTIONS, ThemeState.getTranslation("smali_inst"), Icons.Default.MenuBook)
                    )
                    
                    items(tools) { item ->
                        DrawerToolLink(
                            title = item.second,
                            icon = item.third,
                            isActive = currentDestination == item.first,
                            textColor = textColor,
                            onClick = {
                                if (item.first == MTDestination.TEXT_EDITOR && editingFile == null) {
                                    // Preload MyCoolApp's MainActivity.kt so editor isn't blank
                                    val demoPath = if (workspaceMode == WorkspaceMode.SANDBOX) {
                                        listOf("AndroidCSProjects", "MyCoolApp", "app", "src", "main", "java", "com", "example", "mycoolapp")
                                    } else {
                                        listOf("storage", "emulated", "0", "AndroidCSProjects", "MyCoolApp", "app", "src", "main", "java", "com", "example", "mycoolapp")
                                    }
                                    val resolved = vfs.resolvePath(demoPath)
                                    val actFile = resolved.children.find { it.name == "MainActivity.kt" }
                                    if (actFile != null) {
                                        editingFile = actFile
                                        editingFileParentPath = demoPath
                                        editorTextBuffer = actFile.content
                                    }
                                }
                                currentDestination = item.first
                                coroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                MTMainAppBar(
                    title = when (currentDestination) {
                        MTDestination.FILE_EXPLORER -> {
                            val activePath = if (selectedPane == PaneSelect.LEFT) leftPath else rightPath
                            "/" + activePath.joinToString("/")
                        }
                        MTDestination.TEXT_EDITOR -> editingFile?.name ?: ThemeState.getTranslation("text_editor")
                        MTDestination.TERMINAL_SIMULATOR -> ThemeState.getTranslation("terminal_sim")
                        MTDestination.EXTRACT_APK -> ThemeState.getTranslation("extract_apk")
                        MTDestination.COLOR_PICKER -> ThemeState.getTranslation("color_picker")
                        MTDestination.REMOTE_MANAGEMENT -> ThemeState.getTranslation("remote_manage")
                        MTDestination.PLUGIN_MANAGER -> ThemeState.getTranslation("plugin_manager")
                        MTDestination.SMALI_INSTRUCTIONS -> ThemeState.getTranslation("smali_inst")
                    },
                    subTitle = when (currentDestination) {
                        MTDestination.FILE_EXPLORER -> {
                            val activePath = if (selectedPane == PaneSelect.LEFT) leftPath else rightPath
                            val resolved = vfs.resolvePath(activePath)
                            val foldersCount = resolved.children.count { it.isDirectory }
                            val filesCount = resolved.children.count { !it.isDirectory }
                            "${ThemeState.getTranslation("folders")}: $foldersCount  ${ThemeState.getTranslation("files")}: $filesCount ${ThemeState.getTranslation("disk")}: ${getDiskInfo("/" + activePath.joinToString("/"))}"
                        }
                        MTDestination.TEXT_EDITOR -> "MT Unpack Code Editor v2.1-Pro"
                        else -> "MT Unpack_Dex Utilities Suite"
                    },
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    headerColor = headerColor,
                    allowActionOverlay = currentDestination == MTDestination.FILE_EXPLORER,
                    onSearchAction = {
                        Toast.makeText(context, "Search filter triggered", Toast.LENGTH_SHORT).show()
                    },
                    onToggleDirection = {
                        // Swap Left path and Right path! Extremely useful dual pane feature
                        val leftTemp = leftPath.toList()
                        leftPath.clear()
                        leftPath.addAll(rightPath)
                        rightPath.clear()
                        rightPath.addAll(leftTemp)
                        Toast.makeText(context, "Swapped Panes!", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(backgroundBrush)
            ) {
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "MTNavigatorTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        MTDestination.FILE_EXPLORER -> {
                            FileExplorerScreen(
                                vfs = vfs,
                                leftPath = leftPath,
                                rightPath = rightPath,
                                selectedPane = selectedPane,
                                onPaneClick = { selectedPane = it },
                                leftSelectedFile = leftSelectedFile,
                                rightSelectedFile = rightSelectedFile,
                                onSelectLeftFile = { leftSelectedFile = it },
                                onSelectRightFile = { rightSelectedFile = it },
                                onOpenFile = { file, parentPath ->
                                    editingFile = file
                                    editingFileParentPath = parentPath
                                    editorTextBuffer = vfs.getFileContent(parentPath, file.name)
                                    currentDestination = MTDestination.TEXT_EDITOR
                                },
                                coroutineScope = coroutineScope,
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryText = secondaryText,
                                dividerColor = dividerColor,
                                accentColor = accentColor,
                                vfsVersion = vfsVersion,
                                onVfsUpdate = { vfsVersion++ }
                            )
                        }
                        MTDestination.TEXT_EDITOR -> {
                            TextCodeEditorScreen(
                                fileName = editingFile?.name ?: "untitled.txt",
                                initialText = editorTextBuffer,
                                onSave = { newContent ->
                                    editorTextBuffer = newContent
                                    val fName = editingFile?.name
                                    if (fName != null) {
                                        vfs.updateFileContent(editingFileParentPath, fName, newContent)
                                        vfsVersion++
                                        Toast.makeText(context, "${ThemeState.getTranslation("success")} - Saved virtual file!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onBack = { currentDestination = MTDestination.FILE_EXPLORER },
                                askGemini = askGemini,
                                coroutineScope = coroutineScope,
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryText = secondaryText,
                                accent = accentColor,
                                headerColor = headerColor
                            )
                        }
                        MTDestination.TERMINAL_SIMULATOR -> {
                            TerminalSimulatorView(
                                state = terminalState,
                                surfaceColor = surfaceColor,
                                dividerColor = dividerColor,
                                textColor = textColor,
                                accentColor = accentColor
                            )
                        }
                        MTDestination.EXTRACT_APK -> {
                            ExtractApkView(
                                vfs = vfs,
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryText = secondaryText,
                                accentColor = accentColor,
                                dividerColor = dividerColor,
                                onBackToExplorer = { currentDestination = MTDestination.FILE_EXPLORER }
                            )
                        }
                        MTDestination.COLOR_PICKER -> {
                            ScreenColorPickerView(
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                accentColor = accentColor
                            )
                        }
                        MTDestination.REMOTE_MANAGEMENT -> {
                            RemoteManagementView(
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                accentColor = accentColor
                            )
                        }
                        MTDestination.PLUGIN_MANAGER -> {
                            PluginManagerView(
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryText = secondaryText,
                                accentColor = accentColor,
                                dividerColor = dividerColor
                            )
                        }
                        MTDestination.SMALI_INSTRUCTIONS -> {
                            SmaliInstructionsView(
                                askGemini = askGemini,
                                coroutineScope = coroutineScope,
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryText = secondaryText,
                                accentColor = accentColor,
                                dividerColor = dividerColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// Drawer components 
@Composable
fun DrawerHeaderView(
    selectedTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    language: AppLanguage,
    onLanguageToggle: () -> Unit,
    headerColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor)
            .padding(16.dp)
            .padding(top = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "MT Logo",
                    tint = when (selectedTheme) {
                        AppTheme.CLASSIC_DARK -> Color(0xFFFFB300)
                        AppTheme.MIDNIGHT_BLACK -> Color(0xFF00FF66)
                        AppTheme.DEEP_BLUE -> Color(0xFF64FFDA)
                    },
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "MT Editor Pro",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Unpack_Dex • v2.14_PRO",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }
            }
            
            // Language & Theme controllers row
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onLanguageToggle, modifier = Modifier.size(38.dp)) {
                    Text(
                        text = if (language == AppLanguage.ARABIC) "EN" else "عربي",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF455A64), CircleShape)
                            .padding(6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Professional circular theme toggles
        Text(
            text = if (language == AppLanguage.ARABIC) "تغيير المظهر والرموز" else "Choose Visual Theme",
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val themes = listOf(
                Triple(AppTheme.CLASSIC_DARK, Color(0xFF1E2633), Color(0xFFFFA726)),
                Triple(AppTheme.MIDNIGHT_BLACK, Color(0xFF0F0F0F), Color(0xFF00FF66)),
                Triple(AppTheme.DEEP_BLUE, Color(0xFF172A45), Color(0xFF64FFDA))
            )
            themes.forEach { item ->
                val isSelected = selectedTheme == item.first
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(item.second)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else item.third.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable { onThemeChange(item.first) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(item.third)
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerCategoryHeader(title: String, textColor: Color) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = textColor.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun DrawerStorageItem(
    title: String,
    subtitle: String,
    percentage: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    textColor: Color,
    subColor: Color,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) iconTint.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (isActive) iconTint.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(textColor.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = iconTint)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Spacer(modifier = Modifier.height(2.dp))
            // Render beautiful tiny storage progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(4.dp)
                    .background(textColor.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage)
                        .height(4.dp)
                        .background(iconTint, RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(0.9f)) {
                Text(text = subtitle, fontSize = 11.sp, color = subColor)
                Text(text = "${(percentage * 100).toInt()}%", fontSize = 11.sp, color = iconTint, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DrawerToolLink(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    textColor: Color,
    onClick: () -> Unit
) {
    val bg = if (isActive) textColor.copy(alpha = 0.09f) else Color.Transparent
    val tint = if (isActive) Color(0xFF29B6F6) else textColor.copy(alpha = 0.8f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) Color(0xFF29B6F6) else textColor
        )
    }
}

// App bar that mimics MT Manager with dual search/action utilities
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MTMainAppBar(
    title: String,
    subTitle: String,
    onMenuClick: () -> Unit,
    headerColor: Color,
    allowActionOverlay: Boolean,
    onSearchAction: () -> Unit,
    onToggleDirection: () -> Unit
) {
    TopAppBar(
        title = {
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subTitle,
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Drawer", tint = Color.White)
            }
        },
        actions = {
            if (allowActionOverlay) {
                IconButton(onClick = onToggleDirection) {
                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Swap columns", tint = Color.White)
                }
                IconButton(onClick = onSearchAction) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search directory", tint = Color.White)
                }
            }
            IconButton(onClick = onMenuClick) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Settings info", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = headerColor)
    )
}

// File Explorer Interface (Side-By-Side Symmetric Dual Pane!)
@Composable
fun FileExplorerScreen(
    vfs: VirtualFileSystem,
    leftPath: MutableList<String>,
    rightPath: MutableList<String>,
    selectedPane: PaneSelect,
    onPaneClick: (PaneSelect) -> Unit,
    leftSelectedFile: VirtualFile?,
    rightSelectedFile: VirtualFile?,
    onSelectLeftFile: (VirtualFile?) -> Unit,
    onSelectRightFile: (VirtualFile?) -> Unit,
    onOpenFile: (VirtualFile, List<String>) -> Unit,
    coroutineScope: CoroutineScope,
    surfaceColor: Color,
    textColor: Color,
    secondaryText: Color,
    dividerColor: Color,
    accentColor: Color,
    vfsVersion: Int,
    onVfsUpdate: () -> Unit
) {
    val context = LocalContext.current
    val greenAccent = Color(0xFF66BB6A)
    var operationDialogType by remember { mutableStateOf<String?>(null) } // "copy", "move", "delete", "create_file", "create_folder", "rename"
    var inputNameBuffer by remember { mutableStateOf("") }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val activePath = if (selectedPane == PaneSelect.LEFT) leftPath else rightPath
    val inactivePath = if (selectedPane == PaneSelect.LEFT) rightPath else leftPath
    val activeSelection = if (selectedPane == PaneSelect.LEFT) leftSelectedFile else rightSelectedFile
    val setActiveSelection = if (selectedPane == PaneSelect.LEFT) onSelectLeftFile else onSelectRightFile

    Column(modifier = Modifier.fillMaxSize()) {
        // App-Level Search Filtering Row
        if (isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "تصفية الملفات الحالية..." else "Filter files...",
                            fontSize = 13.sp,
                            color = secondaryText
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = dividerColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = accentColor)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = null, tint = textColor)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = {
                    searchQuery = ""
                    isSearchActive = false
                }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close search", tint = Color.Red)
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Left Pane (Column 50%)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        width = 1.dp,
                        color = if (selectedPane == PaneSelect.LEFT) accentColor.copy(alpha = 0.5f) else Color.Transparent
                    )
            ) {
                PaneFolderList(
                    paneName = "LEFT",
                    vfs = vfs,
                    pathSegments = leftPath,
                    selectedFile = leftSelectedFile,
                    onSelectFile = onSelectLeftFile,
                    onNavigate = { file ->
                        onSelectLeftFile(null)
                        if (file.name == "..") {
                            if (leftPath.isNotEmpty()) leftPath.removeAt(leftPath.size - 1)
                        } else {
                            leftPath.add(file.name)
                        }
                        onVfsUpdate()
                    },
                    onOpenFile = { onOpenFile(it, leftPath.toList()) },
                    isActive = selectedPane == PaneSelect.LEFT,
                    onPaneFocus = { onPaneClick(PaneSelect.LEFT) },
                    searchQuery = if (selectedPane == PaneSelect.LEFT) searchQuery else "",
                    textColor = textColor,
                    subColor = secondaryText,
                    dividerColor = dividerColor,
                    vfsVersion = vfsVersion
                )
            }
            
            // Centered vertical divider spacer
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = dividerColor
            )

            // Right Pane (Column 50%)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        width = 1.dp,
                        color = if (selectedPane == PaneSelect.RIGHT) accentColor.copy(alpha = 0.5f) else Color.Transparent
                    )
            ) {
                PaneFolderList(
                    paneName = "RIGHT",
                    vfs = vfs,
                    pathSegments = rightPath,
                    selectedFile = rightSelectedFile,
                    onSelectFile = onSelectRightFile,
                    onNavigate = { file ->
                        onSelectRightFile(null)
                        if (file.name == "..") {
                            if (rightPath.isNotEmpty()) rightPath.removeAt(rightPath.size - 1)
                        } else {
                            rightPath.add(file.name)
                        }
                        onVfsUpdate()
                    },
                    onOpenFile = { onOpenFile(it, rightPath.toList()) },
                    isActive = selectedPane == PaneSelect.RIGHT,
                    onPaneFocus = { onPaneClick(PaneSelect.RIGHT) },
                    searchQuery = if (selectedPane == PaneSelect.RIGHT) searchQuery else "",
                    textColor = textColor,
                    subColor = secondaryText,
                    dividerColor = dividerColor,
                    vfsVersion = vfsVersion
                )
            }
        }

        // Bottom Operations & Navigation Control bar 
        BottomExplorerBar(
            onGoUp = {
                if (activePath.isNotEmpty()) {
                    activePath.removeAt(activePath.size - 1)
                    setActiveSelection(null)
                }
            },
            onRefresh = {
                setActiveSelection(null)
                onVfsUpdate()
                Toast.makeText(context, if (ThemeState.currentLanguage == AppLanguage.ARABIC) "تم تحديث القائمة الحاليّة" else "Folder View Refreshed", Toast.LENGTH_SHORT).show()
            },
            onSearchToggle = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            },
            onAdd = {
                inputNameBuffer = ""
                operationDialogType = "create_prompt"
            },
            onCopy = {
                if (activeSelection == null) {
                    Toast.makeText(context, if (ThemeState.currentLanguage == AppLanguage.ARABIC) "الرجاء اختيار ملف أو مجلد" else "Please select a file first", Toast.LENGTH_SHORT).show()
                } else {
                    operationDialogType = "copy"
                }
            },
            onMove = {
                if (activeSelection == null) {
                    Toast.makeText(context, if (ThemeState.currentLanguage == AppLanguage.ARABIC) "الرجاء اختيار ملف أو مجلد" else "Please select a file first", Toast.LENGTH_SHORT).show()
                } else {
                    operationDialogType = "move"
                }
            },
            onRename = {
                if (activeSelection == null) {
                    Toast.makeText(context, if (ThemeState.currentLanguage == AppLanguage.ARABIC) "الرجاء اختيار ملف أو مجلد" else "Please select a file first", Toast.LENGTH_SHORT).show()
                } else {
                    inputNameBuffer = activeSelection.name
                    operationDialogType = "rename"
                }
            },
            onDelete = {
                if (activeSelection == null) {
                    Toast.makeText(context, if (ThemeState.currentLanguage == AppLanguage.ARABIC) "الرجاء اختيار ملف أو مجلد" else "Please select a file first", Toast.LENGTH_SHORT).show()
                } else {
                    operationDialogType = "delete"
                }
            },
            accentColor = accentColor,
            surfaceColor = surfaceColor,
            dividerColor = dividerColor,
            textColor = textColor
        )
    }

    // Modal Operations Dialog
    if (operationDialogType != null) {
        AlertDialog(
            onDismissRequest = { operationDialogType = null },
            title = {
                Text(
                    text = when (operationDialogType) {
                        "create_prompt" -> "إنشاء عنصر جديد (New Item)"
                        "actions" -> "عمليات على: ${activeSelection?.name}"
                        "copy" -> ThemeState.getTranslation("copy")
                        "move" -> ThemeState.getTranslation("move")
                        "delete" -> ThemeState.getTranslation("delete")
                        "rename" -> ThemeState.getTranslation("rename")
                        else -> "تأكيد العملية"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textColor
                )
            },
            text = {
                Column {
                    when (operationDialogType) {
                        "create_prompt" -> {
                            Text(text = "اختر نوع العنصر المراد إنشاؤه في المسار الحالي:", color = textColor, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        operationDialogType = "create_file"
                                        inputNameBuffer = "new_code_patch.smali"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ملف نصي", color = Color.White, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        operationDialogType = "create_folder"
                                        inputNameBuffer = "new_folder"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = greenAccent)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مجلد جديد", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        "create_file" -> {
                            Text(text = "اسم الملف البرمجي الجديد:", color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputNameBuffer,
                                onValueChange = { inputNameBuffer = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "create_folder" -> {
                            Text(text = "اسم المجلد الجديد:", color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputNameBuffer,
                                onValueChange = { inputNameBuffer = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "actions" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val ops = listOf(
                                    Triple("copy", ThemeState.getTranslation("copy"), Icons.Default.CopyAll),
                                    Triple("move", ThemeState.getTranslation("move"), Icons.Default.MoveUp),
                                    Triple("rename", ThemeState.getTranslation("rename"), Icons.Default.Edit),
                                    Triple("delete", ThemeState.getTranslation("delete"), Icons.Default.DeleteForever)
                                )
                                ops.forEach { op ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (op.first == "rename") {
                                                    inputNameBuffer = activeSelection?.name ?: ""
                                                }
                                                operationDialogType = op.first
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = op.third, contentDescription = null, tint = accentColor)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = op.second, fontSize = 14.sp, color = textColor)
                                    }
                                }
                            }
                        }
                        "copy" -> {
                            Text(
                                text = "هل تريد نسخ [${activeSelection?.name}] من المسار الحالي إلى النافذة الأخرى؟\n\nتنبيه: سيتم نقل نسخة كاملة من البيانات.",
                                color = textColor
                            )
                        }
                        "move" -> {
                            Text(
                                 text = "هل تريد نقل [${activeSelection?.name}] إلى النافذة الأخرى؟\n\nملاحظة: سيتم نقل الملف الأصلي وحذفه من هنا.",
                                color = textColor
                            )
                        }
                        "rename" -> {
                            Text(text = "تغيير اسم العنصر الحالي:", color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputNameBuffer,
                                onValueChange = { inputNameBuffer = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "delete" -> {
                            Text(
                                text = "تحذير: هل أنت متأكد من رغبتك في حذف [${activeSelection?.name}] بشكل نهائي؟ لا يمكن استعادة هذا الملف لاحقاً.",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (operationDialogType != "create_prompt" && operationDialogType != "actions") {
                    TextButton(
                        onClick = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val activeVal = activeSelection
                                var successToast: String? = null
                                when (operationDialogType) {
                                    "create_file" -> {
                                        if (inputNameBuffer.trim().isNotEmpty()) {
                                            vfs.addFile(activePath, inputNameBuffer.trim(), "# New empty source file decompiled using MT Editor")
                                            successToast = "تم إنشاء الملف بنجاح"
                                        }
                                    }
                                    "create_folder" -> {
                                        if (inputNameBuffer.trim().isNotEmpty()) {
                                            vfs.addFile(activePath, inputNameBuffer.trim(), "", isFolder = true)
                                            successToast = "تم إنشاء المجلد بنجاح"
                                        }
                                    }
                                    "copy" -> {
                                        if (activeVal != null) {
                                            val res = vfs.copyFile(activePath, activeVal.name, inactivePath)
                                            successToast = if (res) "تم النسخ بنجاح" else "خطأ بالنسخ"
                                        }
                                    }
                                    "move" -> {
                                        if (activeVal != null) {
                                            val res = vfs.moveFile(activePath, activeVal.name, inactivePath)
                                            if (res) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    setActiveSelection(null)
                                                }
                                                successToast = "تم النقل بنجاح"
                                            } else {
                                                successToast = "خطأ بالنقل"
                                            }
                                        }
                                    }
                                    "rename" -> {
                                        if (activeVal != null && inputNameBuffer.trim().isNotEmpty()) {
                                            val res = vfs.renameFile(activePath, activeVal.name, inputNameBuffer.trim())
                                            if (res) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    setActiveSelection(null)
                                                }
                                                successToast = "تم تغيير الاسم"
                                            } else {
                                                successToast = "الاسم مستخدم بالفعل"
                                            }
                                        }
                                    }
                                    "delete" -> {
                                        if (activeVal != null) {
                                            vfs.deleteFile(activePath, activeVal.name)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                setActiveSelection(null)
                                            }
                                            successToast = "تم الحذف"
                                        }
                                    }
                                }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (successToast != null) {
                                        Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show()
                                        onVfsUpdate()
                                    }
                                    operationDialogType = null
                                }
                            }
                        }
                    ) {
                        Text("تأكيد", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { operationDialogType = null }) {
                    Text("إلغاء", color = secondaryText)
                }
            },
            containerColor = surfaceColor
        )
    }
}

// File list view for single pane
@Composable
fun PaneFolderList(
    paneName: String,
    vfs: VirtualFileSystem,
    pathSegments: List<String>,
    selectedFile: VirtualFile?,
    onSelectFile: (VirtualFile?) -> Unit,
    onNavigate: (VirtualFile) -> Unit,
    onOpenFile: (VirtualFile) -> Unit,
    isActive: Boolean,
    onPaneFocus: () -> Unit,
    searchQuery: String,
    textColor: Color,
    subColor: Color,
    dividerColor: Color,
    vfsVersion: Int
) {
    val currentDir = vfs.resolvePath(pathSegments)
    
    // Create actual directory item list
    val itemsToShow = remember(currentDir, currentDir.children.size, searchQuery, vfsVersion) {
        val list = mutableListOf<VirtualFile>()
        if (pathSegments.isNotEmpty() && searchQuery.isEmpty()) {
            list.add(VirtualFile("..", isDirectory = true, dateModified = ""))
        }
        val sourceList = if (searchQuery.isNotEmpty()) {
            currentDir.children.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            currentDir.children
        }
        val dirs = sourceList.filter { it.isDirectory }.sortedBy { it.name }
        val files = sourceList.filter { !it.isDirectory }.sortedBy { it.name }
        list.addAll(dirs)
        list.addAll(files)
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onPaneFocus)
            .background(if (isActive) textColor.copy(alpha = 0.02f) else Color.Transparent)
    ) {
        // Quick visual header for current pane path
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(textColor.copy(alpha = 0.05f))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${vfs.resolvePath(pathSegments).name} [${paneName}]",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFF29B6F6) else textColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isActive) Icons.Default.Circle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isActive) Color(0xFF29B6F6) else textColor.copy(alpha = 0.3f),
                modifier = Modifier.size(10.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(itemsToShow) { item ->
                val isSelected = selectedFile?.name == item.name && item.name != ".."
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0xFF29B6F6).copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            onPaneFocus()
                            if (item.name == "..") {
                                onNavigate(item)
                            } else if (item.isDirectory) {
                                onNavigate(item)
                            } else {
                                if (isSelected) {
                                    onOpenFile(item)
                                } else {
                                    onSelectFile(item)
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            item.name == ".." -> Icons.Default.SubdirectoryArrowLeft
                            item.isDirectory -> Icons.Default.Folder
                            item.name.endsWith(".smali") -> Icons.Default.Code
                            item.name.endsWith(".xml") -> Icons.Default.SettingsSystemDaydream
                            item.name.endsWith(".apk") -> Icons.Default.Android
                            else -> Icons.Default.Article
                        },
                        contentDescription = item.name,
                        tint = when {
                            item.name == ".." -> textColor.copy(alpha = 0.5f)
                            item.isDirectory -> Color(0xFFFFB300)
                            item.name.endsWith(".smali") -> Color(0xFFE57373)
                            item.name.endsWith(".apk") -> Color(0xFF81C784)
                            else -> Color(0xFF64B5F6)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontSize = 13.sp,
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.name != "..") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = item.dateModified, fontSize = 10.sp, color = subColor)
                                Text(text = item.sizeString, fontSize = 10.sp, color = subColor)
                            }
                        }
                    }
                }
                Divider(color = dividerColor.copy(alpha = 0.5f))
            }
        }
    }
}

// Bottom Explorer action button item helper
@Composable
fun BottomExplorerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = textColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Horizontally scrollable 8-button bottom action tray
@Composable
fun BottomExplorerBar(
    onGoUp: () -> Unit,
    onRefresh: () -> Unit,
    onSearchToggle: () -> Unit,
    onAdd: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    accentColor: Color,
    surfaceColor: Color,
    dividerColor: Color,
    textColor: Color
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor,
        tonalElevation = 6.dp
    ) {
        Column {
            Divider(color = dividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Go Up
                BottomExplorerAction(
                    icon = Icons.Default.ArrowUpward,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "أعلى" else "Go Up",
                    tint = accentColor,
                    onClick = onGoUp,
                    textColor = textColor
                )
                // 2. Refresh / Swap Pane
                BottomExplorerAction(
                    icon = Icons.Default.Autorenew,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "تحديث" else "Refresh",
                    tint = accentColor,
                    onClick = onRefresh,
                    textColor = textColor
                )
                // 3. Search Filter
                BottomExplorerAction(
                    icon = Icons.Default.Search,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "بحث" else "Search",
                    tint = accentColor,
                    onClick = onSearchToggle,
                    textColor = textColor
                )
                // 4. Create New File/Folder
                BottomExplorerAction(
                    icon = Icons.Default.CreateNewFolder,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "جديد" else "New",
                    tint = accentColor,
                    onClick = onAdd,
                    textColor = textColor
                )
                // 5. Fast Copy to opposite pane
                BottomExplorerAction(
                    icon = Icons.Default.ContentCopy,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "نسخ" else "Copy",
                    tint = accentColor,
                    onClick = onCopy,
                    textColor = textColor
                )
                // 6. Fast Move to opposite pane
                BottomExplorerAction(
                    icon = Icons.Default.MoveUp,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "نقل" else "Move",
                    tint = accentColor,
                    onClick = onMove,
                    textColor = textColor
                )
                // 7. Fast Rename
                BottomExplorerAction(
                    icon = Icons.Default.Edit,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "تسمية" else "Rename",
                    tint = accentColor,
                    onClick = onRename,
                    textColor = textColor
                )
                // 8. Fast Delete
                BottomExplorerAction(
                    icon = Icons.Default.DeleteForever,
                    label = if (ThemeState.currentLanguage == AppLanguage.ARABIC) "حذف" else "Delete",
                    tint = Color.Red,
                    onClick = onDelete,
                    textColor = textColor
                )
            }
        }
    }
}

// Professional Code Editor Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCodeEditorScreen(
    fileName: String,
    initialText: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    askGemini: suspend (String) -> String,
    coroutineScope: CoroutineScope,
    surfaceColor: Color,
    textColor: Color,
    secondaryText: Color,
    accent: Color,
    headerColor: Color
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var searchKeyword by remember { mutableStateOf("") }
    var replaceKeyword by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    
    // Gemini Assistant States
    var aiQuery by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var showAiSheet by remember { mutableStateOf(false) }
    var isAiLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Find & Replace Search Utility
        if (searchActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchKeyword,
                        onValueChange = { searchKeyword = it },
                        label = { Text("بحث عن كلمة (Find)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent)
                    )
                    OutlinedTextField(
                        value = replaceKeyword,
                        onValueChange = { replaceKeyword = it },
                        label = { Text("تبديل بـ (Replace)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent)
                    )
                    IconButton(onClick = { searchActive = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Find", tint = Color.Red)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (searchKeyword.isNotEmpty()) {
                                text = text.replace(searchKeyword, replaceKeyword)
                                Toast.makeText(context, "تم استبدال الكلمات بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("استبدال الكل", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Action Toolbar (AI features, obf, undo, save)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor.copy(alpha = 0.82f))
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // AI assistant floating button
                IconButton(onClick = { showAiSheet = true }) {
                    Icon(imageVector = Icons.Outlined.AutoAwesome, contentDescription = "AI helper", tint = Color(0xFFFFD54F))
                }
                IconButton(onClick = { searchActive = !searchActive }) {
                    Icon(imageVector = Icons.Default.FindReplace, contentDescription = "Find replace", tint = Color.White)
                }
                IconButton(onClick = {
                    // Beautify mock formatting
                    text = text.trim().split("\n").joinToString("\n") { line ->
                        if (line.startsWith(".") || line.startsWith("#")) line else "    " + line.trim()
                    }
                    Toast.makeText(context, "Formatted code successfully!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(imageVector = Icons.Default.FormatAlignLeft, contentDescription = "Beautify", tint = Color.White)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("خروج", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { onSave(text) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(ThemeState.getTranslation("save"), color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Keyboard Helper Accessory Row
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101214))
                .horizontalScroll(scrollState)
                .padding(vertical = 5.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val symbols = listOf("{", "}", "[", "]", "(", ")", ".", ",", ";", "\"", "'", "=", "+", "-", "*", "%", "_", "$", ":", "\\t", "const-string", "return-void", "invoke-virtual")
            symbols.forEach { sym ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF22252B))
                        .clickable {
                            if (sym == "\\t") {
                                text += "    "
                            } else {
                                text += sym
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (sym == "\\t") "TAB" else sym,
                        fontFamily = FontFamily.Monospace,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Editable Code Canvas with Line Numbers
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Line numbering column
            val lines = text.split("\n")
            val lineCount = lines.size
            LazyColumn(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF141414))
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                items((1..lineCount).toList()) { num ->
                    Text(
                        text = "$num",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF555555),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp, bottom = 2.dp)
                    )
                }
            }

            // Editor Box
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE2E8F0) // Highly readable text
                ),
                visualTransformation = CodeSyntaxHighlighter(accent),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F1115),
                    unfocusedContainerColor = Color(0xFF0F1115),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        // Slide up AI Companion Sheet
        if (showAiSheet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(surfaceColor)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "مساعد التعديل والتحليل (Gemini AI)",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 14.sp
                            )
                        }
                        IconButton(onClick = { showAiSheet = false }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Close AI", tint = textColor)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Direct AI Prompt shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val prompt = "Analyze the safety of this code snippet and point out potential errors. Explain in Arabic: $text"
                                    aiResponse = askGemini(prompt)
                                    isAiLoading = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("فحص الأمان", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val prompt = "Explain the logic of this decompiled android source snippet line by line in Arabic:\n\n$text"
                                    aiResponse = askGemini(prompt)
                                    isAiLoading = false
                                    Toast.makeText(context, "Completed explaining!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("اشرح الكود", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                isAiLoading = true
                                coroutineScope.launch {
                                    val prompt = "Refactor and optimize this decompiled function to be faster and clean. Keep syntax valid:\n\n$text"
                                    val result = askGemini(prompt)
                                    aiResponse = "Optimized version ready. Press apply to replace or inspect below."
                                    text = result
                                    isAiLoading = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تحسين تلقائي", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isAiLoading) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accent)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            item {
                                Text(
                                    text = aiResponse.ifEmpty { "حدد أحد الاختصارات في الأعلى أو تواصل مع كود مساعد جيميناي للحصول على شرح كامل وشامل لتعليمات الكلاس والدوال الحالية." },
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.85f),
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Terminal Simulator View
@Composable
fun TerminalSimulatorView(
    state: TerminalState,
    surfaceColor: Color,
    dividerColor: Color,
    textColor: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1416))
            .padding(10.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false
        ) {
            items(state.terminalLogs) { log ->
                Text(
                    text = log.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = when {
                        log.isError -> Color(0xFFEF5350)
                        log.isSystemInfo -> Color(0xFF29B6F6)
                        log.isHeader -> Color(0xFFFFCA28)
                        log.isInput -> Color(0xFF81C784)
                        log.isSuccess -> Color(0xFF9CCC65)
                        else -> Color(0xFFECEFF1)
                    },
                    modifier = Modifier.padding(bottom = 2.0.dp)
                )
            }
        }

        Divider(color = dividerColor.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

        // CommandLine Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.getPromptSymbol(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFFFB300)
            )
            TextField(
                value = state.inputBuffer,
                onValueChange = { state.inputBuffer = it },
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
            IconButton(
                onClick = {
                    state.executeTerminalCommand(state.inputBuffer)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Run", tint = accentColor)
            }
        }
    }
}

// Extractor view
@Composable
fun ExtractApkView(
    vfs: VirtualFileSystem,
    surfaceColor: Color,
    textColor: Color,
    secondaryText: Color,
    accentColor: Color,
    dividerColor: Color,
    onBackToExplorer: () -> Unit
) {
    val context = LocalContext.current
    var isDecompiling by remember { mutableStateOf(false) }
    var apkDecompileLogs by remember { mutableStateOf("") }
    
    val apps = listOf(
        Pair("WhatsApp", "com.whatsapp"),
        Pair("Telegram Messenger", "org.telegram.messenger"),
        Pair("MT Manager (Clone)", "com.aistudio.mtmanager"),
        Pair("Google Play Services", "com.google.android.gms"),
        Pair("System UI", "com.android.systemui")
    )

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Text(
            text = "مستخرج ملفات الـ APK (APK Extraction & Patching)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = textColor,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (isDecompiling) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF151515), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "عمليات التفكيك والتحليل جارية...",
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            text = apkDecompileLogs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6A7)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        isDecompiling = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("تم (متابعة)", color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps) { app ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = app.first,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textColor
                                    )
                                    Text(text = app.second, fontSize = 11.sp, color = secondaryText)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        // Dynamic apk creation based on VFS workspace state
                                        val savePath = if (vfs.customRootPath != null) {
                                            listOf("Download")
                                        } else {
                                            listOf("storage", "emulated", "0", "Download")
                                        }
                                        val newApkName = "${app.first.replace(" ", "_")}_extracted.apk"
                                        vfs.addFile(savePath, newApkName, "[Extracted Binary package payload - signature matching successfully]")
                                        Toast.makeText(context, "تم استخراج ملف الـ APK في Download/ !", Toast.LENGTH_LONG).show()
                                        onBackToExplorer()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Text("استخراج APK", color = Color.White, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        isDecompiling = true
                                        apkDecompileLogs = """
Scanning APK metadata for ${app.second}...
Reading AndroidManifest.xml compressed block...
[Task 1] Decoding resources table (arsc)
[Task 2] Translating AndroidManifest binaries inside XML
[Task 3] Unpacking structures of classes.dex (6.42 MB) ...
[Classes] Found 2,429 smali classes.
[Classes] Translating register operations into Smali opcodes.
Writing files of MyCoolApp tree inside virtual explorer...
Completed! Extracted Smali files can be modified in Codex or MyCoolApp folds.
                                        """.trimIndent()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90A4AE))
                                ) {
                                    Text("تفكيك الأكواد", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Interactive Color picker Screen
@Composable
fun ScreenColorPickerView(
    surfaceColor: Color,
    textColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    var selectedColor by remember { mutableStateOf(Color(0xFF29B6F6)) }
    var cursorOffset by remember { mutableStateOf(Offset(150f, 150f)) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ملتقط الألوان الهندسي (Screen Color Picker Panel)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = textColor,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        cursorOffset += dragAmount
                        // Clamp local bounds logic
                        val x = cursorOffset.x.coerceIn(0f, 600f)
                        val y = cursorOffset.y.coerceIn(0f, 600f)
                        cursorOffset = Offset(x, y)
                        
                        // Map offset to colors
                        val r = (x / 600f * 255).roundToInt().coerceIn(0, 255)
                        val g = (y / 600f * 255).roundToInt().coerceIn(0, 255)
                        val b = (((x + y) / 1200f) * 255).roundToInt().coerceIn(0, 255)
                        selectedColor = Color(r, g, b)
                    }
                },
            colors = CardDefaults.cardColors(containerColor = surfaceColor)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Gradient backdrop previewing color picker mapping
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.Red, Color.Green, Color.Blue, Color.Black),
                            center = center,
                            radius = size.minDimension / 1.1f
                        )
                    )
                    
                    // Render interactive slider target
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = cursorOffset
                    )
                    drawCircle(
                        color = selectedColor,
                        radius = 8f,
                        center = cursorOffset
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hex Output box
        val hexCode = String.format("#%02X%02X%02X", 
            (selectedColor.red * 255).toInt(), 
            (selectedColor.green * 255).toInt(), 
            (selectedColor.blue * 255).toInt()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor, RoundedCornerShape(8.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(1.dp, textColor.copy(alpha = 0.2f), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "كود اللون الحالي:", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    Text(text = hexCode, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(hexCode))
                    Toast.makeText(context, "تم نسخ الكود ($hexCode) للحافظة!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(ThemeState.getTranslation("copy_hex"), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

// Remote management configuration view
@Composable
fun RemoteManagementView(
    surfaceColor: Color,
    textColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    var isServerOn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "الإدارة الملفات عن بعد (SFTP Remote Server)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = textColor
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "حالة الخادم (FTP Server Status)", fontWeight = FontWeight.Bold, color = textColor, fontSize = 13.sp)
                        Text(
                            text = if (isServerOn) ThemeState.getTranslation("active") else ThemeState.getTranslation("inactive"),
                            color = if (isServerOn) Color.Green else textColor.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isServerOn,
                        onCheckedChange = {
                            isServerOn = it
                            Toast.makeText(context, if (it) "تم تفعيل شبكة SFTP المزامنة" else "تم إيقاف المزامنة اللاسلكية", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                    )
                }

                Divider()

                if (isServerOn) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "رابط الاتصال بالأندرويد:", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                        Text(text = "sftp://192.168.1.104:2121/", fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = accentColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "اسم المستخدم: admin", fontSize = 12.sp, color = textColor)
                            Text(text = "كلمة المرور: mt_editor_pro", fontSize = 12.sp, color = textColor)
                        }
                    }
                } else {
                    Text(
                        text = "قم بتشغيل المفتاح في الأعلى لإنشاء اتصال محلي يسمح لك بتصفح وإضافة وتعديل ملفات جهازك الأندرويد عن بعد من متصفح كمبيوترك بسهولة.",
                        fontSize = 12.sp,
                        color = textColor.copy(alpha = 0.65f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// Plugin Manager screen
@Composable
fun PluginManagerView(
    surfaceColor: Color,
    textColor: Color,
    secondaryText: Color,
    accentColor: Color,
    dividerColor: Color
) {
    val items = listOf(
        Pair("Smali Beautifier (مفكك الرموز)", "v1.2.0 • يحسن قراءة الكلاسات والمسجلات والأكواد"),
        Pair("Dex Unpacker Engine", "v3.1.4 • محرك أساسي لتعبئة أكواد dex من APK"),
        Pair("Signature Bypass Plugin", "v2.0.1 • يتجاوز التحقق من التوقيع الرقمي تلقائياً"),
        Pair("String Decrypter", "v1.5.0 • لفك تشفير المحارف والجمل البرمجية المعماة")
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "مدير الإضافات الفنية البرمجية (MT Editor Plugin Manager)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = textColor,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { plugin ->
                var isEnabled by remember { mutableStateOf(true) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = plugin.first, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                            }
                            Text(text = plugin.second, fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                        )
                    }
                }
            }
        }
    }
}

// Searchable index of Smali instructions
@Composable
fun SmaliInstructionsView(
    askGemini: suspend (String) -> String,
    coroutineScope: CoroutineScope,
    surfaceColor: Color,
    textColor: Color,
    secondaryText: Color,
    accentColor: Color,
    dividerColor: Color
) {
    val context = LocalContext.current
    var searchKey by remember { mutableStateOf("") }
    var selectedOpcode by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    
    // Gemini explaining state
    var isExplainingOp by remember { mutableStateOf(false) }
    var opExplanationResult by remember { mutableStateOf("") }

    val opcodes = listOf(
        Triple("const-string vA, \"string\"", "تحميل نص", "تحميل نص برمجي إلى مسجل المسار vA مباشرة."),
        Triple("return-void", "إرجاع دالة فارغة", "إنهاء دالة لا ترجع أي بيانات."),
        Triple("const/4 vA, #int", "تحميل رقم", "تحميل قيمة عددية صحيحة صغيرة إلى المسجل vA (مثال تجاوز التراخيص بنقل 1)."),
        Triple("invoke-virtual {vC..vD}, method_id", "استدعاء دالة كائن", "استدعاء دالة رئيسية على كائن معين مع قنوات تمرير المعاملات."),
        Triple("move-result-object vA", "حفظ الناتج بكائن", "نقل كائن ناتج استدعاء دالة سابقة إلى المسجل vA."),
        Triple("if-eqz vA, :label", "تحقق شرطي فارغ", "الانتقال إلى علامة تسمية label إذا كان المسجل vA يساوي صفراً (خالٍ/فشل)."),
        Triple("goto :label", "الانتقال المباشر", "القفز المباشر وتجاوز السطور السفلية إلى علامة تسمية label المحددة.")
    )

    val filteredOpcodes = if (searchKey.isEmpty()) {
        opcodes
    } else {
        opcodes.filter { it.first.contains(searchKey, ignoreCase = true) || it.second.contains(searchKey) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        OutlinedTextField(
            value = searchKey,
            onValueChange = { searchKey = it },
            label = { Text("ابحث في سجلات Smali (const-string, return...)", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor)
        )
        
        Spacer(modifier = Modifier.height(10.dp))

        if (selectedOpcode != null) {
            val op = selectedOpcode!!
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = op.first, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentColor, fontFamily = FontFamily.Monospace)
                        IconButton(onClick = { 
                            selectedOpcode = null 
                            opExplanationResult = ""
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
                    }
                    Text(text = "الاسم المحلي: ${op.second}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text(text = op.third, fontSize = 12.sp, color = secondaryText)
                    
                    Divider()
                    
                    if (isExplainingOp) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.align(Alignment.CenterHorizontally).size(26.dp))
                    } else if (opExplanationResult.isNotEmpty()) {
                        Text(
                            text = opExplanationResult,
                            fontSize = 11.sp,
                            color = textColor,
                            lineHeight = 15.sp,
                            modifier = Modifier.background(textColor.copy(alpha = 0.05f)).padding(10.dp).fillMaxWidth()
                        )
                    } else {
                        Button(
                            onClick = {
                                isExplainingOp = true
                                coroutineScope.launch {
                                    val prompt = "You are an expert Dalvik Smali engineer. Explain the exact syntax and usage rules of Smali instruction '${op.first}' in Arabic. Be brief and highly technical with simple example."
                                    opExplanationResult = askGemini(prompt)
                                    isExplainingOp = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("اسأل الذكاء الاصطناعي لشرح كود: ${op.first}", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredOpcodes) { op ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedOpcode = op }
                        .background(surfaceColor)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = op.first, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor, fontFamily = FontFamily.Monospace)
                        Text(text = op.second, fontSize = 11.sp, color = secondaryText)
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = secondaryText)
                }
            }
        }
    }
}

class CodeSyntaxHighlighter(val accentColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            var i = 0
            while (i < raw.length) {
                // Check comments
                if (raw.startsWith("//", i)) {
                    val endOfLine = raw.indexOf('\n', i).let { if (it == -1) raw.length else it }
                    withStyle(SpanStyle(color = Color(0xFF6A9955))) { // Clean visual green comments
                        append(raw.substring(i, endOfLine))
                    }
                    i = endOfLine
                    continue
                }
                if (raw.startsWith("#", i)) {
                    val endOfLine = raw.indexOf('\n', i).let { if (it == -1) raw.length else it }
                    withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                        append(raw.substring(i, endOfLine))
                    }
                    i = endOfLine
                    continue
                }
                
                // Double quoted strings
                if (raw[i] == '"') {
                    val nextQuote = raw.indexOf('"', i + 1).let { if (it == -1) raw.length else it + 1 }
                    withStyle(SpanStyle(color = Color(0xFFCE9178))) { // VS Code default color for strings
                        append(raw.substring(i, nextQuote))
                    }
                    i = nextQuote
                    continue
                }

                // Single quoted strings (Smali or Kotlin char literal / reference)
                if (raw[i] == '\'') {
                    val nextQuote = raw.indexOf('\'', i + 1).let { if (it == -1) raw.length else it + 1 }
                    withStyle(SpanStyle(color = Color(0xFFCE9178))) {
                        append(raw.substring(i, nextQuote))
                    }
                    i = nextQuote
                    continue
                }

                // Registers, variables or numbers
                if (raw[i].isDigit()) {
                    var endDigit = i
                    while (endDigit < raw.length && (raw[endDigit].isDigit() || raw[endDigit] == 'x' || raw[endDigit] == 'b' || raw[endDigit] == 'f')) {
                        endDigit++
                    }
                    withStyle(SpanStyle(color = Color(0xFFB5CEA8))) { // Soft number coloring
                        append(raw.substring(i, endDigit))
                    }
                    i = endDigit
                    continue
                }

                // Check letters for keywords
                if (raw[i].isLetter() || raw[i] == '.' || raw[i] == '#') {
                    var endWord = i + 1
                    while (endWord < raw.length && (raw[endWord].isLetterOrDigit() || raw[endWord] == '-' || raw[endWord] == '.' || raw[endWord] == '_' || raw[endWord] == ':')) {
                        endWord++
                    }
                    val word = raw.substring(i, endWord)
                    
                    val isSmaliKeyword = word.startsWith(".") || word.startsWith("invoke-") || word.startsWith("const-") || word == "return-void" || word == "return" || word == "nop" || word == "goto" || word == "move-result" || word == "move-result-object" || word.startsWith("if-")
                    val isLangKeyword = word in listOf(
                        "package", "import", "class", "fun", "val", "var", "private", "public", "protected", "override", "interface", 
                        "void", "static", "final", "constructor", "return", "true", "false", "this", "super", "null", "if", "else", "while"
                    )
                    
                    if (isSmaliKeyword || isLangKeyword) {
                        withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                    } else if (word.startsWith("L") && word.contains(";") && word.length > 2) {
                        // Class descriptors in Smali e.g. Landroid/app/Activity;
                        withStyle(SpanStyle(color = Color(0xFF4EC9B0))) { // Greenish Blue aquamarine for class names
                            append(word)
                        }
                    } else if (word.startsWith("v") && word.length in 2..3 && word[1].isDigit()) {
                        // Smali registers (v0, v1, p0, p1)
                        withStyle(SpanStyle(color = Color(0xFF9CDCFE), fontWeight = FontWeight.SemiBold)) { // Blue soft register
                            append(word)
                        }
                    } else if (word.startsWith("p") && word.length in 2..3 && word[1].isDigit()) {
                        withStyle(SpanStyle(color = Color(0xFF9CDCFE), fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                    } else {
                        append(word)
                    }
                    i = endWord
                } else {
                    append(raw[i])
                    i++
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
