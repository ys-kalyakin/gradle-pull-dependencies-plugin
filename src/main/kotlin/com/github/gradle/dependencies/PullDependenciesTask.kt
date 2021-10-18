package com.github.gradle.dependencies

import org.apache.tools.ant.util.XmlConstants.FEATURE_DISALLOW_DTD
import org.apache.tools.ant.util.XmlConstants.FEATURE_EXTERNAL_ENTITIES
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.File.pathSeparator
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pull project dependencies task
 */
abstract class PullDependenciesTask : DefaultTask() {
    @get:OutputDirectory
    abstract val localRepositoryPath: RegularFileProperty

    @Input
    val isLoadJavaDocs: Property<Boolean> = property(default = true)

    @Input
    val isLoadSources: Property<Boolean> = property(default = true)

    @TaskAction
    fun process() {
        pullDependencies(project)
        project.subprojects.forEach(this::pullDependencies)
    }

    private fun pullDependencies(project: Project) {
        val result = (project.configurations + project.buildscript.configurations)
            .asSequence()
            .filter { it.isCanBeResolved }
            .map {
                Pair(it.incoming.resolutionResult.allDependencies.asSequence()
                    .filter { d -> d is DefaultResolvedDependencyResult }
                    .map { d -> d as DefaultResolvedDependencyResult }
                    .map { d -> d.selected.id }
                    .toList(),
                    it.incoming.files.map { f -> f.name to f }.toMap()
                )
            }
            .reduce { p1, p2 -> p1.first + p2.first to p1.second + p2.second }

        result.first.asSequence()
            .filter { it is ModuleComponentIdentifier }
            .map { it as ModuleComponentIdentifier }
            .forEach {
                findLibrary(result.second, it)?.let { f -> saveFile(it, f) }
            }

        listOf(
            MavenModule::class.java as Class<out Component> to listOf(MavenPomArtifact::class.java to true),
            Pair(
                JvmLibrary::class.java as Class<out Component>, when {
                    isLoadJavaDocs.get() && isLoadSources.get() -> listOf(
                        SourcesArtifact::class.java to false,
                        JavadocArtifact::class.java to false
                    )
                    isLoadJavaDocs.get() -> listOf(JavadocArtifact::class.java to false)
                    else -> listOf(SourcesArtifact::class.java to false)
                }
            )
        ).map { (component, artifacts) ->
            toResolvedComponents(result.first, component, artifacts) to artifacts
        }
        .forEach {
            it.first.forEach { r ->
                processArtifactResult(r, it.second)
            }
        }
    }

    private fun toResolvedComponents(
        componentIdentifiers: Collection<ComponentIdentifier>,
        component: Class<out Component>,
        artifacts: Collection<Pair<Class<out Artifact>, Boolean>>
    ): Set<ComponentArtifactsResult> {
        return project.dependencies.createArtifactResolutionQuery()
            .forComponents(componentIdentifiers)
            .withArtifacts(component, artifacts.map { it.first })
            .execute()
            .resolvedComponents
    }

    private fun findLibrary(fMap: Map<String, File>, identifier: ModuleComponentIdentifier): File? {
        val fileName = identifier.displayName.substringAfter(":").replace(":", "-")

        listOf("jar", "pom").forEach { _ ->
            val file = fMap[fileName]
            if (file != null) {
                return file
            }
        }

        fMap.forEach {
            if (it.key.startsWith(fileName)) {
                return it.value
            }
        }

        return null
    }

    private fun processArtifactResult(
        artifactResult: ComponentArtifactsResult,
        artifactTypes: Collection<Pair<Class<out Artifact>, Boolean>>
    ) {
        artifactTypes.forEach { (artifactType, hasParent) ->
            artifactResult.getArtifacts(artifactType)
                .filterIsInstance<DefaultResolvedArtifactResult>()
                .forEach { result ->
                    saveFile(result.id.componentIdentifier as ModuleComponentIdentifier, result.file)
                    if (hasParent)
                        resolveParentPom(result.file)
                }
        }
    }

