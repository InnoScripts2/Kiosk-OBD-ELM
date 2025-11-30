package com.autoservice.diagnostics.obd

/**
 * Collection of SAE J1979 Mode 01/09 PIDs that we poll by default during kiosk diagnostics.
 * The formulas are derived from the public OBD-II specification and return values in SI units.
 */
object StandardPids {
    val ENGINE_COOLANT_TEMP = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "05",
        description = "Engine coolant temperature",
        unit = "°C",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x05 requires 1 byte" }
            (bytes[0].toInt() and 0xFF) - 40.0
        },
        thresholds = PidThresholds(
            warningHigh = 105.0,
            criticalHigh = 115.0
        )
    )

    val ENGINE_RPM = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "0C",
        description = "Engine speed",
        unit = "rpm",
        formula = { bytes ->
            require(bytes.size >= 2) { "PID 0x0C requires 2 bytes" }
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            ((a * 256) + b) / 4.0
        }
    )

    val VEHICLE_SPEED = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "0D",
        description = "Vehicle speed",
        unit = "km/h",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x0D requires 1 byte" }
            (bytes[0].toInt() and 0xFF).toDouble()
        }
    )

    val SHORT_TERM_FUEL_TRIM_BANK1 = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "06",
        description = "Short term fuel trim (bank 1)",
        unit = "%",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x06 requires 1 byte" }
            val a = bytes[0].toInt() and 0xFF
            ((a - 128) * 100.0) / 128.0
        },
        thresholds = PidThresholds(
            criticalLow = -30.0,
            warningLow = -20.0,
            warningHigh = 20.0,
            criticalHigh = 30.0
        )
    )

    val LONG_TERM_FUEL_TRIM_BANK1 = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "07",
        description = "Long term fuel trim (bank 1)",
        unit = "%",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x07 requires 1 byte" }
            val a = bytes[0].toInt() and 0xFF
            ((a - 128) * 100.0) / 128.0
        },
        thresholds = PidThresholds(
            criticalLow = -25.0,
            warningLow = -15.0,
            warningHigh = 15.0,
            criticalHigh = 25.0
        )
    )

    val INTAKE_MANIFOLD_PRESSURE = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "0B",
        description = "Intake manifold pressure",
        unit = "kPa",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x0B requires 1 byte" }
            (bytes[0].toInt() and 0xFF).toDouble()
        },
        thresholds = PidThresholds(
            warningLow = 20.0,
            criticalLow = 10.0,
            warningHigh = 130.0,
            criticalHigh = 150.0
        )
    )

    val INTAKE_AIR_TEMP = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "0F",
        description = "Intake air temperature",
        unit = "°C",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x0F requires 1 byte" }
            (bytes[0].toInt() and 0xFF) - 40.0
        },
        thresholds = PidThresholds(
            warningLow = -20.0,
            warningHigh = 60.0,
            criticalHigh = 80.0
        )
    )

    val MAF_SENSOR = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "10",
        description = "Mass air flow",
        unit = "g/s",
        formula = { bytes ->
            require(bytes.size >= 2) { "PID 0x10 requires 2 bytes" }
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            ((a * 256) + b) / 100.0
        }
    )

    val THROTTLE_POSITION = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "11",
        description = "Throttle position",
        unit = "%",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x11 requires 1 byte" }
            (bytes[0].toInt() and 0xFF) * 100.0 / 255.0
        }
    )

    val ENGINE_OIL_TEMP = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "5C",
        description = "Engine oil temperature",
        unit = "°C",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x5C requires 1 byte" }
            (bytes[0].toInt() and 0xFF) - 40.0
        },
        thresholds = PidThresholds(
            warningHigh = 115.0,
            criticalHigh = 125.0
        )
    )

    val CATALYST_TEMPERATURE_BANK1_SENSOR1 = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "3C",
        description = "Catalyst temperature (bank 1 sensor 1)",
        unit = "°C",
        formula = { bytes ->
            require(bytes.size >= 2) { "PID 0x3C requires 2 bytes" }
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            ((a * 256) + b) / 10.0 - 40.0
        },
        thresholds = PidThresholds(
            warningHigh = 780.0,
            criticalHigh = 870.0
        )
    )

    val FUEL_LEVEL = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "2F",
        description = "Fuel level input",
        unit = "%",
        formula = { bytes ->
            require(bytes.isNotEmpty()) { "PID 0x2F requires 1 byte" }
            (bytes[0].toInt() and 0xFF) * 100.0 / 255.0
        },
        thresholds = PidThresholds(
            criticalLow = 5.0,
            warningLow = 15.0
        )
    )

    val CONTROL_MODULE_VOLTAGE = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "42",
        description = "Control module voltage",
        unit = "V",
        formula = { bytes ->
            require(bytes.size >= 2) { "PID 0x42 requires 2 bytes" }
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            ((a * 256) + b) / 1000.0
        },
        thresholds = PidThresholds(
            warningLow = 11.8,
            criticalLow = 11.3,
            warningHigh = 14.7,
            criticalHigh = 15.5
        )
    )

    val VIN = PID(
        mode = ObdMode.VEHICLE_INFORMATION,
        pid = "02",
        description = "Vehicle Identification Number",
        unit = null,
        formula = { 0.0 } // VIN is handled separately via raw payload
    )

    private val allPids: List<PID> = listOf(
        ENGINE_COOLANT_TEMP,
        ENGINE_RPM,
        VEHICLE_SPEED,
        SHORT_TERM_FUEL_TRIM_BANK1,
        LONG_TERM_FUEL_TRIM_BANK1,
        INTAKE_MANIFOLD_PRESSURE,
        INTAKE_AIR_TEMP,
        MAF_SENSOR,
        THROTTLE_POSITION,
        ENGINE_OIL_TEMP,
        CATALYST_TEMPERATURE_BANK1_SENSOR1,
        FUEL_LEVEL,
        CONTROL_MODULE_VOLTAGE,
        VIN
    )

    private val registry: Map<String, PID> = allPids.associateBy { key(it.mode.code, it.pid) }

    /** Default polling set used by kiosk diagnostics. */
    val defaultSet: List<PID> = listOf(
        ENGINE_COOLANT_TEMP,
        ENGINE_RPM,
        VEHICLE_SPEED,
        SHORT_TERM_FUEL_TRIM_BANK1,
        LONG_TERM_FUEL_TRIM_BANK1,
        INTAKE_MANIFOLD_PRESSURE,
        ENGINE_OIL_TEMP,
        CATALYST_TEMPERATURE_BANK1_SENSOR1,
        INTAKE_AIR_TEMP,
        MAF_SENSOR,
        THROTTLE_POSITION,
        FUEL_LEVEL,
        CONTROL_MODULE_VOLTAGE,
        VIN
    )

    fun lookup(mode: String, pid: String): PID? = registry[key(mode, pid)]

    fun lookup(mode: ObdMode, pid: String): PID? = lookup(mode.code, pid)

    fun thresholds(pid: PID): PidThresholds? = pid.thresholds?.takeUnless { it.isEmpty }

    fun thresholds(mode: String, pid: String): PidThresholds? = lookup(mode, pid)?.thresholds?.takeUnless { it.isEmpty }

    fun thresholds(mode: ObdMode, pid: String): PidThresholds? = thresholds(mode.code, pid)

    fun all(): List<PID> = allPids

    private fun key(mode: String, pid: String): String {
        val normalizedMode = normalize(mode)
        val normalizedPid = normalize(pid)
        return "$normalizedMode:$normalizedPid"
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim()
        val withoutPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2)
        } else {
            trimmed
        }
        return withoutPrefix.uppercase().padStart(2, '0')
    }
}
