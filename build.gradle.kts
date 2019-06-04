import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.CopySpecEnhancement
import org.ajoberstar.grgit.Configurable
import org.ajoberstar.grgit.Grgit
import org.asciidoctor.gradle.jvm.AbstractAsciidoctorTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

tasks.wrapper {
    gradleVersion = "5.4.1"
    distributionType = Wrapper.DistributionType.ALL
}

plugins {
    id("com.github.ben-manes.versions") version "0.21.0"
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.ajoberstar.grgit") version "3.1.1"
    id("org.asciidoctor.jvm.convert") version "2.2.0"
    id("org.asciidoctor.jvm.pdf") version "2.2.0"
    id("org.asciidoctor.jvm.epub") version "2.2.0"
    id("nebula.deb") version "6.2.1"
    idea
    application
}

version = "1.12.0"

val javaVersion = JavaVersion.VERSION_1_8

idea {
    project.jdkName = javaVersion.name

    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

repositories {
    mavenCentral()

    // For asciidoctor, grgit
    jcenter()

    // For svnkit
    maven("https://repository.mulesoft.org/nexus/content/repositories/public/")
}

license {
    header = rootProject.file("license_header.txt")
    exclude("**/*.json")
    exclude("**/*.yml")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useTestNG {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }
}

application {
    mainClassName = "svnserver.server.Main"
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}

tasks.getByName<JavaExec>("run") {
    args = listOf("-c", "$projectDir/src/test/resources/config-local.yml")
}

dependencies {
    compile("org.eclipse.jgit:org.eclipse.jgit:5.3.1.201904271842-r")
    compile("org.tmatesoft.svnkit:svnkit:1.10.0")
    compile("org.yaml:snakeyaml:1.24")
    compile("com.beust:jcommander:1.72")
    compile("org.ini4j:ini4j:0.5.4")
    compile("org.mapdb:mapdb:3.0.7")
    compile("com.unboundid:unboundid-ldapsdk:4.0.10")
    compile("org.eclipse.jetty:jetty-servlet:9.4.18.v20190429")
    compile("org.gitlab:java-gitlab-api:4.1.0")
    compile("org.bitbucket.b_c:jose4j:0.6.5")
    compile("com.github.zeripath:java-gitea-api:1.7.4")

    val gitLfsJava = "0.13.2"
    compile("ru.bozaro.gitlfs:gitlfs-pointer:$gitLfsJava")
    compile("ru.bozaro.gitlfs:gitlfs-client:$gitLfsJava")
    compile("ru.bozaro.gitlfs:gitlfs-server:$gitLfsJava")

    compile("com.google.oauth-client:google-oauth-client:1.29.0")
    compile("com.google.http-client:google-http-client-jackson2:1.29.1")
    compile("org.jetbrains:annotations:17.0.0")
    compile("org.slf4j:slf4j-api:1.7.26")

    val classindex = "org.atteo.classindex:classindex:3.6"
    compile(classindex)
    annotationProcessor(classindex)

    runtime("org.apache.logging.log4j:log4j-slf4j18-impl:2.11.2")

    testCompile("org.testcontainers:testcontainers:1.11.3")
    testCompile("org.testng:testng:6.14.3")
}

tasks.jar {
    archiveFileName.set("${project.name}.jar")
    manifest {
        attributes(
                "Main-Class" to "svnserver.server.Main",
                "Class-Path" to createLauncherClassPath()
        )
    }
}

val compileDocs by tasks.creating(Copy::class) {
    group = "documentation"
    dependsOn(tasks.asciidoctor, tasks.asciidoctorEpub, tasks.asciidoctorPdf)

    from("$buildDir/docs/asciidoc") {
        into("htmlsingle")
    }
    from("$buildDir/docs/asciidocEpub")
    from("$buildDir/docs/asciidocPdf")
    from("$projectDir") {
        include("*.adoc", "LICENSE")
    }
    into(file("$buildDir/doc"))
}

tasks.asciidoctor {
    configure()

    resources {
        from("src/docs/asciidoc") {
            include("examples/**")
            include("images/**")
        }
    }
}

tasks.asciidoctorEpub {
    configure()
    ebookFormats("epub3")
}

tasks.asciidoctorPdf {
    configure()
}

fun AbstractAsciidoctorTask.configure() {
    baseDirFollowsSourceDir()
    sources(delegateClosureOf<PatternSet> {
        include("git-as-svn.adoc")
    })
    val commitDateTime = getCommitDateTime()
    attributes(mapOf(
            "source-highlighter" to "coderay",
            "docdate" to commitDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),
            "doctime" to commitDateTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
    ))
}

distributions {
    main {
        contents {
            into("doc") {
                from(compileDocs)
            }
            into("tools") {
                from("$projectDir/tools")
            }
        }
    }
}

tasks.processResources {
    from(sourceSets.main.get().resources.srcDirs) {
        include("**/VersionInfo.properties")

        expand(mapOf(
                "version" to project.version,
                "revision" to Grgit.open(mapOf("dir" to projectDir)).head().id,
                "tag" to (System.getenv("TRAVIS_TAG") ?: "")
        ))
    }
}

val debianControl by tasks.creating(Copy::class) {
    from("$projectDir/src/main/deb") {
        exclude("**/changelog")
    }
    from("$projectDir/src/main/deb") {
        include("**/changelog")

        expand(mapOf(
                "version" to project.version,
                "date" to DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(getCommitDateTime())
        ))
    }
    into(file("$buildDir/debPackage/package"))
}

val compileDeb by tasks.creating(Exec::class) {
    dependsOn(tasks.installDist, debianControl)

    workingDir = file("$buildDir/debPackage/package")
    executable = "dpkg-buildpackage"
    args("-uc", "-us")
}

fun perm(owner: Byte, group: Byte, others: Byte): Int {
    return (owner.toInt() shl 6) or (group.toInt() shl 3) or (others.toInt() shl 0)
}

val distLfsDeb by tasks.creating(Deb::class) {
    packageName = "git-as-svn-lfs"

    user = "root"
    permissionGroup = "root"

    // See https://github.com/nebula-plugins/gradle-ospackage-plugin/issues/59#issuecomment-128718128
    directory("/etc/git-as-svn", perm(7, 5, 5), "root", "root")

    into("/etc/git-as-svn") {
        from("tools") {
            include("*.cfg")
            fileMode = perm(6, 4, 0)
            CopySpecEnhancement.addParentDirs(this, false)
            CopySpecEnhancement.permissionGroup(this, "git")
        }
    }

    into("/usr/bin") {
        from("tools") {
            fileMode = perm(7, 5, 5)

            include("git-lfs-authenticate")
        }
    }
}

tasks.assembleDist {
    dependsOn(distLfsDeb)
}

tasks.distZip {
    archiveFileName.set("${project.name}_${project.version}.zip")
}

tasks.distTar {
    archiveFileName.set("${project.name}_${project.version}.tbz2")
    compression = Compression.BZIP2
}

fun createLauncherClassPath(): String {
    val projectArtifacts = configurations.archives.get().artifacts.map { it.file }
    val fullArtifacts = configurations.archives.get().artifacts.map { it.file } + configurations.runtime.get().files
    val vendorJars = fullArtifacts.minus(projectArtifacts).map { it.name }
    return vendorJars.joinToString(" ")
}

fun getCommitDateTime(): ZonedDateTime {
    return Grgit.open(Configurable { dir = projectDir }).head().dateTime
}
