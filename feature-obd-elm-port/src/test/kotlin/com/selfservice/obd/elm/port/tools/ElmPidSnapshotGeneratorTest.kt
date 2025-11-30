package com.selfservice.obd.elm.port.tools

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertTrue

class ElmPidSnapshotGeneratorTest {
    @Test
    fun `snapshot generator produces non-empty files`() {
        val tempDir = Files.createTempDirectory("elmPidSnapshots")
        val outputs = ElmPidSnapshotGenerator.writeSnapshots(tempDir)
        assertTrue(outputs.isNotEmpty(), "Expected generator to create at least one snapshot file")
        outputs.forEach { path ->
            val file = path.toFile()
            assertTrue(file.exists(), "Snapshot ${path.absolutePathString()} was not created")
            assertTrue(file.length() > 0, "Snapshot ${path.absolutePathString()} is empty")
        }
    }
}
