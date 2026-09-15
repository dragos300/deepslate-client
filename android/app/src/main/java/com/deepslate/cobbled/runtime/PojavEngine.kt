package com.deepslate.cobbled.runtime

import android.content.Context
import android.os.Build
import com.deepslate.cobbled.core.PrepareProgress
import com.deepslate.cobbled.core.cobbledDownloadClient
import com.deepslate.cobbled.core.cobbledHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.util.zip.ZipInputStream

class PojavEngine(
    context: Context,
    private val http: OkHttpClient = cobbledDownloadClient(cobbledHttpClient()),
) {
    val root: File = File(context.filesDir, "pojav").apply { mkdirs() }
    val jreHome: File = File(root, "jre")
    val nativesDir: File = File(root, "natives")
    val pojavExec: File get() = File(nativesDir, "libpojavexec.so")

    fun abi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    fun isReady(): Boolean = File(jreHome, "bin/java").exists() && pojavExec.exists()

    fun findLib(name: String): File? =
        jreHome.walkTopDown().firstOrNull { it.isFile && it.name == name }
            ?: nativesDir.listFiles()?.firstOrNull { it.name == name }

    suspend fun ensure(onProgress: (PrepareProgress) -> Unit) = withContext(Dispatchers.IO) {
        nativesDir.mkdirs()
        if (!File(jreHome, "bin/java").exists()) {
            onProgress(PrepareProgress("Downloading Android JRE (Pojav OpenJDK 17)…"))
            val tar = File(root, "jre17.tar.xz")
            download(jreUrl(), tar, onProgress, "Android JRE")
            onProgress(PrepareProgress("Unpacking JRE…"))
            extractTarXz(tar, jreHome)
            tar.delete()
            File(jreHome, "bin/java").setExecutable(true, false)
        }
        if (!pojavExec.exists()) {
            onProgress(PrepareProgress("Downloading Pojav engine natives (~150 MB)…"))
            val apk = File(root, "PojavLauncher.apk")
            download(POJAV_APK, apk, onProgress, "Pojav natives")
            onProgress(PrepareProgress("Extracting Pojav native libraries…"))
            extractApkNatives(apk, nativesDir, abi())
            apk.delete()
        }
        if (!pojavExec.exists()) {
            throw IllegalStateException(
                "Pojav engine natives were missing after extract (${abi()}). This preview ships arm64 and x86_64 libraries from the official Pojav APK.",
            )
        }
    }

    fun load() {
        val preferred = listOf(
            "libc++_shared.so",
            "libbytehook.so",
            "libexithook.so",
            "libopenal.so",
            "libglfw.so",
            "libgl4es_114.so",
            "libgl4es.so",
            "libfreetype.so",
            "liblwjgl.so",
            "liblwjgl_opengl.so",
            "liblwjgl_stb.so",
            "libpojavexec.so",
            "libpojavexec_awt.so",
        )
        val loaded = mutableSetOf<String>()
        for (name in preferred) {
            val file = File(nativesDir, name)
            if (file.exists()) {
                runCatching { System.load(file.absolutePath) }
                loaded += name
            }
        }
        nativesDir.listFiles()?.filter { it.name.endsWith(".so") && it.name !in loaded }?.forEach { file ->
            runCatching { System.load(file.absolutePath) }
        }
        if (!pojavExec.exists()) {
            throw IllegalStateException("libpojavexec.so is missing.")
        }
    }

    fun ldLibraryPath(): String {
        val jvm = findLib("libjvm.so")?.parentFile
        val jli = findLib("libjli.so")?.parentFile
        val jreLib = File(jreHome, "lib")
        return listOfNotNull(
            nativesDir.absolutePath,
            jvm?.absolutePath,
            jli?.absolutePath,
            jreLib.absolutePath,
            File(jreLib, "jli").absolutePath,
            File(jreLib, "server").absolutePath,
            File(jreLib, "client").absolutePath,
        ).distinct().joinToString(":")
    }

    private fun jreUrl(): String {
        val abi = abi()
        return when {
            abi.contains("arm64") -> JRE_ARM64
            abi.contains("armeabi") || abi.contains("armv7") -> JRE_ARM32
            abi.contains("x86_64") -> JRE_X86_64
            else -> throw IllegalStateException(
                "No Pojav OpenJDK build for $abi. Cobbled currently needs arm64, arm32, or x86_64.",
            )
        }
    }

    private fun download(
        url: String,
        dest: File,
        onProgress: (PrepareProgress) -> Unit,
        label: String,
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val req = Request.Builder().url(url).header("Accept", "*/*").get().build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Download failed (${res.code}) $url")
            val total = res.body?.contentLength() ?: -1L
            val input = res.body?.byteStream() ?: error("empty body")
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0L) {
                        val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                        onProgress(
                            PrepareProgress(
                                "$label $pct%",
                                read.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                                total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            ),
                        )
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun extractTarXz(archive: File, dest: File) {
        dest.mkdirs()
        XZInputStream(archive.inputStream().buffered()).use { xz ->
            TarArchiveInputStream(xz).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val out = File(dest, entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                    if (entry.name.contains("/bin/") || entry.name.endsWith(".so")) {
                        out.setExecutable(true, false)
                    }
                }
            }
        }
        flattenSingleRoot(dest)
    }

    private fun flattenSingleRoot(dest: File) {
        val kids = dest.listFiles() ?: return
        if (kids.size == 1 && kids[0].isDirectory && !File(dest, "bin").exists()) {
            val inner = kids[0]
            inner.listFiles()?.forEach { child ->
                val target = File(dest, child.name)
                if (!child.renameTo(target)) child.copyRecursively(target, overwrite = true)
            }
            inner.deleteRecursively()
        }
    }

    private fun extractApkNatives(apk: File, dest: File, abi: String) {
        dest.mkdirs()
        val prefixes = listOf("lib/$abi/") +
            if (abi.contains("arm64")) listOf("lib/arm64-v8a/") else emptyList()
        ZipInputStream(apk.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val prefix = prefixes.firstOrNull { entry.name.startsWith(it) } ?: continue
                val name = entry.name.removePrefix(prefix)
                if (!name.endsWith(".so") || name.contains('/')) continue
                val out = File(dest, name)
                out.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    companion object {
        const val POJAV_APK =
            "https://github.com/PojavLauncherTeam/PojavLauncher/releases/download/gladiolus/PojavLauncher.apk"
        const val JRE_ARM64 =
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ca01427/jre17-arm64-20220817-release.tar.xz"
        const val JRE_ARM32 =
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ca01427/jre17-arm32-20220815-release.tar.xz"
        const val JRE_X86_64 =
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ca01427/jre17-x86_64-20220817-release.tar.xz"
    }
}
