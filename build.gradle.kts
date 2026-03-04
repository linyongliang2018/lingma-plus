plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.liangyonglin"
version = "1.2.0"

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
}

intellij {
    version.set("2024.1.7")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
}

dependencies {
    implementation(kotlin("stdlib"))
}

// 通义灵码插件的本地路径
val lingmaPluginZipPath = "D:\\sdk\\pluginTest\\lingma-jetbrains-2.6.7.zip"

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("243.*")
        changeNotes.set("""
            <h3>版本 1.2.0</h3>
            <h4>更新内容</h4>
            <ul>
                <li>✨ 新增「开始注解」：多选类后批量调用灵码注释生成功能（TriggerCosyCodeGenerateCommentGenerationAction），@Data 类整类发送、普通类按方法拆分</li>
                <li>✨ 新增「扫描贮存区修改文件」：支持基于 git diff --cached --name-only 扫描已暂存的 Java 文件，自动忽略未加入 Git 管理的文件</li>
                <li>✨ 右键菜单增强：支持对选中类执行批量优化、批量注释和批量解释，多选时行为与顶部按钮保持一致</li>
                <li>✨ 统一时间间隔配置：优化 / 注释 / 解释三种批量调用统一复用 Lingma 配置中的最小/最大时间间隔</li>
                <li>✨ Git 扫描逻辑重用：封装通用 Git 扫描与 .java 文件解析工具，最近修改与贮存区扫描共享实现</li>
            </ul>
        """.trimIndent())
    }

    // 自动安装通义灵码插件到沙箱环境
    // 必须在 prepareSandbox 之后执行，确保沙箱环境已经准备好
    register("installLingmaPlugin", Copy::class) {
        description = "自动安装通义灵码插件到调试沙箱环境"
        group = "intellij"
        
        val pluginZipFile = file(lingmaPluginZipPath)
        
        // 必须等待 prepareSandbox 完成后再执行
        mustRunAfter("prepareSandbox")
        
        // 检查插件文件是否存在
        onlyIf {
            if (!pluginZipFile.exists()) {
                println("警告: 通义灵码插件文件不存在: $lingmaPluginZipPath")
                return@onlyIf false
            }
            true
        }
        
        // 从 zip 文件解压
        from(zipTree(pluginZipFile))
        
        // 复制到沙箱的 plugins 目录
        into(layout.buildDirectory.dir("idea-sandbox/plugins"))
        
        // Gradle Copy 任务默认会覆盖目标文件，因此无需额外设置 isOverwrite。
        // 如果要确保覆盖，可以使用 duplicatesStrategy
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        
        doFirst {
            val targetPluginsDir = layout.buildDirectory.dir("idea-sandbox/plugins").get().asFile
            println("正在安装通义灵码插件...")
            println("  源文件: ${pluginZipFile.absolutePath}")
            println("  目标目录: ${targetPluginsDir.absolutePath}")
        }
        
        doLast {
            val targetPluginsDir = layout.buildDirectory.dir("idea-sandbox/plugins").get().asFile
            val installedPlugins = targetPluginsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            println("✓ 通义灵码插件已自动安装到沙箱环境")
            println("  目标位置: ${targetPluginsDir.absolutePath}")
            println("  已安装的插件: ${installedPlugins.joinToString(", ")}")
        }
    }

    // 让 prepareSandbox 完成后自动安装插件
    // 这样无论是通过 runIde 还是直接运行，插件都会在沙箱准备后自动安装
    named("prepareSandbox") {
        finalizedBy("installLingmaPlugin")
    }

    // 确保 runIde 也会安装插件
    named("runIde") {
        dependsOn("installLingmaPlugin")
    }
}

