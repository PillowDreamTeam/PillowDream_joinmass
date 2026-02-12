dependencies {
    // MySQL驱动
    implementation("mysql:mysql-connector-java:8.0.33")
}

repositories {
    mavenCentral()
}

tasks.shadowJar {
    archiveBaseName.set("PillowDream_joinmass-Velocity")
    archiveVersion.set("")
    relocate("com.mysql.cj", "com.baiying.pillowdream.libs.mysql")
    // 排除本地模拟的API类
    exclude("com/velocitypowered/api/**")
    exclude("javax/inject/**")
    destinationDirectory.set(file("../build/libs"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
