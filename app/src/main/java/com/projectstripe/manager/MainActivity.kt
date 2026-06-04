package com.projectstripe.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProjectStripeApp() }
    }
}

private enum class Screen(val title: String) {
    Home("Home"),
    Setup("Setup"),
    Tools("Tools"),
    Logs("Logs"),
    Settings("Settings"),
}

@Composable
private fun ProjectStripeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val magiskStatusChecker = remember { MagiskStatusChecker() }
    val nativeBridge = remember { VectorNativeBridge(magiskStatusChecker) }
    val checker = remember { VectorConnectionChecker(context.applicationContext, magiskStatusChecker, nativeBridge) }
    var selectedScreen by remember { mutableStateOf(Screen.Home) }
    var status by remember { mutableStateOf(ProjectStripeStatus.initial()) }
    var lastAction by remember { mutableStateOf<VectorActionResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    fun refreshChecks() {
        scope.launch {
            isChecking = true
            status = checker.check()
            isChecking = false
        }
    }

    fun runNativeAction(refreshAfter: Boolean = false, action: suspend VectorNativeBridge.() -> VectorActionResult) {
        scope.launch {
            isChecking = true
            val result = nativeBridge.action()
            lastAction = result
            if (refreshAfter) {
                status = checker.check()
            }
            isChecking = false
        }
    }

    LaunchedEffect(Unit) { refreshChecks() }

    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Color(0xFF2563EB),
                secondary = Color(0xFF0F766E),
                surface = Color(0xFFF8FAFC),
                background = Color(0xFFFFFFFF),
            )
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = { Text(screen.title.take(1)) },
                            label = { Text(screen.title) },
                        )
                    }
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (selectedScreen) {
                    Screen.Home -> HomeScreen(padding, status, isChecking, ::refreshChecks) { selectedScreen = it }
                    Screen.Setup -> SetupScreen(padding, status, isChecking, ::refreshChecks)
                    Screen.Tools ->
                        ToolsScreen(
                            padding = padding,
                            status = status,
                            isBusy = isChecking,
                            lastAction = lastAction,
                            onRefresh = ::refreshChecks,
                            onCliStatus = { runNativeAction { refreshCliStatus() } },
                            onReadModules = { runNativeAction { readModulesForLog() } },
                            onReadLog = { verbose -> runNativeAction { readVectorLog(verbose) } },
                            onSetConfig = { key, enabled -> runNativeAction(refreshAfter = true) { setConfigValue(key, enabled) } },
                        )
                    Screen.Logs -> LogsScreen(padding, status)
                    Screen.Settings -> SettingsScreen(padding, status)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    status: ProjectStripeStatus,
    isChecking: Boolean,
    onRefresh: () -> Unit,
    openScreen: (Screen) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(ProjectStripeConfig.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Vector native manager interface", color = Color(0xFF475569))
        }
        item { StatusHero(status.nextStep) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRefresh, enabled = !isChecking) { Text(if (isChecking) "Checking..." else "Run checks") }
                OutlinedButton(onClick = { openScreen(Screen.Setup) }) { Text("Setup") }
            }
        }
        item {
            SectionTitle("Status")
            StatusGrid(status)
        }
        item { NativeSummaryCard(status.nativeStatus) }
        item {
            SectionTitle("Open")
            ActionButtons(openScreen)
        }
    }
}

@Composable
private fun SetupScreen(
    padding: PaddingValues,
    status: ProjectStripeStatus,
    isChecking: Boolean,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Vector setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(status.nextStep, color = Color(0xFF475569))
        }
        item { Button(onClick = onRefresh, enabled = !isChecking) { Text(if (isChecking) "Checking..." else "Check setup") } }
        item { SectionTitle("System checks") }
        item { StatusRow("Root access", status.magiskStatus.root) }
        item { StatusRow("Magisk", status.magiskStatus.magiskVersion) }
        item { StatusRow("Zygisk", status.magiskStatus.zygisk) }
        item { StatusRow("Vector module folder", status.moduleCheck.status) }
        item { InfoCard("Android SDK", status.magiskStatus.androidSdk.toString()) }
        item { InfoCard("Device codename", status.magiskStatus.deviceCodename) }
        item { SectionTitle("Vector native paths") }
        item { StatusRow(ProjectStripeConfig.VECTOR_BASE_PATH, status.nativeStatus.paths.base) }
        item { StatusRow(ProjectStripeConfig.VECTOR_CLI_PATH, status.nativeStatus.paths.cli) }
        item { StatusRow(ProjectStripeConfig.VECTOR_SOCKET_PATH, status.nativeStatus.paths.socket) }
        item { StatusRow(ProjectStripeConfig.VECTOR_CONFIG_PATH, status.nativeStatus.paths.config) }
        item { StatusRow(ProjectStripeConfig.VECTOR_DATABASE_PATH, status.nativeStatus.paths.database) }
        item { StatusRow(ProjectStripeConfig.VECTOR_LOG_PATH, status.nativeStatus.paths.logs) }
        item { StatusRow(ProjectStripeConfig.VECTOR_MODULE_DATA_PATH, status.nativeStatus.paths.moduleData) }
        item {
            InfoCard(
                title = "Detected module folders",
                body = status.moduleCheck.detectedFolders.ifEmpty { listOf("None") }.joinToString("\n"),
            )
        }
        item {
            InfoCard(
                title = "Manager packages",
                body = status.managerPackages.ifEmpty { listOf("None") }.joinToString("\n"),
            )
        }
        item { SectionTitle("Magisk module metadata") }
        if (status.nativeStatus.moduleMetadata.isEmpty()) {
            item { InfoCard("No Vector module metadata", "No Vector, LSPosed, or libxposed module.prop match was readable.") }
        } else {
            items(status.nativeStatus.moduleMetadata) { module -> ModuleMetadataCard(module) }
        }
    }
}

