plugins {
    application
}

dependencies {
    implementation(project(":shared"))
    implementation("io.netty:netty-all:${property("nettyVersion")}")
    implementation("org.joml:joml:${property("jomlVersion")}")
}

application {
    mainClass.set("com.picpay.fps.server.GameServer")
}
