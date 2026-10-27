package app.aemu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import app.aemu.R
import app.aemu.core.GuestImage
import app.aemu.core.VmSettings

private data class Res(val label: String, val w: Int, val h: Int, val dpi: Int)

private val RESOLUTIONS = listOf(
    Res("480×800", 480, 800, 240),
    Res("540×960", 540, 960, 240),
    Res("720×1280", 720, 1280, 320),
)

private val RAM_STEPS = listOf(0, 512, 768, 1024, 1536, 2048, 3072, 4096)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(img: GuestImage, onDismiss: () -> Unit, onSave: (VmSettings) -> Unit) {
    var s by remember { mutableStateOf(img.settings) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AemuTokens.SpaceXl, vertical = AemuTokens.SpaceXl)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = img.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AemuTokens.TouchMin)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsGroup(title = stringResource(R.string.vs_screen)) {
                    Column(
                        modifier = Modifier.padding(AemuTokens.SpaceLg),
                        verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceMd)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RESOLUTIONS.forEachIndexed { i, r ->
                                ToggleButton(
                                    checked = s.width == r.w && s.height == r.h,
                                    onCheckedChange = { s = s.copy(width = r.w, height = r.h, density = r.dpi) },
                                    shapes = when (i) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        RESOLUTIONS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { role = Role.RadioButton },
                                ) { Text(r.label, maxLines = 1) }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceSm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NumField(stringResource(R.string.vs_width), s.width, Modifier.weight(1f)) {
                                s = s.copy(width = it.coerceIn(240, 2160) and 0x7ffffffe)
                            }
                            NumField(stringResource(R.string.vs_height), s.height, Modifier.weight(1f)) {
                                s = s.copy(height = it.coerceIn(320, 3840) and 0x7ffffffe)
                            }
                            NumField(stringResource(R.string.vs_dpi), s.density, Modifier.weight(1f)) {
                                s = s.copy(density = it.coerceIn(96, 640))
                            }
                        }
                        Text(
                            text = stringResource(R.string.vs_screen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsGroup(title = stringResource(R.string.vs_perf)) {
                    Toggle(stringResource(R.string.vs_gpu), stringResource(R.string.vs_gpu_sub), s.gpu) { s = s.copy(gpu = it) }
                    GroupDivider()
                    Toggle(stringResource(R.string.vs_hwui), stringResource(R.string.vs_hwui_sub), s.hwui) { s = s.copy(hwui = it) }
                    GroupDivider()
                    Toggle(stringResource(R.string.vs_fulldex), stringResource(R.string.vs_fulldex_sub), s.fullDexopt) { s = s.copy(fullDexopt = it) }
                    GroupDivider()
                    val ramIdx = RAM_STEPS.indexOf(s.ramMb).coerceAtLeast(0)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AemuTokens.SpaceLg, vertical = AemuTokens.SpaceMd)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.vs_ram),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (s.ramMb == 0) stringResource(R.string.vs_ram_auto) else "${s.ramMb} MB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = ramIdx.toFloat(),
                            onValueChange = { s = s.copy(ramMb = RAM_STEPS[it.toInt()]) },
                            valueRange = 0f..RAM_STEPS.lastIndex.toFloat(),
                            steps = RAM_STEPS.size - 2
                        )
                    }
                    GroupDivider()
                    Toggle(stringResource(R.string.vs_lowram), stringResource(R.string.vs_lowram_sub), s.lowRam) { s = s.copy(lowRam = it) }
                    GroupDivider()
                    Toggle(stringResource(R.string.vs_proxy), stringResource(R.string.vs_proxy_sub), s.netProxy) { s = s.copy(netProxy = it) }
                }

                SettingsGroup(title = stringResource(R.string.vs_radio_title)) {
                    Toggle(stringResource(R.string.vs_radio), stringResource(R.string.vs_radio_sub), s.radio) { s = s.copy(radio = it) }
                    if (s.radio) {
                        GroupDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceSm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AemuTokens.SpaceLg, vertical = AemuTokens.SpaceMd)
                        ) {
                            OutlinedTextField(
                                value = s.imei,
                                onValueChange = { v -> s = s.copy(imei = v.filter(Char::isDigit).take(15)) },
                                label = { Text(stringResource(R.string.imei_label)) },
                                placeholder = { Text(VmSettings.DEFAULT_IMEI) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { s = s.copy(imei = VmSettings.randomImei()) },
                                shape = AemuTokens.ShapeButton,
                                border = AemuTokens.ButtonSecondaryBorder
                            ) {
                                Text(stringResource(R.string.vs_random))
                            }
                        }
                    }
                }

                SettingsGroup(title = stringResource(R.string.vs_controls)) {
                    Toggle(stringResource(R.string.vs_nav), stringResource(R.string.vs_nav_sub), s.showNavBar) { s = s.copy(showNavBar = it) }
                    if (s.showNavBar) {
                        GroupDivider()
                        NavBarSetup(s) { s = it }
                    }
                    GroupDivider()
                    Toggle(stringResource(R.string.vs_awake), stringResource(R.string.vs_awake_sub), s.keepScreenOn) { s = s.copy(keepScreenOn = it) }
                    GroupDivider()
                    Toggle(stringResource(R.string.as_led), stringResource(R.string.as_led_sub), s.showLed) { s = s.copy(showLed = it) }
                    GroupDivider()
                    Toggle(stringResource(R.string.vs_single), stringResource(R.string.vs_single_sub), s.mtMode == 4) {
                        s = s.copy(mtMode = if (it) 4 else 0)
                    }
                }

                Button(
                    onClick = { onSave(s) },
                    shape = AemuTokens.ShapeButton,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AemuTokens.RowHeight)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceSm)) {
        Column(modifier = Modifier.padding(horizontal = AemuTokens.SpaceSm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            shape = AemuTokens.ShapeCard,
            color = AemuTokens.SurfaceCardTranslucent,
            border = AemuTokens.CardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
internal fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = AemuTokens.SpaceLg),
        color = AemuTokens.DividerSubtle
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NavBarSetup(s: VmSettings, set: (VmSettings) -> Unit) {
    val keys = s.navKeys.split(',').filter { it in VmSettings.NAV_ALL }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AemuTokens.SpaceLg, vertical = AemuTokens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceSm)
    ) {
        for (k in keys + VmSettings.NAV_ALL.filter { it !in keys }) {
            val on = k in keys
            FilterChip(
                selected = on,
                label = { Text(navKeyLabel(k)) },
                onClick = { set(s.copy(navKeys = (if (on) keys - k else keys + k).joinToString(","))) }
            )
        }
    }
    GroupDivider()
    Toggle(stringResource(R.string.vs_nav_compact), stringResource(R.string.vs_nav_compact_sub), s.navSize == 0) {
        set(s.copy(navSize = if (it) 0 else 1))
    }
}

@Composable
internal fun navKeyLabel(k: String) = stringResource(
    when (k) {
        "back" -> R.string.back
        "home" -> R.string.home
        "recents" -> R.string.recents
        "menu" -> R.string.menu
        "search" -> R.string.nav_search
        "volup" -> R.string.vol_up
        "voldown" -> R.string.vol_down
        "power" -> R.string.power
        else -> R.string.nav_dpad
    }
)

@Composable
internal fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                set(!on)
            }
            .padding(horizontal = AemuTokens.SpaceLg, vertical = AemuTokens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(AemuTokens.SpaceXs))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = on,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                set(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = AemuTokens.SurfaceInteractive,
                uncheckedBorderColor = AemuTokens.BorderInteractive
            )
        )
    }
}

@Composable
private fun NumField(label: String, value: Int, modifier: Modifier, set: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.let(set)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