    private fun resolveParentPom(pom: File) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            with(factory) {
                setAttribute(FEATURE_EXTERNAL_ENTITIES, false)
                setAttribute(FEATURE_DISALLOW_DTD, false)
                setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
            val documentBuilder = factory.newDocumentBuilder()
            val document = documentBuilder.parse(pom)
            val parentNodeList = document.getElementsByTagName("parent")
            if (parentNodeList.length > 0) {
                //val parent = parentNodeList.item(0).textContent
                val parentNode = parentNodeList.item(0) as Element
                val moduleId = ModuleComponentIdentifierImpl(
                    parentNode.getElementsByTagName("groupId").item(0).textContent,
                    parentNode.getElementsByTagName("artifactId").item(0).textContent,
                    parentNode.getElementsByTagName("version").item(0).textContent
                )
                toResolvedComponents(
                    listOf(moduleId),
                    MavenModule::class.java,
                    listOf(MavenPomArtifact::class.java to true)
                ).forEach { result ->
                    processArtifactResult(result, listOf(MavenPomArtifact::class.java to true))
                }
            }
            var dependenciesNodeList = document.getElementsByTagName("dependencies")
            if (dependenciesNodeList.length > 0) {
                dependenciesNodeList = (dependenciesNodeList.item(0) as Element).getElementsByTagName("dependency")
                for (i in 0 until dependenciesNodeList.length) {
                    val dependency = dependenciesNodeList.item(i) as Element
                    dependency.getElementValueByTagName("type")?.let {
                        if ("pom" == it) {
                            val moduleId = ModuleComponentIdentifierImpl(
                                dependency.getElementValueByTagName("groupId")?: "",
                                dependency.getElementValueByTagName("artifactId")?: "",
                                findVersion(document, dependency.getElementValueByTagName("version")?: "")
                            )
                            toResolvedComponents(
                                listOf(moduleId),
                                MavenModule::class.java,
                                listOf(MavenPomArtifact::class.java to true)
                            ).forEach { result ->
                                processArtifactResult(result, listOf(MavenPomArtifact::class.java to true))
                            }
                        }
                    }

                }
            }
        } catch (e: Exception) {
            logger.error("Error while parse POM", e)
        }
    }

    private fun findVersion(document: Document, property: String): String {
        val properties = document.documentElement.getElementsByTagName("properties")
        if (properties.length > 0) {
            (properties.item(0) as Element).getElementValueByTagName(property)?.let {
                if (it.startsWith("$"))
                    return findVersion(document, it)
            }
        }
        return property
    }

    private fun saveFile(identifier: ModuleComponentIdentifier, file: File) {
        val path = identifier.group.split(".") + identifier.module + identifier.version
        val repositoryPath = Paths.get(
            localRepositoryPath.get().asFile.absolutePath,
            path.joinToString(separator = pathSeparator)
        )
        repositoryPath.toFile().mkdirs()
        val destinationPath = Paths.get(repositoryPath.toAbsolutePath().toString(), file.name)

        logger.info("Saving file ${file.name} to $destinationPath")
        if (!Files.exists(destinationPath)) {
            Files.copy(file.toPath(), destinationPath, REPLACE_EXISTING)
        }
    }
}

/**
 * Module description
 */
data class ModuleComponentIdentifierImpl(
    private val _group: String,
    private val _module: String,
    private val _version: String
) : ModuleComponentIdentifier {
    override fun getGroup(): String = _group
    override fun getModule(): String = _module
    override fun getVersion(): String = _version

    override fun getDisplayName(): String {
        return listOf(group, module, version).joinToString(separator = ":")
    }

    override fun getModuleIdentifier(): ModuleIdentifier {
        return object : ModuleIdentifier {
            override fun getGroup(): String {
                return this@ModuleComponentIdentifierImpl.group
            }

            override fun getName(): String {
                return module
            }
        }
    }
}

internal fun Element.getElementValueByTagName(tagName: String): String? {
    val nodeList = this.getElementsByTagName(tagName)
    if (nodeList.length > 0)
        return nodeList.item(0).textContent
    return null
}

internal inline fun <reified T> DefaultTask.property(default: T? = null): Property<T> =
    project.objects.property(T::class.java).apply {
        set(default)
    }
