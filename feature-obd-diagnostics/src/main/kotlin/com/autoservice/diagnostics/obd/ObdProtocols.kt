package com.autoservice.diagnostics.obd

enum class ObdProtocol(val command: String) {
    AUTO("ATSP0"),
    SAE_J1850_PWM("ATSP1"),
    SAE_J1850_VPW("ATSP2"),
    ISO_9141_2("ATSP3"),
    ISO_14230_4_KWP("ATSP4"),
    ISO_15765_4_CAN_11_500("ATSP5"),
    ISO_15765_4_CAN_29_500("ATSP6"),
    ISO_15765_4_CAN_11_250("ATSP7"),
    ISO_15765_4_CAN_29_250("ATSP8")
}
