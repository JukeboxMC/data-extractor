plugins {
    kotlin("jvm") version "1.9.20"
}

group = "org.jukeboxmc.extractor"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

dependencies {
    implementation("com.nimbusds:nimbus-jose-jwt:9.10.1")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.cloudburstmc.protocol:bedrock-codec:3.0.0.Beta1-SNAPSHOT")
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta1-SNAPSHOT")
}