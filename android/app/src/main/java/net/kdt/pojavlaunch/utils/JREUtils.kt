package net.kdt.pojavlaunch.utils

import android.content.Context
import android.view.Surface
import java.io.File

/**
 * JNI surface for libpojavexec.so from PojavLauncher (GPL-3.0).
 * Native method names match PojavLauncher's pojavexec library.
 */
object JREUtils {
    @JvmStatic external fun chdir(path: String): Int
    @JvmStatic external fun dlopen(libPath: String): Boolean
    @JvmStatic external fun setLdLibraryPath(ldLibraryPath: String)
    @JvmStatic external fun setupBridgeWindow(surface: Any)
    @JvmStatic external fun releaseBridgeWindow()
    @JvmStatic external fun initializeHooks()
    @JvmStatic external fun setupExitMethod(context: Context)

    fun load(pojavExec: String) {
        System.load(pojavExec)
        runCatching { initializeHooks() }
    }

    fun attachWindow(surface: Surface) {
        setupBridgeWindow(surface)
    }

    fun initJavaRuntime(jreHome: File) {
        val found = jreHome.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.toList()
        val first = listOf(
            "libjli.so",
            "libjvm.so",
            "libverify.so",
            "libjava.so",
            "libnet.so",
            "libnio.so",
            "libawt.so",
            "libawt_headless.so",
            "libfreetype.so",
            "libfontmanager.so",
        )
        for (name in first) {
            found.find { it.name == name }?.let { runCatching { dlopen(it.absolutePath) } }
        }
        for (file in found) {
            runCatching { dlopen(file.absolutePath) }
        }
    }
}
