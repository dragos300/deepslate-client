package com.deepslate.cobbled.runtime

import android.os.Bundle
import android.system.Os
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.deepslate.cobbled.core.CobbledJson
import com.deepslate.cobbled.core.LaunchPlan
import com.deepslate.cobbled.core.McAccount
import com.deepslate.cobbled.ui.theme.Accent
import com.deepslate.cobbled.ui.theme.CobbledTheme
import com.deepslate.cobbled.ui.theme.SlateBg
import com.deepslate.cobbled.ui.theme.SlateInk
import com.deepslate.cobbled.ui.theme.SlateMuted
import com.oracle.dalvik.VMLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.utils.JREUtils
import java.io.File

class GameActivity : ComponentActivity(), SurfaceHolder.Callback {
    private var status by mutableStateOf("Preparing Pojav engine…")
    private var error by mutableStateOf<String?>(null)
    private var overlay by mutableStateOf(true)
    private var surfaceView: SurfaceView? = null
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CobbledTheme {
                Box(Modifier.fillMaxSize().background(SlateBg)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            SurfaceView(context).also { view ->
                                surfaceView = view
                                view.holder.addCallback(this@GameActivity)
                                view.layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                )
                            }
                        },
                    )
                    if (overlay) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xCC06080A))
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (error == null) CircularProgressIndicator(color = Accent)
                                Text(
                                    error ?: status,
                                    color = if (error != null) Accent else SlateInk,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                                Text(
                                    "First run downloads the Pojav JRE and native engine. After that, Minecraft starts in this screen.",
                                    color = SlateMuted,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (started) return
        started = true
        lifecycleScope.launch {
            try {
                val planFile = File(intent.getStringExtra(EXTRA_LAUNCH_PLAN) ?: error("Missing launch plan."))
                val plan = CobbledJson.decodeFromString(LaunchPlan.serializer(), planFile.readText())
                val account = CobbledJson.decodeFromString(
                    McAccount.serializer(),
                    intent.getStringExtra(EXTRA_ACCOUNT) ?: error("Missing Microsoft account."),
                )
                val ram = intent.getIntExtra(EXTRA_RAM, 2)
                val engine = PojavEngine(this@GameActivity)
                engine.ensure { status = it.status }
                status = "Loading Pojav natives…"
                withContext(Dispatchers.IO) {
                    engine.load()
                    JREUtils.load(engine.pojavExec.absolutePath)
                    applyEnvironment(engine, plan)
                    JREUtils.setupExitMethod(applicationContext)
                    JREUtils.initJavaRuntime(engine.jreHome)
                    val gl = LaunchCommand.glLibrary(engine.nativesDir)
                    if (File(gl).exists()) JREUtils.dlopen(gl)
                    JREUtils.chdir(plan.gameDir)
                }
                JREUtils.attachWindow(holder.surface)
                status = "Starting Minecraft…"
                val metrics = resources.displayMetrics
                val args = LaunchCommand.minecraftArgs(
                    plan = plan,
                    account = account,
                    ramGb = ram,
                    jreHome = engine.jreHome,
                    pojavNatives = engine.nativesDir,
                    windowWidth = metrics.widthPixels,
                    windowHeight = metrics.heightPixels,
                )
                overlay = false
                val code = withContext(Dispatchers.IO) {
                    VMLauncher.launchJVM(args)
                }
                error = "Minecraft exited ($code)."
                overlay = true
            } catch (err: Throwable) {
                error = err.message ?: err.toString()
                overlay = true
            }
        }
    }

    private fun applyEnvironment(engine: PojavEngine, plan: LaunchPlan) {
        val ld = engine.ldLibraryPath()
        val env = mapOf(
            "JAVA_HOME" to engine.jreHome.absolutePath,
            "JAVA_TOOL_OPTIONS" to "",
            "HOME" to File(plan.gameDir).parentFile?.absolutePath.orEmpty().ifBlank { filesDir.absolutePath },
            "TMPDIR" to cacheDir.absolutePath,
            "LD_LIBRARY_PATH" to ld,
            "PATH" to "${File(engine.jreHome, "bin").absolutePath}:${Os.getenv("PATH") ?: ""}",
            "POJAV_NATIVEDIR" to engine.nativesDir.absolutePath,
            "POJAV_RENDERER" to "opengles2",
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "force_glsl_extensions_warn" to "true",
            "allow_higher_compat_version" to "true",
            "allow_glsl_extension_directive_midshader" to "true",
            "MESA_GLSL_CACHE_DIR" to cacheDir.absolutePath,
            "AWTSTUB_WIDTH" to resources.displayMetrics.widthPixels.toString(),
            "AWTSTUB_HEIGHT" to resources.displayMetrics.heightPixels.toString(),
        )
        env.forEach { (key, value) ->
            runCatching { Os.setenv(key, value, true) }
        }
        JREUtils.setLdLibraryPath(ld)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runCatching { JREUtils.releaseBridgeWindow() }
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_LAUNCH_PLAN = "launch_plan"
        const val EXTRA_RAM = "ram"
        const val EXTRA_VERSION = "version"
        const val EXTRA_VERSION_JSON = "version_json"
        const val EXTRA_CLIENT_JAR = "client_jar"
        const val EXTRA_GAME_DIR = "game_dir"
    }
}
