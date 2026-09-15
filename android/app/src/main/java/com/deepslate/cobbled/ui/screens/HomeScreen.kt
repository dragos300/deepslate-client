package com.deepslate.cobbled.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepslate.cobbled.core.ModLoader
import com.deepslate.cobbled.core.ServerEntry
import com.deepslate.cobbled.core.VersionGroup
import com.deepslate.cobbled.data.LauncherUiState
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.BodyFont
import com.deepslate.cobbled.ui.theme.DisplayFont
import com.deepslate.cobbled.ui.theme.PlayBot
import com.deepslate.cobbled.ui.theme.PlayInk
import com.deepslate.cobbled.ui.theme.PlayTop
import com.deepslate.cobbled.ui.theme.SlateFaint
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateLine
import com.deepslate.cobbled.ui.theme.SlateLineStrong
import com.deepslate.cobbled.ui.theme.SlateMuted
import com.deepslate.cobbled.ui.theme.SlateText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: LauncherUiState,
    onPlay: () -> Unit,
    onMicrosoftLogin: () -> Unit,
    onSignOut: () -> Unit,
    onSelectVersion: (ModLoader, String) -> Unit,
    onLoadVersions: () -> Unit,
    onSaveServer: (String, String, String) -> Unit,
    onRemoveServer: (ServerEntry) -> Unit,
) {
    var versionsOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var serverName by remember { mutableStateOf("") }
    var serverAddress by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("25565") }
    val account = state.account
    val welcome = when {
        account != null -> "Welcome back, ${account.name} — signed in and ready to launch."
        else -> "Sign in with Microsoft to download Java Edition onto this phone."
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = "Cobbled",
            color = SlateInk,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 46.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1.5).sp,
        )
        Text(
            text = "DEEPSLATE CLIENT",
            color = Accent,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 6.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Text(welcome, color = SlateMuted, fontFamily = BodyFont, modifier = Modifier.fillMaxWidth(0.92f))
        Spacer(Modifier.height(22.dp))

        VersionChip(
            label = state.versionLabel,
            enabled = !state.preparing,
            onClick = {
                onLoadVersions()
                versionsOpen = true
            },
        )
        Spacer(Modifier.height(12.dp))
        PlayButton(
            preparing = state.preparing,
            status = state.progress?.status ?: "Preparing…",
            progress = state.progress?.fraction ?: 0f,
            signedIn = account != null,
            onClick = onPlay,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(icon = { Icon(Icons.Filled.Memory, null, tint = Accent, modifier = Modifier.size(16.dp)) }, text = "RAM ${state.settings.ramGb} GB")
            Pill(
                text = if (account != null) account.name else "Not signed in",
                onClick = { accountOpen = true },
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("SERVERS", color = SlateMuted, fontFamily = BodyFont, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Save Java addresses for later. Join still needs the on-device runtime.", color = SlateFaint, fontFamily = BodyFont)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DarkField(value = serverName, onValueChange = { serverName = it }, hint = "Name", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkField(value = serverAddress, onValueChange = { serverAddress = it }, hint = "Address", modifier = Modifier.weight(1.4f))
            DarkField(
                value = serverPort,
                onValueChange = { serverPort = it.filter { ch -> ch.isDigit() }.take(5) },
                hint = "Port",
                modifier = Modifier.weight(0.7f),
                keyboard = KeyboardType.Number,
            )
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Save server") {
            onSaveServer(serverName, serverAddress, serverPort)
            serverName = ""
            serverAddress = ""
            serverPort = "25565"
        }
        Spacer(Modifier.height(12.dp))
        if (state.settings.servers.isEmpty()) {
            Text("No saved servers yet.", color = SlateFaint, fontFamily = BodyFont)
        } else {
            state.settings.servers.forEach { server ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SlateLine, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(server.name, color = SlateInk, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
                        Text("${server.address}:${server.port}", color = SlateMuted, fontFamily = BodyFont)
                    }
                    TextButton(onClick = { onRemoveServer(server) }) {
                        Text("Remove", color = Accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (versionsOpen) {
        VersionSheet(
            groups = state.groups,
            loading = state.groupsLoading,
            onClose = { versionsOpen = false },
            onPick = { loader, mc ->
                onSelectVersion(loader, mc)
                versionsOpen = false
            },
        )
    }
    if (accountOpen) {
        AccountSheet(
            signedInName = account?.name,
            onMicrosoft = {
                accountOpen = false
                onMicrosoftLogin()
            },
            onSignOut = {
                accountOpen = false
                onSignOut()
            },
            onClose = { accountOpen = false },
        )
    }
}

@Composable
private fun VersionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x990A1012))
            .border(1.dp, SlateLineStrong, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("VERSION", color = SlateMuted, fontFamily = BodyFont, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
            Text(label, color = SlateInk, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
        }
        Text("Change", color = Accent, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayButton(
    preparing: Boolean,
    status: String,
    progress: Float,
    signedIn: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .then(
                if (preparing) {
                    Modifier
                        .background(Color(0xD90A1012))
                        .border(1.dp, SlateLineStrong, shape)
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(PlayTop, PlayBot)))
                },
            )
            .clickable(enabled = !preparing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (preparing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Accent.copy(alpha = 0.18f * progress)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (preparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(status, color = SlateText, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PlayInk)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (signedIn) "PLAY" else "SIGN IN & PLAY",
                    color = PlayInk,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, icon: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x660A1012))
            .border(1.dp, SlateLine, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = SlateText, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(hint, color = SlateFaint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SlateInk,
            unfocusedTextColor = SlateInk,
            focusedBorderColor = Accent,
            unfocusedBorderColor = SlateLineStrong,
            focusedContainerColor = Color(0x6606080A),
            unfocusedContainerColor = Color(0x6606080A),
            cursorColor = Accent,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Accent.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, color = Accent, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSheet(
    groups: List<VersionGroup>,
    loading: Boolean,
    onClose: () -> Unit,
    onPick: (ModLoader, String) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var family by remember { mutableStateOf<VersionGroup?>(null) }
    var minecraft by remember { mutableStateOf("latest") }
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xF2141A1C),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Choose version", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (loading && groups.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else when (step) {
                0 -> {
                    SheetRow("Latest release", "Always up to date") {
                        minecraft = "latest"
                        family = null
                        step = 2
                    }
                    groups.forEach { group ->
                        SheetRow(group.title, "${group.versions.size} versions") {
                            family = group
                            step = 1
                        }
                    }
                }
                1 -> {
                    TextButton(onClick = { step = 0 }) { Text("← All versions", color = Accent) }
                    family?.versions?.forEach { id ->
                        SheetRow(id, family?.title ?: "") {
                            minecraft = id
                            step = 2
                        }
                    }
                }
                else -> {
                    TextButton(onClick = { step = if (family != null) 1 else 0 }) {
                        Text("← Back", color = Accent)
                    }
                    SheetRow("Vanilla", "Official Minecraft") { onPick(ModLoader.VANILLA, minecraft) }
                    SheetRow("Fabric", "Needed for Deepslate UI and the client pack") {
                        onPick(ModLoader.FABRIC, minecraft)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x3310181A))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, color = SlateInk, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SlateMuted, fontFamily = BodyFont)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    signedInName: String?,
    onMicrosoft: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xF2141A1C)) {
        Column(Modifier.padding(20.dp)) {
            Text(signedInName ?: "Not signed in", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Text(
                "A Microsoft account that owns Java Edition is required. Cobbled does not support cracked or offline play.",
                color = SlateMuted,
                fontFamily = BodyFont,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            SheetRow("Microsoft login", "Xbox + Minecraft profile") { onMicrosoft() }
            if (signedInName != null) {
                SheetRow("Sign out", "Clear the saved session") { onSignOut() }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun RuntimeDialog(version: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2141A1C),
        title = {
            Text("Instance ready", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Minecraft $version is on this phone, along with your Microsoft session and mods folder.\n\n" +
                    "Cobbled still needs the on-device Java / OpenGL runtime before it can start the game. That is the next slice — Play will launch from here once it lands.",
                color = SlateText,
                fontFamily = BodyFont,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", color = Accent) }
        },
    )
}
