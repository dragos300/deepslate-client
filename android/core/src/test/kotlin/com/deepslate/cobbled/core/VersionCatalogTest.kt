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
    fun authorizeUrlUsesXboxClientAndDesktopRedirect() {
        val url = auth.authorizeUrl()
        assertEquals("login.live.com", url.host)
        assertEquals(MicrosoftAuthClient.CLIENT_ID, url.queryParameter("client_id"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(MicrosoftAuthClient.REDIRECT_URI, url.queryParameter("redirect_uri"))
        assertTrue(url.toString().contains("oauth20_authorize"))
    }

    @Test
    fun extractsCodeFromDesktopRedirect() {
        val code = auth.codeFromRedirect(
            "https://login.live.com/oauth20_desktop.srf?code=ABC123&lc=1033",
        )
        assertEquals("ABC123", code)
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
    }
}
