package app.aemu.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.aemu.BuildConfig
import app.aemu.R
import app.aemu.core.GuestImage
import app.aemu.update.AppUpdateManager
import app.aemu.update.ReleaseInfo
import app.aemu.update.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val ACTION_BOOT = "app.aemu.BOOT"

class MainActivity : ComponentActivity() {
    private val model: LibraryModel by viewModels()

    private var style = ""
    private fun styleOf(c: Context) = app.aemu.AppPrefs.language(c)

    override fun attachBaseContext(base: Context) {
        style = styleOf(base)
        super.attachBaseContext(app.aemu.AppPrefs.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askPermissions()
        handleView(intent)
        setContent { AemuTheme { Library(model) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleView(intent)
        if (styleOf(applicationContext) != style) recreate()
    }

    override fun onResume() {
        super.onResume()
        if (styleOf(applicationContext) != style) {
            recreate()
            return
        }
        model.refresh()
    }

    private fun handleView(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) {
            val u = i.data
            if (u != null) {
                model.import(u)
            } else {
                val p = i.getStringExtra("path") ?: i.getStringExtra("file")
                if (p != null) model.import(android.net.Uri.fromFile(java.io.File(p)))
            }
        }
        val importPath = i?.getStringExtra("import")
        if (importPath != null) {
            model.import(android.net.Uri.fromFile(java.io.File(importPath)))
        }
        if (i?.action == ACTION_BOOT) i.getStringExtra("id")?.let { VmActivity.start(this, it) }
    }

    private fun askPermissions() {
        val want = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) want += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 32) want += Manifest.permission.WRITE_EXTERNAL_STORAGE
        val need = want.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Library(model: LibraryModel) {
    val images by model.images.collectAsState()
    val imp by model.import.collectAsState()
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.import(it) } }
    var settingsFor by remember { mutableStateOf<GuestImage?>(null) }
    var deleteFor by remember { mutableStateOf<GuestImage?>(null) }
    var resetFor by remember { mutableStateOf<GuestImage?>(null) }
    var renameFor by remember { mutableStateOf<GuestImage?>(null) }
    var help by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(false) }
    var containerFrom by remember { mutableStateOf<GuestImage?>(null) }

    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val downloadErrStr = stringResource(R.string.update_download_error)

    LaunchedEffect(Unit) {
        if (app.aemu.AppPrefs.autoCheckUpdates(ctx)) {
            val now = System.currentTimeMillis()
            if (now - app.aemu.AppPrefs.lastUpdateCheck(ctx) > 3600_000L) {
                app.aemu.AppPrefs.setLastUpdateCheck(ctx, now)
                val res = AppUpdateManager.checkLatestRelease()
                res.getOrNull()?.let { rel ->
                    if (AppUpdateManager.isNewerVersion(rel.versionName, BuildConfig.VERSION_NAME)) {
                        updateState = UpdateState.Available(rel)
                    }
                }
            }
        }
    }

    fun startDownload(rel: ReleaseInfo) {
        updateState = UpdateState.Downloading(rel, 0f, 0L, rel.sizeBytes)
        downloadJob = scope.launch {
            try {
                val file = AppUpdateManager.downloadApk(ctx, rel) { progress, downloaded, total ->
                    updateState = UpdateState.Downloading(rel, progress, downloaded, total)
                }
                updateState = UpdateState.ReadyToInstall(rel, file)
                AppUpdateManager.installApk(ctx, file)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    updateState = UpdateState.Error(e.localizedMessage ?: downloadErrStr)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AemuTokens.ScreenGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(onClick = { help = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.help),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { ctx.startActivity(Intent(ctx, AppSettingsActivity::class.java)) }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                HomeFab(onClick = { library = true })
            },
        ) { pad ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentPadding = PaddingValues(
                    start = AemuTokens.SpaceLg,
                    end = AemuTokens.SpaceLg,
                    top = AemuTokens.SpaceSm,
                    bottom = AemuTokens.SpaceBottomFab
                ),
                verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceMd),
            ) {
                item { StorageAccessCard() }
                item {
                    AnimatedVisibility(
                        visible = imp.active || imp.error != null || imp.done != null,
                        enter = fadeIn(AemuTokens.tweenStandard()),
                        exit = fadeOut(AemuTokens.tweenFast())
                    ) {
                        ImportCard(imp, onCancel = model::cancelImport, onDismiss = model::dismissImport)
                    }
                }
                if (images.isEmpty() && !imp.active) {
                    item {
                        EmptyState(
                            onImport = { pick.launch(arrayOf("*/*")) },
                            onHelp = { help = true }
                        )
                    }
                }
                items(images, key = { it.id }) { img ->
                    MachineCard(
                        img = img,
                        baseName = images.firstOrNull { it.id == img.baseId }?.name,
                        onStart = { VmActivity.start(ctx, img.id) },
                        onSettings = { settingsFor = img },
                        onRename = { renameFor = img },
                        onNewMachine = { containerFrom = img },
                        onReset = { resetFor = img },
                        onDelete = { deleteFor = img },
                    )
                }
            }
        }
    }

    settingsFor?.let { img ->
        SettingsSheet(
            img = img,
            onDismiss = { settingsFor = null },
            onSave = { s ->
                model.updateSettings(img, s)
                settingsFor = null
            }
        )
    }

    resetFor?.let { img ->
        LayeredDialog(
            title = stringResource(R.string.reset_title, img.name),
            icon = Icons.Rounded.RestartAlt,
            onDismiss = { resetFor = null },
            secondaryText = stringResource(R.string.cancel),
            onSecondary = { resetFor = null },
            primaryText = stringResource(R.string.reset_data),
            onPrimary = {
                resetFor = null
                if (model.vmRunning()) {
                    android.widget.Toast.makeText(ctx, R.string.reset_running, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    model.resetData(img)
                }
            }
        ) {
            Text(
                text = stringResource(R.string.reset_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    deleteFor?.let { img ->
        LayeredDialog(
            title = stringResource(R.string.delete_title, img.name),
            icon = Icons.Rounded.Delete,
            onDismiss = { deleteFor = null },
            secondaryText = stringResource(R.string.cancel),
            onSecondary = { deleteFor = null },
            primaryText = stringResource(R.string.delete),
            onPrimary = {
                model.delete(img)
                deleteFor = null
            }
        ) {
            Text(
                text = stringResource(R.string.delete_text, Formatter.formatShortFileSize(ctx, img.sizeBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    renameFor?.let { img ->
        var name by remember(img) { mutableStateOf(img.name) }
        LayeredDialog(
            title = stringResource(R.string.rename),
            onDismiss = { renameFor = null },
            secondaryText = stringResource(R.string.cancel),
            onSecondary = { renameFor = null },
            primaryText = stringResource(R.string.save),
            onPrimary = {
                model.rename(img, name.trim().ifEmpty { img.name })
                renameFor = null
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (help) HelpDialog(onDismiss = { help = false })

    if (library) {
        LibrarySheet(
            firmwares = images.filter { it.baseId.isEmpty() },
            onDismiss = { library = false },
            onImportFile = {
                library = false
                pick.launch(arrayOf("*/*"))
            },
            onImportFolder = { f ->
                library = false
                model.import(android.net.Uri.fromFile(f))
            },
            onNewMachine = {
                library = false
                containerFrom = it
            }
        )
    }

    containerFrom?.let { src ->
        ContainerDialog(
            src = src,
            images = images,
            onDismiss = { containerFrom = null },
            onCreate = { from, name, copy, settings ->
                containerFrom = null
                model.clone(from, name, copy, settings)
            }
        )
    }

    UpdateDialog(
        state = updateState,
        onDismiss = { updateState = UpdateState.Idle },
        onStartDownload = ::startDownload,
        onCancelDownload = {
            downloadJob?.cancel()
            updateState = UpdateState.Idle
        },
        onInstall = { file -> AppUpdateManager.installApk(ctx, file) },
        onOpenUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
    )
}

@Composable
private fun ContainerDialog(
    src: GuestImage,
    images: List<GuestImage>,
    onDismiss: () -> Unit,
    onCreate: (GuestImage, String, Boolean, app.aemu.core.VmSettings) -> Unit
) {
    val from by remember { mutableStateOf(src) }
    var name by remember(from) {
        mutableStateOf("${from.name} (${images.count { it.baseId == from.id || it.baseId == from.baseId.ifEmpty { "-" } } + 2})")
    }
    var copy by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(1) }
    var settings by remember(from) { mutableStateOf(from.settings) }
    fun applyPreset(p: Int) {
        preset = p
        settings = when (p) {
            0 -> settings.copy(gpu = true, hwui = true, lowRam = false)
            1 -> settings.copy(gpu = true, hwui = true, lowRam = false)
            else -> settings.copy(gpu = false, hwui = false, lowRam = true, width = 720, height = 1280)
        }
    }

    LayeredDialog(
        title = stringResource(R.string.vmos_add_title),
        icon = Icons.Rounded.ContentCopy,
        onDismiss = onDismiss,
        secondaryText = stringResource(R.string.cancel),
        onSecondary = onDismiss,
        primaryText = stringResource(R.string.container_create),
        onPrimary = { onCreate(from, name.trim().ifEmpty { from.name }, copy, settings) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceMd)
        ) {
            Surface(
                shape = AemuTokens.ShapeCover,
                color = AemuTokens.SurfaceCardTranslucent,
                border = AemuTokens.CardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(AemuTokens.SpaceMd)) {
                    Text(
                        text = stringResource(R.string.section_systems),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${from.name} · ${from.displayVersion}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = from.skin,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.vmos_preset_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceSm)
            ) {
                FilterChip(
                    selected = preset == 0,
                    onClick = { applyPreset(0) },
                    label = { Text(stringResource(R.string.vmos_preset_perf), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = preset == 1,
                    onClick = { applyPreset(1) },
                    label = { Text(stringResource(R.string.vmos_preset_balanced), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = preset == 2,
                    onClick = { applyPreset(2) },
                    label = { Text(stringResource(R.string.vmos_preset_lite), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AemuTokens.ShapeCover)
                    .clickable { copy = !copy },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(copy, { copy = it })
                Text(
                    text = stringResource(R.string.container_copy_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.container_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageAccessCard() {
    if (Build.VERSION.SDK_INT < 30) return
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
    val life = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = android.os.Environment.isExternalStorageManager()
            }
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    if (granted) return
    Surface(
        shape = AemuTokens.ShapeCard,
        color = AemuTokens.SurfaceCardTranslucent,
        border = AemuTokens.CardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(AemuTokens.SpaceXl)) {
            Text(
                text = stringResource(R.string.storage_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AemuTokens.SpaceXs))
            Text(
                text = stringResource(R.string.storage_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AemuTokens.SpaceMd))
            Button(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }.onFailure {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                },
                shape = AemuTokens.ShapeButton,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.height(AemuTokens.TouchMin)
            ) {
                Text(stringResource(R.string.allow))
            }
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, onHelp: () -> Unit) {
    Surface(
        shape = AemuTokens.ShapeCard,
        color = AemuTokens.SurfaceCardTranslucent,
        border = AemuTokens.CardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AemuTokens.SpaceXxl),
            verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceMd)
        ) {
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AemuTokens.SpaceXs))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onImport,
                    shape = AemuTokens.ShapeButton,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(AemuTokens.TouchMin)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(AemuTokens.IconSm)
                    )
                    Spacer(Modifier.width(AemuTokens.SpaceSm))
                    Text(stringResource(R.string.add_import), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onHelp,
                    shape = AemuTokens.ShapeButton,
                    border = AemuTokens.ButtonSecondaryBorder,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AemuTokens.SurfaceInteractive,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.height(AemuTokens.TouchMin)
                ) {
                    Text(stringResource(R.string.empty_help), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ImportCard(s: ImportState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = AemuTokens.ShapeCard,
        color = AemuTokens.SurfaceCardTranslucent,
        border = AemuTokens.CardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(AemuTokens.SpaceXl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.active) {
                    LoadingIndicator(Modifier.size(AemuTokens.NavBarCompactSize))
                } else {
                    Icon(
                        imageVector = if (s.error != null) Icons.Rounded.Warning else Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(AemuTokens.SpaceMd))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            s.error != null -> stringResource(R.string.import_failed)
                            s.active -> stringResource(R.string.import_running)
                            else -> stringResource(R.string.import_done)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = s.file,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(AemuTokens.SpaceMd))
            when {
                s.error != null -> Text(
                    text = s.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                s.active -> {
                    if (s.progress in 0f..1f) {
                        LinearProgressIndicator(
                            progress = { s.progress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = AemuTokens.SurfaceInteractive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AemuTokens.ProgressHeight)
                                .clip(AemuTokens.ShapeSmall)
                        )
                    } else {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = AemuTokens.SurfaceInteractive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AemuTokens.ProgressHeight)
                                .clip(AemuTokens.ShapeSmall)
                        )
                    }
                    Spacer(Modifier.height(AemuTokens.SpaceSm))
                    Text(
                        text = s.step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                s.done != null -> {
                    Text(
                        text = "${s.done.name} · ${s.done.displayVersion} · ${s.done.skin}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    s.done.warnings.forEach {
                        Text(
                            text = "- $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(AemuTokens.SpaceMd))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = if (s.active) onCancel else onDismiss,
                    shape = AemuTokens.ShapeButton,
                    border = AemuTokens.ButtonSecondaryBorder
                ) {
                    Text(stringResource(if (s.active) R.string.cancel else R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    LayeredDialog(
        title = stringResource(R.string.help_title),
        icon = Icons.Rounded.Info,
        onDismiss = onDismiss,
        primaryText = stringResource(R.string.ok),
        onPrimary = onDismiss
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.help_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
