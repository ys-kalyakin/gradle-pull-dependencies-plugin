import java.io.FileInputStream
import java.util.*

plugins {
    java
    `java-gradle-plugin`
    `maven-publish`
    `signing`
    kotlin("jvm") version "1.5.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("com.gradle.plugin-publish") version "0.16.0"
}

val currentVersion = "0.3"

group = "io.github.ys-kalyakin"
version = "$currentVersion"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(gradleApi())
    testCompile("junit", "junit", "4.12")
}

gradlePlugin {
    plugins {
        create("io.github.ys-kalyakin.gradle-pull-dependencies-plugin") {
            id = "io.github.ys-kalyakin.gradle-pull-dependencies-plugin"
            implementationClass = "com.github.gradle.dependencies.PullDependenciesPlugin"
        }
    }
}

buildscript {
    dependencies {
        classpath("io.github.gradle-nexus:publish-plugin:1.1.0")
    }
}

apply(plugin = "io.github.gradle-nexus.publish-plugin")

extra["ossrhUserName"] = System.getenv("OSSRH_USERNAME")
extra["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
extra["sonatypeStagingProfileID"] = System.getenv("SONATYPE_STAGING_PROFILE_ID")
extra["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
extra["signing.password"] = System.getenv("SIGNING_PASSWORD")
extra["signing.key"] = System.getenv("SIGNING_KEY")

val localFile = project.rootProject.file("local.properties")
if (localFile.exists()) {
    val p = Properties()
    val fis = FileInputStream(localFile)
    p.load(fis)
    p.asSequence()
        .filter {(_, v) -> v != null && v != ""}
        .forEach { (k, v) -> extra[k.toString()] = v.toString()}
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("gradle-pull-dependencies-plugin")
                description.set("Gradle Plugin for pulling dependencies")
                url.set("https://github.com/ys-kalyakin/gradle-pull-dependencies-plugin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://www.mit.edu/~amini/LICENSE.md")
                    }
                }
                developers {
                    developer {
                        id.set("ys-kalyakin")
                        name.set("Yuriy Kalyakin")
                        email.set("ys.kalyakin@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/ys-kalyakin/gradle-pull-dependencies-plugin.git")
                    developerConnection.set("scm:git:ssh://github.com/ys-kalyakin/gradle-pull-dependencies-plugin.git")
                    url.set("https://github.com/ys-kalyakin/gradle-pull-dependencies-plugin")
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        extra["signing.keyId"].toString(),
        extra["signing.key"].toString(),
        extra["signing.password"].toString()
    )
    sign(publishing.publications)
}

nexusPublishing {
    repositories {
        create("central") {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(extra["ossrhUserName"].toString())
            password.set(extra["ossrhPassword"].toString())
        }
    }
}

pluginBundle {
    website = "https://github.com/ys-kalyakin/gradle-pull-dependencies-plugin"
    vcsUrl = "https://github.com/ys-kalyakin/gradle-pull-dependencies-plugin"
    description = "Gradle Plugin for pulling dependencies from remote repositories"

    (plugins) {
        "io.github.ys-kalyakin.gradle-pull-dependencies-plugin" {
            displayName = "Gradle pull dependencies plugin"
            tags = listOf("individual", "tags", "per", "plugin")
            version = "$currentVersion"
        }

    }

    mavenCoordinates {
        groupId = "io.github.ys-kalyakin"
        artifactId = "gradle-pull-dependencies-plugin"
        version = "$currentVersion"
    }
}
