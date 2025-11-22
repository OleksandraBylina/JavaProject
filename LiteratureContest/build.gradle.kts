plugins {
    id("java")
    id("application")
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

application {
    mainClass.set("server.HttpServer")
}
