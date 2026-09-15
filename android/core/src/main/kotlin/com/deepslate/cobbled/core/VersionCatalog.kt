package com.deepslate.cobbled.core

object VersionCatalog {
    fun familyOf(versionId: String): String {
        val mc = VersionRef.parse(versionId).minecraft
        if (mc == "latest" || mc == "latest-release" || mc == "latest-snapshot") return "latest"
        val parts = mc.split('.')
        if (parts.isEmpty()) return mc
        val major = parts[0]
        if (major.all { it.isDigit() } && major.length == 2 && (major.toIntOrNull() ?: 0) >= 25) {
            return major
        }
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else mc
    }

    fun titleOf(family: String): String = UPDATE_TITLES[family] ?: "Minecraft $family"

    fun sortKey(version: String): List<Int> =
        version.split('.').map { bit -> bit.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    fun compareMc(a: String, b: String): Int {
        val aa = sortKey(a)
        val bb = sortKey(b)
        val len = maxOf(aa.size, bb.size)
        for (i in 0 until len) {
            val d = (aa.getOrElse(i) { 0 }) - (bb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun groupReleases(
        releaseIds: List<String>,
        fabricSupported: Set<String>? = null,
        limitPerFamily: Int = 12,
    ): List<VersionGroup> {
        val byFamily = linkedMapOf<String, MutableList<String>>()
        for (id in releaseIds) {
            if (fabricSupported != null && id !in fabricSupported) continue
            val fam = familyOf(id)
            byFamily.getOrPut(fam) { mutableListOf() }.add(id)
        }
        val families = byFamily.keys.sortedWith { a, b ->
            when {
                a == "latest" -> -1
                b == "latest" -> 1
                else -> compareMc(b, a)
            }
        }
        return families.map { family ->
            val versions = (byFamily[family] ?: emptyList())
                .sortedWith { a, b -> compareMc(b, a) }
                .take(limitPerFamily)
            VersionGroup(family, titleOf(family), versions)
        }
    }
}
