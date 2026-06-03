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
import androidx.compose.foundation.layout.width
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
    val checker = remember { VectorConnectionChecker(context.applicationContext) }
    var selectedScreen by remember { mutableStateOf(Screen.Home) }
    var status by remember { mutableStateOf(ProjectStripeStatus.initial()) }
    var isChecking by remember { mutableStateOf(false) }

    fun refreshChecks() {
        scope.launch {
            isChecking = true
            status = checker.check()
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
                    Screen.Tools -> ToolsScreen(padding, ::refreshChecks)
                    Screen.Logs -> LogsScreen(padding)
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
            Text("Vector manager interface", color = Color(0xFF475569))
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
        item { StatusRow("Root access", status.magiskStatus.root) }
        item { StatusRow("Magisk", status.magiskStatus.magiskVersion) }
        item { StatusRow("Zygisk", status.magiskStatus.zygisk) }
        item { StatusRow("Vector module", status.moduleCheck.status) }
        item { InfoCard("Android SDK", status.magiskStatus.androidSdk.toString()) }
        item { InfoCard("Device codename", status.magiskStatus.deviceCodename) }
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
    }
}

@Composable
private fun ToolsScreen(padding: PaddingValues, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Local Project Stripe and Vector checks only", color = Color(0xFF475569))
        }
        items(ProjectStripeConfig.tools) { tool ->
            ToolCard(tool, onRefresh)
        }
    }
}

@Composable
private fun LogsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Logs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Connection and command results", color = Color(0xFF475569))
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
        if (ProjectStripeLogger.entries.isEmpty()) {
            item { InfoCard("No logs yet", "Run setup checks to collect status and command output.") }
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
            Text("Read-only safety boundaries", color = Color(0xFF475569))
        }
        item { InfoCard("Package", ProjectStripeConfig.PACKAGE_NAME) }
        item { InfoCard("Vector framework", ProjectStripeConfig.VECTOR_REFERENCE_NAME) }
        item { InfoCard("Module root", ProjectStripeConfig.MODULE_ROOT) }
        item { InfoCard("Current setup step", status.nextStep) }
        item { InfoCard("Safety", "No auto-install. No auto-enable scopes. No external app scope changes.") }
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
        StatusRow("Vector", status.moduleCheck.status)
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
private fun ToolCard(tool: ProjectStripeTool, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (tool.enabled) Color(0xFFFFFFFF) else Color(0xFFF1F5F9))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tool.description, color = Color(0xFF475569))
            Spacer(Modifier.height(10.dp))
            if (tool.title.startsWith("Refresh")) {
                Button(onClick = onRefresh) { Text("Run") }
            } else {
                Text(if (tool.enabled) "Available" else "Disabled", color = if (tool.enabled) Color(0xFF047857) else Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, status: StatusValue) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
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
