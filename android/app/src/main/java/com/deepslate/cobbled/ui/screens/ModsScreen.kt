package com.deepslate.cobbled.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deepslate.cobbled.core.ModHit
import com.deepslate.cobbled.data.LauncherUiState
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.BodyFont
import com.deepslate.cobbled.ui.theme.DisplayFont
import com.deepslate.cobbled.ui.theme.SlateFaint
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateLine
import com.deepslate.cobbled.ui.theme.SlateMuted

@Composable
fun ModsScreen(
    state: LauncherUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onInstall: (ModHit) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Mods", color = SlateInk, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text(state.versionLabel, color = SlateMuted, fontFamily = BodyFont)
        Spacer(Modifier.height(12.dp))
        DarkField(
            value = state.modQuery,
            onValueChange = onQuery,
            hint = "Search Modrinth…",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(if (state.modsBusy) "Working…" else "Search") {
            if (!state.modsBusy) onSearch()
        }
        Spacer(Modifier.height(10.dp))
        Text(state.modStatus, color = SlateFaint, fontFamily = BodyFont)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(state.mods, key = { it.projectId }) { hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x660A1012))
                        .border(1.dp, SlateLine, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (hit.iconUrl != null) {
                        AsyncImage(
                            model = hit.iconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x223FD4B8)),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(hit.title, color = SlateInk, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
                        Text(
                            "${hit.author} · ${"%,d".format(hit.downloads)} downloads",
                            color = SlateMuted,
                            fontFamily = BodyFont,
                        )
                    }
                    GhostButton("Install") { if (!state.modsBusy) onInstall(hit) }
                }
            }
        }
    }
}
