import org.gradle.internal.os.OperatingSystem

plugins {
    application
}

val lwjglVersion = property("lwjglVersion") as String
val jomlVersion = property("jomlVersion") as String

val lwjglNatives = when {
    OperatingSystem.current().isMacOsX -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    OperatingSystem.current().isLinux -> "natives-linux"
    else -> "natives-windows"
}

dependencies {
    implementation(project(":shared"))
    implementation("io.netty:netty-all:${property("nettyVersion")}")
    implementation("org.joml:joml:$jomlVersion")

    // LWJGL BOM
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    // LWJGL modules
    listOf(
        "lwjgl",
        "lwjgl-glfw",
        "lwjgl-vulkan",
        "lwjgl-stb",
        "lwjgl-shaderc",
        "lwjgl-openal"
    ).forEach { module ->
        implementation("org.lwjgl:$module")
        if (module != "lwjgl-vulkan" || OperatingSystem.current().isMacOsX) {
            runtimeOnly("org.lwjgl:$module::$lwjglNatives")
        }
    }
}

application {
    mainClass.set("com.picpay.fps.client.game.GameClient")
}

tasks.named<JavaExec>("run") {
    // Required for Java 21+ with LWJGL native access
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED")
    // Required for macOS GLFW
    if (OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
        // Vulkan/MoltenVK discovery via Homebrew
        environment("DYLD_LIBRARY_PATH", "/opt/homebrew/lib:" + (System.getenv("DYLD_LIBRARY_PATH") ?: ""))
        environment("VK_ICD_FILENAMES", System.getenv("VK_ICD_FILENAMES") ?: "")
    }
    // Pass arguments: serverHost playerName
    args = listOf(
        System.getProperty("server") ?: "127.0.0.1",
        System.getProperty("name") ?: "Player"
    )
}
