package com.deepslate.cobbled.core

import kotlinx.serialization.Serializable

const val APP_NAME = "Cobbled Deepslate Client"
const val APP_VERSION = "0.2.0-alpha"
const val USER_AGENT = "CobbledDeepslate/$APP_VERSION (+https://github.com/dragos300/deepslate-client)"

@Serializable
enum class ModLoader { VANILLA, FABRIC }

@Serializable
data class McAccount(
    val uuid: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String,
    val xuid: String,
    val kind: AuthKind = AuthKind.XBOX_LIVE,
) {
    val uuidDashed: String
        get() {
            val raw = uuid.replace("-", "")
            if (raw.length != 32) return uuid
            return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
        }

    val skinUrl: String
        get() = "https://crafatar.com/avatars/${uuid.replace("-", "")}?size=128&overlay"
}

@Serializable
data class ServerEntry(
    val name: String,
    val address: String,
    val port: Int = 25565,
)

@Serializable
data class LauncherSettings(
    val lastVersion: String = "fabric:latest",
    val loader: ModLoader = ModLoader.FABRIC,
    val ramGb: Int = 2,
    val servers: List<ServerEntry> = emptyList(),
    val enhancedPack: Boolean = true,
)

data class VersionRef(
    val loader: ModLoader,
    val minecraft: String,
) {
    val storageKey: String
        get() = if (loader == ModLoader.FABRIC) "fabric:$minecraft" else minecraft

    val label: String
        get() {
            val mc = if (minecraft == "latest") "Latest release" else minecraft
            return if (loader == ModLoader.FABRIC) "Fabric $mc" else mc
        }

    companion object {
        fun parse(raw: String): VersionRef {
            val v = raw.trim().ifEmpty { "latest" }
            return when {
                v.startsWith("fabric:") -> VersionRef(ModLoader.FABRIC, v.removePrefix("fabric:").ifEmpty { "latest" })
                v.startsWith("vanilla:") -> VersionRef(ModLoader.VANILLA, v.removePrefix("vanilla:").ifEmpty { "latest" })
                else -> VersionRef(ModLoader.VANILLA, v)
            }
        }
    }
}

data class VersionGroup(
    val family: String,
    val title: String,
    val versions: List<String>,
)

data class ModHit(
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val downloads: Int,
    val iconUrl: String?,
    val author: String,
    val url: String,
)

data class PrepareProgress(
    val status: String,
    val value: Int = 0,
    val maximum: Int = 0,
) {
    val fraction: Float
        get() = if (maximum > 0) (value.toFloat() / maximum).coerceIn(0f, 1f) else 0f
}

val UPDATE_TITLES: Map<String, String> = mapOf(
    "latest" to "Latest",
    "26" to "2026 Drops",
    "1.21" to "Tricky Trials",
    "1.20" to "Trails & Tales",
    "1.19" to "The Wild",
    "1.18" to "Caves & Cliffs II",
    "1.17" to "Caves & Cliffs I",
    "1.16" to "Nether Update",
    "1.15" to "Buzzy Bees",
    "1.14" to "Village & Pillage",
    "1.13" to "Update Aquatic",
    "1.12" to "World of Color",
    "1.11" to "Exploration",
    "1.10" to "Frostburn",
    "1.9" to "Combat Update",
    "1.8" to "Bountiful Update",
    "1.7" to "Changed the World",
    "1.6" to "Horse Update",
    "1.5" to "Redstone Update",
    "1.4" to "Pretty Scary",
    "1.3" to "Minecraft 1.3",
    "1.2" to "Minecraft 1.2",
    "1.1" to "Minecraft 1.1",
    "1.0" to "Adventure Update",
)
