pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Maven Central 의 IP 별 다운로드 제한(429)에 걸릴 때를 위한 동일 내용 미러
        maven("https://maven-central.storage-download.googleapis.com/maven2")
    }
}
rootProject.name = "TravelYunnan"
include(":app")
