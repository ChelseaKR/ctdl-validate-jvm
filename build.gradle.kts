import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    application
    jacoco
    id("com.diffplug.spotless") version "8.10.0"
    id("com.github.spotbugs") version "6.5.11"
}

group = "io.github.chelseakr"

// Deliberately unversioned. Nothing here has been released or published to any
// package registry, and CITATION.cff carries no version for the same reason.
version = "0.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")

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

    // Those files, and the documents PublishedFiguresTest holds to the figures it
    // derives, are not on any classpath, so Gradle cannot see them from the task
    // graph. Without declaring them, editing a fixture or a README count alone
    // leaves :test UP-TO-DATE and the gate reports green on a change it never
    // looked at.
    //
    // This file is in the list because PublishedFiguresTest reads the JaCoCo
    // branch floor out of it and holds three sentences to that number. Gradle
    // does currently rerun :test on any change to this script, so unlike the
    // others this entry is not what makes that check bite today; it is here
    // because the test really does read the file, and a declaration that says so
    // does not depend on build-script invalidation staying that coarse.
    inputs
        .files(
            fileTree("parity"),
            file("README.md"),
            file("CITATION.cff"),
            file("CONTRIBUTING.md"),
            file("docs/ROADMAP.md"),
            fileTree(".github/workflows"),
            file("build.gradle.kts"),
            file("src/main/resources/vendor/SOURCES.md"),
        )
        .withPropertyName("repositoryFilesTheTestsRead")
        .withPathSensitivity(PathSensitivity.RELATIVE)

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
