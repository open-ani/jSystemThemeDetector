import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

plugins {
    id("java")
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "org.openani.jsystemthemedetector"
version = "3.8"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.compileJava {
    // placing dependencies on the module path
    // https://discuss.gradle.org/t/gradle-doesnt-add-modules-to-module-path-during-compile/27382
    inputs.property("moduleName", "com.jthemedetector")
    doFirst {
        options.compilerArgs = listOf("--module-path", classpath.asPath)
        classpath = files() // Clear the default classpath
    }
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.32")

    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation("ch.qos.logback:logback-core:1.2.3")

    // JNA
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")

    // JFA (exclude different version of JNA)
    implementation("de.jangassen:jfa:1.2.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }

    // OSHI
    implementation("com.github.oshi:oshi-core:5.8.6")

    implementation("io.github.g00fy2:versioncompare:1.4.1")

    implementation("org.jetbrains:annotations:22.0.0")
}

mavenPublishing {
    configure(JavaLibrary(JavadocJar.Empty(), true))
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name = "jSystemThemeDetector"
        description =
            "(Fork of) Java library for detecting that the (desktop) operating system uses dark UI theme or not."
        url = "https://github.com/open-ani/jSystemThemeDetector"

        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        developers {
            developer {
                id = "Dansoftowner"
                name = "Daniel Gyoerffy"
                email = "Dansoftowner@users.noreply.github.io"
            }
            developer {
                id = "openani"
                name = "OpenAni"
                organization = "OpenAni"
                roles = listOf("publisher")
                email = "support@openani.org"
            }
        }

        scm {
            connection = "scm:git:https://github.com/open-ani/jSystemThemeDetector.git"
            developerConnection = "scm:git:git@github.com:open-ani/jSystemThemeDetector.git"
            url = "https://github.com/open-ani/jSystemThemeDetector"
        }
    }
}