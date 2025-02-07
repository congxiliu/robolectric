import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

import java.util.jar.JarFile

@SuppressWarnings("GroovyUnusedDeclaration")
class ShadowsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.apply plugin: 'idea'

        project.extensions.create("shadows", ShadowsPluginExtension)

        project.dependencies {
            annotationProcessor project.project(":processor")
        }

        // write generated Java into its own dir... see https://github.com/gradle/gradle/issues/4956
        def generatedSrcDir = project.file("build/generated/src/apt/main")

        project.sourceSets.main.java { srcDir generatedSrcDir }

        project.tasks.named("compileJava").configure { task ->
            task.options.annotationProcessorGeneratedSourcesDirectory = generatedSrcDir

            task.doFirst {
                options.compilerArgs.add("-Aorg.robolectric.annotation.processing.jsonDocsEnabled=true")
                options.compilerArgs.add("-Aorg.robolectric.annotation.processing.jsonDocsDir=${project.layout.buildDirectory.get().asFile}/docs/json")
                options.compilerArgs.add("-Aorg.robolectric.annotation.processing.shadowPackage=${project.shadows.packageName}")
                options.compilerArgs.add("-Aorg.robolectric.annotation.processing.sdkCheckMode=${project.shadows.sdkCheckMode}")
                options.compilerArgs.add("-Aorg.robolectric.annotation.processing.sdks=${project.rootProject.layout.buildDirectory.get().asFile}/sdks.txt")
            }
        }

        // include generated sources in javadoc jar
        project.tasks.named("javadoc").configure { task ->
            task.source(generatedSrcDir)
        }

        // verify that we have the apt-generated files in our javadoc and sources jars
        project.tasks.named("javadocJar").configure { task ->
            task.doLast {
                def shadowPackageNameDir = project.shadows.packageName.replaceAll(/\./, '/')
                checkForFile(task.archivePath, "${shadowPackageNameDir}/Shadows.html")
            }
        }

        project.tasks.named("sourcesJar").configure { task ->
            task.doLast {
                def shadowPackageNameDir = project.shadows.packageName.replaceAll(/\./, '/')
                checkForFile(task.archivePath, "${shadowPackageNameDir}/Shadows.java")
            }
        }

        project.rootProject.configAnnotationProcessing += project

        /* Prevents sporadic compilation error:
         * 'Bad service configuration file, or exception thrown while constructing
         *  Processor object: javax.annotation.processing.Processor: Error reading
         *  configuration file'
         *
         * See https://discuss.gradle.org/t/gradle-not-compiles-with-solder-tooling-jar/7583/20
         */
        project.tasks.withType(JavaCompile).configureEach { options.fork = true }
    }

    static class ShadowsPluginExtension {
        String packageName
        String sdkCheckMode = "WARN"
    }

    private static void checkForFile(jar, String name) {
        def files = new JarFile(jar).entries().collect { it.name }.toSet()

        if (!files.contains(name)) {
            throw new RuntimeException("Missing file ${name} in ${jar}")
        }
    }
}
