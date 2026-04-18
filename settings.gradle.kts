import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            setUrl("https://maven.aliyun.com/repository/public/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/spring/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/google/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/gradle-plugin/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/grails-core/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/apache-snapshots/")
        }
        google()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
        id("org.jetbrains.intellij.platform") version "2.14.0"
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.14.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        maven {
            setUrl("https://maven.aliyun.com/repository/public/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/spring/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/google/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/gradle-plugin/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/grails-core/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/apache-snapshots/")
        }
        google()
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "lingma-plus"
