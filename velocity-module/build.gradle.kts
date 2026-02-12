dependencies {
    // 编译时依赖Velocity API（仅用于编译，打包时排除）
    compileOnly("com.velocitypowered:velocity-api:3.1.2")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.2")
    // MySQL驱动（运行时依赖）
    implementation("mysql:mysql-connector-java:8.0.33")
}

repositories {
    mavenCentral()
    // Velocity官方仓库
    maven("https://repo.velocitypowered.com/releases/")
}

tasks.shadowJar {
    archiveBaseName.set("PillowDream_joinmass-Velocity")
    archiveVersion.set("")
    // 排除Velocity API
    exclude("com/velocitypowered/api/**")
    relocate("com.mysql.cj", "com.baiying.pillowdream.libs.mysql")
    destinationDirectory.set(file("../build/libs"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
