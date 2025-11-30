/**
 * Адаптированный enum ObdProtocols из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/enums/ObdProtocols.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Изменены пакеты, добавлена совместимость с текущей архитектурой
 */
package com.selfservice.obd.core.donor.enums

/**
 * Протоколы OBD поддерживаемые ELM327 адаптерами
 * 
 * @property value символьное представление протокола для команды AT SP
 */
enum class ObdProtocol(val value: Char) {
    /**
     * Автовыбор протокола и сохранение
     */
    AUTO('0'),
    
    /**
     * SAE J1850 PWM - 41.6 kbaud (Ford)
     */
    SAE_J1850_PWM('1'),
    
    /**
     * SAE J1850 VPW - 10.4 kbaud (GM)
     */
    SAE_J1850_VPW('2'),
    
    /**
     * ISO 9141-2 - 5 baud init (Азиатские производители)
     */
    ISO_9141_2('3'),
    
    /**
     * ISO 14230-4 KWP - 5 baud init
     */
    ISO_14230_4_KWP('4'),
    
    /**
     * ISO 14230-4 KWP - Fast init
     */
    ISO_14230_4_KWP_FAST('5'),
    
    /**
     * ISO 15765-4 CAN - 11 bit ID, 500 kbaud (наиболее распространён с 2008+)
     */
    ISO_15765_4_CAN('6'),
    
    /**
     * ISO 15765-4 CAN - 29 bit ID, 500 kbaud
     */
    ISO_15765_4_CAN_B('7'),
    
    /**
     * ISO 15765-4 CAN - 11 bit ID, 250 kbaud
     */
    ISO_15765_4_CAN_C('8'),
    
    /**
     * ISO 15765-4 CAN - 29 bit ID, 250 kbaud
     */
    ISO_15765_4_CAN_D('9'),
    
    /**
     * SAE J1939 CAN - 29 bit ID, 250 kbaud (грузовики, настраивается)
     */
    SAE_J1939_CAN('A'),
    
    /**
     * USER1 CAN - 11 bit ID, 125 kbaud (настраивается пользователем)
     */
    USER1_CAN('B'),
    
    /**
     * USER2 CAN - 11 bit ID, 50 kbaud (настраивается пользователем)
     */
    USER2_CAN('C')
}
