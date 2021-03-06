/*
 * MIT License
 *
 * Copyright (C) 2020 The SimpleCloud authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package eu.thesimplecloud.launcher.dependency

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.thesimplecloud.api.depedency.Dependency
import eu.thesimplecloud.api.external.ResourceFinder
import eu.thesimplecloud.launcher.startup.Launcher
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet


class DependencyLoader : IDependencyLoader {


    companion object {
        val INSTANCE = DependencyLoader()
    }

    private val repositories = CopyOnWriteArraySet<String>()

    private val dependencies = CopyOnWriteArraySet<Dependency>()

    private val webSearchedDependencies = CopyOnWriteArraySet<Dependency>()

    private val loadedDependencies = CopyOnWriteArraySet<Dependency>()

    override fun addRepositories(vararg repositories: String) {
        this.repositories.addAll(repositories)
    }

    fun addRepositories(repositories: List<String>) {
        this.repositories.addAll(repositories)
    }

    fun addDependencies(vararg dependencies: Dependency) {
        this.dependencies.addAll(dependencies)
    }

    fun addDependencies(dependencies: List<Dependency>) {
        this.dependencies.addAll(dependencies)
    }

    override fun downloadDependencies(dependencies: List<Dependency>) {
        val allDependencies = ArrayList<Dependency>()
        for (dependency in dependencies) {
            if (dependency.getDownloadedFile().exists()) continue
            allDependencies.add(dependency)
            appendSubDependenciesOfDependency(dependency, allDependencies, true)
        }
        allDependencies.filter { it.groupId != "junit"}.let { dependencies ->
            removeAllRedundantDependencies(dependencies).forEach { downloadDependency(it) }
        }

    }

    override fun installDependencies() {
        Dependency.POM_DIR.mkdirs()
        checkDependenciesToDownloadDependencies()
        val allDependencies = this.dependencies.filter { it.groupId != "org.slf4j" }.toMutableList()
        downloadDependencies(allDependencies)
        this.dependencies.forEach { appendSubDependenciesOfDependency(it, allDependencies, false) }
        allDependencies.filter { it.groupId != "junit" }.let { dependencies ->
            removeAllRedundantDependencies(dependencies).forEach { installDependency(it) }
        }

    }


    private fun isLoggerAvailable() =
            try {
                Launcher.instance.logger
                true
            } catch (ex: Exception) {
                false
            }
    /*
    fun installDependencies() {
        val file = File("dependencies/")
        file.mkdirs()
        checkDependenciesToDownloadDependencies()

        if (isLoggerAvailable()) Launcher.instance.logger.console("Starting loading pom information of dependencies...") else println("Starting loading pom information of dependencies...")
        val allDependencies = ArrayList<Dependency>()
        allDependencies.addAll(this.dependencies)
        if (!allDependencies.all { it.getDownloadedFile().exists() })
            this.dependencies.forEach { appendSubDependenciesOfDependency(it, allDependencies) }
        removeAllRedundantDependencies(allDependencies.filter { it.groupId != "junit" }).forEach { installDependency(it) }
        if (isLoggerAvailable())
            Launcher.instance.logger.success("Installed dependencies successfully.")
        else
            println("Installed dependencies successfully.")
    }
        */

    private fun removeAllRedundantDependencies(allDependencies: List<Dependency>): Collection<Dependency> {
        val list = HashSet<Dependency>()
        for (dependency in allDependencies) {
            val equalDependencies = allDependencies.filter { (dependency.groupId + "-" + dependency.artifactId) == (it.groupId + "-" + it.artifactId) }
            val singleDependency = equalDependencies.reduce { acc, dependency -> getDependencyWithNewerVersion(acc, dependency) }
            list.add(singleDependency)
        }
        return list.distinct()
    }

    private fun getDependencyWithNewerVersion(dependency: Dependency, otherDependency: Dependency): Dependency {
        val dependencyVersion = getVersionStringAsIntArray(dependency.version)
        val otherDependencyVersion = getVersionStringAsIntArray(dependency.version)
        if (dependencyVersion[0] > otherDependencyVersion[0]) return dependency
        if (otherDependencyVersion[0] > dependencyVersion[0]) return otherDependency

        if (dependencyVersion[1] > otherDependencyVersion[1]) return dependency
        if (otherDependencyVersion[1] > dependencyVersion[1]) return otherDependency

        if (dependencyVersion[2] > otherDependencyVersion[2]) return dependency
        if (otherDependencyVersion[2] > dependencyVersion[2]) return otherDependency

        return dependency
    }

    private fun getVersionStringAsIntArray(version: String): Array<Int> {
        val versionParts = version.split(".")
        val major = versionParts[0].toInt()
        val minor = versionParts[1].toInt()
        val patch = versionParts.getOrNull(2)
        val pathBuilder = StringBuilder()
        if (patch == null) return arrayOf(major, minor, 0)
        for (char in patch.toCharArray()) {
            if (char - '0' in 0..9) {
                pathBuilder.append(char)
            } else {
                break
            }
        }
        return arrayOf(major, minor, pathBuilder.toString().toInt())
    }

    private fun checkDependenciesToDownloadDependencies() {
        val dependencies = listOf(Dependency("org.apache.maven", "maven-model", "3.3.9"),
                Dependency("org.codehaus.plexus", "plexus-utils", "3.3.0"),
                Dependency("com.google.code.gson", "gson", "2.8.6"),
                Dependency("org.codehaus.woodstox", "stax2-api", "4.2"),
                Dependency("com.fasterxml.woodstox", "woodstox-core", "6.0.2"),
                Dependency("com.fasterxml.jackson.core", "jackson-databind", "2.10.1"),
                Dependency("com.fasterxml.jackson.core", "jackson-annotations", "2.10.1"),
                Dependency("com.fasterxml.jackson.core", "jackson-core", "2.10.1"),
                Dependency("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", "2.10.1"))
        try {
            Class.forName("org.apache.maven.model.io.xpp3.MavenXpp3Reader")
        } catch (e: Exception) {
            dependencies.forEach { installDependency(it) }
        }

    }

    private fun appendSubDependenciesOfDependency(dependency: Dependency, dependencyList: MutableList<Dependency>, useWeb: Boolean) {
        if (useWeb && this.webSearchedDependencies.contains(dependency)) return
        if (useWeb) {
            if (isLoggerAvailable())
                Launcher.instance.logger.console("Searching dependencies of ${dependency.artifactId}-${dependency.version}")
            else
                println("Searching dependencies of ${dependency.artifactId}-${dependency.version}")
            webSearchedDependencies.add(dependency)
        }
        for (repoURL in repositories) {
            val pomContent = if (useWeb) dependency.getPomContent(repoURL) else {
                val downloadedPomFile = dependency.getDownloadedPomFile()
                if (!downloadedPomFile.exists())
                    null
                else
                    downloadedPomFile.readText()
            }
            pomContent ?: continue
            val reader = MavenXpp3Reader()
            val model = reader.read(ByteArrayInputStream(pomContent.toByteArray()))
            val subDependencies = model.dependencies.filter { it.groupId != "junit" }.filter { it.scope == null || it.scope == "compile" }.filter { !it.isOptional }
            for (mavenSubDependency in subDependencies) {
                if (mavenSubDependency.groupId.contains("$") || mavenSubDependency.artifactId.contains("$")) {
                    continue
                }
                val newVersion = when {
                    mavenSubDependency.groupId == model.parent?.groupId -> {
                        model.parent.version
                    }
                    mavenSubDependency.version == null -> {
                        if (useWeb)
                            getLatestVersionOfDependencyFromWeb(mavenSubDependency.groupId, mavenSubDependency.artifactId)
                        else {
                            val text = Dependency(mavenSubDependency.groupId, mavenSubDependency.artifactId, "UNKNOWN").getDownloadedLastVersionFile().readText()
                            if (text.isBlank())
                                null
                            else
                                getLatestVersionFromXML(text)
                        }
                    }
                    mavenSubDependency.version.contains("$") -> {
                        getVersionOfPlaceHolder(model, mavenSubDependency)
                    }
                    else -> {
                        mavenSubDependency.version
                    }
                }
                newVersion ?: continue
                val subDependency = Dependency(mavenSubDependency.groupId, mavenSubDependency.artifactId, newVersion)
                dependencyList.add(subDependency)
                appendSubDependenciesOfDependency(subDependency, dependencyList, useWeb)
            }
            break
        }
    }

    private fun getLatestVersionFromXML(string: String): String? {
        val xmlMapper = XmlMapper()
        //Reading the XML
        val jsonNode: JsonNode = xmlMapper.readTree(string.toByteArray())
        //Create a new ObjectMapper
        val objectMapper = ObjectMapper()
        //Get JSON as a string
        val value = objectMapper.writeValueAsString(jsonNode)
        return getLatestVersion(value)
    }

    fun getLatestVersionOfDependencyFromWeb(groupId: String, artifactId: String): String? {
        for (repository in repositories) {
            return getLatestVersionOfDependencyFromWeb(groupId, artifactId, repository) ?: continue
        }
        return null
    }

    fun getLatestVersionOfDependencyFromWeb(groupId: String, artifactId: String, repositoryURL: String): String? {
        val tmpDependency = Dependency(groupId, artifactId, "UNKNOWN")
        val content = tmpDependency.getMetaDataContent(repositoryURL) ?: return null
        return getLatestVersionFromXML(content)
    }

    private fun getLatestVersion(jsonString: String): String? {
        val jsonObject = JsonParser.parseString(jsonString) as JsonObject
        val versioning = jsonObject["versioning"]?.asJsonObject ?: return null
        return versioning["latest"]?.asString
    }

    private fun getVersionOfPlaceHolder(model: Model, mavenSubDependency: org.apache.maven.model.Dependency): String? {
        val version = mavenSubDependency.version
        val editedVersion = version.dropLast(1).drop(2)
        return model.properties.getProperty(editedVersion)
    }

    private fun installDependency(dependency: Dependency) {
        if (!dependency.getDownloadedFile().exists()) {
            downloadDependency(dependency)
        }
        if (!dependency.getDownloadedFile().exists()) throw FileNotFoundException("Failed to download dependency ${dependency.artifactId}-${dependency.version}")
        ResourceFinder.addToClassLoader(dependency.getDownloadedFile())
        this.loadedDependencies.add(dependency)
    }

    private fun downloadDependency(dependency: Dependency) {
        if (dependency.getDownloadedFile().exists()) return
        repositories.forEach { repoUrl ->
            try {
                dependency.download(repoUrl)
                dependency.getPomContent(repoUrl)?.let { dependency.getDownloadedPomFile().writeText(it) }
                dependency.getMetaDataContent(repoUrl)?.let { dependency.getDownloadedLastVersionFile().writeText(it) }
            } catch (ex: IOException) {
                //ignore exception
            }
        }
        if (dependency.getDownloadedFile().exists()) {
            if (isLoggerAvailable())
                Launcher.instance.logger.console("Downloaded dependency ${dependency.artifactId}-${dependency.version}.jar")
            else
                println("Downloaded dependency ${dependency.artifactId}-${dependency.version}.jar")
        }
    }

    fun getLoadedDependencies(): Collection<Dependency> {
        return this.loadedDependencies
    }
}