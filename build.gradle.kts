plugins {
    java
    application
    id("com.gradleup.shadow") version "9.3.1"   // 最新版（2026年1月發布），完全支援 Gradle 9.3+
}

group = "me.manu.botlauncher"   // 可改成你喜歡的
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))   // 保持 Java 8 兼容，也支援到 21/25
    }
}

application {
    mainClass.set("BotLauncher")   // 如果你的 BotLauncher.java 有 package，例如 package com.example; 則改成 "com.example.BotLauncher"
}

repositories {
    mavenCentral()
}

tasks.withType<Jar> {
    archiveVersion.set("")   // ← 關鍵修正：直接設空字串，避免 null 問題
    // 或如果你想保留版本號： archiveVersion.set(project.version as String)
}

tasks.shadowJar {
    archiveBaseName.set("BotLauncher")
    archiveClassifier.set("")          // 沒有 -all 或其他後綴 → 輸出 BotLauncher.jar
    // archiveVersion.set("")          // 如果上面全局沒設，這裡也可以單獨設

    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    mergeServiceFiles()                // 合併 META-INF/services 等，防衝突
}

// 讓 gradle build 自動包含 shadowJar
tasks.build {
    dependsOn(tasks.shadowJar)
}