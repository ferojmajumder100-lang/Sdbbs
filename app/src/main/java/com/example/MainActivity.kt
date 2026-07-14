package com.example

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.DialogConfig
import com.example.data.DialogRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var repository: DialogRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        repository = DialogRepository(database.dialogDao())

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainDashboardScreen(
                        repository = repository,
                        modifier = Modifier.padding(innerPadding),
                        onExportZip = { config -> exportZip(config) },
                        onShareJson = { config -> shareJson(config) }
                    )
                }
            }
        }
    }

    private fun shareJson(config: DialogConfig) {
        try {
            val jsonStr = generateJsonString(config)
            val cacheFile = File(cacheDir, "${config.name.replace(" ", "_")}_config.json")
            FileOutputStream(cacheFile).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", cacheFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share JSON Configuration"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing JSON: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportZip(config: DialogConfig) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Load classes.dex from assets
                val assetsDexBytes = assets.open("classes.dex").use { it.readBytes() }

                // 2. Generate hook.txt content
                val hookText = generateHookInstructions(config)

                // 3. Create ZIP in cache
                val zipFile = File(cacheDir, "${config.name.replace(" ", "_")}_injection.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // Add hook.txt
                    zos.putNextEntry(ZipEntry("hook.txt"))
                    zos.write(hookText.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Add classes.dex
                    zos.putNextEntry(ZipEntry("classes.dex"))
                    zos.write(assetsDexBytes)
                    zos.closeEntry()
                }

                // 4. Trigger share/save sheet
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", zipFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Save/Export Injection ZIP"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// Generate the beautiful integration instruction set
fun generateHookInstructions(config: DialogConfig): String {
    val intervalStr = if (config.checkInterval > 0) "${config.checkInterval}" else "10"
    return """========================================================================
DYNAMIC DIALOG & TOAST INJECTION GUIDE
Generated Config: ${config.name}
========================================================================

These two files (classes.dex and hook.txt) are all you need to inject custom 
dialogs/toasts dynamically into any target Android Application!

------------------------------------------------------------------------
METHOD A: DYNAMIC CLASS LOADING (HIGHLY RECOMMENDED)
------------------------------------------------------------------------
If you don't want to decompile/merge DEX files of the target application,
you can load this classes.dex dynamically from assets at runtime!

1. Place the generated 'classes.dex' inside the target app's 'assets/' folder.
2. In the target app's main Launcher Activity 'onCreate' or Application 'onCreate' 
   method, inject the following Java code snippet:

```java
try {
    // 1. Copy classes.dex from assets to internal storage so we can load it
    java.io.File dexInternalPath = new java.io.File(getFilesDir(), "injected_loader.dex");
    if (!dexInternalPath.exists()) {
        java.io.InputStream is = getAssets().open("classes.dex");
        java.io.FileOutputStream os = new java.io.FileOutputStream(dexInternalPath);
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        is.close();
        os.close();
    }

    // 2. Load the class dynamically
    dalvik.system.DexClassLoader dexClassLoader = new dalvik.system.DexClassLoader(
        dexInternalPath.getAbsolutePath(),
        getCacheDir().getAbsolutePath(),
        null,
        getClassLoader()
    );

    // 3. Invoke DialogLoader.init(context, configUrl, checkIntervalSeconds)
    Class<?> clazz = dexClassLoader.loadClass("com.example.dialogsdk.DialogLoader");
    java.lang.reflect.Method method = clazz.getMethod("init", 
        android.content.Context.class, 
        String.class, 
        int.class
    );
    
    // Execute Hook (polls raw JSON URL continuously)
    method.invoke(null, this, "${config.configUrl.ifEmpty { "https://your-raw-json-link-here" }}", $intervalStr);

} catch (Exception e) {
    e.printStackTrace();
}
```

------------------------------------------------------------------------
METHOD B: MERGE AND DIRECT CALL
------------------------------------------------------------------------
If you merge classes.dex directly into the target app's DEX files (e.g. using Dexpatcher or smali):

1. Put this line in the target app's Launcher Activity 'onCreate' method:

```java
com.example.dialogsdk.DialogLoader.init(this, "${config.configUrl.ifEmpty { "https://your-raw-json-link-here" }}", $intervalStr);
```

========================================================================
CONFIG JSON FORMAT (Host this on Pastebin or Github Raw)
========================================================================
Make sure your remote JSON URL returns exactly this structure:

{
  "status": "${config.status}", 
  "title": "${config.title}",
  "message": "${config.message}",
  "type": "${config.type}",
  "is_cancelable": ${config.isCancelable},
  "button_text": "${config.buttonText}",
  "action_url": "${config.actionUrl}"
}

Status Note:
- Set status to "off" to trigger the Dialog/Toast immediately.
- Set status to "on" to run normally (hides/dismisses active dialogs).
"""
}

fun generateJsonString(config: DialogConfig): String {
    val json = JSONObject().apply {
        put("status", config.status)
        put("title", config.title)
        put("message", config.message)
        put("type", config.type)
        put("is_cancelable", config.isCancelable)
        put("button_text", config.buttonText)
        put("action_url", config.actionUrl)
    }
    return json.toString(2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    repository: DialogRepository,
    modifier: Modifier = Modifier,
    onExportZip: (DialogConfig) -> Unit,
    onShareJson: (DialogConfig) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form States
    var currentId by remember { mutableStateOf(0) }
    var configName by remember { mutableStateOf("My Custom Dialog") }
    var statusValue by remember { mutableStateOf("off") } // "off" = dialog active, "on" = normal
    var dialogTitle by remember { mutableStateOf("Update Required") }
    var dialogMessage by remember { mutableStateOf("Please update your app to the latest version to prevent block.") }
    var selectedType by remember { mutableStateOf("dialog") } // "dialog" or "toast"
    var isCancelable by remember { mutableStateOf(false) }
    var buttonText by remember { mutableStateOf("Update Now") }
    var actionUrl by remember { mutableStateOf("https://play.google.com") }
    var configUrl by remember { mutableStateOf("https://pastebin.com/raw/example") }
    var checkInterval by remember { mutableStateOf("10") }

    // Navigation/Panel active states
    var activeTab by remember { mutableStateOf(0) } // 0: Editor, 1: History, 2: Integration Hook
    val historyConfigs by repository.allConfigs.collectAsState(initial = emptyList())

    // Update form when a config is loaded from history
    val loadConfig = { config: DialogConfig ->
        currentId = config.id
        configName = config.name
        statusValue = config.status
        dialogTitle = config.title
        dialogMessage = config.message
        selectedType = config.type
        isCancelable = config.isCancelable
        buttonText = config.buttonText
        actionUrl = config.actionUrl
        configUrl = config.configUrl
        checkInterval = config.checkInterval.toString()
        activeTab = 0 // jump to editor
        Toast.makeText(context, "Loaded: ${config.name}", Toast.LENGTH_SHORT).show()
    }

    // Reset editor
    val resetForm = {
        currentId = 0
        configName = "My Custom Dialog"
        statusValue = "off"
        dialogTitle = "Update Required"
        dialogMessage = "Please update your app to the latest version."
        selectedType = "dialog"
        isCancelable = false
        buttonText = "Update Now"
        actionUrl = "https://play.google.com"
        configUrl = ""
        checkInterval = "10"
    }

    val currentConfig = DialogConfig(
        id = currentId,
        name = configName,
        status = statusValue,
        title = dialogTitle,
        message = dialogMessage,
        type = selectedType,
        isCancelable = isCancelable,
        buttonText = buttonText,
        actionUrl = actionUrl,
        configUrl = configUrl,
        checkInterval = checkInterval.toIntOrNull() ?: 10
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Hero Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner_1784017488885),
                contentDescription = "Developer dashboard banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Beautiful semi-transparent overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE60D1117)),
                            startY = 0f,
                            endY = 320f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "DEX DIALOG BUILDER",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = "Create and export beautiful dynamic injection dialogs",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF8B949E),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Standard Material 3 TabRow for pristine navigation
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF161B22),
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("BUILDER", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Build, contentDescription = "Editor") }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("HISTORY", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.History, contentDescription = "History") }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("HOOK CODE", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Code, contentDescription = "Hook Instructions") }
            )
        }

        // Content Area depending on current Tab
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                0 -> {
                    // Editor Panel
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title Section: Configuration General Details
                        item {
                            Text(
                                "Configuration Details",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = configName,
                                onValueChange = { configName = it },
                                label = { Text("Configuration Name") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("config_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF30363D)
                                )
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Dialog Type Selector
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Display Type", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF161B22))
                                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (selectedType == "dialog") MaterialTheme.colorScheme.primary else Color.Transparent)
                                                .clickable { selectedType = "dialog" }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Dialog",
                                                color = if (selectedType == "dialog") Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (selectedType == "toast") MaterialTheme.colorScheme.primary else Color.Transparent)
                                                .clickable { selectedType = "toast" }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Toast",
                                                color = if (selectedType == "toast") Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                // Status Selector ("off" triggers block, "on" skips)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Status Mode", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF161B22))
                                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (statusValue == "off") Color(0xFFEA6060) else Color.Transparent)
                                                .clickable { statusValue = "off" }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "OFF (Active)",
                                                color = if (statusValue == "off") Color.White else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (statusValue == "on") Color(0xFF2EA043) else Color.Transparent)
                                                .clickable { statusValue = "on" }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "ON (Normal)",
                                                color = if (statusValue == "on") Color.White else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Text content configuration
                        item {
                            Text(
                                "Content & Copywriting",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = dialogTitle,
                                onValueChange = { dialogTitle = it },
                                label = { Text("Dialog Title") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedType == "dialog",
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF30363D)
                                )
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = dialogMessage,
                                onValueChange = { dialogMessage = it },
                                label = { Text("Notification Message") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF30363D)
                                )
                            )
                        }

                        if (selectedType == "dialog") {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Force Status (Non-Cancelable)", fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(
                                            "If active, users cannot dismiss or close the dialog",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8B949E)
                                        )
                                    }
                                    Switch(
                                        checked = !isCancelable,
                                        onCheckedChange = { isCancelable = !it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = Color(0xFF161B22)
                                        )
                                    )
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = buttonText,
                                        onValueChange = { buttonText = it },
                                        label = { Text("Button Text") },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color(0xFF30363D)
                                        )
                                    )

                                    OutlinedTextField(
                                        value = actionUrl,
                                        onValueChange = { actionUrl = it },
                                        label = { Text("Redirect/Update URL") },
                                        modifier = Modifier.weight(2.5f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color(0xFF30363D)
                                        )
                                    )
                                }
                            }
                        }

                        // Remote Link Configuration
                        item {
                            Text(
                                "Remote Configuration Link (JSON Source)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = configUrl,
                                onValueChange = { configUrl = it },
                                label = { Text("Pastebin / GitHub RAW JSON URL") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("https://raw.githubusercontent.com/.../config.json") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF30363D)
                                )
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = checkInterval,
                                onValueChange = { checkInterval = it },
                                label = { Text("Continuous Check Interval (Seconds)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF30363D)
                                )
                            )
                        }

                        // Live Preview Section
                        item {
                            Text(
                                "Smartphone Live Preview",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Smartphone-like container rendering the preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF06090D))
                                    .border(2.dp, Color(0xFF30363D), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                // Background mock content of a host app
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Status Bar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("09:41", color = Color(0xFF8B949E), fontSize = 10.sp)
                                        Icon(
                                            Icons.Default.Wifi,
                                            contentDescription = "Wifi",
                                            tint = Color(0xFF8B949E),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    // Mock Host App layout
                                    Text(
                                        "My Target App",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Divider(color = Color(0xFF1F242C))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(35.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF161B22))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF161B22))
                                    )
                                }

                                // Interactive Overlay mimicking the built dialog
                                if (statusValue == "off") {
                                    if (selectedType == "dialog") {
                                        // Centered Dialog Card Mockup
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .align(Alignment.Center)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF1F242C))
                                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                                                .padding(16.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = dialogTitle.ifEmpty { "Title" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = dialogMessage.ifEmpty { "Message text here..." },
                                                    fontSize = 13.sp,
                                                    color = Color(0xFFC9D1D9),
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    if (isCancelable) {
                                                        TextButton(onClick = {}) {
                                                            Text("Cancel", color = Color(0xFF8B949E), fontSize = 12.sp)
                                                        }
                                                    }
                                                    Button(
                                                        onClick = {},
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(buttonText.ifEmpty { "Update" }, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Toast Mockup on bottom
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .offset(y = (-16).dp)
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(Color(0xFF21262D))
                                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(24.dp))
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                dialogMessage.ifEmpty { "Toast notification message" },
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    // Normally running notification status label
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x332EA043))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            "Status: ON\nTarget app running normally",
                                            color = Color(0xFF56D364),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Interactive Spawning Button
                        item {
                            Button(
                                onClick = {
                                    if (selectedType == "dialog") {
                                        val builder = AlertDialog.Builder(context)
                                        builder.setTitle(dialogTitle)
                                        builder.setMessage(dialogMessage)
                                        builder.setCancelable(isCancelable)
                                        builder.setPositiveButton(buttonText) { _, _ ->
                                            if (!actionUrl.isEmpty()) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        if (isCancelable) {
                                            builder.setNegativeButton("Cancel", null)
                                        }
                                        builder.create().show()
                                    } else {
                                        Toast.makeText(context, dialogMessage, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trigger_preview_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Test Dialog", tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LAUNCH REAL DEVICE PREVIEW", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Storage Actions & Persistence
                        item {
                            Text(
                                "Save & Export Injection Tools",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Save locally to database
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (currentId == 0) {
                                                val generatedId = repository.insert(currentConfig)
                                                currentId = generatedId.toInt()
                                                Toast.makeText(context, "Saved to local History!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                repository.update(currentConfig)
                                                Toast.makeText(context, "Updated successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SAVE CONFIG")
                                }

                                // Clear Form
                                OutlinedButton(
                                    onClick = {
                                        resetForm()
                                        Toast.makeText(context, "Form Cleared", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("NEW / RESET")
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Export dynamic ZIP containing Dex + Hook.txt
                                Button(
                                    onClick = { onExportZip(currentConfig) },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .testTag("export_zip_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = "ZIP", tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EXPORT ZIP (DEX + HOOK)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                // Copy/Share RAW config JSON
                                Button(
                                    onClick = { onShareJson(currentConfig) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30363D)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SHARE JSON", fontSize = 13.sp)
                                }
                            }
                        }

                        // Clipboard JSON Option
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "JSON Preview (config.json)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Config JSON", generateJsonString(currentConfig))
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "JSON Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = generateJsonString(currentConfig),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF8B949E),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // History Screen
                    if (historyConfigs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Empty History",
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF30363D)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No saved dialog configurations found",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B949E),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Build custom setups in the BUILDER tab and save them to find them listed here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF30363D),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    "Saved Dialog Setups",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            items(historyConfigs) { config ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { loadConfig(config) },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                    border = BorderStroke(1.dp, Color(0xFF30363D))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    config.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = Color.White
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    // Type label badge
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (config.type == "dialog") Color(0x3358A6FF) else Color(0x33FFD000))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            config.type.uppercase(),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (config.type == "dialog") Color(0xFF58A6FF) else Color(0xFFFFD000)
                                                        )
                                                    }
                                                    // Status label badge
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (config.status == "off") Color(0x33EA6060) else Color(0x332EA043))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            if (config.status == "off") "ACTIVE (OFF)" else "NORMAL (ON)",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (config.status == "off") Color(0xFFEA6060) else Color(0xFF2EA043)
                                                        )
                                                    }
                                                }
                                            }

                                            // Action Buttons inside Card
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(onClick = { onExportZip(config) }) {
                                                    Icon(Icons.Default.Download, contentDescription = "Export ZIP", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(onClick = {
                                                    coroutineScope.launch {
                                                        repository.deleteById(config.id)
                                                        Toast.makeText(context, "Deleted configuration", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEA6060))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "Message: ${config.message}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8B949E),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (config.configUrl.isNotEmpty()) {
                                            Text(
                                                "Remote URL: ${config.configUrl}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF58A6FF),
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Integration Hook Instructions Panel
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                "How to Inject Dynamic Dialogs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        item {
                            Text(
                                "This dynamic system is driven by classes.dex loaded via your app's hook. It checks your GitHub/Pastebin Raw JSON file at specified intervals and triggers dialogues dynamically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8B949E)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                border = BorderStroke(1.dp, Color(0xFF30363D))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "hook.txt Integration Output",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Hook Instructions", generateHookInstructions(currentConfig))
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Instructions Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Hook Code", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = generateHookInstructions(currentConfig),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFC9D1D9)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
