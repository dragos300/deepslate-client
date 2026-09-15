package com.deepslate.cobbled.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class PreparedInstance(
    val minecraftId: String,
    val versionJson: File,
    val clientJar: File,
    val modsDir: File,
    val root: File,
    val launchPlan: File,
    val nativesDir: File,
    val gameDir: File,
)

class InstancePreparer(
    private val versions: VersionRepository,
    private val http: OkHttpClient = cobbledHttpClient(),
    private val downloads: OkHttpClient = cobbledDownloadClient(http),
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
        val gameDir = File(root, "instances/${ref.storageKey.replace(':', '_')}").apply { mkdirs() }
        val modsDir = File(gameDir, "mods").apply { mkdirs() }
        val nativesDir = File(root, "natives/$mc").apply { mkdirs() }
        val librariesDir = File(root, "libraries")
        val assetsDir = File(root, "assets")
        val classpath = LinkedHashSet<String>()

        onProgress(PrepareProgress("Fetching $mc metadata…"))
        val (meta, rawJson) = versions.versionMeta(mc)
        jsonFile.writeText(rawJson)

        val clientUrl = meta.downloads?.client?.url
            ?: throw IllegalStateException("Version $mc has no client download.")
        if (!jarFile.exists() || jarFile.length() == 0L) {
            onProgress(PrepareProgress("Downloading Minecraft $mc…"))
            downloadTo(clientUrl, jarFile)
        }

        val libs = meta.libraries
        var i = 0
        for (lib in libs) {
            i++
            if (!LibrarySupport.allowedOnLinux(lib.rules)) continue
            val art = lib.downloads?.artifact ?: continue
            val url = art.url ?: continue
            val path = art.path ?: LibrarySupport.mavenPath(lib.name)
            if (LibrarySupport.isNativeArtifact(lib.name, path)) continue
            val dest = File(librariesDir, path)
            if (dest.exists() && dest.length() > 0L) {
                classpath += dest.absolutePath
                continue
            }
            if (i % 6 == 0) onProgress(PrepareProgress("Libraries $i/${libs.size}", i, libs.size))
            downloadTo(url, dest)
            classpath += dest.absolutePath
        }

        var mainClass = meta.mainClass ?: "net.minecraft.client.main.Main"
        var versionName = mc
        var loaderName = "vanilla"
        if (ref.loader == ModLoader.FABRIC) {
            onProgress(PrepareProgress("Resolving Fabric loader…"))
            val loader = versions.latestFabricLoader(mc)
            versionName = "fabric-loader-$loader-$mc"
            loaderName = "fabric"
            val profile = versions.fabricProfile(mc, loader)
            mainClass = profile.mainClass ?: "net.fabricmc.loader.impl.launch.knot.KnotClient"
            val fabricLibs = profile.libraries
            var fi = 0
            for (lib in fabricLibs) {
                fi++
                val path = LibrarySupport.mavenPath(lib.name)
                if (LibrarySupport.isNativeArtifact(lib.name, path)) continue
                val dest = File(librariesDir, path)
                if (!dest.exists() || dest.length() == 0L) {
                    val base = lib.url?.let { if (it.endsWith('/')) it else "$it/" }
                        ?: "https://maven.fabricmc.net/"
                    onProgress(PrepareProgress("Fabric libraries $fi/${fabricLibs.size}", fi, fabricLibs.size))
                    downloadTo(base + path, dest)
                }
                classpath += dest.absolutePath
            }
        }

        classpath += jarFile.absolutePath

        val assetIndexMeta = meta.assetIndex
            ?: throw IllegalStateException("Version $mc has no asset index.")
        val indexesDir = File(assetsDir, "indexes").apply { mkdirs() }
        val indexFile = File(indexesDir, "${assetIndexMeta.id}.json")
        if (!indexFile.exists() || indexFile.length() == 0L) {
            onProgress(PrepareProgress("Downloading asset index…"))
            downloadTo(assetIndexMeta.url, indexFile)
        }
        val index = CobbledJson.decodeFromString(AssetIndexFile.serializer(), indexFile.readText())
        downloadAssets(assetsDir, index, onProgress)

        val plan = LaunchPlan(
            minecraftId = mc,
            versionName = versionName,
            mainClass = mainClass,
            classpath = classpath.toList(),
            nativesDir = nativesDir.absolutePath,
            gameDir = gameDir.absolutePath,
            assetsDir = assetsDir.absolutePath,
            assetIndex = assetIndexMeta.id,
            clientJar = jarFile.absolutePath,
            loader = loaderName,
        )
        val planFile = File(versionDir, "cobbled-launch.json")
        planFile.writeText(CobbledJson.encodeToString(LaunchPlan.serializer(), plan))

        onProgress(PrepareProgress("Instance files ready", 1, 1))
        PreparedInstance(
            minecraftId = mc,
            versionJson = jsonFile,
            clientJar = jarFile,
            modsDir = modsDir,
            root = root,
            launchPlan = planFile,
            nativesDir = nativesDir,
            gameDir = gameDir,
        )
    }

    private suspend fun downloadAssets(
        assetsDir: File,
        index: AssetIndexFile,
        onProgress: (PrepareProgress) -> Unit,
    ) {
        val objectsDir = File(assetsDir, "objects").apply { mkdirs() }
        val entries = index.objects.entries.toList()
        if (entries.isEmpty()) return
        val remaining = entries.filter { (name, obj) ->
            val dest = objectFile(objectsDir, obj.hash)
            !dest.exists() || dest.length() == 0L
        }
        if (remaining.isEmpty()) return
        onProgress(PrepareProgress("Downloading ${remaining.size} Minecraft assets…", 0, remaining.size))
        val done = AtomicInteger(0)
        val gate = Semaphore(6)
        coroutineScope {
            remaining.map { (_, obj) ->
                async {
                    gate.withPermit {
                        val dest = objectFile(objectsDir, obj.hash)
                        if (!dest.exists() || dest.length() == 0L) {
                            val url = "https://resources.download.minecraft.net/${obj.hash.substring(0, 2)}/${obj.hash}"
                            runCatching { downloadTo(url, dest) }
                        }
                        val n = done.incrementAndGet()
                        if (n == remaining.size || n % 25 == 0) {
                            onProgress(PrepareProgress("Assets $n/${remaining.size}", n, remaining.size))
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun objectFile(objectsDir: File, hash: String): File =
        File(objectsDir, "${hash.substring(0, 2)}/$hash")

    private fun downloadTo(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val req = Request.Builder().url(url).header("Accept", "*/*").get().build()
        downloads.newCall(req).execute().use { res ->
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
