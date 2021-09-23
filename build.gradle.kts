plugins {
    java
    `java-gradle-plugin`
    kotlin("jvm") version "1.5.0"
}

group = "com.github"
version = "0.1-SNAPSHOT"

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
        create("gradle-pull-dependencies-plugin") {
            id = "gradle-pull-dependencies-plugin"
            implementationClass = "com.github.gradle.dependencies.PullDependenciesPlugin"
        }
    }
}