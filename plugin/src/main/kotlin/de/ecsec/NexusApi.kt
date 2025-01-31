package de.ecsec

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.io.asSource
import kotlinx.serialization.json.Json
import org.gradle.api.logging.Logger
import java.io.File
import java.time.Instant

const val defaultDeleteTimeout = 2 * 60 * 1000L

class NexusApi(client: HttpClient, val logger: Logger, val deletionTimeout: Long = defaultDeleteTimeout) {

    private val client: HttpClient = client.config {
        followRedirects = true
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun deleteRemoteContent(rawRepoName: String, rawRepoFolder: String) {
        val assets = searchAssets(rawRepoName, rawRepoFolder)
        assets.forEach {
            logger.info("Deleting asset ${it.path}")
            deleteAsset(it.id)
        }

        waitForAssetDeletion(rawRepoName, rawRepoFolder)
    }

    suspend fun deleteAsset(assetId: String) {
        client.delete("service/rest/v1/assets") {
            url {
                appendPathSegments(assetId)
            }
        }
    }

    suspend fun waitForAssetDeletion(rawRepoName: String, rawRepoFolder: String) {
        var startTime = Instant.now()

        do {
            val assets = searchAssets(rawRepoName, rawRepoFolder)
            if (assets.isNotEmpty()) {
                logger.debug("Waiting for asset deletion.")
                delay(1000)
            }

            // check timeout
            if (Instant.now().isAfter(startTime.plusMillis(deletionTimeout))) {
                val msg = "Timeout waiting for remote content deletion of '$rawRepoName:$rawRepoFolder'."
                logger.error(msg)
                throw RuntimeException(msg)
            }
        } while (assets.isNotEmpty())
    }

    suspend fun searchAssets(rawRepoName: String, path: String): List<Asset> {
        val path = path.correctPath()
        val searchStr = "$path/*"
        val assets = mutableListOf<Asset>()

        var continuationToken: String? = null
        do {
            val result: AssetSearchResult = client.get("service/rest/v1/search/assets") {
                url {
                    parameters.append("repository", rawRepoName)
                    parameters.append("name", searchStr)
                    continuationToken?.let {
                        parameters.append("continuationToken", it)
                    }
                }
            }.body()
            assets.addAll(result.items.filter {
                it.path.startsWith("$path/")
            })
            continuationToken = result.continuationToken
        } while (continuationToken != null)

        logger.debug("Found ${assets.size} assets.")
        return assets
    }

    suspend fun uploadFile(rawRepoName: String, path: String, file: File) {
        val path = path.correctPath()
        logger.info("Uploading file '$path/${file.name}'.")

        val response = client.submitFormWithBinaryData(
            url = "service/rest/v1/components",
            formData = formData {
                append("raw.directory", path)
                append("raw.asset1", file.name, ContentType.Application.OctetStream) {
                    this.transferFrom(file.inputStream().asSource())
                }
                append("raw.asset1.filename", file.name)
            }
        ) {
            url {
                parameters.append("repository", rawRepoName)
            }
            method = HttpMethod.Post
        }
    }

}


fun String.correctPath(): String {
    return this.removeTrailingSlash().addLeadingSlash()
}

private fun String.removeTrailingSlash(): String {
    return if (this.endsWith("/")) {
        this.dropLast(1)
    } else {
        this
    }
}

private fun String.addLeadingSlash(): String {
    return if (this.startsWith("/")) {
        this
    } else {
        "/$this"
    }
}
