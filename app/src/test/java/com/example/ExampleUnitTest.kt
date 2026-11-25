package com.example

import com.example.core.ApiKeyVault
import com.example.core.ContactCandidate
import com.example.core.ContactLookupResult
import com.example.core.DeviceActionValidator
import com.example.core.OfflineSiriCommandParser
import com.example.core.OfflineSiriIntent
import com.example.model.AuthEventType
import com.example.model.BugCategory
import com.example.model.BugStatus
import com.example.model.ResponseParser
import com.example.model.SessionStatus
import com.example.model.ZenvixModelOption
import com.example.model.isVerifiedOwnerAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `parse thinking and artifact tags cleanly`() {
        val raw = """
            <thinking>Plan a responsive card layout.</thinking>
            Here is your widget.
            <artifact title="Clock Widget" language="html">
            <div>12:00</div>
            </artifact>
        """.trimIndent()

        val parsed = ResponseParser.parse(raw, isFinal = true)
        assertEquals("Plan a responsive card layout.", parsed.thinkingSummary)
        assertTrue(parsed.visibleText.contains("Here is your widget."))
        assertNotNull(parsed.artifact)
        assertEquals("Clock Widget", parsed.artifact?.title)
        assertEquals("html", parsed.artifact?.language)
        assertEquals("<div>12:00</div>", parsed.artifact?.content)
    }

    @Test
    fun `fallback extracts substantial fenced code block as artifact`() {
        val raw = """
            Here is the Kotlin helper:
            ```kotlin
            fun greet(name: String): String {
                val clean = name.trim()
                return if (clean.isEmpty()) {
                    "Hello!"
                } else {
                    "Hello, ${'$'}clean!"
                }
            }
            ```
        """.trimIndent()

        val parsed = ResponseParser.parse(raw, isFinal = true)
        assertNotNull(parsed.artifact)
        assertEquals("kotlin", parsed.artifact?.language)
        assertTrue(parsed.artifact?.content?.contains("fun greet") == true)
    }

    @Test
    fun `resolveSpec resolves external BYOK models and built-in cloud models`() {
        val externalModels = ApiKeyVault.defaultExternalModels()

        val opus = ZenvixModelOption.resolveSpec("opus-5.5", externalModels)
        assertEquals("Opus 5.5", opus.displayName)
        assertTrue(opus.isExternalApi)
        assertFalse(opus.isFreeBuiltIn)

        val febal = ZenvixModelOption.resolveSpec("febal-5.1", externalModels)
        assertEquals("Febal 5.1", febal.displayName)
        assertTrue(febal.isExternalApi)

        val sonnet = ZenvixModelOption.resolveSpec("sonnet-5.5", externalModels)
        assertEquals("Sonnet 5.5", sonnet.displayName)
        assertTrue(sonnet.isExternalApi)

        val builtIn = ZenvixModelOption.resolveSpec("gemini-3.8-flash", externalModels)
        assertEquals("Zenvix Sonnet 3.8", builtIn.displayName)
        assertFalse(builtIn.isExternalApi)
        assertTrue(builtIn.isFreeBuiltIn)

        val unknownCustom = ZenvixModelOption.resolveSpec("custom/provider-model", externalModels)
        assertEquals("custom/provider-model", unknownCustom.displayName)
        assertTrue(unknownCustom.isExternalApi)
    }

    @Test
    fun `owner verification strictly requires verified primary owner email`() {
        assertTrue(isVerifiedOwnerAccount("anugrah.business.2.3@gmail.com", isEmailVerified = true))
        assertTrue(isVerifiedOwnerAccount("  ANUGRAH.BUSINESS.2.3@GMAIL.COM ", isEmailVerified = true))
        assertFalse(isVerifiedOwnerAccount("anugrah.business.2.3@gmail.com", isEmailVerified = false))
        assertFalse(isVerifiedOwnerAccount("anugrahkun2010@gmail.com", isEmailVerified = true))
        assertFalse(isVerifiedOwnerAccount(null, isEmailVerified = true))
        assertFalse(isVerifiedOwnerAccount("", isEmailVerified = true))
    }

    @Test
    fun `bug category and status parse cleanly with safe fallbacks`() {
        assertEquals(BugCategory.AI_CHAT, BugCategory.fromRaw("AI_CHAT"))
        assertEquals(BugCategory.YOUR_APP, BugCategory.fromRaw("YOUR_APP"))
        assertEquals(BugCategory.OTHER, BugCategory.fromRaw("UNKNOWN_CATEGORY"))

        assertEquals(BugStatus.OPEN, BugStatus.fromRaw("OPEN"))
        assertEquals(BugStatus.IN_PROGRESS, BugStatus.fromRaw("IN_PROGRESS"))
        assertEquals(BugStatus.FIXED, BugStatus.fromRaw("FIXED"))
        assertEquals(BugStatus.OPEN, BugStatus.fromRaw("INVALID_STATUS"))
    }

    @Test
    fun `session status and auth event types resolve safely`() {
        assertEquals(SessionStatus.SIGNED_IN, SessionStatus.fromRaw("SIGNED_IN"))
        assertEquals(SessionStatus.SIGNED_OUT, SessionStatus.fromRaw("SIGNED_OUT"))
        assertEquals(SessionStatus.SIGNED_IN, SessionStatus.fromRaw("UNKNOWN"))

        assertEquals(AuthEventType.LOGIN, AuthEventType.fromRaw("LOGIN"))
        assertEquals(AuthEventType.LOGOUT, AuthEventType.fromRaw("LOGOUT"))
        assertEquals(AuthEventType.LOGIN, AuthEventType.fromRaw(null))
    }

    @Test
    fun `phone normalization validates digits and rejects invalid text`() {
        assertEquals("9876543210", DeviceActionValidator.normalizePhoneNumber("98765 43210"))
        assertEquals("+919876543210", DeviceActionValidator.normalizePhoneNumber("+91 987-654-3210"))
        assertNull(DeviceActionValidator.normalizePhoneNumber("call mom"))
        assertNull(DeviceActionValidator.normalizePhoneNumber("12"))
    }

    @Test
    fun `url sanitizer only allows safe http or https links`() {
        assertEquals("https://youtube.com", DeviceActionValidator.sanitizeSafeHttpUrl("youtube.com"))
        assertEquals("https://example.org/test", DeviceActionValidator.sanitizeSafeHttpUrl("https://example.org/test"))
        assertNull(DeviceActionValidator.sanitizeSafeHttpUrl("javascript:alert(1)"))
        assertNull(DeviceActionValidator.sanitizeSafeHttpUrl("file:///etc/passwd"))
        assertNull(DeviceActionValidator.sanitizeSafeHttpUrl("content://contacts/1"))
        assertNull(DeviceActionValidator.sanitizeSafeHttpUrl("intent://scan/#Intent;end"))
    }

    @Test
    fun `contact matcher resolves single match ambiguity and not found safely`() {
        val contacts = listOf(
            ContactCandidate("Mom", "+919800000001"),
            ContactCandidate("Rahul Sharma", "+919800000002"),
            ContactCandidate("Rahul Verma", "+919800000003")
        )

        val momMatch = DeviceActionValidator.evaluateContactMatches("Mummy", contacts)
        assertTrue(momMatch is ContactLookupResult.ExactMatch)
        assertEquals("Mom", (momMatch as ContactLookupResult.ExactMatch).candidate.displayName)

        val rahulMatch = DeviceActionValidator.evaluateContactMatches("Rahul", contacts)
        assertTrue(rahulMatch is ContactLookupResult.Ambiguous)
        assertEquals(2, (rahulMatch as ContactLookupResult.Ambiguous).candidates.size)

        val missing = DeviceActionValidator.evaluateContactMatches("Suresh", contacts)
        assertTrue(missing is ContactLookupResult.NotFound)
    }

    @Test
    fun `app query mapper maps whatsapp youtube instagram chrome cleanly`() {
        assertTrue(DeviceActionValidator.mapAppQueryToPackages("WhatsApp kholo").contains("com.whatsapp"))
        assertTrue(DeviceActionValidator.mapAppQueryToPackages("YouTube").contains("com.google.android.youtube"))
        assertTrue(DeviceActionValidator.mapAppQueryToPackages("Instagram").contains("com.instagram.android"))
        assertTrue(DeviceActionValidator.mapAppQueryToPackages("Chrome").contains("com.android.chrome"))
        assertTrue(DeviceActionValidator.isSettingsQuery("Open device settings"))
    }

    @Test
    fun `offline Siri parser handles Hinglish and English device control intents`() {
        assertTrue(OfflineSiriCommandParser.parse("WhatsApp kholo") is OfflineSiriIntent.OpenWhatsApp)
        assertTrue(OfflineSiriCommandParser.parse("Hey Zenvix, open WhatsApp") is OfflineSiriIntent.OpenWhatsApp)

        val torchOn = OfflineSiriCommandParser.parse("Torch on karo") as? OfflineSiriIntent.SetFlashlight
        assertNotNull(torchOn)
        assertTrue(torchOn!!.enabled)

        val torchOff = OfflineSiriCommandParser.parse("Flashlight band karo") as? OfflineSiriIntent.SetFlashlight
        assertNotNull(torchOff)
        assertFalse(torchOff!!.enabled)

        assertTrue(OfflineSiriCommandParser.parse("Battery kitni hai") is OfflineSiriIntent.CheckBattery)

        val timeIntent = OfflineSiriCommandParser.parse("Time kya hua hai") as? OfflineSiriIntent.CheckDateTime
        assertNotNull(timeIntent)
        assertFalse(timeIntent!!.wantDate)

        val dateIntent = OfflineSiriCommandParser.parse("Aaj ki tarikh kya hai") as? OfflineSiriIntent.CheckDateTime
        assertNotNull(dateIntent)
        assertTrue(dateIntent!!.wantDate)

        val alarm = OfflineSiriCommandParser.parse("Set alarm 7:30 am") as? OfflineSiriIntent.SetAlarm
        assertNotNull(alarm)
        assertEquals(7, alarm!!.hour24)
        assertEquals(30, alarm.minute)

        val timer = OfflineSiriCommandParser.parse("5 minute timer") as? OfflineSiriIntent.SetTimer
        assertNotNull(timer)
        assertEquals(300, timer!!.seconds)

        val volDown = OfflineSiriCommandParser.parse("Volume kam karo") as? OfflineSiriIntent.AdjustVolume
        assertEquals("down", volDown?.mode)

        val wifiPanel = OfflineSiriCommandParser.parse("Wifi settings kholo") as? OfflineSiriIntent.OpenSystemPanel
        assertEquals("wifi", wifiPanel?.panelType)

        val callMom = OfflineSiriCommandParser.parse("Mummy ko call karo") as? OfflineSiriIntent.CallContact
        assertEquals("mummy", callMom?.contactName)

        val callNum = OfflineSiriCommandParser.parse("Call 9876543210") as? OfflineSiriIntent.MakeCall
        assertEquals("9876543210", callNum?.phoneNumber)

        val openYt = OfflineSiriCommandParser.parse("Open YouTube") as? OfflineSiriIntent.OpenApp
        assertEquals("youtube", openYt?.appName)

        assertTrue(OfflineSiriCommandParser.parse("Tumko kaun banaya") is OfflineSiriIntent.AskCreator)
        assertNull(OfflineSiriCommandParser.parse("Explain photosynthesis in detail"))
    }
}
