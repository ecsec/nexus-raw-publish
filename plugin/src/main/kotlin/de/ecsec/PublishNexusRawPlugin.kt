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

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

const val extName = "publishNexusRaw"


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
    val deletionTimeout: Property<Long>
    val nexusUrl: Property<String>
    val repoName: Property<String>
    val repoFolder: Property<String>
    val username: Property<String>
    val password: Property<String>

    val inputDir: DirectoryProperty
}



abstract class PublishNexusRawTask : DefaultTask() {

    @get:Input
    abstract val deletionTimeout: Property<Long>
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
            this.deletionTimeout.set(it.deletionTimeout.orElse(defaultDeleteTimeout))
            this.nexusUrl.set(it.nexusUrl)
            this.repoName.set(it.repoName)
            this.repoFolder.set(it.repoFolder)
            this.username.set(it.username)
            this.password.set(it.password)

            this.inputDir.set(it.inputDir)
        }
    }

    @TaskAction
    fun doPublish(): Unit = runBlocking {
        val baseUrl = nexusUrl.get()
        val rawRepoName = repoName.get()
        val rawRepoFolder = repoFolder.get().correctPath()

        HttpClient(Java) {
            // configure client
            defaultRequest {
                url(baseUrl)
                basicAuth(username.get(), password.get())
            }
        }.use { client ->
            val api = NexusApi(client, logger)
            api.deleteRemoteContent(rawRepoName, rawRepoFolder)

            // upload each file
            logger.info("Uploading files to Nexus Raw Repo.")
            inputDir.asFileTree.forEach {
                if (!it.isDirectory) {
                    val relPath = it.toRelativeString(inputDir.asFile.get())
                    api.uploadFile(rawRepoName, "$rawRepoFolder/$relPath", it)
                }
            }
        }
    }

}
