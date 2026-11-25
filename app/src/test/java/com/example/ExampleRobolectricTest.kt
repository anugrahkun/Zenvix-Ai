package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zenvix AI", appName)
    }

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
}
