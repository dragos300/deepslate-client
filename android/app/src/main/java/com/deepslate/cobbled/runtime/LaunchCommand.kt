package com.deepslate.cobbled.runtime

import com.deepslate.cobbled.core.APP_VERSION
import com.deepslate.cobbled.core.LaunchPlan
import com.deepslate.cobbled.core.McAccount
import java.io.File

object LaunchCommand {
    fun minecraftArgs(
        plan: LaunchPlan,
        account: McAccount,
        ramGb: Int,
        jreHome: File,
        pojavNatives: File,
        windowWidth: Int,
        windowHeight: Int,
    ): Array<String> {
        val natives = listOf(pojavNatives.absolutePath, plan.nativesDir)
            .filter { it.isNotBlank() }
            .joinToString(File.pathSeparator)
        val gl = glLibrary(pojavNatives)
        val ram = ramGb.coerceIn(1, 8)
        val width = windowWidth.coerceAtLeast(320)
        val height = windowHeight.coerceAtLeast(180)
        return arrayOf(
            "java",
            "-Xms512M",
            "-Xmx${ram}G",
            "-Djava.home=${jreHome.absolutePath}",
            "-Djava.library.path=$natives",
            "-Djna.boot.library.path=${pojavNatives.absolutePath}",
            "-Dorg.lwjgl.opengl.libname=$gl",
            "-Dorg.lwjgl.glfw.libname=${File(pojavNatives, "libglfw.so").absolutePath}",
            "-Dorg.lwjgl.freetype.libname=${File(pojavNatives, "libfreetype.so").absolutePath}",
            "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
            "-Dglfwstub.windowWidth=$width",
            "-Dglfwstub.windowHeight=$height",
            "-Dglfwstub.initEgl=false",
            "-Dos.name=Linux",
            "-Dos.version=Android",
            "-Djava.net.preferIPv4Stack=true",
            "-Dlog4j2.formatMsgNoLookups=true",
            "-Dminecraft.launcher.brand=CobbledDeepslate",
            "-Dminecraft.launcher.version=$APP_VERSION",
            "-cp",
            plan.classpath.joinToString(File.pathSeparator),
            plan.mainClass,
            "--username", account.name,
            "--version", plan.versionName,
            "--gameDir", plan.gameDir,
            "--assetsDir", plan.assetsDir,
            "--assetIndex", plan.assetIndex,
            "--uuid", account.uuid.replace("-", ""),
            "--accessToken", account.accessToken,
            "--userType", "msa",
            "--versionType", "release",
            "--width", width.toString(),
            "--height", height.toString(),
        )
    }

    internal fun glLibrary(natives: File): String {
        val names = listOf("libgl4es_114.so", "libgl4es.so", "libtinywrapper.so")
        return names.map { File(natives, it) }.firstOrNull { it.exists() }?.absolutePath
            ?: "libgl4es_114.so"
    }
}
