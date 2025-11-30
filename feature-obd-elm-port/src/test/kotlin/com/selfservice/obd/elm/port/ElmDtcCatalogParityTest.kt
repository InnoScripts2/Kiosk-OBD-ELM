package com.selfservice.obd.elm.port

import com.selfservice.obd.core.dtc.DtcCatalog
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.elm.port.adapter.ElmDtcDefinitionAdapter
import com.selfservice.obd.elm.port.statics.DtcUtils
import kotlin.test.Test
import kotlin.test.assertTrue

class ElmDtcCatalogParityTest {

    private val donorEntries by lazy {
        DtcUtils.loadAll().map { ElmDtcDefinitionAdapter.asDefinition(it) }
    }

    @Test
    fun `donor dtc entries exist in canonical catalog`() {
        val missing = mutableListOf<String>()
        donorEntries.forEach { donor ->
            if (DtcCatalog.find(donor.code) == null) {
                missing += donor.code
            }
        }

        assertTrue(missing.isEmpty(), "Canonical DTC catalog is missing donor entries: ${missing.take(25).joinToString()}${suffixIfTrimmed(missing)}")
    }

    @Test
    fun `donor dtc descriptions align with canonical labels`() {
        val mismatched = mutableListOf<String>()
        donorEntries.forEach { donor ->
            val canonical = DtcCatalog.find(donor.code) ?: return@forEach
            if (canonical.system !in DESCRIPTION_SYSTEMS) return@forEach
            if (!descriptionEquivalent(donor.label, canonical.label)) {
                mismatched += "${donor.code}: donor='${donor.label}' canonical='${canonical.label}'"
            }
        }

        assertTrue(mismatched.isEmpty(), "DTC description mismatches detected: ${mismatched.take(25).joinToString()}${suffixIfTrimmed(mismatched)}")
    }

    private fun descriptionEquivalent(donor: String, canonical: String): Boolean {
        if (hasReservedMarker(donor)) return true

        val donorTokens = tokenize(donor)
        val canonicalTokens = tokenize(canonical)
        if (donorTokens.isEmpty()) return true
        if (donorTokens.size <= 2 && donorTokens.any { it.length <= 3 }) return true

        val overlap = donorTokens.count { token -> token in canonicalTokens }
        val ratio = overlap.toDouble() / donorTokens.size
        return ratio >= 0.5 || canonicalTokens.containsAll(donorTokens.take(2))
    }

    private fun tokenize(raw: String): List<String> = preprocess(raw)
        .split(NON_ALPHANUMERIC)
        .map { it.trim(*TRIM_CHARS) }
        .filter { it.isNotEmpty() && it.length > 2 }
        .map { translateToken(it) }
        .flatMap { expandToken(it) }
        .distinct()

    private fun preprocess(raw: String): String {
        var normalized = raw.lowercase()
        PHRASE_ALIASES.forEach { (phrase, replacement) ->
            normalized = normalized.replace(phrase, replacement)
        }
        return normalized
    }

    private fun expandToken(token: String): List<String> {
        val expanded = mutableSetOf(token)
        TOKEN_SUBSTRINGS.forEach { substring ->
            if (substring != token && token.contains(substring)) {
                expanded += substring
            }
        }
        TOKEN_SYNONYMS[token]?.let { expanded += it }
        return expanded.toList()
    }

    private fun translateToken(token: String): String {
        CYRILLIC_TOKEN_TRANSLATIONS[token]?.let { return it }
        CYRILLIC_STEM_TRANSLATIONS.forEach { (stem, replacement) ->
            if (token.startsWith(stem)) return replacement
        }
        return token
    }

    private fun hasReservedMarker(value: String): Boolean =
        RESERVED_MARKERS.any { marker -> value.contains(marker, ignoreCase = true) }

    private fun suffixIfTrimmed(items: List<String>): String {
        val trimmed = items.size - 25
        return if (trimmed > 0) " …(+${trimmed})" else ""
    }

