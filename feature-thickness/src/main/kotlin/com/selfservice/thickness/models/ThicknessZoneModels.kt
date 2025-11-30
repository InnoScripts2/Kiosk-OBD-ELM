package com.selfservice.thickness.models

/**
 * Модели зон измерений толщины ЛКП
 * 
 * Структура кузова разделена на 60 точек измерений,
 * сгруппированных по элементам кузова.
 * 
 * @since Session 12B
 */

/**
 * Зона кузова для измерений
 */
data class ThicknessZone(
    val id: String,
    val name: String,
    val displayName: String,
    val bodyPart: BodyPart,
    val position: ZonePosition,
    val index: Int // 0-59
)

/**
 * Позиция зоны на кузове
 */
data class ZonePosition(
    val row: Int, // строка в UI grid (0-7)
    val column: Int // колонка в UI grid (0-9)
)

/**
 * Элемент кузова
 */
enum class BodyPart(val displayName: String) {
    HOOD("Капот"),
    ROOF("Крыша"),
    TRUNK("Багажник"),
    FRONT_FENDER_LEFT("Переднее левое крыло"),
    FRONT_FENDER_RIGHT("Переднее правое крыло"),
    REAR_FENDER_LEFT("Заднее левое крыло"),
    REAR_FENDER_RIGHT("Заднее правое крыло"),
    FRONT_DOOR_LEFT("Передняя левая дверь"),
    FRONT_DOOR_RIGHT("Передняя правая дверь"),
    REAR_DOOR_LEFT("Задняя левая дверь"),
    REAR_DOOR_RIGHT("Задняя правая дверь"),
    SIDE_SILL_LEFT("Левый порог"),
    SIDE_SILL_RIGHT("Правый порог"),
    FRONT_BUMPER("Передний бампер"),
    REAR_BUMPER("Задний бампер");
}

/**
 * Результат измерения для конкретной зоны
 */
data class ZoneMeasurement(
    val zone: ThicknessZone,
    val value: Float?, // микроны (μm), null если измерение не выполнено
    val status: MeasurementStatus,
    val timestamp: Long
)

/**
 * Статус измерения зоны
 */
enum class MeasurementStatus {
    /** Измерение не выполнено */
    PENDING,
    /** Измерение в процессе */
    IN_PROGRESS,
    /** Измерение выполнено успешно */
    VALID,
    /** Ошибка измерения */
    ERROR,
    /** Превышен таймаут */
    TIMEOUT,
    /** Значение вне допустимого диапазона */
    OUT_OF_RANGE
}

/**
 * Классификация значения измерения
 */
enum class MeasurementClassification {
    /** Заводская покраска (60-120 мкм) */
    FACTORY,
    /** Незначительная перекраска (120-180 мкм) */
    MINOR_REPAINT,
    /** Значительная перекраска (180-300 мкм) */
    MAJOR_REPAINT,
    /** Шпатлёвка/ремонт (>300 мкм) */
    BODY_WORK,
    /** Слишком тонкая (< 60 мкм) */
    TOO_THIN,
    /** Неизвестно */
    UNKNOWN;
    
    companion object {
        /**
         * Классифицировать измерение по значению
         */
        fun classify(value: Float?): MeasurementClassification {
            return when {
                value == null -> UNKNOWN
                value < 60f -> TOO_THIN
                value <= 120f -> FACTORY
                value <= 180f -> MINOR_REPAINT
                value <= 300f -> MAJOR_REPAINT
                else -> BODY_WORK
            }
        }
    }
}

/**
 * Набор всех зон для измерений (60 точек)
 */
object ThicknessZoneLayout {
    
    /**
     * Все зоны для измерений
     */
    val zones: List<ThicknessZone> by lazy {
        buildZones()
    }
    
    /**
     * Получить зону по индексу
     */
    fun getZone(index: Int): ThicknessZone? {
        return zones.getOrNull(index)
    }
    
    /**
     * Получить зону по ID
     */
    fun getZoneById(id: String): ThicknessZone? {
        return zones.find { it.id == id }
    }
    
    /**
     * Получить зоны элемента кузова
     */
    fun getZonesByBodyPart(bodyPart: BodyPart): List<ThicknessZone> {
        return zones.filter { it.bodyPart == bodyPart }
    }
    
