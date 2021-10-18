package com.github.gradle.dependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.nio.file.Paths
import java.util.*

/**
 * Pull project dependencies plugin
 */
class PullDependenciesPlugin : Plugin<Project> {
    companion object {
        private const val TASK_NAME = "pullDependencies"
    }

    override fun apply(target: Project) {
        target.configure(listOf(target)) {
            it.extensions.create(TASK_NAME, PullDependenciesExtension::class.java)
        }

        target.tasks.register(TASK_NAME, PullDependenciesTask::class.java) {
            val extension = target.extensions.getByName(TASK_NAME) as PullDependenciesExtension
            it.localRepositoryPath.set(getLocalRepositoryPath(target))
            it.isLoadJavaDocs.set(extension.loadJavaDocs)
            it.isLoadSources.set(extension.loadSources)
        }
    }

    private fun getLocalRepositoryPath(project: Project) : File? {
        return Optional.ofNullable(project.extensions.getByName(TASK_NAME))
            .map { it as PullDependenciesExtension}
            .map { it.localRepositoryPath }
            .orElse(Paths.get(project.projectDir.absolutePath, "libs", "repository").toFile())
    }
}