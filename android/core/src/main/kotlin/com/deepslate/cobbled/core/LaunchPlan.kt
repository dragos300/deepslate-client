package com.deepslate.cobbled.core

import kotlinx.serialization.Serializable

@Serializable
data class LaunchPlan(
    val minecraftId: String,
    val versionName: String,
    val mainClass: String,
    val classpath: List<String>,
    val nativesDir: String,
    val gameDir: String,
    val assetsDir: String,
    val assetIndex: String,
    val clientJar: String,
    val loader: String = "vanilla",
)
