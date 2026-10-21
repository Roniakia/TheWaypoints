package de.dimskiy.waypoints.platform.offline

import io.mockk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.net.ssl.HttpsURLConnection

class PackTransportTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://github.com/Roniakia/WaypointsKaroo/releases/download/poi-data-test/region.zip"
    private fun response(status: Int, body: String, range: String? = null) = mockk<HttpsURLConnection>(relaxed = true).apply {
        every { responseCode } returns status
        every { inputStream } answers { ByteArrayInputStream(body.toByteArray()) }
        every { getHeaderField("Content-Length") } returns body.length.toString()
        every { getHeaderField("Content-Range") } returns range
    }

    @Test fun resumesAtVerifiedRange() {
        val file = temporary.newFile().apply { writeText("abc") }
        val connection = response(206, "def", "bytes 3-5/6")
        HttpsPackTransport { connection }.download(url, file, 6, {}, {})
        assertEquals("abcdef", file.readText())
        verify { connection.setRequestProperty("Range", "bytes=3-"); connection.disconnect() }
    }

    @Test fun replacesPartialWhenServerIgnoresRange() {
        val file = temporary.newFile().apply { writeText("bad") }
        HttpsPackTransport { response(200, "abcdef") }.download(url, file, 6, {}, {})
        assertEquals("abcdef", file.readText())
    }

    @Test fun rejectsWrongRangeWithoutChangingPartial() {
        val file = temporary.newFile().apply { writeText("abc") }
        try {
            HttpsPackTransport { response(206, "def", "bytes 2-4/6") }.download(url, file, 6, {}, {})
            fail("Bad range accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals("abc", file.readText())
    }

    @Test fun preservesInterruptedBytesAndRejectsOversizeResponses() {
        val file = temporary.newFile()
        val connection = response(200, "abcdef")
        every { connection.inputStream } returns object : ByteArrayInputStream("abc".toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (available() == 0) throw IOException("Connection lost")
                return super.read(buffer, offset, length)
            }
        }
        try { HttpsPackTransport { connection }.download(url, file, 6, {}, {}); fail() } catch (_: IOException) { }
        assertEquals("abc", file.readText())
        try { HttpsPackTransport { response(200, "too large") }.read(url, 3); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsRedirectToCleartextOrUnexpectedHost() {
        for (redirect in listOf("http://github.com/pack", "https://evil.test/pack")) {
            val connection = response(302, "")
            every { connection.getHeaderField("Location") } returns redirect
            var opens = 0
            try { HttpsPackTransport { opens++; connection }.read(url, 100); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(1, opens)
            verify { connection.disconnect() }
        }
    }
}