@Composable
private fun ToolsScreen(
    padding: PaddingValues,
    status: ProjectStripeStatus,
    isBusy: Boolean,
    lastAction: VectorActionResult?,
    onRefresh: () -> Unit,
    onCliStatus: () -> Unit,
    onReadModules: () -> Unit,
    onReadLog: (Boolean) -> Unit,
    onSetConfig: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Native Vector controls with safe Project Stripe boundaries", color = Color(0xFF475569))
        }
        item { NativeActionCard("Refresh all checks", "Re-run Android, Magisk, Vector native path, CLI, and module checks.", isBusy, onRefresh) }
        item { NativeActionCard("Read CLI status", "Ask Vector's own CLI for framework version, version code, enabled modules, and notification state.", isBusy, onCliStatus) }
        item { NativeActionCard("Read native module list", "List Vector-reported modules without enabling, disabling, or changing scopes.", isBusy, onReadModules) }
        item { NativeActionCard("Read module log", "Dump the current Vector module log through the CLI.", isBusy) { onReadLog(false) } }
        item { NativeActionCard("Read verbose log", "Dump Vector verbose daemon output through the CLI.", isBusy) { onReadLog(true) } }
        item {
            ConfigToggleCard(
                title = "Status notification",
                description = "Toggle Vector daemon status notification through config set.",
                key = "status-notification",
                status = status.nativeStatus.statusNotification,
                isBusy = isBusy,
                onSetConfig = onSetConfig,
            )
        }
        item {
            ConfigToggleCard(
                title = "Verbose logging",
                description = "Toggle Vector verbose daemon logging through config set.",
                key = "verbose-log",
                status = status.nativeStatus.verboseLog,
                isBusy = isBusy,
                onSetConfig = onSetConfig,
            )
        }
        item {
            InfoCard(
                "Locked native actions",
                "Project Stripe does not install Magisk modules, enable external app scopes, reset Vector databases, reboot the device, uninstall apps, or force-stop packages.",
            )
        }
        if (lastAction != null) {
            item { ActionResultCard(lastAction) }
        }
    }
}

@Composable
private fun LogsScreen(padding: PaddingValues, status: ProjectStripeStatus) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Logs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Connection checks, root commands, and Vector CLI results", color = Color(0xFF475569))
                }
                OutlinedButton(
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Project Stripe logs")
                                .putExtra(Intent.EXTRA_TEXT, ProjectStripeLogger.exportText())
                        context.startActivity(Intent.createChooser(intent, "Export Project Stripe logs"))
                    }
                ) {
                    Text("Export")
                }
            }
        }
        item { SectionTitle("Latest command results") }
        if (status.magiskStatus.commandResults.isEmpty()) {
            item { InfoCard("No command results", "Run setup checks to collect command output.") }
        } else {
            items(status.magiskStatus.commandResults) { result -> CommandResultCard(result) }
        }
        item { SectionTitle("Project Stripe logbook") }
        if (ProjectStripeLogger.entries.isEmpty()) {
            item { InfoCard("No logs yet", "Run setup checks or native tools to collect status and command output.") }
        } else {
            items(ProjectStripeLogger.entries) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.time, style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
                        Text(entry.message)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, status: ProjectStripeStatus) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Read-only native integration boundaries", color = Color(0xFF475569))
        }
        item { InfoCard("Package", ProjectStripeConfig.PACKAGE_NAME) }
        item { InfoCard("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
        item { InfoCard("Vector framework", ProjectStripeConfig.VECTOR_REFERENCE_NAME) }
        item { InfoCard("Magisk module root", ProjectStripeConfig.MODULE_ROOT) }
        item { InfoCard("Vector native root", ProjectStripeConfig.VECTOR_BASE_PATH) }
        item { InfoCard("Vector CLI", ProjectStripeConfig.VECTOR_CLI_PATH) }
        item { StatusRow("Status notification config", status.nativeStatus.statusNotification) }
        item { StatusRow("Verbose logging config", status.nativeStatus.verboseLog) }
        item { InfoCard("Current setup step", status.nextStep) }
        item { InfoCard("Safety", "No auto-install. No auto-enable scopes. No external app scope changes. No destructive daemon maintenance actions.") }
    }
}

