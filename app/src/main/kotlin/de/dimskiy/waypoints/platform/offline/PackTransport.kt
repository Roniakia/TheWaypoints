package de.dimskiy.waypoints.platform.offline

import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

interface PackTransport {
    @Throws(IOException::class)
    fun read(url: String, maximumBytes: Int, checkCancelled: () -> Unit = {}): ByteArray
    @Throws(IOException::class)
    fun download(url: String, destination: File, expectedBytes: Long,
        progress: (Long) -> Unit, checkCancelled: () -> Unit)
}

class HttpsPackTransport(
    private val connect: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : PackTransport {
    private fun connection(value: String, offset: Long = 0): HttpsURLConnection {
        var url = URL(value)
        repeat(6) {
            require(url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443) &&
                url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) { "Unsafe download URL" }
            val connection = connect(url)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "WaypointsKaroo-offline-pois")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = connection.responseCode
            if (status !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(!location.isNullOrBlank()) { "Missing redirect location" }
            url = URL(url, location)
        }
        throw IOException("Too many download redirects")
    }

    override fun read(url: String, maximumBytes: Int, checkCancelled: () -> Unit): ByteArray {
        val connection = connection(url)
        try {
            if (connection.responseCode != 200) throw IOException("Catalogue HTTP ${connection.responseCode}")
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    checkCancelled()
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(output.size().toLong() + size <= maximumBytes) { "Response exceeds size limit" }
                    output.write(buffer, 0, size)
                }
            }
            return output.toByteArray()
        } finally { connection.disconnect() }
    }

    override fun download(url: String, destination: File, expectedBytes: Long, progress: (Long) -> Unit, checkCancelled: () -> Unit) {
        require(expectedBytes > 0)
        if (destination.length() > expectedBytes) check(destination.delete())
        var offset = destination.length()
        if (offset == expectedBytes) { progress(offset); return }
        val connection = connection(url, offset)
        try {
            val status = connection.responseCode
            when (status) {
                200 -> offset = 0 // Server ignored Range: replace the partial file, never append it.
                206 -> {
                    val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                    require(range != null && range.groupValues[1].toLong() == offset &&
                        range.groupValues[3].toLong() == expectedBytes &&
                        range.groupValues[2].toLong() == expectedBytes - 1) { "Incorrect resumed range" }
                }
                else -> throw IOException("Pack HTTP $status")
            }
            val length = connection.getHeaderField("Content-Length")?.toLongOrNull()
            require(length == null || length == expectedBytes - offset) { "Incorrect response length" }
            java.io.FileOutputStream(destination, offset > 0).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var received = offset
                    progress(received)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(received <= expectedBytes) { "Download exceeds expected size" }
                        output.write(buffer, 0, count)
                        progress(received)
                    }
                    output.fd.sync()
                    if (received != expectedBytes) throw IOException("Download interrupted; will resume")
                }
            }
        } finally { connection.disconnect() }
    }
}