    private fun buildZones(): List<ThicknessZone> {
        val result = mutableListOf<ThicknessZone>()
        var index = 0
        
        // Капот (6 точек) - строка 0, колонки 2-7 для визуального центрирования
        repeat(6) { col ->
            result.add(createZone("hood_$col", "Капот точка ${col + 1}", BodyPart.HOOD, 0, col + 2, index++))
        }
        
        // Переднее левое крыло (3 точки) - строка 1, колонки 0-2
        repeat(3) { col ->
            result.add(createZone("fender_fl_$col", "Переднее левое крыло точка ${col + 1}", 
                BodyPart.FRONT_FENDER_LEFT, 1, col, index++))
        }
        
        // Передний бампер (4 точки) - строка 1, колонки 3-6
        repeat(4) { col ->
            result.add(createZone("bumper_f_$col", "Передний бампер точка ${col + 1}", 
                BodyPart.FRONT_BUMPER, 1, col + 3, index++))
        }

        // Переднее правое крыло (3 точки) - строка 1, колонки 7-9
        repeat(3) { col ->
            result.add(createZone("fender_fr_$col", "Переднее правое крыло точка ${col + 1}", 
                BodyPart.FRONT_FENDER_RIGHT, 1, col + 7, index++))
        }
        
        // Передняя левая дверь (4 точки) - строка 2, колонки 0-3
        repeat(4) { col ->
            result.add(createZone("door_fl_$col", "Передняя левая дверь точка ${col + 1}", 
                BodyPart.FRONT_DOOR_LEFT, 2, col, index++))
        }
        
        // Передняя правая дверь (4 точки) - строка 2, колонки 4-7
        repeat(4) { col ->
            result.add(createZone("door_fr_$col", "Передняя правая дверь точка ${col + 1}", 
                BodyPart.FRONT_DOOR_RIGHT, 2, col + 4, index++))
        }
        
        // Задняя левая дверь (4 точки) - строка 3, колонки 0-3
        repeat(4) { col ->
            result.add(createZone("door_rl_$col", "Задняя левая дверь точка ${col + 1}", 
                BodyPart.REAR_DOOR_LEFT, 3, col, index++))
        }
        
        // Задняя правая дверь (4 точки) - строка 3, колонки 4-7
        repeat(4) { col ->
            result.add(createZone("door_rr_$col", "Задняя правая дверь точка ${col + 1}", 
                BodyPart.REAR_DOOR_RIGHT, 3, col + 4, index++))
        }

        // Левый порог (3 точки) - строка 4, колонки 0-2
        repeat(3) { col ->
            result.add(createZone("sill_l_$col", "Левый порог точка ${col + 1}",
                BodyPart.SIDE_SILL_LEFT, 4, col, index++))
        }

        // Правый порог (3 точки) - строка 4, колонки 7-9
        repeat(3) { col ->
            result.add(createZone("sill_r_$col", "Правый порог точка ${col + 1}",
                BodyPart.SIDE_SILL_RIGHT, 4, col + 7, index++))
        }
        
        // Заднее левое крыло (3 точки) - строка 5, колонки 0-2
        repeat(3) { col ->
            result.add(createZone("fender_rl_$col", "Заднее левое крыло точка ${col + 1}", 
                BodyPart.REAR_FENDER_LEFT, 5, col, index++))
        }
        
        // Заднее правое крыло (3 точки) - строка 5, колонки 7-9
        repeat(3) { col ->
            result.add(createZone("fender_rr_$col", "Заднее правое крыло точка ${col + 1}", 
                BodyPart.REAR_FENDER_RIGHT, 5, col + 7, index++))
        }
        
        // Крыша (6 точек) - строка 6, колонки 2-7
        repeat(6) { col ->
            result.add(createZone("roof_$col", "Крыша точка ${col + 1}", BodyPart.ROOF, 6, col + 2, index++))
        }
        
        // Багажник (6 точек) - строка 7, колонки 2-7
        repeat(6) { col ->
            result.add(createZone("trunk_$col", "Багажник точка ${col + 1}", BodyPart.TRUNK, 7, col + 2, index++))
        }
        
        // Задний бампер (4 точки) - строка 7, колонки 0-1 и 8-9 (по 2 точки на каждую сторону)
        repeat(2) { col ->
            result.add(createZone("bumper_r_left_$col", "Задний бампер левая секция ${col + 1}", 
                BodyPart.REAR_BUMPER, 7, col, index++))
        }
        repeat(2) { col ->
            result.add(createZone("bumper_r_right_$col", "Задний бампер правая секция ${col + 1}", 
                BodyPart.REAR_BUMPER, 7, col + 8, index++))
        }
        
        return result
    }
    
