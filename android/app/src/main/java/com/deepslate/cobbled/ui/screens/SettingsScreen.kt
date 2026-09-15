package com.deepslate.cobbled.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepslate.cobbled.core.APP_NAME
import com.deepslate.cobbled.core.APP_VERSION
import com.deepslate.cobbled.data.LauncherUiState
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.BodyFont
import com.deepslate.cobbled.ui.theme.DisplayFont
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateMuted
import com.deepslate.cobbled.ui.theme.SlateText

@Composable
fun SettingsScreen(
    state: LauncherUiState,
    onRam: (Int) -> Unit,
    onEnhancedPack: (Boolean) -> Unit,
    onMicrosoftLogin: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Settings", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text("Tune memory for the future JVM and keep your Microsoft session here.", color = SlateMuted, fontFamily = BodyFont)
        Spacer(Modifier.height(20.dp))

        Text("Allocated memory", color = SlateInk, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
        Text("${state.settings.ramGb} GB — used when the on-device runtime starts the game.", color = SlateMuted, fontFamily = BodyFont)
        Slider(
            value = state.settings.ramGb.toFloat(),
            onValueChange = { onRam(it.toInt()) },
            valueRange = 1f..6f,
            steps = 4,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Enhanced client pack", color = SlateInk, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
                Text(
                    "Same Sodium / Lithium stack as desktop Deepslate. Sync lands with the runtime.",
                    color = SlateMuted,
                    fontFamily = BodyFont,
                )
            }
            Switch(
                checked = state.settings.enhancedPack,
                onCheckedChange = onEnhancedPack,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = SlateInk),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Account", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text(
            state.account?.let { "Signed in as ${it.name}" } ?: "Not signed in",
            color = SlateText,
            fontFamily = BodyFont,
        )
        Spacer(Modifier.height(10.dp))
        GhostButton("Microsoft login") { onMicrosoftLogin() }
        if (state.account != null) {
            Spacer(Modifier.height(8.dp))
            GhostButton("Sign out") { onSignOut() }
        }

        Spacer(Modifier.height(28.dp))
        Text("About", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text(
            "$APP_NAME $APP_VERSION\nAndroid launcher for Minecraft Java Edition. Not affiliated with Mojang or Microsoft.\nGame files download from Mojang after you sign in with an account that owns Java Edition.",
            color = SlateMuted,
            fontFamily = BodyFont,
        )
        Spacer(Modifier.height(32.dp))
    }
}
