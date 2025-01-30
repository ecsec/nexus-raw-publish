package de.ecsec

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test

@EnabledIfEnvironmentVariables(value = [
    EnabledIfEnvironmentVariable(named = "MVN_ECSEC_USERNAME", matches = ".*"),
    EnabledIfEnvironmentVariable(named = "MVN_ECSEC_PASSWORD", matches = ".*")
])
class NexusApiTest {

    val repoName = "data-private"

    lateinit var client: HttpClient
    @BeforeTest
    fun setupClient() {
        val baseUrl = "https://mvn.ecsec.de/"
        val username = System.getenv("MVN_ECSEC_USERNAME")
        val password = System.getenv("MVN_ECSEC_PASSWORD")

        client = HttpClient(Java) {
            // configure client
            defaultRequest {
                url(baseUrl)
                basicAuth(username, password)
            }
        }
    }

    @Test
    fun `test upload and deletion`(): Unit = runBlocking {

        val logger = mock<Logger>()
        val api = NexusApi(client, logger)

        val repoDir = "sampleData"
        val assetRoot = File("src/test/resources/$repoDir")

        api.deleteRemoteContent(repoName, repoDir)

        assetRoot.walkTopDown().forEach {
            if (it.isFile) {
                api.uploadFile(repoName, "$repoDir/${it.parentFile.toRelativeString(assetRoot)}", it)
            }
        }

        delay(1000)
        api.deleteRemoteContent(repoName, repoDir)
    }
}
