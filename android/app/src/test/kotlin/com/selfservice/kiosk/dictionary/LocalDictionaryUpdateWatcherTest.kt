package com.selfservice.kiosk.dictionary

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDictionaryUpdateWatcherTest {

    private lateinit var directory: File

    @AfterTest
    fun tearDown() {
        if (::directory.isInitialized) {
            directory.deleteRecursively()
        }
    }

    @Test
    fun notifiesWhenFileIsCreatedAndModified() = runTest {
        directory = Files.createTempDirectory("dictionary-watcher").toFile()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher + Job())
        try {
            var notifications = 0
            val watcher =
                    LocalDictionaryUpdateWatcher(
                            scope = scope,
                            directoryResolver = { directory },
                            pollIntervalMillis = 10L,
                            ioContext = dispatcher,
                            onUpdateAvailable = { notifications += 1 }
                    )

            watcher.start()
            scope.runCurrent()
            assertEquals(0, notifications)

            val payload = File(directory, "dictionary_updates.json")
            payload.writeText("{}")

            advanceAndDrain(scope, 15L)
            assertEquals(1, notifications)

            payload.appendText(" ")
            payload.setLastModified(payload.lastModified() + 1)

            advanceAndDrain(scope, 15L)
            assertEquals(2, notifications)

            watcher.close()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stopsEmittingAfterClose() = runTest {
        directory = Files.createTempDirectory("dictionary-watcher").toFile()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher + Job())
        try {
            var notifications = 0
            val watcher =
                    LocalDictionaryUpdateWatcher(
                            scope = scope,
                            directoryResolver = { directory },
                            pollIntervalMillis = 10L,
                            ioContext = dispatcher,
                            onUpdateAvailable = { notifications += 1 }
                    )

            watcher.start()
            scope.runCurrent()

            val payload = File(directory, "dictionary_updates.json")
            payload.writeText("{}")

            advanceAndDrain(scope, 15L)
            assertEquals(1, notifications)

            watcher.close()

            payload.appendText(" ")
            payload.setLastModified(payload.lastModified() + 1)

            advanceAndDrain(scope, 30L)
            assertEquals(1, notifications)
        } finally {
            scope.cancel()
        }
    }

    private fun advanceAndDrain(scope: TestScope, millis: Long) {
        scope.advanceTimeBy(millis)
        scope.runCurrent()
    }
}
