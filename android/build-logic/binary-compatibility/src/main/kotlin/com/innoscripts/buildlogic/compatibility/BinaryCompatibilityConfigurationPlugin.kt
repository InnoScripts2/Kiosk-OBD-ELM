package com.innoscripts.buildlogic.compatibility

import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class BinaryCompatibilityConfigurationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("org.jetbrains.kotlinx.binary-compatibility-validator")

        target.extensions.configure<ApiValidationExtension> {
            nonPublicMarkers.add("com.badoo.reaktive.utils.InternalReaktiveApi")

            val optionalProjects = listOf(
                "benchmarks",
                "jmh",
                "sample-mpp-module",
                "sample-android-app",
                "sample-js-browser-app",
                "sample-linuxx64-app",
            )

            val availableProjects = target.rootProject.allprojects.map { it.name }.toSet()

            if (target.hasProperty("check_publication")) {
                if (availableProjects.contains("check-publication")) {
                    ignoredProjects.add("check-publication")
                }
            } else {
                optionalProjects
                    .filter { availableProjects.contains(it) }
                    .forEach { ignoredProjects.add(it) }
            }
        }
    }
}
