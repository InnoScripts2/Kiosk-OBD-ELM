package com.autoservice.diagnostics.obd.commands

import com.autoservice.diagnostics.obd.PID

/**
 * Default concrete command that just wraps a PID definition.
 */
class SimpleObdCommand(pid: PID) : ObdCommand(pid)
