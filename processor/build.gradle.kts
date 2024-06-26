import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.vanniktech.maven.publish)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.devtools.ksp)
    implementation(libs.squareup.kotlinpoet)
    implementation(libs.squareup.kotlinpoet.ksp)
}

mavenPublishing {
    coordinates(
        gradleProjectConfig.versions.group.get(),
        gradleProjectConfig.versions.artifact.get(),
        gradleProjectConfig.versions.version.get()
    )
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    pom {
        inceptionYear.set("2024")
        name.set("Clean Architecture Mapper")
        url.set("https://github.com/Timbermir/clean-architecture-mapper")
        description.set("Clean Architecture Mapper - KSP simple Clean Architecture Data Classes Processor")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/Timbermir/clean-architecture-mapper")
            connection.set("scm:git:git://github.com/Timbermir/clean-architecture-mapper.git")
            developerConnection.set("scm:git@github.com:Timbermir/clean-architecture-mapper.git")
        }

        developers {
            developer {
                id.set(findProperty("developerId").toString())
                name.set("timplifier")
                email.set("timplifier@gmail.com")
                url.set("https://github.com/timplifier")
            }
        }
    }
    signAllPublications()
}