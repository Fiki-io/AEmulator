package app.aemu.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.aemu.AppPrefs
import app.aemu.BuildConfig
import app.aemu.R
import app.aemu.update.AppUpdateManager
import app.aemu.update.ReleaseInfo
import app.aemu.update.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object Links {
    const val GITHUB = "https://github.com/Fiki-io/AEmulator"
    const val AUTHOR = "https://github.com/Fiki-io"
    const val GITHUB_DISPLAY = "github.com/Fiki-io/AEmulator"
    const val AUTHOR_DISPLAY = "Fiki-io"
}

class AppSettingsActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppPrefs.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AemuTheme { AppSettings(onBack = ::finish, onRestyle = ::restart) } }
    }

    private fun restart() {
        recreate()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettings(onBack: () -> Unit, onRestyle: () -> Unit) {
    val ctx = LocalContext.current
    var langSheet by remember { mutableStateOf(false) }
    var themeSheet by remember { mutableStateOf(false) }
    var defaults by remember { mutableStateOf(AppPrefs.defaults(ctx)) }
    var theme by remember { mutableStateOf(AppPrefs.theme(ctx)) }
    var dynamic by remember { mutableStateOf(AppPrefs.dynamicColor(ctx)) }
    val scope = rememberCoroutineScope()
    var autoUpdates by remember { mutableStateOf(AppPrefs.autoCheckUpdates(ctx)) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val networkErrStr = stringResource(R.string.update_network_error)
    val downloadErrStr = stringResource(R.string.update_download_error)

    fun checkForUpdates() {
        updateState = UpdateState.Checking
        scope.launch {
            val res = AppUpdateManager.checkLatestRelease()
            res.fold(
                onSuccess = { rel ->
                    if (rel != null) {
                        if (AppUpdateManager.isNewerVersion(rel.versionName, BuildConfig.VERSION_NAME)) {
                            updateState = UpdateState.Available(rel)
                        } else {
                            updateState = UpdateState.UpToDate(BuildConfig.VERSION_NAME, rel.tagName)
                        }
                    } else {
                        updateState = UpdateState.UpToDate(BuildConfig.VERSION_NAME, "")
                    }
                },
                onFailure = { err ->
                    updateState = UpdateState.Error(err.localizedMessage ?: networkErrStr)
                }
            )
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

    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    val langTag = AppPrefs.language(ctx)
    val langName = AppPrefs.LANGUAGES.firstOrNull { it.first == langTag }?.second?.ifEmpty { null }
        ?: stringResource(R.string.as_lang_system)

    val themeOptions = listOf(
        AppPrefs.THEME_SYSTEM to stringResource(R.string.as_theme_system),
        AppPrefs.THEME_LIGHT to stringResource(R.string.as_theme_light),
        AppPrefs.THEME_DARK to stringResource(R.string.as_theme_dark),
    )
    val currentThemeName = themeOptions.firstOrNull { it.first == theme }?.second ?: stringResource(R.string.as_theme_dark)

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
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
        ) { pad ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AemuTokens.SpaceLg,
                    end = AemuTokens.SpaceLg,
                    top = pad.calculateTopPadding() + AemuTokens.SpaceSm,
                    bottom = pad.calculateBottomPadding() + AemuTokens.SpaceXxl
                ),
                verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceXl),
            ) {
                item { AboutCard() }

                item {
                    SettingsGroup(title = stringResource(R.string.as_updates)) {
                        Toggle(
                            stringResource(R.string.as_auto_updates),
                            stringResource(R.string.as_auto_updates_sub),
                            autoUpdates
                        ) {
                            autoUpdates = it
                            AppPrefs.setAutoCheckUpdates(ctx, it)
                        }
                        GroupDivider()
                        NavRow(
                            icon = Icons.Rounded.SystemUpdate,
                            title = stringResource(R.string.as_check_updates),
                            value = BuildConfig.VERSION_NAME
                        ) {
                            checkForUpdates()
                        }
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.as_appearance)) {
                        NavRow(
                            icon = Icons.Rounded.Translate,
                            title = stringResource(R.string.as_language),
                            value = langName
                        ) {
                            langSheet = true
                        }
                        GroupDivider()
                        NavRow(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.as_theme),
                            value = currentThemeName
                        ) {
                            themeSheet = true
                        }
                        if (Build.VERSION.SDK_INT >= 31) {
                            GroupDivider()
                            Toggle(
                                stringResource(R.string.as_dynamic),
                                stringResource(R.string.as_dynamic_sub),
                                dynamic
                            ) {
                                dynamic = it
                                AppPrefs.setDynamicColor(ctx, it)
                            }
                        }
                    }
                }

                item {
                    SettingsGroup(
                        title = stringResource(R.string.as_defaults),
                        subtitle = stringResource(R.string.as_defaults_sub)
                    ) {
                        fun set(s: app.aemu.core.VmSettings) {
                            defaults = s
                            AppPrefs.setDefaults(ctx, s)
                        }
                        Toggle(stringResource(R.string.vs_gpu), stringResource(R.string.vs_gpu_sub), defaults.gpu) {
                            set(defaults.copy(gpu = it))
                        }
                        GroupDivider()
                        Toggle(stringResource(R.string.vs_proxy), stringResource(R.string.vs_proxy_sub), defaults.netProxy) {
                            set(defaults.copy(netProxy = it))
                        }
                        GroupDivider()
                        Toggle(stringResource(R.string.vs_nav), stringResource(R.string.vs_nav_sub), defaults.showNavBar) {
                            set(defaults.copy(showNavBar = it))
                        }
                        GroupDivider()
                        Toggle(stringResource(R.string.vs_awake), stringResource(R.string.vs_awake_sub), defaults.keepScreenOn) {
                            set(defaults.copy(keepScreenOn = it))
                        }
                        GroupDivider()
                        Toggle(stringResource(R.string.as_led), stringResource(R.string.as_led_sub), defaults.showLed) {
                            set(defaults.copy(showLed = it))
                        }
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.as_links)) {
                        NavRow(
                            icon = Icons.Rounded.Person,
                            title = stringResource(R.string.as_author),
                            value = Links.AUTHOR_DISPLAY
                        ) {
                            open(Links.AUTHOR)
                        }
                        GroupDivider()
                        NavRow(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.as_github),
                            value = Links.GITHUB_DISPLAY
                        ) {
                            open(Links.GITHUB)
                        }
                    }
                }
            }
        }
    }

    if (langSheet) {
        SingleChoiceBottomSheet(
            title = stringResource(R.string.as_language),
            options = AppPrefs.LANGUAGES.map { (tag, name) ->
                tag to name.ifEmpty { stringResource(R.string.as_lang_system) }
            },
            selectedKey = langTag,
            onSelect = { tag ->
                langSheet = false
                if (tag != langTag) {
                    AppPrefs.setLanguage(ctx, tag)
                    onRestyle()
                }
            },
            onDismiss = { langSheet = false }
        )
    }

    if (themeSheet) {
        SingleChoiceBottomSheet(
            title = stringResource(R.string.as_theme),
            options = themeOptions,
            selectedKey = theme,
            onSelect = { selected ->
                themeSheet = false
                if (selected != theme) {
                    theme = selected
                    AppPrefs.setTheme(ctx, selected)
                }
            },
            onDismiss = { themeSheet = false }
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
        onOpenUrl = { url -> open(url) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SingleChoiceBottomSheet(
    title: String,
    options: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AemuTokens.ShapeSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimColor = AemuTokens.ScrimDark,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AemuTokens.SheetTopGlow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = AemuTokens.SpaceXl)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AemuTokens.SpaceXl, vertical = AemuTokens.SpaceLg)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(AemuTokens.NavBarCompactSize)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LazyColumn {
                    itemsIndexed(options) { idx, (key, label) ->
                        if (idx > 0) GroupDivider()
                        val selected = key == selectedKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AemuTokens.RowHeight)
                                .clickable { onSelect(key) }
                                .padding(horizontal = AemuTokens.SpaceXl),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(AemuTokens.IconMd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Surface(
        shape = AemuTokens.ShapeCard,
        color = AemuTokens.SurfaceCardTranslucent,
        border = AemuTokens.CardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AemuTokens.SpaceXl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(AemuTokens.LogoSize)
                    .clip(AemuTokens.ShapeCover)
            )
            Spacer(Modifier.width(AemuTokens.SpaceLg))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceSm)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.as_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(AemuTokens.SpaceXs))
                Text(
                    text = stringResource(R.string.as_about),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AemuTokens.RowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = AemuTokens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AemuTokens.IconLg)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AemuTokens.IconMd)
        )
    }
}
