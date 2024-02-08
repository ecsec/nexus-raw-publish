package de.ecsec

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull


class PublishNexusRawPluginTest {
    @Test fun `plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("de.ecsec.nexus-raw-publish")

        // Verify the result
        assertNotNull(project.tasks.findByName("publishNexusRaw"))
    }
}
