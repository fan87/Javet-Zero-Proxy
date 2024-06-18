plugins {
    id("java-library")
    kotlin("jvm") version "1.9.20"
    `maven-publish`
}

group = "me.fan87"
version = "1.0-SNAPSHOT"


// Include `java` directory into the build
sourceSets.all {
    kotlin.srcDirs("src/${name}/java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.caoccao.javet:javet:3.1.3") // Linux and Windows (x86_64)


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

if (tasks.findByName("compileJava") != null) {
    tasks.compileJava {
        options.encoding = "utf-8"
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components.getByName("java"))
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "GitHub"
            url = uri("https://maven.pkg.github.com/fan87/Javet-Zero-Proxy")
            credentials {
                username = project.findProperty("gpr.user")?.toString() ?: System.getenv("GH_USER")
                password = project.findProperty("gpr.key")?.toString() ?: System.getenv("GH_KEY")
            }
        }
    }
    publications.configureEach {
        if (this is MavenPublication) {
            pom {
                url.set("https://github.com/fan87/Javet-Zero-Proxy")
                scm {
                    url.set("https://github.com/fan87/Javet-Zero-Proxy")
                }
                licenses {
                    license {
                        name.set("Apache License v2.0")
                        url.set("https://apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
            }
        }
    }
}