package com.autoservice.diagnostics.obd

enum class ObdMode(val code: String) {
    CURRENT_DATA("01"),
    FREEZE_FRAME("02"),
    DIAGNOSTIC_CODES("03"),
    CLEAR_CODES("04"),
    OXYGEN_SENSORS("05"),
    ONBOARD_TESTS("06"),
    PENDING_CODES("07"),
    CONTROL_COMMANDS("08"),
    VEHICLE_INFORMATION("09"),
    PERMANENT_DTCS("0A")
}
