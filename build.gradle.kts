plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = "com.picpay.fps"
    version = "0.1.0"

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
    }
}
