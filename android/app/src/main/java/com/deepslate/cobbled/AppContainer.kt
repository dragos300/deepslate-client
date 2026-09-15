package com.deepslate.cobbled

import android.content.Context
import com.deepslate.cobbled.core.InstancePreparer
import com.deepslate.cobbled.core.MicrosoftAuthClient
import com.deepslate.cobbled.core.ModrinthClient
import com.deepslate.cobbled.core.VersionRepository
import com.deepslate.cobbled.core.cobbledHttpClient
import com.deepslate.cobbled.data.LocalStore
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val http = cobbledHttpClient()
    val store = LocalStore(appContext)
    val auth = MicrosoftAuthClient(http)
    val versions = VersionRepository(http)
    val mods = ModrinthClient(http)
    val preparer = InstancePreparer(versions, http)
    val minecraftRoot: File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "minecraft").apply { mkdirs() }
}
