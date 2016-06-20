package com.mobiledefense.gradle

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.VerificationTask
import org.gradle.testing.jacoco.tasks.JacocoReport

class UnitTestCoverageReport implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply("jacoco")
        project.jacoco {
            toolVersion "0.7.6.201602180812"
        }

        addUnitTestCodeCoverageTasks(toProject: project, withVariants: variants(inProject: project))
        applyTasks(project)
    }

    private void addUnitTestCodeCoverageTasks(Map args) {
        Project project = args['toProject']
        Set<? extends BaseVariant> variants = args['withVariants']

        variants.all { variant ->
            project.task("create${variant.name.capitalize()}UnitTestCoverageReport", type: JacocoReport, dependsOn: ["clean", "test${variant.name.capitalize()}UnitTest"]) {
                def jacocoExcludes = [
                        'android/**',
                        '**/*$$*',
                        '**/R.class',
                        '**/R$*.class',
                        '**/BuildConfig.*',
                        '**/Manifest*.*'
                ]

                group = "verification"
                description = "Generate Jacoco coverage reports after running unit tests for ${variant.name.capitalize()}."

                classDirectories = project.fileTree(
                        dir: variant.javaCompile.destinationDir,
                        excludes: jacocoExcludes
                )
                sourceDirectories = project.files(variant.javaCompile.source)
                executionData = project.files("${project.buildDir}/jacoco/test${variant.name.capitalize()}UnitTest.exec")

                reports {
                    html.enabled = true
                    xml.enabled = false
                    html.destination "${project.buildDir}/reports/testsCoverage/${variant.name}/html"
                }

                ext.reportDir = "${project.buildDir}/reports/testsCoverage/${variant.name}/html"
            }
        }
    }

    private Set<? extends BaseVariant> variants(Map args) {
        Project project = args['inProject']

        if (project.plugins.hasPlugin(AppPlugin)) {
            return project.android.applicationVariants
        } else if (project.plugins.hasPlugin(LibraryPlugin)) {
            return project.android.libraryVariants
        } else {
            return []
        }
    }

    void applyTasks(final Project project) {
        project.gradle.taskGraph.whenReady {
            if (project.gradle.taskGraph.allTasks.find { it.name =~ /^create.*CoverageReport$/ } != null) {
                project.gradle.taskGraph.allTasks.each { task ->
                    if (task instanceof VerificationTask) {
                        ((VerificationTask)task).ignoreFailures = true
                        System.out.println("NOTE: Ignoring test failures for ${task.name} while creating code coverage report")
                    }
                }

                // Since the code coverage task silently writes to the build outputs without
                // mentioning where it wrote, or really that it even wrote anything at all
                // do our fellow developers a favor and let them know where to find the reports.
                project.gradle.taskGraph.allTasks.each { task ->
                    def isCoverageReport = task.name =~ /^create.*CoverageReport$/
                    if (isCoverageReport) {
                        task << {
                            println "##################################################"
                            println '# The code coverage report can be found at:'
                            println "# file://${task.reportDir}/index.html"
                            println "##################################################"
                        }
                    }
                }
            }
        }
    }
}
