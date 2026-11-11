package com.mediacraft.app.update

import java.io.Closeable
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

internal interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

internal class HttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: InputStream,
    private val closeAction: () -> Unit,
) : Closeable {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    override fun close() {
        runCatching { body.close() }
        closeAction()
    }
}

internal class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            requestMethod = "GET"
            useCaches = false
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            val status = connection.responseCode
            val body = if (status >= 400) connection.errorStream else connection.inputStream
            HttpResponse(
                status = status,
                headers = connection.headerFields.entries
                    .filter { it.key != null && it.value != null }
                    .associate { it.key!! to it.value!! },
                body = body ?: ByteArrayInputStream(ByteArray(0)),
                closeAction = connection::disconnect,
            )
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
    }
}

internal class UpdateNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
