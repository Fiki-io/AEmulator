package app.aemu.ui

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.aemu.R
import app.aemu.core.GuestImage

@Composable
fun MachineCard(
    img: GuestImage,
    baseName: String?,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onRename: () -> Unit,
    onNewMachine: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) AemuTokens.PressScale else 1f,
        animationSpec = AemuTokens.tweenFast(),
        label = "card_press"
    )
    var menu by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onStart()
        },
        interactionSource = press,
        shape = AemuTokens.ShapeCard,
        color = AemuTokens.SurfaceCardTranslucent,
        border = AemuTokens.CardBorder,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AemuTokens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
        ) {
            Box(
                modifier = Modifier
                    .size(AemuTokens.CoverSize)
                    .clip(AemuTokens.ShapeCover)
                    .background(AemuTokens.SurfaceIconBox)
                    .border(AemuTokens.BorderWidth, AemuTokens.BorderSubtle, AemuTokens.ShapeCover),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = img.release.substringBefore(' ').take(4),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.api_level, img.api),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = img.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AemuTokens.SpaceXs))
                val meta = listOfNotNull(
                    img.skin.takeIf { it.isNotBlank() },
                    "${img.settings.width}×${img.settings.height}",
                    Formatter.formatShortFileSize(ctx, img.sizeBytes),
                    if (img.lastBootMs > 0) stringResource(R.string.last_boot, (img.lastBootMs / 1000).toInt()) else null,
                ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (baseName != null) {
                    Text(
                        text = stringResource(R.string.container_of, baseName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart()
                },
                shape = AemuTokens.ShapeButton,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.height(AemuTokens.TouchMin)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.start),
                    modifier = Modifier.size(AemuTokens.IconSm)
                )
                Spacer(Modifier.width(AemuTokens.SpaceXs))
                Text(stringResource(R.string.start), maxLines = 1)
            }

            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(AemuTokens.TouchMin)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = AemuTokens.ShapeCover,
                    border = AemuTokens.CardBorder
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = { menu = false; onSettings() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_machine)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onNewMachine() }
                    )
                    HorizontalDivider(color = AemuTokens.DividerSubtle)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reset_data)) },
                        leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                        onClick = { menu = false; onReset() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeFab(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    FloatingActionButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = AemuTokens.ShapePill,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.add_firmware),
            modifier = Modifier.size(AemuTokens.IconLg)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(
    firmwares: List<GuestImage>,
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: (java.io.File) -> Unit,
    onNewMachine: (GuestImage) -> Unit,
) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf(emptyList<java.io.File>()) }
    DisposableEffect(Unit) {
        val t = Thread { files = app.aemu.core.FirmwareFolder.list() }.apply { start() }
        onDispose { t.interrupt() }
    }
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
                    .padding(horizontal = AemuTokens.SpaceXl, vertical = AemuTokens.SpaceXl)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vmos_add_title),
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

                Surface(
                    onClick = onImportFile,
                    shape = AemuTokens.ShapeCard,
                    color = AemuTokens.SurfaceInteractive,
                    border = AemuTokens.ButtonSecondaryBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(AemuTokens.SpaceLg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(AemuTokens.TouchMin)
                                .clip(AemuTokens.ShapeCover)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(AemuTokens.IconLg)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.add_import),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(AemuTokens.SpaceXs))
                            Text(
                                text = stringResource(R.string.add_import_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsGroup(title = stringResource(R.string.vmos_tab_roms)) {
                    if (firmwares.isEmpty()) {
                        Text(
                            text = stringResource(R.string.lib_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(AemuTokens.SpaceLg)
                        )
                    } else {
                        firmwares.forEachIndexed { idx, fw ->
                            if (idx > 0) GroupDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNewMachine(fw) }
                                    .padding(AemuTokens.SpaceLg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(AemuTokens.TouchMin)
                                        .clip(AemuTokens.ShapeCover)
                                        .background(AemuTokens.SurfaceIconBox)
                                        .border(AemuTokens.BorderWidth, AemuTokens.BorderSubtle, AemuTokens.ShapeCover),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fw.release.substringBefore(' ').take(4),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fw.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${fw.displayVersion} · ${fw.skin}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onNewMachine(fw) },
                                    shape = AemuTokens.ShapeButton,
                                    border = AemuTokens.ButtonSecondaryBorder
                                ) {
                                    Text(stringResource(R.string.container_create), maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (files.isNotEmpty()) {
                    SettingsGroup(title = stringResource(R.string.fw_folder_title)) {
                        files.take(12).forEachIndexed { idx, f ->
                            if (idx > 0) GroupDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onImportFolder(f) }
                                    .padding(AemuTokens.SpaceLg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceLg)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(AemuTokens.IconLg)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = f.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = Formatter.formatShortFileSize(ctx, f.length()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onImportFolder(f) },
                                    shape = AemuTokens.ShapeButton,
                                    border = AemuTokens.ButtonSecondaryBorder
                                ) {
                                    Text(stringResource(R.string.fw_folder_import), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
