import me.champeau.gradle.igp.gitRepositories
import org.eclipse.jgit.api.Git
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

rootProject.name = "Dicio"
include(":app")
include(":skill")
// we use includeBuild here since the plugin is a compile-time dependency
includeBuild("sentences-compiler-plugin")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // not using version catalog because it is not available in settings.gradle.kts
    id("me.champeau.includegit") version "0.1.6"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}


// All of the code below handles depending on libraries from git repos, in particular dicio-numbers,
// dicio-skill and dicio-sentences-compiler. The git commits to checkout can be updated here.
// If you want to use a local copy of the projects (provided that you have cloned them in
// `../dicio-*`), you can add `useLocalDicioLibraries=true` in `local.properties`.

data class IncludeGitRepo(
    val name: String,
    val uri: String,
    val projectPath: String,
    val commit: String,
)

val includeGitRepos = listOf(
    IncludeGitRepo(
        name = "dicio-numbers",
        uri = "https://github.com/Stypox/dicio-numbers",
        projectPath = ":numbers",
        commit = "66fd44b79585f952b76d16e5578d4d6aa5bc030c",
    ),
    IncludeGitRepo(
        name = "dicio-sentences-compiler",
        uri = "https://github.com/Stypox/dicio-sentences-compiler",
        projectPath = ":sentences_compiler",
        commit = "7d83fe5a3d6dff2fc81b5c40783a1d82ada293d3",
    ),
)

val localProperties = Properties().apply {
    try {
        load(FileInputStream(File(rootDir, "local.properties")))
    } catch (e: Throwable) {
        println("Warning: can't read local.properties: $e")
    }
}

if (localProperties.getOrDefault("useLocalDicioLibraries", "") == "true") {
    for (repo in includeGitRepos) {
        includeBuild("../${repo.name}") {
            dependencySubstitution {
                substitute(module("git.included.build:${repo.name}"))
                    .using(project(repo.projectPath))
            }
        }
    }

} else {
    // if the repo has already been cloned, the gitRepositories plugin is buggy and doesn't
    // fetch the remote repo before trying to checkout the commit (in case the commit has changed),
    // and doesn't clone the repo again if the remote changed, so we need to do it manually
    for (repo in includeGitRepos) {
        val file = File("$rootDir/checkouts/${repo.name}")
        if (file.isDirectory) {
            val git = Git.open(file)
            val sameRemote = git.remoteList().call()
                .any { rem -> rem.urIs.any { uri -> uri.toString() == repo.uri } }
            if (sameRemote) {
                // the commit may have changed, fetch again
                git.fetch().call()
            } else {
                // the remote changed, delete the repository and start from scratch
                println("Git: remote for ${repo.name} changed, deleting the current folder")
                file.deleteRecursively()
            }
        }
    }

    gitRepositories {
        for (repo in includeGitRepos) {
            include(repo.name) {
                uri.set(repo.uri)
                commit.set(repo.commit)
                autoInclude.set(false)
                includeBuild("") {
                    dependencySubstitution {
                        substitute(module("git.included.build:${repo.name}"))
                            .using(project(repo.projectPath))
                    }
                }
            }
        }
    }
}
