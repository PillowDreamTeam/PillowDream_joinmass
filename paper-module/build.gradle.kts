dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // MySQL驱动（可选）
    implementation("mysql:mysql-connector-java:8.0.33")
}

tasks.shadowJar {
    archiveBaseName.set("PillowDream_joinmass-Paper")
    archiveVersion.set("")
    relocate("com.mysql.cj", "com.baiying.pillowdream.libs.mysql")
    destinationDirectory.set(file("../build/libs"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
