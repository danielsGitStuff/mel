import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar

// This is a standard Groovy class, not a plugin.
class MelApplicationHelper {

    // A static method we can call from our plugins
    static void configure(Project project) {

        // 1. Get the main resources output directory
        //    (This is usually 'build/resources/main')
        def resourcesOutputDir = project.sourceSets.main.output.resourcesDir

        // 2. Register the task to generate the file
        project.tasks.register('generateVersionProperties') {
            description = 'Generates version.properties from project version'

            // 3. Define the output files
            def resourceOutputFile = new File(resourcesOutputDir, "de/mel/version.properties")
            def externalOutputFile = project.layout.buildDirectory.dir("libs").get().file("${project.name}-${project.version}-fat.jar.properties")
            outputs.file(resourceOutputFile)
            outputs.file(externalOutputFile)

            doLast {
                def variant = project.findProperty('variant') ?: 'unknown'
                def props = new Properties()
                props['version'] = project.version.toString()
                props['commit'] = project.ext.commit.toString()
                props['variant'] = variant

                // 4. Write the resource file (directly into build/resources/main/de/mel/)
                def propsFile = resourceOutputFile
                propsFile.parentFile.mkdirs()
                propsFile.withWriter { w -> props.store(w, null) }

                // 5. Write the external file
                def extPropsFile = externalOutputFile.asFile
                extPropsFile.parentFile.mkdirs()
                extPropsFile.withWriter { w -> props.store(w, null) }
            }
        }

        // 6. Make processResources depend on our task
        //    This ensures our file is generated *before* processResources runs.
        //    processResources will then see the file and include it.
        project.tasks.named('processResources') {
            dependsOn project.tasks.named('generateVersionProperties')
        }

        // 7. Register the fatJar task
        project.tasks.register('fatJar', Jar) {
            group = 'build'
            description = 'Builds a runnable "fat" JAR with all dependencies.'
            archiveClassifier = 'fat'

            dependsOn project.tasks.named('generateVersionProperties')

            manifest {
                attributes(
                        'Main-Class': (project.application.mainClass)
                )
            }

            // 1. Add this project's own files
            from {
                project.sourceSets.main.output
            }

            // 2. Create the CopySpec
            def dependencySpec = project.copySpec {
                //
                // --- THIS IS THE FIX ---
                //
                // We pass the list of files/trees directly to 'from()',
                // not wrapped in another closure.
                from(project.configurations.runtimeClasspath.collect {
                    it.isDirectory() ? it : project.zipTree(it)
                })
                // --- END OF FIX ---

                // The excludes are applied to this spec
                exclude "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"
                exclude "module-info.class"
                exclude "META-INF/substrate/**"
                exclude "META-INF/versions/**"
                exclude "META-INF/*.kotlin_module"
                exclude "de/mel/version.properties"
            }

            // 3. Add the configured CopySpec to the jar
            with dependencySpec

            // 4. Ensures subproject jars are built first
            dependsOn project.configurations.runtimeClasspath.buildDependencies
        }

        // 8. Register the cleanup task (unchanged)
        project.tasks.register('cleanupOldJars') {
            // ... (code is unchanged) ...
        }

        // 9. Hook cleanup to run after 'fatJar' (unchanged)
        project.tasks.named('fatJar') {
            finalizedBy('cleanupOldJars')
        }
    }
}