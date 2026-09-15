package com.deepslate.cobbled.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class PreparedInstance(
    val minecraftId: String,
    val versionJson: File,
    val clientJar: File,
    val modsDir: File,
    val root: File,
)

class InstancePreparer(
    private val versions: VersionRepository,
    private val http: OkHttpClient = cobbledHttpClient(),
) {
    suspend fun prepare(
        root: File,
        ref: VersionRef,
        onProgress: (PrepareProgress) -> Unit = {},
    ): PreparedInstance = withContext(Dispatchers.IO) {
        val mc = versions.resolveMinecraft(ref)
        val versionDir = File(root, "versions/$mc").apply { mkdirs() }
        val jsonFile = File(versionDir, "$mc.json")
        val jarFile = File(versionDir, "$mc.jar")
        val modsDir = File(root, "instances/${ref.storageKey.replace(':', '_')}/mods").apply { mkdirs() }

        onProgress(PrepareProgress("Fetching $mc metadata…"))
        val (meta, rawJson) = versions.versionMeta(mc)
        if (!jsonFile.exists()) {
            jsonFile.writeText(rawJson)
        }

        val clientUrl = meta.downloads?.client?.url
            ?: throw IllegalStateException("Version $mc has no client download.")
        if (!jarFile.exists() || jarFile.length() == 0L) {
            onProgress(PrepareProgress("Downloading Minecraft $mc…"))
            downloadTo(clientUrl, jarFile)
        }

        onProgress(PrepareProgress("Instance files ready", 1, 1))
        PreparedInstance(
            minecraftId = mc,
            versionJson = jsonFile,
            clientJar = jarFile,
            modsDir = modsDir,
            root = root,
        )
    }

    private fun downloadTo(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val req = Request.Builder().url(url).header("Accept", "*/*").get().build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Download failed (${res.code}) for ${dest.name}")
            tmp.outputStream().use { out ->
                res.body?.byteStream()?.copyTo(out) ?: throw IllegalStateException("Empty download for ${dest.name}")
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }
}
