# Gradle Plugin for pulling dependencies from remote repositories
Plugin adds the task ```pullDependencies``` that pulls dependencies into a local repository that can be used as maven repository

# Usage 
build.gradle
```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'io.github.ys-kalyakin:gradle-pull-dependencies-plugin:0.1'
    }
}

apply plugin: 'io.github.ys-kalyakin.gradle-pull-dependencies-plugin'

pullDependencies {
    localRepositoryPath = Paths.get(rootDir.absolutePath, 'libs', 'repository').toFile()
    loadJavaDocs = true
    loadSources = true
}
```