    companion object {
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}0-9]+")
        private val TRIM_CHARS = charArrayOf(',', '.', '/', '-', '(', ')', '\'', '"')
        private val TOKEN_SUBSTRINGS = listOf("turbo", "super", "charger")
        private val TOKEN_SYNONYMS = mapOf(
            "turbocharger" to listOf("turbo"),
            "supercharger" to listOf("super"),
            "underspeed" to listOf("low"),
            "overspeed" to listOf("high"),
            "pcm" to listOf("module", "control"),
            "ecm" to listOf("module", "control"),
            "tcm" to listOf("module", "control"),
            "egr" to listOf("exhaust", "recirculation"),
            "exhaust" to listOf("egr"),
            "recirculation" to listOf("egr"),
            "evap" to listOf("evaporative", "vapor"),
            "evaporative" to listOf("evap"),
            "vapor" to listOf("evap"),
            "map" to listOf("manifold", "absolute", "pressure", "sensor"),
            "maf" to listOf("air", "flow", "sensor"),
            "air" to listOf("maf"),
            "flow" to listOf("maf"),
            "pressure" to listOf("map"),
            "sensor" to listOf("map", "maf"),
            "trap" to listOf("filter", "adsorber"),
            "adsorber" to listOf("trap"),
            "filter" to listOf("trap")
        )
        private val RESERVED_MARKERS = listOf("reserved", "not used", "зарезервировано")
        private val DESCRIPTION_SYSTEMS = setOf(
            ObdDtcDefinition.System.POWERTRAIN,
            ObdDtcDefinition.System.NETWORK
        )
        private val PHRASE_ALIASES = linkedMapOf(
            "exhaust gas recirculation" to "egr",
            "gas recirculation" to "egr",
            "выхлоп gas recirculation" to "egr",
            "evaporative emission" to "evap",
            "evaporative emissions" to "evap",
            "secondary air injection" to "air",
            "malfunction indicator lamp" to "mil"
        )
        private val CYRILLIC_TOKEN_TRANSLATIONS = mapOf(
            "выхлоп" to "exhaust",
            "воздух" to "air",
            "воздушной" to "air",
            "воздуха" to "air",
            "обнаружен" to "detected",
            "обнаружено" to "detected",
            "обнаружены" to "detected",
            "обнаруженный" to "detected",
            "пропуск" to "misfire",
            "пропуска" to "misfire",
            "пропуски" to "misfire",
            "случайный" to "random",
            "множественный" to "multiple",
            "поток" to "flow",
            "управление" to "control",
            "управления" to "control",
            "система" to "system",
            "системы" to "system",
            "систем" to "system",
            "клапан" to "valve",
            "клапана" to "valve",
            "катализатор" to "catalyst",
            "эффективность" to "efficiency",
            "испарительная" to "evaporative",
            "испарительный" to "evaporative",
            "паров" to "vapor",
            "пар" to "vapor",
            "улавливания" to "capture",
            "потоки" to "flow",
            "топливо" to "fuel",
            "топлива" to "fuel",
            "коррекция" to "trim",
            "коррекции" to "trim",
            "корректировка" to "trim",
            "температура" to "temperature",
            "температуры" to "temperature",
            "датчик" to "sensor",
            "датчика" to "sensor",
            "датчики" to "sensor",
            "цепь" to "circuit",
            "цепи" to "circuit",
            "низкий" to "low",
            "низкое" to "low",
            "низкая" to "low",
            "высокий" to "high",
            "высокое" to "high",
            "высокая" to "high",
            "напряжение" to "voltage",
            "напряжения" to "voltage",
            "прерывистый" to "intermittent",
            "индикатор" to "indicator",
            "индикатора" to "indicator",
            "службы" to "service",
            "регулятор" to "regulator",
            "регулятора" to "regulator",
            "коленвал" to "crankshaft",
            "распредвал" to "camshaft",
            "двигатель" to "engine",
            "двигателя" to "engine",
            "положение" to "position",
            "положения" to "position",
            "сопротивление" to "resistance",
            "сопротивления" to "resistance",
            "сигнал" to "signal",
            "сигнала" to "signal",
            "сигналы" to "signals",
            "перепутаны" to "swapped",
            "наддув" to "boost",
            "скорость" to "speed",
            "скорости" to "speed",
            "форсунка" to "injector",
            "форсунки" to "injector",
            "объем" to "volume",
            "объём" to "volume",
            "соленоид" to "solenoid",
            "соленоида" to "solenoid",
            "ряд" to "bank",
            "ряда" to "bank",
            "байпас" to "bypass",
            "обрыв" to "open",
            "превышение" to "overspeed",
            "корреляция" to "correlation",
            "инжектор" to "injector",
            "инжектора" to "injector",
            "турбо" to "turbo",
            "турбонагнетатель" to "turbocharger",
            "турбонагнетателя" to "turbocharger",
            "нагнетатель" to "supercharger",
            "нагнетателя" to "supercharger",
            "вестгейт" to "wastegate",
            "вестгейта" to "wastegate",
            "резерв" to "reserved",
            "зарезервировано" to "reserved",
            "служба" to "service",
            "коллектор" to "manifold",
            "барометрическое" to "barometric",
            "барометрического" to "barometric",
            "давление" to "pressure",
            "давления" to "pressure",
            "давлений" to "pressure",
            "давлен" to "pressure",
            "окружающая" to "ambient",
            "среда" to "environment",
            "рампа" to "rail",
            "рампе" to "rail",
            "утечка" to "leak",
            "большая" to "large",
            "малая" to "small",
            "небольшая" to "small",
            "очень" to "very",
            "впускного" to "intake",
            "впуск" to "intake",
            "охлаждающей" to "coolant",
            "охлаждающая" to "coolant",
            "жидкости" to "coolant",
            "жидкость" to "coolant",
            "педаль" to "pedal",
            "заслонка" to "throttle",
            "дроссельная" to "throttle",
            "дроссельной" to "throttle",
            "цилиндр" to "cylinder",
            "цилиндра" to "cylinder",
            "цилиндры" to "cylinder",
            "зажигание" to "ignition",
            "вход" to "input",
            "входа" to "input",
            "распределитель" to "distributor",
            "распределителя" to "distributor",
            "расходомер" to "maf",
            "расходомера" to "maf",
            "абсолютный" to "absolute",
            "абсолютного" to "absolute",
            "катушка" to "coil",
            "катушки" to "coil",
            "катушек" to "coil",
            "модуль" to "module",
            "модуля" to "module",
            "выброс" to "emission",
            "выбросы" to "emission",
            "продувка" to "purge",
            "вентиляция" to "vent",
            "вентиляционный" to "vent",
            "холостой" to "idle",
            "ход" to "idle",
            "обороты" to "rpm",
            "нагреватель" to "heater",
            "нагревателя" to "heater",
            "порог" to "threshold",
            "порога" to "threshold",
            "основной" to "main",
            "теплый" to "warm",
            "нагрев" to "warm",
            "ниже" to "below",
            "блок" to "module",
            "блока" to "module",
            "связь" to "communication",
            "связи" to "communication",
            "трансмиссия" to "transmission",
            "трансмиссии" to "transmission",
            "трансмиссией" to "transmission",
            "автомобиль" to "vehicle",
            "автомобиля" to "vehicle",
            "шина" to "bus",
            "шины" to "bus",
            "запрос" to "request",
            "насос" to "pump",
            "насоса" to "pump",
            "заклинило" to "stuck",
            "ловушка" to "trap",
            "частиц" to "particulate",
            "bus" to "bus",
            "absolute" to "absolute",
            "корреляции" to "correlation"
        )
        private val CYRILLIC_STEM_TRANSLATIONS = listOf(
            "производ" to "performance",
            "управлен" to "control",
            "солено" to "solenoid",
            "индик" to "indicator",
            "инжект" to "injector",
            "регулят" to "regulator",
            "температур" to "temperature",
            "корреляц" to "correlation",
            "цилинд" to "cylinder",
            "рамп" to "rail",
            "давлен" to "pressure",
            "коррект" to "trim",
            "катуш" to "coil",
            "испарит" to "evaporative",
            "вентиляц" to "vent",
            "нагрев" to "heater",
            "выброс" to "emission",
            "трансмисс" to "transmission",
            "связ" to "communication",
            "автомоб" to "vehicle",
            "насос" to "pump",
            "запрос" to "request",
            "ловуш" to "trap",
            "частиц" to "particulate"
        )
    }
}