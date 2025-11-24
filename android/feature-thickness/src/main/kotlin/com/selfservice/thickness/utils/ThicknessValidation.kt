package com.selfservice.thickness.utils

import com.selfservice.thickness.models.ThicknessDeviceConfig
import com.selfservice.thickness.models.ThicknessDeviceError
import com.selfservice.thickness.models.MeasurementStatus
import com.selfservice.thickness.models.ZoneMeasurement

/**
 * Утилиты валидации данных толщиномера
 * @since Session 12B
 */
object ThicknessValidation {
    fun validateMeasurementValue(value: Float): Result<Float> {
        return when {
            value.isNaN() -> Result.failure(
                ThicknessDeviceError.ParseError("Value is NaN", ByteArray(0))
            )
            value.isInfinite() -> Result.failure(
                ThicknessDeviceError.ParseError("Value is Infinite", ByteArray(0))
            )
            value < ThicknessDeviceConfig.MIN_VALID_MEASUREMENT -> Result.failure(
                ThicknessDeviceError.OutOfRangeError(value)
            )
            value > ThicknessDeviceConfig.MAX_VALID_MEASUREMENT -> Result.failure(
                ThicknessDeviceError.OutOfRangeError(value)
            )
            else -> Result.success(value)
        }
    }
    
    fun validateMeasurements(measurements: List<ZoneMeasurement>): ValidationResult {
        val valid = mutableListOf<ZoneMeasurement>()
        val invalid = mutableListOf<ValidationError>()
        
        measurements.forEachIndexed { index, measurement ->
            when {
                measurement.status != MeasurementStatus.VALID -> {
                    invalid.add(ValidationError(index, measurement.zone.id, "Invalid status: ${measurement.status}"))
                }
                measurement.value == null -> {
                    invalid.add(ValidationError(index, measurement.zone.id, "Null value"))
                }
                measurement.value!! < ThicknessDeviceConfig.MIN_VALID_MEASUREMENT ||
                measurement.value!! > ThicknessDeviceConfig.MAX_VALID_MEASUREMENT -> {
                    invalid.add(ValidationError(index, measurement.zone.id, "Value out of range: ${measurement.value}"))
                }
                else -> {
                    valid.add(measurement)
                }
            }
        }
        
        return ValidationResult(valid, invalid, measurements.size)
    }
}

data class ValidationResult(
    val valid: List<ZoneMeasurement>,
    val invalid: List<ValidationError>,
    val totalCount: Int
) {
    val validPercentage: Float
        get() = if (totalCount > 0) (valid.size.toFloat() / totalCount) * 100f else 0f
    val hasErrors: Boolean get() = invalid.isNotEmpty()
    val validCount: Int get() = valid.size
    val invalidCount: Int get() = invalid.size
}

data class ValidationError(val index: Int, val zone: String, val reason: String)

fun ZoneMeasurement.isValid(): Boolean {
    return status == MeasurementStatus.VALID && 
           value != null && 
           value!! >= ThicknessDeviceConfig.MIN_VALID_MEASUREMENT &&
           value!! <= ThicknessDeviceConfig.MAX_VALID_MEASUREMENT
}

fun List<ZoneMeasurement>.filterValid(): List<ZoneMeasurement> = filter { it.isValid() }
fun List<ZoneMeasurement>.validPercentage(): Float {
    if (isEmpty()) return 0f
    return (count { it.isValid() }.toFloat() / size) * 100f
}
