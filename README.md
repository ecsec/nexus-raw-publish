# About

This is a Gradle plugin to publish files to a Nexus raw repository.
It has been build to upload documentation to a Nexus repository of type raw, but it can be used for any other file type as well.
The plugin is compatible with Sonatype Nexus version 3.

This is a very early version of the plugin.
In case you miss any features, feel free to open an issue or a pull request. 


# Installation

The plugin is availabe on the ecsec Nexus Repository.
To use it, add the following entry to your `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
		maven {
			url = uri("https://mvn.ecsec.de/repository/public")
		}
    }
}
```

The plugin can then be loaded in the `build.gradle.kts` file as follows:
```kotlin
plugins {
	id("de.ecsec.nexus-raw-publish") version "0.9.0"
}
```

# Configuration

The plugin can be configured using the `publishNexusRaw` extension and identical properties in the `PublishNexusRawTask`.

The following properties are available:
- `nexusUrl` (String): The base URL of the Nexus installation. Typically something like `https://mvn.example.com/`. 
- `repoName` (String): The name of the repository.
- `repoFolder` (String): The folder in the repository to upload to.
- `username` (String): The username to authenticate with.
- `password` (String): The password to authenticate with.
- `inputDir` (Directory): The directory whose content to upload to the Nexus repository.

The plugin automatically registers a task with the name `publishNexusRaw`, which uses the configuration values from the extension.
The [example section](#Example) below provides more details on how the plugin could be configured.


# Example

```kotlin
import de.ecsec.PublishNexusRawTask

publishNexusRaw {
	nexusUrl = "https://mvn.example.com/"
	repoName = "docs-repo"
	username = System.getenv("MVN_EXAMPLE_USERNAME") ?: project.findProperty("mvnUsernameExample") as String?
	password = System.getenv("MVN_EXAMPLE_PASSWORD") ?: project.findProperty("mvnPasswordExample") as String?
	inputDir = layout.buildDirectory.dir("docs/asciidoc")
}

tasks.register("publishNexusRawVersion", PublishNexusRawTask::class) {
	group = "publishing"
	repoFolder = "mysoftware/doc/${project.version}"
	dependsOn("asciidoctor")
}
tasks.register("publishNexusRawLatest", PublishNexusRawTask::class) {
	group = "publishing"
	repoFolder = "mysoftware/doc/latest"
	dependsOn("asciidoctor")
}
tasks.register("publishNexusRawDev", PublishNexusRawTask::class) {
	group = "publishing"
	repoFolder = "mysoftware/doc/dev"
	dependsOn("asciidoctor")
}
```


# License

This work is licensed under the GNU GPLv3.
