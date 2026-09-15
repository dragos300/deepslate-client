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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepslate.cobbled.auth.MicrosoftLoginActivity
import com.deepslate.cobbled.data.LauncherViewModel
import com.deepslate.cobbled.runtime.GameActivity
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
        consumeAccountExtra(intent)
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
                val json = result.data?.getStringExtra(MicrosoftLoginActivity.EXTRA_ACCOUNT)
                    ?: return@rememberLauncherForActivityResult
                viewModel.onExternalAccount(json)
            }

            LaunchedEffect(state.launch) {
                val launch = state.launch ?: return@LaunchedEffect
                startActivity(
                    Intent(this@MainActivity, GameActivity::class.java).apply {
                        putExtra(GameActivity.EXTRA_ACCOUNT, launch.accountJson)
                        putExtra(GameActivity.EXTRA_LAUNCH_PLAN, launch.launchPlanPath)
                        putExtra(GameActivity.EXTRA_RAM, launch.ramGb)
                    },
                )
                viewModel.clearLaunch()
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
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAccountExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncAccount()
    }

    private fun consumeAccountExtra(intent: Intent?) {
        val json = intent?.getStringExtra(MicrosoftLoginActivity.EXTRA_ACCOUNT) ?: return
        intent.removeExtra(MicrosoftLoginActivity.EXTRA_ACCOUNT)
        viewModel.onExternalAccount(json)
    }
}
