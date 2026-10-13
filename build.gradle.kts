
plugins {
    java
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/nukkit.jar"))
    compileOnly(files("libs/LlamaEconomy-1.0.0.1-SNAPSHOT.jar"))
    compileOnly(files("libs/LuckPerms-Nukkit-5.5.20.jar"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
