package com.johnreyes.fitbod

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Drive v3 REST client. It uses the same `drive.file` scope, folder and file
 * names as the original app, so it only ever sees `FitBod/fitbod-sync.json` and any
 * other files this Cloud project's app created.
 */
class DriveClient(private val token: String) {

    fun findFolder(): String? =
        query("name='$FOLDER' and mimeType='$FOLDER_MIME' and trashed=false").firstOrNull()

    fun createFolder(): String {
        val body = """{"name":"$FOLDER","mimeType":"$FOLDER_MIME"}"""
        return id(request("POST", "$API?fields=id", body.toByteArray(), "application/json"))
    }

    fun findFile(folderId: String): String? =
        query("name='$FILE' and '$folderId' in parents and trashed=false").firstOrNull()

    fun download(fileId: String): String =
        String(request("GET", "$API/$fileId?alt=media"), Charsets.UTF_8)

    fun update(fileId: String, json: String) {
        // HttpURLConnection has no PATCH; Google APIs accept the override header.
        request(
            "POST", "$UPLOAD/$fileId?uploadType=media", json.toByteArray(Charsets.UTF_8),
            "application/json", mapOf("X-HTTP-Method-Override" to "PATCH"),
        )
    }

    fun create(folderId: String, json: String): String {
        val boundary = "fitbod" + System.nanoTime()
        val meta = """{"name":"$FILE","parents":["$folderId"],"mimeType":"application/json"}"""
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n")
            append("--$boundary\r\nContent-Type: application/json\r\n\r\n$json\r\n--$boundary--")
        }.toByteArray(Charsets.UTF_8)
        return id(request("POST", "$UPLOAD?uploadType=multipart&fields=id", body, "multipart/related; boundary=$boundary"))
    }

    private fun query(q: String): List<String> {
        val url = "$API?q=" + URLEncoder.encode(q, "UTF-8") + "&spaces=drive&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc"
        val o = Json.parseToJsonElement(String(request("GET", url))) as JsonObject
        return (o["files"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content }
    }

    private fun id(bytes: ByteArray): String =
        (Json.parseToJsonElement(String(bytes)) as JsonObject)["id"]!!.jsonPrimitive.content

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code == 401) throw AuthExpired()
            if (code !in 200..299) {
                val err = c.errorStream?.readBytes()?.let { String(it) }.orEmpty()
                throw IOException("Drive $method failed ($code): ${err.take(300)}")
            }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    class AuthExpired : IOException("Google sign-in expired")

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val FOLDER = "FitBod"
        const val FILE = "fitbod-sync.json"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val API = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}
