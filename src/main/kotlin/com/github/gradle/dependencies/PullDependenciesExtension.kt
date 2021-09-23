package com.github.gradle.dependencies

import java.io.File

/**
 * Extension for plugin configuration
 */
open class PullDependenciesExtension(
    var localRepositoryPath: File? = null,
    var loadJavaDocs: Boolean = true,
    var loadSources: Boolean = true
) {}