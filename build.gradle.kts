plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = "com.baiying"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// 只为子模块单独配置Java版本（避开根项目语法问题）
subprojects {
    apply(plugin = "java") // 子项目统一应用java插件
    apply(plugin = "com.github.johnrengelman.shadow") // 子项目统一应用shadow插件

    // 正确配置Java版本（Kotlin DSL标准写法）
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 统一构建任务：打包两个模块
tasks.register("buildAll") {
    dependsOn(":velocity-module:shadowJar")
    dependsOn(":paper-module:shadowJar")
    group = "build"
    description = "Build both Velocity and Paper modules"
}
