package app.aemu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import app.aemu.R
import app.aemu.update.AppUpdateManager
import app.aemu.update.ReleaseInfo
import app.aemu.update.UpdateState
import java.io.File

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDismiss: () -> Unit,
    onStartDownload: (ReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    when (state) {
        is UpdateState.Idle -> {}

        is UpdateState.Checking -> {
            LayeredDialog(
                title = stringResource(R.string.as_check_updates),
                icon = Icons.Rounded.Sync,
                onDismiss = onDismiss,
                secondaryText = stringResource(R.string.cancel),
                onSecondary = onDismiss,
            ) {
                Text(
                    text = stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AemuTokens.SpaceLg))
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = AemuTokens.SurfaceInteractive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AemuTokens.ProgressHeight)
                        .clip(AemuTokens.ShapeSmall)
                )
            }
        }

        is UpdateState.Available -> {
            val rel = state.release
            LayeredDialog(
                title = stringResource(R.string.update_available_title),
                icon = Icons.Rounded.SystemUpdate,
                onDismiss = onDismiss,
                secondaryText = stringResource(R.string.update_later),
                onSecondary = onDismiss,
                primaryText = stringResource(R.string.update_btn),
                onPrimary = { onStartDownload(rel) }
            ) {
                Text(
                    text = stringResource(R.string.update_available_msg, rel.tagName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (rel.sizeBytes > 0) {
                    Spacer(Modifier.height(AemuTokens.SpaceXs))
                    Text(
                        text = stringResource(R.string.update_size, AppUpdateManager.formatBytes(rel.sizeBytes)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (rel.body.isNotBlank()) {
                    Spacer(Modifier.height(AemuTokens.SpaceMd))
                    Surface(
                        shape = AemuTokens.ShapeCover,
                        color = AemuTokens.SurfaceCardTranslucent,
                        border = AemuTokens.CardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = AemuTokens.SheetMaxListHeight)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(AemuTokens.SpaceMd)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = rel.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        is UpdateState.Downloading -> {
            LayeredDialog(
                title = stringResource(R.string.update_downloading),
                icon = Icons.Rounded.Download,
                onDismiss = onCancelDownload,
                secondaryText = stringResource(R.string.cancel),
                onSecondary = onCancelDownload,
            ) {
                LinearProgressIndicator(
                    progress = { if (state.totalBytes > 0) state.progress else 0f },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = AemuTokens.SurfaceInteractive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AemuTokens.ProgressHeight)
                        .clip(AemuTokens.ShapeSmall)
                )
                Spacer(Modifier.height(AemuTokens.SpaceSm))
                val percent = (state.progress * 100).toInt()
                val downloadedStr = AppUpdateManager.formatBytes(state.downloadedBytes)
                val totalStr = if (state.totalBytes > 0) AppUpdateManager.formatBytes(state.totalBytes) else ""
                Text(
                    text = if (totalStr.isNotEmpty()) "$percent% ($downloadedStr / $totalStr)" else "$percent% ($downloadedStr)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is UpdateState.ReadyToInstall -> {
            LayeredDialog(
                title = stringResource(R.string.update_ready_title),
                icon = Icons.Rounded.CheckCircle,
                onDismiss = onDismiss,
                secondaryText = stringResource(R.string.close),
                onSecondary = onDismiss,
                primaryText = stringResource(R.string.update_install),
                onPrimary = { onInstall(state.file) }
            ) {
                Text(
                    text = stringResource(R.string.update_ready_msg, state.release.tagName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is UpdateState.UpToDate -> {
            LayeredDialog(
                title = stringResource(R.string.update_up_to_date_title),
                icon = Icons.Rounded.CheckCircle,
                onDismiss = onDismiss,
                secondaryText = stringResource(R.string.update_open_github),
                onSecondary = { onOpenUrl(AppUpdateManager.GITHUB_REPO_URL) },
                primaryText = stringResource(R.string.ok),
                onPrimary = onDismiss
            ) {
                Text(
                    text = stringResource(R.string.update_latest, state.currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.latestTag.isNotBlank()) {
                    Spacer(Modifier.height(AemuTokens.SpaceXs))
                    Text(
                        text = stringResource(R.string.update_latest_github, state.latestTag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is UpdateState.Error -> {
            LayeredDialog(
                title = stringResource(R.string.update_error_title),
                icon = Icons.Rounded.ErrorOutline,
                onDismiss = onDismiss,
                primaryText = stringResource(R.string.ok),
                onPrimary = onDismiss
            ) {
                Text(
                    text = stringResource(R.string.update_error, state.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun LayeredDialog(
    title: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryText: String? = null,
    onPrimary: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AemuTokens.ShapeDialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = AemuTokens.CardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .background(AemuTokens.SheetTopGlow)
                    .padding(AemuTokens.SpaceXxl)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(AemuTokens.NavBarCompactSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AemuTokens.IconSm)
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(AemuTokens.TouchMin)
                                .clip(AemuTokens.ShapeCover)
                                .background(AemuTokens.SurfaceIconBox)
                                .border(AemuTokens.BorderWidth, AemuTokens.BorderSubtle, AemuTokens.ShapeCover),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(AemuTokens.IconLg)
                            )
                        }
                        Spacer(Modifier.height(AemuTokens.SpaceMd))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AemuTokens.SpaceXxl)
                    )
                    Spacer(Modifier.height(AemuTokens.SpaceMd))
                    content()
                    if (secondaryText != null || primaryText != null) {
                        Spacer(Modifier.height(AemuTokens.SpaceXl))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AemuTokens.SpaceMd)
                        ) {
                            if (secondaryText != null && onSecondary != null) {
                                OutlinedButton(
                                    onClick = onSecondary,
                                    shape = AemuTokens.ShapeButton,
                                    border = AemuTokens.ButtonSecondaryBorder,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = AemuTokens.SurfaceInteractive,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(AemuTokens.TouchMin)
                                ) {
                                    Text(secondaryText, maxLines = 1)
                                }
                            }
                            if (primaryText != null && onPrimary != null) {
                                Button(
                                    onClick = onPrimary,
                                    shape = AemuTokens.ShapeButton,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(AemuTokens.TouchMin)
                                ) {
                                    Text(primaryText, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
