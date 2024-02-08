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

import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.isSuccessful
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.Instant
import kotlin.random.Random

val extName = "publishNexusRaw"

/**
 * Plugin that publishes data to a nexus raw repository.
 */
class PublishNexusRawPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(extName, PublishNexusRawExtension::class.java)
        // Register a task
        project.tasks.register("publishNexusRaw", PublishNexusRawTask::class.java) {
            it.group = "publishing"
            it.description = "Publishes data to a Sonatype Nexus raw repository."
        }
    }
}

interface PublishNexusRawExtension {
    val nexusUrl: Property<String>
    val repoName: Property<String>
    val repoFolder: Property<String>
    val username: Property<String>
    val password: Property<String>

    val inputDir: DirectoryProperty
}



abstract class PublishNexusRawTask : DefaultTask() {

    private val waitTimeout = 30 * 1000L

    @get:Input
    abstract val nexusUrl: Property<String>
    @get:Input
    abstract val repoName: Property<String>
    @get:Input
    abstract val repoFolder: Property<String>
    @get:Input
    abstract val username: Property<String>
    @get:Input
    abstract val password: Property<String>

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    init {
        // map extension properties to task properties
        project.extensions.configure<PublishNexusRawExtension>(extName) {
            this.nexusUrl.set(it.nexusUrl)
            this.repoName.set(it.repoName)
            this.repoFolder.set(it.repoFolder)
            this.username.set(it.username)
            this.password.set(it.password)

            this.inputDir.set(it.inputDir)
        }
    }

    @TaskAction
    fun doPublish() {
        val baseUrl = nexusUrl.get().let {
            // make sure the base url does not end with /
            if (it.endsWith("/")) it.dropLast(1) else it
        }
        val rawRepoName = repoName.get()
        val rawRepoFolder = repoFolder.get().let {
            // make sure the folder name does not start with /
            if (it.startsWith("/")) it.drop(1) else it
        }

        deleteRemoteContent(baseUrl, rawRepoName, rawRepoFolder)
        waitForContentDeletion(baseUrl, rawRepoName, rawRepoFolder)

        // upload each file
        logger.info("Uploading files to Nexus Raw Repo.")
        inputDir.asFileTree
            .visit {
                if (!it.isDirectory) {
                    val relPath = it.relativePath.pathString
                    uploadFile(baseUrl, rawRepoName, rawRepoFolder, relPath, it.file)
                }
            }
    }

    fun deleteRemoteContent(baseUrl: String, rawRepoName: String, rawRepoFolder: String) {
        // expected: 200 -> {"tid":10,"action":"coreui_Component","method":"deleteFolder","result":{"success":true,"data":null},"type":"rpc"}
        val tid = Random.nextInt()
        "$baseUrl/service/extdirect".httpPost()
            .header("Content-Type" to "application/json")
            .authentication().basic(username.get(), password.get())
            .body("""{"action": "coreui_Component", "method": "deleteFolder", "data": ["${rawRepoFolder}", "${rawRepoName}"], "type": "rpc", "tid": $tid}""")
            .responseString().also { (_, response, result) ->
                if (response.isSuccessful) {
                    logger.debug("Remote content deletion command sent successfully.")
                    // TODO: check content of response
                } else {
                    logger.error("Failed to delete remote content: ${result.get()}")
                    throw RuntimeException("Failed to delete remote content: ${result.get()}")
                }
            }
    }

    fun waitForContentDeletion(baseUrl: String, rawRepoName: String, rawRepoFolder: String) {
        var contentNotFound = false
        var startTime = Instant.now()

        while (!contentNotFound) {
            val (_, response, _) = "$baseUrl/service/rest/repository/browse/$rawRepoName/$rawRepoFolder/".httpGet()
                .authentication().basic(username.get(), password.get())
                .response()
            if (response.statusCode == 404) {
                logger.info("Remote content deleted successfully.")
                contentNotFound = true
            } else if (response.isSuccessful) {
                Thread.sleep(1000)
            } else {
                val msg = "Check for remote content deletion of '$rawRepoName:$rawRepoFolder' failed with status code ${response.statusCode}."
                logger.error(msg)
                throw RuntimeException(msg)
            }

            // check timeout
            if (Instant.now().isAfter(startTime.plusMillis(waitTimeout))) {
                val msg = "Timeout waiting for remote content deletion of '$rawRepoName:$rawRepoFolder'."
                logger.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    fun uploadFile(baseUrl: String, rawRepoName: String, rawRepoFolder: String, relPath: String, file: File) {
        logger.debug("Uploading file '$relPath'.")

        "$baseUrl/repository/$rawRepoName/$rawRepoFolder/$relPath".httpPut()
            .authentication().basic(username.get(), password.get())
            .header("Content-Type", FileDataPart.guessContentType(file))
            .body(file)
            .responseString().also { (_, response, _) ->
                if (response.isSuccessful) {
                    logger.debug("File '$relPath' uploaded successfully.")
                } else {
                    val msg = "Failed to upload file '$relPath'."
                    logger.error(msg)
                    throw RuntimeException(msg)
                }
            }
    }

}
