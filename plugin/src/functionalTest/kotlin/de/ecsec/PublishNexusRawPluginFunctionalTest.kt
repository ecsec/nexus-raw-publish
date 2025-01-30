/****************************************************************************
 * Copyright (C) 2024 ecsec GmbH
 * Contact: ecsec GmbH (info@ecsec.de)
 *
 * This file is part of the nexus-raw-publish Gradle plugin.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 ***************************************************************************/

package de.ecsec

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.matching.AnythingPattern
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

    val assetIds = listOf("ZGF0YS1wcml2YXRlOmE5YzcxMGU2", "ZGF0YS1wcml2YXRlOmI4Zjk3MmM")

    val searchResult = """
        {
          "items": [
            {
              "downloadUrl": "https://mvn.ecsec.de/repository/test-repo/test-folder/index.html",
              "path": "/test-folder/index.html",
              "id": "${assetIds[0]}",
              "repository": "test-repo",
              "format": "raw",
              "checksum": {
                "sha1": "d489889587df342a9510b2f4a25bdb04c684f857",
                "sha256": "e16245bca4817df7ec4f331b15483c086d076b45ba8d00b29f75e2061f598b99",
                "sha512": "24d85142fc41d3a1a1467eeee10e27b92aeaa25dc96ca8e93276cd14f0bc4a3635776b64c7366e98a720d3f06951432c40c4c5069ab6e50652b584de4730941a",
                "md5": "6f0f97693006fd6342ecbad41176ecea"
              },
              "contentType": "text/html",
              "lastModified": "2025-01-30T14:48:28.923+00:00",
              "lastDownloaded": null,
              "uploader": "tobias.wich",
              "uploaderIp": "90.167.76.48",
              "fileSize": 181,
              "blobCreated": null,
              "blobStoreName": null,
              "raw": {}
            },
            {
              "downloadUrl": "https://mvn.ecsec.de/repository/test-repo/test-folder/assets/style.css",
              "path": "/test-folder/assets/style.css",
              "id": "${assetIds[1]}",
              "repository": "test-repo",
              "format": "raw",
              "checksum": {
                "sha1": "cee5ae7daa6a9ec0ccfb6f2b8cbe1d8275e28b08",
                "sha256": "845a6d22e6bea0e0abf7b8a768fa7f59c652871d314a8d36430a5b63a1e4d4d6",
                "sha512": "7940f2140c044bbad8675623ebcb2ddcb2eb8495a05c4cd0a7875c370b20166699ead8247ad74eb97e701c41f654c5df7fdbd81dc8136e6d95e08e5da867004a",
                "md5": "7646eb0e75534566a648453d04621d1e"
              },
              "contentType": "text/css",
              "lastModified": "2025-01-30T14:48:28.739+00:00",
              "lastDownloaded": null,
              "uploader": "tobias.wich",
              "uploaderIp": "90.167.76.48",
              "fileSize": 28,
              "blobCreated": null,
              "blobStoreName": null,
              "raw": {}
            }
          ],
          "continuationToken": null
        }
    """.trimIndent()

    @Test
    fun pushToProtectedServer(wmRuntimeInfo: WireMockRuntimeInfo) {
        val repoUrl = wmRuntimeInfo.httpBaseUrl
        val repoName = "test-repo"
        val repoFolder = "test-folder"

        val user = "test-user"
        val pass = "test-pass"

        // search files to delete
        stubFor(
            get(urlPathEqualTo("/service/rest/v1/search/assets"))
                .inScenario("waitForContentDeletion")
                .whenScenarioStateIs(Scenario.STARTED)
                .withQueryParam("repository", equalTo(repoName))
                .withQueryParam("name", equalTo("/$repoFolder/*"))
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(searchResult)
                )
        )

        // delete files
        stubFor(
            delete(urlPathTemplate("/service/rest/v1/assets/{assetId}"))
                .inScenario("waitForContentDeletion")
                .willSetStateTo("contentDeleted")
                .withBasicAuth(user, pass)
                .withPathParam("assetId", or(equalTo(assetIds[0]), equalTo(assetIds[1])))
                .willReturn(aResponse()
                    .withStatus(204)
                )
        )

        // wait for deletion
        stubFor(
            get(urlPathEqualTo("/service/rest/v1/search/assets"))
                .inScenario("waitForContentDeletion")
                .whenScenarioStateIs("contentDeleted")
                .withQueryParam("repository", equalTo(repoName))
                .withQueryParam("name", equalTo("/$repoFolder/*"))
                .withBasicAuth(user, pass)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"items":[], "continuationToken": null}""")
                )
        )


        // upload files
        stubFor(
            post(urlPathEqualTo("/service/rest/v1/components"))
                .withBasicAuth(user, pass)
                .withQueryParam("repository", equalTo(repoName))
                .withMultipartRequestBody(
                    aMultipart()
                        .withHeader("Content-Disposition", containing("name=raw.directory"))
                        .withBody(equalTo("/$repoFolder/index.html"))
                )
                .withMultipartRequestBody(
                    aMultipart()
                        .withHeader("Content-Disposition", containing("name=raw.asset1.filename"))
                        .withBody(equalTo("index.html"))
                )
                .withMultipartRequestBody(
                    aMultipart()
                        .withHeader("Content-Disposition", containing("name=raw.asset1"))
                        .withHeader("Content-Type", equalTo("application/octet-stream"))
                        .withBody(AnythingPattern())
                )
                .willReturn(
                    aResponse()
                        .withStatus(200)
                )
        )

        stubFor(
            post(urlPathEqualTo("/service/rest/v1/components"))
                .withBasicAuth(user, pass)
                .withQueryParam("repository", equalTo(repoName))
                .withMultipartRequestBody(
                    aMultipart()
                        .withHeader("Content-Disposition", containing("name=raw.directory"))
                        .withBody(equalTo("/$repoFolder/assets/style.css"))
                )
                .withMultipartRequestBody(
                    aMultipart()
                        .withHeader("Content-Disposition", containing("name=raw.asset1.filename"))
                        .withBody(equalTo("style.css"))
                )
                .withMultipartRequestBody(
                    aMultipart()
                        .withHeader("Content-Disposition", containing("name=raw.asset1"))
                        .withHeader("Content-Type", equalTo("application/octet-stream"))
                        .withBody(AnythingPattern())
                )
                .willReturn(aResponse()
                    .withStatus(200)
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
