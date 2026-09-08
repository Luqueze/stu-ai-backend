plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.sonarqube)
}

allprojects {
    group = "com.aiexam"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        // Spring AI 1.0.0-M6 é uma versão milestone, não publicada no Maven Central
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

sonar {
    properties {
        property("sonar.projectKey", "Luqueze_stu-ai")
        property("sonar.organization", "luqueze")
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
