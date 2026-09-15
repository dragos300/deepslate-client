package com.deepslate.cobbled.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ModrinthClient(
    private val http: OkHttpClient = cobbledHttpClient(),
) {
    suspend fun search(
        query: String,
        gameVersion: String,
        loader: String = "fabric",
        limit: Int = 20,
    ): List<ModHit> = withContext(Dispatchers.IO) {
        val facets = buildJsonArray {
            addJsonArray { add("project_type:mod") }
            addJsonArray { add("categories:${loader.ifBlank { "fabric" }}") }
            addJsonArray { add("versions:$gameVersion") }
        }
        val url = "$API/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.trim())
            .addQueryParameter("limit", minOf(40, limit).toString())
            .addQueryParameter("index", if (query.isBlank()) "downloads" else "relevance")
            .addQueryParameter("facets", facets.toString())
            .build()
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("Modrinth search failed (${res.code})")
            CobbledJson.decodeFromString(ModrinthSearchResponse.serializer(), text).hits.map { hit ->
                ModHit(
                    projectId = hit.projectId,
                    slug = hit.slug,
                    title = hit.title,
                    description = hit.description,
                    downloads = hit.downloads,
                    iconUrl = hit.iconUrl,
                    author = hit.author,
                    url = "https://modrinth.com/mod/${hit.slug}",
                )
            }
        }
    }

    suspend fun downloadLatest(
        projectId: String,
        gameVersion: String,
        loader: String,
        destDir: File,
        onStatus: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val url = "$API/project/$projectId/version".toHttpUrl().newBuilder()
            .addQueryParameter("game_versions", "[\"$gameVersion\"]")
            .addQueryParameter("loaders", "[\"$loader\"]")
            .build()
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("Modrinth version list failed (${res.code})")
            val versions = CobbledJson.decodeFromString(ListSerializer(ModrinthVersion.serializer()), text)
            val file = versions.firstOrNull()?.files?.firstOrNull { it.primary }
                ?: versions.firstOrNull()?.files?.firstOrNull()
            if (file == null) throw IllegalStateException("No Modrinth file for $gameVersion / $loader")
            destDir.mkdirs()
            val out = File(destDir, file.filename)
            onStatus("Downloading ${file.filename}…")
            val dl = Request.Builder().url(file.url).header("Accept", "*/*").get().build()
            http.newCall(dl).execute().use { bodyRes ->
                if (!bodyRes.isSuccessful) throw IllegalStateException("Download failed (${bodyRes.code})")
                out.outputStream().use { dest ->
                    bodyRes.body?.byteStream()?.copyTo(dest)
                }
            }
            out
        }
    }
}

@kotlinx.serialization.Serializable
private data class ModrinthVersion(val files: List<ModrinthFile> = emptyList())

@kotlinx.serialization.Serializable
private data class ModrinthFile(
    val url: String,
    val filename: String,
    val primary: Boolean = false,
)

private const val API = "https://api.modrinth.com/v2"
