import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.xguard"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("com.intellij.java")
        instrumentationTools()
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.xguard.jetbrains-plugin"
        name = "XGuard"
        version = "1.0.0"
        description = """
            XGuard - Development-time Security Guardrail for LLM Applications.
            
            Detects semantic security risks in Prompt/Agent code in real-time,
            provides attribution explanations and fix suggestions.
            Based on YuFeng-XGuard-Reason model.
        """.trimIndent()

        vendor {
            name = "XGuard Team"
        }

        ideaVersion {
            sinceBuild = "232"
            untilBuild = "242.*"
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
        }
    }

    test {
        useJUnitPlatform()
    }
}
