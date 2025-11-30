/**
 * Адаптированный enum ObdModes из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/enums/ObdModes.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Изменены пакеты, добавлена совместимость с текущей архитектурой
 */
package com.selfservice.obd.core.donor.enums

/**
 * Все OBD режимы согласно спецификации SAE J1979
 * 
 * @property value символьное представление режима
 */
enum class ObdMode(val value: Char) {
    /**
     * Mode 01 - Возвращает текущие значения датчиков (Live Data)
     */
    MODE_01('1'),
    
    /**
     * Mode 02 - Freeze Frame данные в момент возникновения ошибки
     */
    MODE_02('2'),
    
    /**
     * Mode 03 - Сохранённые коды ошибок (Stored DTCs)
     */
    MODE_03('3'),
    
    /**
     * Mode 04 - Очистка кодов ошибок и Freeze Frame
     */
    MODE_04('4'),
    
    /**
     * Mode 05 - Результаты самодиагностики кислородных датчиков
     */
    MODE_05('5'),
    
    /**
     * Mode 06 - Результаты самодиагностики других систем
     */
    MODE_06('6'),
    
    /**
     * Mode 07 - Неподтверждённые коды ошибок (Pending DTCs)
     */
    MODE_07('7'),
    
    /**
     * Mode 08 - Управление другими бортовыми системами (редко используется)
     */
    MODE_08('8'),
    
    /**
     * Mode 09 - Информация об автомобиле (VIN, калибровки и т.д.)
     */
    MODE_09('9'),
    
    /**
     * Mode 0A - Постоянные коды ошибок (Permanent DTCs)
     */
    MODE_0A('A');
    
    /**
     * Целочисленное значение режима
     */
    val intValue: Int
        get() = value.toString().toInt(16)
    
    override fun toString(): String = value.toString()
}