    private fun createZone(
        id: String,
        displayName: String,
        bodyPart: BodyPart,
        row: Int,
        col: Int,
        index: Int
    ): ThicknessZone {
        return ThicknessZone(
            id = id,
            name = id,
            displayName = displayName,
            bodyPart = bodyPart,
            position = ZonePosition(row, col),
            index = index
        )
    }
}

/**
 * Результаты всех измерений
 */
data class ThicknessReport(
    val sessionId: String,
    val measurements: List<ZoneMeasurement>,
    val timestamp: Long,
    val vehicleType: String,
    val analysis: ThicknessAnalysis
)

/**
 * Анализ результатов измерений
 */
data class ThicknessAnalysis(
    val avgValue: Float,
    val minValue: Float,
    val maxValue: Float,
    val deviations: Int, // количество отклонений от нормы
    val factoryCount: Int,
    val repaintCount: Int,
    val bodyWorkCount: Int,
    val recommendation: String
) {
    companion object {
        /**
         * Анализировать измерения
         */
        fun analyze(measurements: List<ZoneMeasurement>): ThicknessAnalysis {
            val validMeasurements = measurements.filter { 
                it.status == MeasurementStatus.VALID && it.value != null 
            }
            
            if (validMeasurements.isEmpty()) {
                return ThicknessAnalysis(
                    avgValue = 0f,
                    minValue = 0f,
                    maxValue = 0f,
                    deviations = 0,
                    factoryCount = 0,
                    repaintCount = 0,
                    bodyWorkCount = 0,
                    recommendation = "Недостаточно данных для анализа"
                )
            }
            
            val values = validMeasurements.mapNotNull { it.value }
            val avg = values.average().toFloat()
            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 0f
            
            var factoryCount = 0
            var repaintCount = 0
            var bodyWorkCount = 0
            
            validMeasurements.forEach { measurement ->
                val classification = MeasurementClassification.classify(measurement.value)
                when (classification) {
                    MeasurementClassification.FACTORY -> factoryCount++
                    MeasurementClassification.MINOR_REPAINT,
                    MeasurementClassification.MAJOR_REPAINT -> repaintCount++
                    MeasurementClassification.BODY_WORK -> bodyWorkCount++
                    else -> {}
                }
            }
            
            val deviations = repaintCount + bodyWorkCount
            val recommendation = generateRecommendation(factoryCount, repaintCount, bodyWorkCount, validMeasurements.size)
            
            return ThicknessAnalysis(
                avgValue = avg,
                minValue = min,
                maxValue = max,
                deviations = deviations,
                factoryCount = factoryCount,
                repaintCount = repaintCount,
                bodyWorkCount = bodyWorkCount,
                recommendation = recommendation
            )
        }
        
        private fun generateRecommendation(
            factoryCount: Int,
            repaintCount: Int,
            bodyWorkCount: Int,
            totalCount: Int
        ): String {
            val factoryPercent = (factoryCount.toFloat() / totalCount * 100).toInt()
            
            return when {
                bodyWorkCount > 5 -> "Обнаружены значительные следы кузовного ремонта ($bodyWorkCount зон). Рекомендуется детальный осмотр."
                repaintCount > totalCount / 2 -> "Более половины кузова перекрашено. Возможно, автомобиль участвовал в ДТП."
                factoryPercent >= 90 -> "Автомобиль в отличном состоянии. Более $factoryPercent% покрытия заводское."
                factoryPercent >= 70 -> "Состояние хорошее. Незначительные следы перекраски."
                else -> "Обнаружены следы ремонта. Рекомендуется детальная проверка."
            }
        }
    }
}
