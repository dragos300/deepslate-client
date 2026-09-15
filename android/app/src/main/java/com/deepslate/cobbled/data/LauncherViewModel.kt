package com.deepslate.cobbled.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepslate.cobbled.CobbledApplication
import com.deepslate.cobbled.core.AuthException
import com.deepslate.cobbled.core.CobbledJson
import com.deepslate.cobbled.core.LauncherSettings
import com.deepslate.cobbled.core.McAccount
import com.deepslate.cobbled.core.ModHit
import com.deepslate.cobbled.core.ModLoader
import com.deepslate.cobbled.core.PrepareProgress
import com.deepslate.cobbled.core.ServerEntry
import com.deepslate.cobbled.core.VersionGroup
import com.deepslate.cobbled.core.VersionRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LauncherPage { HOME, MODS, SETTINGS }

data class GameLaunch(
    val accountJson: String,
    val launchPlanPath: String,
    val ramGb: Int,
)

data class LauncherUiState(
    val page: LauncherPage = LauncherPage.HOME,
    val account: McAccount? = null,
    val settings: LauncherSettings = LauncherSettings(),
    val versionLabel: String = VersionRef.parse("fabric:latest").label,
    val resolvedMinecraft: String? = null,
    val groups: List<VersionGroup> = emptyList(),
    val groupsLoading: Boolean = false,
    val preparing: Boolean = false,
    val progress: PrepareProgress? = null,
    val toast: String? = null,
    val launch: GameLaunch? = null,
    val mods: List<ModHit> = emptyList(),
    val modQuery: String = "",
    val modStatus: String = "Search Modrinth for Fabric mods that match your version.",
    val modsBusy: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CobbledApplication
    private val c = app.container

    private val _state = MutableStateFlow(
        LauncherUiState(
            account = c.store.loadAccount(),
            settings = c.store.loadSettings(),
        ),
    )
    val state: StateFlow<LauncherUiState> = _state

    init {
        val ref = VersionRef.parse(_state.value.settings.lastVersion)
        _state.update { it.copy(versionLabel = ref.label) }
        refreshVersionLabel()
    }

    fun setPage(page: LauncherPage) {
        val account = _state.value.account
        if (page == LauncherPage.MODS && account == null) {
            _state.update { it.copy(toast = "Mods need a Microsoft account that owns Java Edition.") }
            return
        }
        _state.update { it.copy(page = page) }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    fun clearLaunch() {
        _state.update { it.copy(launch = null, preparing = false, progress = null) }
    }

    fun syncAccount() {
        val stored = c.store.loadAccount()
        if (stored != _state.value.account) {
            _state.update { it.copy(account = stored) }
        }
    }

    fun onExternalAccount(json: String) {
        runCatching { CobbledJson.decodeFromString(McAccount.serializer(), json) }
            .onSuccess(::onSignedIn)
            .onFailure { onSignInFailed(it.message ?: "Could not read Microsoft profile.") }
    }

    fun setRam(gb: Int) {
        updateSettings { it.copy(ramGb = gb.coerceIn(1, 6)) }
    }

    fun setEnhancedPack(enabled: Boolean) {
        updateSettings { it.copy(enhancedPack = enabled) }
    }

    fun setModQuery(query: String) {
        _state.update { it.copy(modQuery = query) }
    }

    fun onSignedIn(account: McAccount) {
        c.store.saveAccount(account)
        _state.update {
            it.copy(account = account, toast = "Signed in as ${account.name}.")
        }
    }

    fun onSignInFailed(message: String) {
        _state.update { it.copy(toast = message) }
    }

    fun signOut() {
        c.store.saveAccount(null)
        _state.update {
            it.copy(
                account = null,
                page = if (it.page == LauncherPage.MODS) LauncherPage.HOME else it.page,
                toast = "Signed out.",
            )
        }
    }

    fun saveServer(name: String, address: String, portText: String) {
        val host = address.trim()
        if (host.isEmpty()) return
        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: 25565
        val entry = ServerEntry(name = name.trim().ifEmpty { host }, address = host, port = port)
        updateSettings { settings ->
            val next = settings.servers.toMutableList()
            val idx = next.indexOfFirst { it.address.equals(entry.address, true) && it.port == entry.port }
            if (idx >= 0) next[idx] = entry else next.add(entry)
            settings.copy(servers = next)
        }
    }

    fun removeServer(entry: ServerEntry) {
        updateSettings { settings ->
            settings.copy(servers = settings.servers.filterNot { it == entry })
        }
    }

    fun loadVersionGroups() {
        val current = _state.value
        if (current.groups.isNotEmpty() || current.groupsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(groupsLoading = true) }
            runCatching { c.versions.groups(fabricOnly = false) }
                .onSuccess { groups -> _state.update { it.copy(groups = groups, groupsLoading = false) } }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            groupsLoading = false,
                            toast = err.message ?: "Could not load versions.",
                        )
                    }
                }
        }
    }

    fun selectVersion(loader: ModLoader, minecraft: String) {
        val ref = VersionRef(loader, minecraft)
        updateSettings { it.copy(lastVersion = ref.storageKey, loader = loader) }
        _state.update { it.copy(versionLabel = ref.label, resolvedMinecraft = null) }
        refreshVersionLabel()
    }

    fun play() {
        val account = _state.value.account
        if (account == null) {
            _state.update { it.copy(toast = "Sign in with Microsoft to continue.") }
            return
        }
        if (_state.value.preparing) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    preparing = true,
                    launch = null,
                    progress = PrepareProgress("Refreshing Microsoft session…"),
                )
            }
            try {
                val fresh = runCatching { c.auth.refresh(account) }.getOrElse { err ->
                    if (err is AuthException) throw err
                    account
                }
                c.store.saveAccount(fresh)
                _state.update { it.copy(account = fresh) }

                val ref = VersionRef.parse(_state.value.settings.lastVersion)
                val prepared = c.preparer.prepare(c.minecraftRoot, ref) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                _state.update {
                    it.copy(
                        preparing = true,
                        progress = PrepareProgress("Starting Minecraft ${prepared.minecraftId}…"),
                        launch = GameLaunch(
                            accountJson = CobbledJson.encodeToString(McAccount.serializer(), fresh),
                            launchPlanPath = prepared.launchPlan.absolutePath,
                            ramGb = it.settings.ramGb,
                        ),
                    )
                }
            } catch (err: Exception) {
                _state.update {
                    it.copy(
                        preparing = false,
                        progress = null,
                        toast = err.message ?: "Could not prepare the instance.",
                    )
                }
            }
        }
    }

    fun searchMods() {
        val account = _state.value.account ?: return
        if (_state.value.modsBusy) return
        viewModelScope.launch {
            _state.update { it.copy(modsBusy = true, modStatus = "Searching Modrinth…") }
            try {
                c.auth.refresh(account).let { fresh ->
                    c.store.saveAccount(fresh)
                    _state.update { it.copy(account = fresh) }
                }
            } catch (_: Exception) {
                /* search does not require a live token */
            }
            try {
                val ref = VersionRef.parse(_state.value.settings.lastVersion)
                val mc = c.versions.resolveMinecraft(ref)
                val hits = c.mods.search(_state.value.modQuery, mc, "fabric")
                _state.update {
                    it.copy(
                        mods = hits,
                        modsBusy = false,
                        resolvedMinecraft = mc,
                        modStatus = "${hits.size} mods · $mc · fabric",
                    )
                }
            } catch (err: Exception) {
                _state.update {
                    it.copy(
                        modsBusy = false,
                        mods = emptyList(),
                        modStatus = err.message ?: "Search failed.",
                    )
                }
            }
        }
    }

    fun installMod(hit: ModHit) {
        if (_state.value.modsBusy) return
        viewModelScope.launch {
            _state.update { it.copy(modsBusy = true, modStatus = "Installing ${hit.title}…") }
            try {
                val ref = VersionRef.parse(_state.value.settings.lastVersion)
                val prepared = c.preparer.prepare(c.minecraftRoot, ref)
                val file = c.mods.downloadLatest(hit.projectId, prepared.minecraftId, "fabric", prepared.modsDir)
                _state.update {
                    it.copy(
                        modsBusy = false,
                        modStatus = "Installed ${file.name}",
                    )
                }
            } catch (err: Exception) {
                _state.update {
                    it.copy(
                        modsBusy = false,
                        modStatus = err.message ?: "Install failed.",
                    )
                }
            }
        }
    }

    private fun refreshVersionLabel() {
        viewModelScope.launch {
            val ref = VersionRef.parse(_state.value.settings.lastVersion)
            val resolved = runCatching { c.versions.resolveMinecraft(ref) }.getOrNull()
            val label = if (ref.minecraft == "latest" && resolved != null) {
                if (ref.loader == ModLoader.FABRIC) "Fabric $resolved" else resolved
            } else {
                ref.label
            }
            _state.update { it.copy(versionLabel = label, resolvedMinecraft = resolved) }
        }
    }

    private fun updateSettings(block: (LauncherSettings) -> LauncherSettings) {
        val next = block(_state.value.settings)
        c.store.saveSettings(next)
        _state.update { it.copy(settings = next) }
    }
}
