dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.1.2")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.2")
    
    // MySQL连接池+驱动+配置解析
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("mysql:mysql-connector-java:8.0.33")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.velocitypowered.com/snapshots/") // Velocity官方仓库
}

tasks.shadowJar {
    archiveBaseName.set("PillowDream_joinmass-Velocity")
    archiveVersion.set("")
    relocate("com.zaxxer.hikari", "com.baiying.pillowdream.libs.hikari")
    relocate("com.moandjiezana.toml", "com.baiying.pillowdream.libs.toml")
    relocate("com.mysql.cj", "com.baiying.pillowdream.libs.mysql")
    destinationDirectory.set(file("../build/libs"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
