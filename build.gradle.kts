import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.liangyonglin"
version = "1.5.3"

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.4")
        bundledPlugin("com.intellij.java")
    }
    implementation(kotlin("stdlib"))
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "252.*"
        }
        changeNotes.set(
            """
            <h3>版本 1.5.3</h3>
            <h4>更新内容</h4>
            <ul>
                <li>🔧 构建迁移至 IntelliJ Platform Gradle Plugin 2.x，开发目标 IC 2025.2.4，兼容 2024.2–2025.2.x（字节码/Java 21）</li>
                <li>📝 JavaClassScanner 按钮两行布局（继承 1.5.2）</li>
            </ul>
            """.trimIndent(),
        )
    }
}

// 通义灵码插件的本地路径
val lingmaPluginZipPath = "D:\\sdk\\pluginTest\\tongyi-jetbrains-2.1.5.zip"

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    val prepareSandbox = named<PrepareSandboxTask>("prepareSandbox")

    register<Copy>("installLingmaPlugin") {
        description = "自动安装通义灵码插件到调试沙箱环境"
        group = "intellij"

        val pluginZipFile = file(lingmaPluginZipPath)

        mustRunAfter(prepareSandbox)

        onlyIf {
            if (!pluginZipFile.exists()) {
                println("警告: 通义灵码插件文件不存在: $lingmaPluginZipPath")
                return@onlyIf false
            }
            true
        }

        from(zipTree(pluginZipFile))
        into(prepareSandbox.flatMap { it.sandboxPluginsDirectory })
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        doFirst {
            val targetPluginsDir = prepareSandbox.get().sandboxPluginsDirectory.get().asFile
            println("正在安装通义灵码插件...")
            println("  源文件: ${pluginZipFile.absolutePath}")
            println("  目标目录: ${targetPluginsDir.absolutePath}")
        }

        doLast {
            val targetPluginsDir = prepareSandbox.get().sandboxPluginsDirectory.get().asFile
            val installedPlugins = targetPluginsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            println("✓ 通义灵码插件已自动安装到沙箱环境")
            println("  目标位置: ${targetPluginsDir.absolutePath}")
            println("  已安装的插件: ${installedPlugins.joinToString(", ")}")
        }
    }

    prepareSandbox {
        finalizedBy("installLingmaPlugin")
    }

    named("runIde") {
        dependsOn("installLingmaPlugin")
    }

    named("buildSearchableOptions") {
        dependsOn("installLingmaPlugin")
    }
}
