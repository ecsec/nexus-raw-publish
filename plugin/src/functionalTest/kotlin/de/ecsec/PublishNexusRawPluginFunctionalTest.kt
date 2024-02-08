package de.ecsec

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.FileSystems
import kotlin.test.Test


@WireMockTest
class PublishNexusRawPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    @Test fun pushToProtectedServer(wmRuntimeInfo: WireMockRuntimeInfo) {
        val repoUrl = wmRuntimeInfo.httpBaseUrl
        val repoName = "test-repo"
        val repoFolder = "test-folder"

        val user = "test-user"
        val pass = "test-pass"

        // delete folder
        stubFor(
            post("/service/extdirect")
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"tid": "{{jsonPath request.body '\$.tid'}}", "action": "coreui_Component", "method": "deleteFolder", "result": {"success": true, "data": null}, "type": "rpc"}""")
                    .withTransformers("response-template")
                )
        )

        // wait for deletion
        stubFor(
            get("/service/rest/repository/browse/$repoName/$repoFolder/")
                .inScenario("waitForContentDeletion")
                .whenScenarioStateIs(Scenario.STARTED)
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(200)
                )
                .willSetStateTo("contentDeleted")
        )
        stubFor(
            get("/service/rest/repository/browse/$repoName/$repoFolder/")
                .inScenario("waitForContentDeletion")
                .whenScenarioStateIs("contentDeleted")
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(404)
                )
        )

        // upload files
        stubFor(
            put("/repository/test-repo/test-folder/index.html")
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(201)
                )
        )
        stubFor(
            put("/repository/test-repo/test-folder/assets/style.css")
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(201)
                )
        )

        // Set up the test build
        settingsFile.writeText("")
        buildFile.writeText("""
            plugins {
                id('de.ecsec.nexus-raw-publish')
            }
            
            publishNexusRaw {
                nexusUrl = "$repoUrl"
                repoName = "$repoName"
                repoFolder = "$repoFolder"
                username = "$user"
                password = "$pass"
                inputDir = layout.projectDirectory.dir("sampleData")
            }
        """.trimIndent())

        // copy test data
        val origDataPath = FileSystems.getDefault().getPath("build/resources/functionalTest/sampleData")
        origDataPath.toFile().copyRecursively(projectDir.resolve("sampleData"))

        // Run the build
        val runner = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("publishNexusRaw")
            .withProjectDir(projectDir)
        runner.build()
    }
}
