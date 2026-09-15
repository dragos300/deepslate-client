package com.deepslate.cobbled

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepslate.cobbled.auth.MicrosoftLoginActivity
import com.deepslate.cobbled.core.CobbledJson
import com.deepslate.cobbled.core.McAccount
import com.deepslate.cobbled.data.LauncherViewModel
import com.deepslate.cobbled.ui.CobbledApp
import com.deepslate.cobbled.ui.theme.CobbledTheme
import com.deepslate.cobbled.ui.theme.SlateBg

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(SlateBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(SlateBg.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val login = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode != Activity.RESULT_OK) {
                    val error = result.data?.getStringExtra(MicrosoftLoginActivity.EXTRA_ERROR)
                    if (!error.isNullOrBlank()) viewModel.onSignInFailed(error)
                    return@rememberLauncherForActivityResult
                }
                val json = result.data?.getStringExtra(MicrosoftLoginActivity.EXTRA_ACCOUNT) ?: return@rememberLauncherForActivityResult
                runCatching { CobbledJson.decodeFromString(McAccount.serializer(), json) }
                    .onSuccess(viewModel::onSignedIn)
                    .onFailure { viewModel.onSignInFailed(it.message ?: "Could not read Microsoft profile.") }
            }

            CobbledTheme {
                CobbledApp(
                    state = state,
                    onPage = viewModel::setPage,
                    onPlay = {
                        if (state.account == null) {
                            login.launch(Intent(this, MicrosoftLoginActivity::class.java))
                        } else {
                            viewModel.play()
                        }
                    },
                    onMicrosoftLogin = {
                        login.launch(Intent(this, MicrosoftLoginActivity::class.java))
                    },
                    onSignOut = viewModel::signOut,
                    onSelectVersion = viewModel::selectVersion,
                    onLoadVersions = viewModel::loadVersionGroups,
                    onSaveServer = viewModel::saveServer,
                    onRemoveServer = viewModel::removeServer,
                    onRam = viewModel::setRam,
                    onEnhancedPack = viewModel::setEnhancedPack,
                    onModQuery = viewModel::setModQuery,
                    onSearchMods = viewModel::searchMods,
                    onInstallMod = viewModel::installMod,
                    onDismissToast = viewModel::clearToast,
                    onDismissRuntime = viewModel::dismissRuntime,
                )
            }
        }
    }
}
