pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    ivy {
      name = "AndroidLibXrayLite"
      url = uri("https://github.com/2dust/AndroidLibXrayLite/releases/download")
      patternLayout {
        artifact("v[revision]/[artifact].[ext]")
      }
      metadataSources {
        artifact()
      }
      content {
        includeModule("com.github.2dust", "libv2ray")
      }
    }
  }
}

rootProject.name = "XRay Pulse"

include(":app")