@Composable
private fun StatusHero(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Project Stripe status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color(0xFF1E3A8A))
        }
    }
}

@Composable
private fun StatusGrid(status: ProjectStripeStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusRow("Root", status.magiskStatus.root)
        StatusRow("Magisk", status.magiskStatus.magiskVersion)
        StatusRow("Zygisk", status.magiskStatus.zygisk)
        StatusRow("Vector module", status.moduleCheck.status)
        StatusRow("Vector native daemon", status.nativeStatus.connection)
        StatusRow("Vector CLI", status.nativeStatus.paths.cli)
        StatusRow("Vector socket", status.nativeStatus.paths.socket)
    }
}

@Composable
private fun NativeSummaryCard(native: VectorNativeSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Native Vector bridge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(native.connection.detail, color = Color(0xFF166534))
            Spacer(Modifier.height(10.dp))
            Text("CLI modules: ${native.modules.size}", color = Color(0xFF475569))
            Text("Module metadata matches: ${native.moduleMetadata.size}", color = Color(0xFF475569))
            if (native.cliStatus.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                native.cliStatus.entries.take(4).forEach { (key, value) ->
                    Text("$key: $value", color = Color(0xFF475569))
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(openScreen: (Screen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openScreen(Screen.Setup) }) { Text("Setup") }
            Button(onClick = { openScreen(Screen.Tools) }) { Text("Tools") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openScreen(Screen.Logs) }) { Text("Logs") }
            OutlinedButton(onClick = { openScreen(Screen.Settings) }) { Text("Settings") }
        }
    }
}

@Composable
private fun NativeActionCard(title: String, description: String, isBusy: Boolean, onRun: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xFF475569))
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRun, enabled = !isBusy) { Text(if (isBusy) "Working..." else "Run") }
        }
    }
}

@Composable
private fun ConfigToggleCard(
    title: String,
    description: String,
    key: String,
    status: StatusValue,
    isBusy: Boolean,
    onSetConfig: (String, Boolean) -> Unit,
) {
    val checked = status.asBoolean()
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, color = Color(0xFF475569))
                Spacer(Modifier.height(8.dp))
                Text(status.detail, color = Color(0xFF64748B))
            }
            Switch(
                checked = checked ?: false,
                enabled = checked != null && !isBusy,
                onCheckedChange = { onSetConfig(key, it) },
            )
        }
    }
}

@Composable
private fun ActionResultCard(result: VectorActionResult) {
    val color = if (result.success) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(result.body.ifBlank { "No output returned." }, color = Color(0xFF334155))
        }
    }
}

@Composable
private fun ModuleMetadataCard(module: VectorModuleMetadata) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(module.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(module.summary, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun CommandResultCard(result: CommandResult) {
    Card(colors = CardDefaults.cardColors(containerColor = if (result.success) Color(0xFFF8FAFC) else Color(0xFFFEF2F2))) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(result.command, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (result.success) "Success" else "Failed", color = if (result.success) Color(0xFF047857) else Color(0xFFB91C1C))
            val body = result.output.ifBlank { result.error.ifBlank { "No output." } }
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun StatusRow(title: String, status: StatusValue) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(status)
            }
            Spacer(Modifier.height(8.dp))
            Text(status.detail, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun StatusPill(status: StatusValue) {
    val color =
        when (status) {
            is StatusValue.Present -> Color(0xFF047857)
            is StatusValue.Missing -> Color(0xFFB91C1C)
            is StatusValue.Unknown -> Color(0xFFB45309)
        }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(status.label, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Divider(Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(4.dp))
}

private fun StatusValue.asBoolean(): Boolean? {
    val raw = detail.substringAfter("=", detail).trim().lowercase()
    return when (raw) {
        "true", "1", "enabled", "yes" -> true
        "false", "0", "disabled", "no" -> false
        else -> null
    }
}
