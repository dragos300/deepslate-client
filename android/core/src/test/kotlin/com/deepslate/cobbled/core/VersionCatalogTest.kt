package com.deepslate.cobbled.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCatalogTest {
    @Test
    fun familyUsesMinorForClassicReleases() {
        assertEquals("1.21", VersionCatalog.familyOf("1.21.4"))
        assertEquals("1.20", VersionCatalog.familyOf("1.20.1"))
        assertEquals("1.8", VersionCatalog.familyOf("1.8.9"))
    }

    @Test
    fun familyUsesYearFor26Drops() {
        assertEquals("26", VersionCatalog.familyOf("26.1"))
    }

    @Test
    fun groupsNewestFamiliesFirst() {
        val groups = VersionCatalog.groupReleases(
            listOf("1.21.4", "1.21.3", "1.20.6", "1.20.1", "1.8.9"),
            limitPerFamily = 2,
        )
        assertEquals(listOf("1.21", "1.20", "1.8"), groups.map { it.family })
        assertEquals("Tricky Trials", groups[0].title)
        assertEquals(listOf("1.21.4", "1.21.3"), groups[0].versions)
        assertEquals(listOf("1.20.6", "1.20.1"), groups[1].versions)
    }

    @Test
    fun fabricFilterDropsUnsupportedIds() {
        val groups = VersionCatalog.groupReleases(
            listOf("1.21.4", "1.8.9"),
            fabricSupported = setOf("1.21.4"),
        )
        assertEquals(listOf("1.21"), groups.map { it.family })
    }

    @Test
    fun versionRefRoundTrip() {
        val fabric = VersionRef.parse("fabric:1.21.4")
        assertEquals(ModLoader.FABRIC, fabric.loader)
        assertEquals("Fabric 1.21.4", fabric.label)
        assertEquals("fabric:1.21.4", fabric.storageKey)
        assertEquals("Latest release", VersionRef.parse("latest").label)
    }
}

class MicrosoftAuthRedirectTest {
    private val auth = MicrosoftAuthClient()

    @Test
    fun authorizeUrlUsesPojavRedirectUrl() {
        val url = auth.authorizeUrl()
        assertTrue(url.contains("login.live.com/oauth20_authorize"))
        assertTrue(url.contains("client_id=${MicrosoftAuthClient.CLIENT_ID}"))
        assertTrue(url.contains("redirect_url="))
        assertTrue(!url.contains("redirect_uri="))
    }

    @Test
    fun extractsCodeFromDesktopRedirect() {
        val code = auth.codeFromRedirect(
            "https://login.live.com/oauth20_desktop.srf?code=ABC123&lc=1033",
        )
        assertEquals("ABC123", code)
    }

    @Test
    fun extractsCodeFromMsXalRedirect() {
        val code = auth.codeFromRedirect(
            "ms-xal-00000000402b5328://auth/?code=XYZ789&state=1",
        )
        assertEquals("XYZ789", code)
        assertTrue(auth.isLoginRedirect("ms-xal-00000000402b5328://auth/?code=XYZ789"))
    }

    @Test
    fun ignoresUnrelatedUrls() {
        assertNull(auth.codeFromRedirect("https://login.live.com/oauth20_authorize.srf?foo=1"))
        assertNull(auth.codeFromRedirect("https://example.com/?code=nope"))
    }

    @Test(expected = AuthException::class)
    fun mapsMicrosoftErrorOnRedirect() {
        auth.codeFromRedirect(
            "https://login.live.com/oauth20_desktop.srf?error=access_denied&error_description=user%20cancelled",
        )
    }

    @Test(expected = AuthException::class)
    fun mapsCancelOnMsXal() {
        auth.codeFromRedirect("ms-xal-00000000402b5328://auth/?res=cancel")
    }
}

class AccountModelTest {
    @Test
    fun dashesUuidAndBuildsSkinUrl() {
        val account = McAccount(
            uuid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            name = "Steve",
            accessToken = "t",
            refreshToken = "r",
            xuid = "x",
        )
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", account.uuidDashed)
        assertTrue(account.skinUrl.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertEquals(AuthKind.XBOX_LIVE, account.kind)
    }
}

class LaunchPlanTest {
    @Test
    fun roundTripsJson() {
        val plan = LaunchPlan(
            minecraftId = "1.21.4",
            versionName = "fabric-loader-0.16.9-1.21.4",
            mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient",
            classpath = listOf("/tmp/a.jar", "/tmp/b.jar"),
            nativesDir = "/tmp/natives",
            gameDir = "/tmp/game",
            assetsDir = "/tmp/assets",
            assetIndex = "17",
            clientJar = "/tmp/client.jar",
            loader = "fabric",
        )
        val json = CobbledJson.encodeToString(LaunchPlan.serializer(), plan)
        val back = CobbledJson.decodeFromString(LaunchPlan.serializer(), json)
        assertEquals(plan, back)
        assertTrue(json.contains("KnotClient"))
    }
}

class LibrarySupportTest {
    @Test
    fun mavenPathBuildsStandardJar() {
        assertEquals(
            "com/mojang/logging/1.2.7/logging-1.2.7.jar",
            LibrarySupport.mavenPath("com.mojang:logging:1.2.7"),
        )
    }

    @Test
    fun mavenPathKeepsClassifier() {
        assertEquals(
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux-arm64.jar",
            LibrarySupport.mavenPath("org.lwjgl:lwjgl:3.3.3:natives-linux-arm64"),
        )
    }

    @Test
    fun librariesWithoutRulesAreAllowed() {
        assertTrue(LibrarySupport.allowedOnLinux(null))
        assertTrue(LibrarySupport.allowedOnLinux(emptyList()))
    }

    @Test
    fun osxOnlyLibrariesAreSkipped() {
        val rules = listOf(
            MojangRule("allow", MojangOsRule("osx")),
        )
        assertTrue(!LibrarySupport.allowedOnLinux(rules))
    }

    @Test
    fun linuxAllowWins() {
        val rules = listOf(
            MojangRule("allow", null),
            MojangRule("disallow", MojangOsRule("osx")),
        )
        assertTrue(LibrarySupport.allowedOnLinux(rules))
    }

    @Test
    fun nativeArtifactsAreDetected() {
        assertTrue(LibrarySupport.isNativeArtifact("org.lwjgl:lwjgl:3.3.3:natives-linux"))
        assertTrue(LibrarySupport.isNativeArtifact("lwjgl", "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"))
        assertTrue(!LibrarySupport.isNativeArtifact("com.mojang:logging:1.2.7", "com/mojang/logging/1.2.7/logging-1.2.7.jar"))
    }
}
