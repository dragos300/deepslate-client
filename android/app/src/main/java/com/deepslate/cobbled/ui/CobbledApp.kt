package com.deepslate.cobbled.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deepslate.cobbled.R
import com.deepslate.cobbled.core.ModHit
import com.deepslate.cobbled.core.ModLoader
import com.deepslate.cobbled.core.ServerEntry
import com.deepslate.cobbled.data.LauncherPage
import com.deepslate.cobbled.data.LauncherUiState
import com.deepslate.cobbled.ui.screens.HomeScreen
import com.deepslate.cobbled.ui.screens.ModsScreen
import com.deepslate.cobbled.ui.screens.SettingsScreen
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.AccentSoft
import com.deepslate.cobbled.ui.theme.BodyFont
import com.deepslate.cobbled.ui.theme.SlateBg
import com.deepslate.cobbled.ui.theme.SlateFaint
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateLine
import com.deepslate.cobbled.ui.theme.SlateMuted
import kotlinx.coroutines.delay

@Composable
fun CobbledApp(
    state: LauncherUiState,
    onPage: (LauncherPage) -> Unit,
    onPlay: () -> Unit,
    onMicrosoftLogin: () -> Unit,
    onSignOut: () -> Unit,
    onSelectVersion: (ModLoader, String) -> Unit,
    onLoadVersions: () -> Unit,
    onSaveServer: (String, String, String) -> Unit,
    onRemoveServer: (ServerEntry) -> Unit,
    onRam: (Int) -> Unit,
    onEnhancedPack: (Boolean) -> Unit,
    onModQuery: (String) -> Unit,
    onSearchMods: () -> Unit,
    onInstallMod: (ModHit) -> Unit,
    onDismissToast: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(SlateBg)) {
        AsyncImage(
            model = R.drawable.wallpaper_deepslate,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = painterResource(R.drawable.wallpaper_deepslate),
            error = painterResource(R.drawable.wallpaper_deepslate),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xF0050709),
                            Color(0x99040709),
                            Color(0xE6050709),
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBrand(state)
            Box(Modifier.weight(1f)) {
                when (state.page) {
                    LauncherPage.HOME -> HomeScreen(
                        state = state,
                        onPlay = onPlay,
                        onMicrosoftLogin = onMicrosoftLogin,
                        onSignOut = onSignOut,
                        onSelectVersion = onSelectVersion,
                        onLoadVersions = onLoadVersions,
                        onSaveServer = onSaveServer,
                        onRemoveServer = onRemoveServer,
                    )
                    LauncherPage.MODS -> ModsScreen(
                        state = state,
                        onQuery = onModQuery,
                        onSearch = onSearchMods,
                        onInstall = onInstallMod,
                    )
                    LauncherPage.SETTINGS -> SettingsScreen(
                        state = state,
                        onRam = onRam,
                        onEnhancedPack = onEnhancedPack,
                        onMicrosoftLogin = onMicrosoftLogin,
                        onSignOut = onSignOut,
                    )
                }
            }
            BottomRail(page = state.page, onPage = onPage)
        }

        AnimatedVisibility(
            visible = state.toast != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
        ) {
            val message = state.toast
            if (message != null) {
                LaunchedEffect(message) {
                    delay(4200)
                    onDismissToast()
                }
                Snackbar(
                    containerColor = Color(0xF20C1214),
                    contentColor = SlateInk,
                    shape = RoundedCornerShape(14.dp),
                ) { Text(message, fontFamily = BodyFont) }
            }
        }
    }
}

@Composable
private fun TopBrand(state: LauncherUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AccentSoft)
                .border(1.dp, Accent.copy(alpha = 0.42f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Cobbled", color = SlateInk, fontFamily = BodyFont)
            Text("DEEPSLATE CLIENT", color = Accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        val account = state.account
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AccentSoft)
                .border(1.dp, SlateLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (account != null) {
                AsyncImage(model = account.skinUrl, contentDescription = account.name, modifier = Modifier.fillMaxSize())
            } else {
                Text("?", color = SlateMuted)
            }
        }
    }
}

@Composable
private fun BottomRail(page: LauncherPage, onPage: (LauncherPage) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC070A0C))
            .border(1.dp, SlateLine, RoundedCornerShape(22.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RailTab("Home", Icons.Filled.Home, page == LauncherPage.HOME) { onPage(LauncherPage.HOME) }
        RailTab("Mods", Icons.Filled.Extension, page == LauncherPage.MODS) { onPage(LauncherPage.MODS) }
        RailTab("Settings", Icons.Filled.Settings, page == LauncherPage.SETTINGS) { onPage(LauncherPage.SETTINGS) }
    }
}

@Composable
private fun RailTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(if (selected) AccentSoft else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Accent else SlateFaint,
            modifier = Modifier.size(22.dp),
        )
        Text(label, color = if (selected) SlateInk else SlateMuted, fontFamily = BodyFont        )
    }
}
