plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    alias(libs.plugins.publish)

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    alias(libs.plugins.jvm)

    // coverage report
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}


repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation(libs.fuel)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use Kotlin Test framework
            useKotlinTest(libs.versions.kotlin)
        }

        // Create a new test suite
        val functionalTest by registering(JvmTestSuite::class) {
            // Use Kotlin Test framework
            useKotlinTest(libs.versions.kotlin)

            dependencies {
                // functionalTest test suite depends on the production code in tests
                implementation(project())
                implementation(libs.wiremock)
            }

            targets {
                all {
                    // This test suite should run after the built-in test suite has run its tests
                    testTask.configure { shouldRunAfter(test) }
                }
            }
        }
    }
}

gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])

tasks.named<Task>("check") {
    // Include functionalTest as part of the check lifecycle
    dependsOn(testing.suites.named("functionalTest"))
}


gradlePlugin {
    vcsUrl = "https://github.com/ecsec/nexus-raw-publish"
    website = "https://github.com/ecsec/nexus-raw-publish"

    plugins {
        create("publishNexusRaw") {
            id = "de.ecsec.nexus-raw-publish"
            displayName = "Nexus Raw Publish Plugin"
            description = "Gradle plugin that publishes data to a Sonatype Nexus raw repository."
            tags.set(listOf("sontaype", "nexus", "publish", "raw", "repository"))
            implementationClass = "de.ecsec.PublishNexusRawPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "ecsecReleases"
            url = uri("https://mvn.ecsec.de/repository/openecard-release/")
            credentials {
                username = System.getenv("MVN_ECSEC_USERNAME") ?: project.findProperty("mvnUsernameEcsec") as String?
                password = System.getenv("MVN_ECSEC_PASSWORD") ?: project.findProperty("mvnPasswordEcsec") as String?
            }
        }
    }
}
