/**
 * Ported from https://github.com/bradbarnhill/AndroidOBD (MIT License).
 */
package com.selfservice.obd.elm.port.commands

import com.selfservice.obd.elm.port.models.PID
import com.selfservice.obd.elm.port.statics.PersistentStorage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.system.measureTimeMillis

abstract class BaseObdCommand {
    protected val buffer: ArrayList<Int> = ArrayList()
    private var cmd: String? = null
    private var useImperialUnits = false
    private var rawData: String? = null
    internal var ignoreResult: Boolean = false

    companion object {
        const val NODATA = "NODATA"
        const val SEARCHING = "SEARCHING"
        const val DATA = "DATA"
        const val ELM327 = "ELM327"

        internal lateinit var pid: PID
    }

    protected val currentPid: PID
        get() = pid

    protected constructor()

    private constructor(command: String?) {
        this.cmd = command
    }

    internal constructor(command: String, pid: PID) : this(command.trim()) {
        Companion.pid = pid
    }

    constructor(other: BaseObdCommand) : this(other.cmd)

    abstract val formattedResult: String
    abstract val name: String

    protected abstract fun performCalculations()

    @Throws(IOException::class, InterruptedException::class)
    fun run(inputStream: InputStream, out: OutputStream): BaseObdCommand {
        synchronized(BaseObdCommand::class.java) {
            currentPid.retrievalTime = measureTimeMillis {
                if (currentPid.isPersistent && PersistentStorage.containsPid(currentPid)) {
                    readPersistent()
                } else {
                    sendCommand(out)
                    readResult(inputStream)
                }
            }
        }
        return this
    }

    fun rawResult(): String {
        rawData = if (rawData == null || rawData!!.contains(SEARCHING) || rawData!!.contains(DATA) || rawData!!.contains(ELM327)) {
            NODATA
        } else {
            rawData
        }
        return rawData!!
    }

    fun useImperialUnits(): Boolean = useImperialUnits

    fun setImperialUnits(isImperial: Boolean) {
        useImperialUnits = isImperial
    }

    fun setIgnoreResult(ignore: Boolean): BaseObdCommand {
        ignoreResult = ignore
        return this
    }

    protected fun evaluatePayload(payload: List<Int>) {
        rawData = payload.joinToString(separator = "") { Integer.toHexString(it).padStart(2, '0') }
        buffer.clear()
        buffer.addAll(payload)
        if (!ignoreResult) {
            performCalculations()
        }
    }

    private fun readPersistent() {
        if (currentPid.isPersistent && PersistentStorage.containsPid(currentPid)) {
            PersistentStorage.getElement(currentPid)?.let { cached ->
                currentPid.calculatedResult = cached.calculatedResult
                currentPid.calculatedResultString = cached.calculatedResultString
                currentPid.data = cached.data
            }
        }
    }

    private fun storePersistent() {
        if (currentPid.isPersistent && !PersistentStorage.containsPid(currentPid)) {
            PersistentStorage.addElement(currentPid)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun sendCommand(out: OutputStream) {
        out.write((cmd!! + "\r").toByteArray())
        out.flush()
    }

    @Throws(IOException::class)
    private fun readResult(inputStream: InputStream) {
        readRawData(inputStream)
        if (!ignoreResult) {
            fillBuffer()
            performCalculations()
            storePersistent()
        }
    }

    private fun fillBuffer() {
        buffer.clear()
        rawData?.chunked(2)?.forEach {
            runCatching { buffer.add(Integer.decode("0x$it")) }
        }
    }

    @Throws(IOException::class)
    private fun readRawData(inputStream: InputStream) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val result = StringBuilder()
        var code = reader.read()
        while (code > -1) {
            val char = code.toChar()
            if (char == '>') break
            result.append(char)
            code = reader.read()
        }
        rawData = result.toString().trim().let { data ->
            val index = data.indexOf(13.toChar())
            if (index >= 0) data.substring(index + 1) else data
        }
    }
}
