package com.deepslate.cobbled.core

object LibrarySupport {
    fun mavenPath(name: String): String {
        val bits = name.split(':')
        require(bits.size >= 3) { "Not a maven coordinate: $name" }
        val group = bits[0].replace('.', '/')
        val artifact = bits[1]
        val version = bits[2]
        val classifier = bits.getOrNull(3)
        val file = if (classifier != null) "$artifact-$version-$classifier.jar" else "$artifact-$version.jar"
        return "$group/$artifact/$version/$file"
    }

    fun allowedOnLinux(rules: List<MojangRule>?): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (osMatchesLinux(rule.os)) {
                allowed = rule.action.equals("allow", ignoreCase = true)
            }
        }
        return allowed
    }

    fun isNativeArtifact(name: String, path: String? = null): Boolean {
        val blob = "$name ${path.orEmpty()}".lowercase()
        return blob.contains(":natives") || blob.contains("natives-")
    }

    private fun osMatchesLinux(os: MojangOsRule?): Boolean {
        if (os == null) return true
        val name = os.name ?: return true
        return name.equals("linux", ignoreCase = true)
    }
}
