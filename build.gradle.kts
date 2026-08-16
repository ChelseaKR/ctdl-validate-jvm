import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    application
    jacoco
    id("com.diffplug.spotless") version "7.0.2"
    id("com.github.spotbugs") version "6.1.7"
}

group = "io.github.chelseakr"

// Deliberately unversioned. Nothing here has been released or published to any
// package registry, and CITATION.cff carries no version for the same reason.
version = "0.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.github.chelseakr.ctdlvalidate.Cli")
}

// The floor is Java 17. See docs/adr/0002-java-17-floor.md.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
    // The parity suite reads parity/fixtures and parity/expected relative to the
    // repository root, not whatever working directory Gradle hands the tests.
    systemProperty("ctdlvalidate.repoRoot", rootDir.absolutePath)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

// `./gradlew verify` is the whole gate, and it is exactly what CI runs.
tasks.register("verify") {
    group = "verification"
    description = "Compile, format check, static analysis, tests, and the coverage gate."
    dependsOn(
        tasks.named("spotlessCheck"),
        tasks.named("spotbugsMain"),
        tasks.named("spotbugsTest"),
        tasks.named("test"),
        tasks.named("jacocoTestCoverageVerification"),
    )
}
