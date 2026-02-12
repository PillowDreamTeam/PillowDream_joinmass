plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = "com.baiying"
    version = "1.0.0"

    // 仅对应用了java插件的子项目配置sourceCompatibility
    plugins.withId("java") {
        java {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17 // 新增targetCompatibility，保证编译输出兼容
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// 统一构建任务：打包两个模块
tasks.register("buildAll") {
    dependsOn(":velocity-module:shadowJar")
    dependsOn(":paper-module:shadowJar")
    group = "build"
    description = "Build both Velocity and Paper modules"
}
