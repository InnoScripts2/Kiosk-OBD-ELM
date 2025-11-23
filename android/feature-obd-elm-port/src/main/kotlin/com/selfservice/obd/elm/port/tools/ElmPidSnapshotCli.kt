package com.selfservice.obd.elm.port.tools

import com.selfservice.obd.elm.port.enums.ObdModes
import java.io.IOException
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Simple CLI wrapper around [ElmPidSnapshotGenerator].
 * Usage:
 *   ./gradlew :feature-obd-elm-port:generateElmPidSnapshots --args "<outputDir> [--modes=01,04,09]"
 */
object ElmPidSnapshotCli {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsageAndExit("Missing output directory argument")
        }
        val (outputArg, modeArg) = parseArguments(args)
        val outputDir = Paths.get(outputArg).toAbsolutePath()
        val modes = parseModes(modeArg)
        try {
            val files = ElmPidSnapshotGenerator.writeSnapshots(outputDir, modes)
            println("Generated ${files.size} PID snapshot file(s) in $outputDir")
            files.forEach { println(" - ${it.toAbsolutePath()}") }
        } catch (error: IOException) {
            error.printStackTrace()
            printUsageAndExit("Snapshot generation failed: ${error.message}")
        }
    }

    private fun parseArguments(args: Array<String>): Pair<String, String?> {
        val output = args[0]
        val modeFlag = args.drop(1).firstOrNull { it.startsWith("--modes=") }
        val modeValue = modeFlag?.substringAfter("--modes=")?.ifBlank { null }
        return output to modeValue
    }

    private fun parseModes(argument: String?): List<ObdModes> {
        if (argument.isNullOrBlank()) {
            return listOf(ObdModes.MODE_01, ObdModes.MODE_04, ObdModes.MODE_09)
        }
        val requested = argument.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (requested.isEmpty()) {
            return listOf(ObdModes.MODE_01, ObdModes.MODE_04, ObdModes.MODE_09)
        }
        return requested.map { token ->
            when (token.uppercase()) {
                "01", "1", "0X01" -> ObdModes.MODE_01
                "04", "4", "0X04" -> ObdModes.MODE_04
                "09", "9", "0X09" -> ObdModes.MODE_09
                else -> error("Unsupported mode token '$token'")
            }
        }
    }

    private fun printUsageAndExit(message: String): Nothing {
        System.err.println(message)
        System.err.println("Usage: <outputDir> [--modes=01,04,09]")
        exitProcess(1)
    }
}
