package com.selfservice.kiosk.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.selfservice.kiosk.KioskApp
import java.util.Locale

class MdmCommandReceiver @VisibleForTesting internal constructor(
    private val dispatcher: CommandDispatcher,
    private val allowedPackagesProvider: () -> Set<String>
) : BroadcastReceiver() {

    constructor() : this(
        dispatcher = CommandDispatcher { context, command ->
            KioskApp.from(context).dispatchManagedMdmCommand(command)
        },
        allowedPackagesProvider = { MdmRuntimeConfig.allowedPackages() }
    )

    override fun onReceive(context: Context, intent: Intent?) {
        intent ?: return
        if (intent.action != MdmCommandIntents.ACTION_MANAGED_COMMAND) {
            return
        }
        val sender = resolveSender(context.packageManager, allowedPackagesProvider())
        if (!sender.isAllowed) {
            Log.w(TAG, "Rejected MDM command from unauthorized sender uid=${sender.uid} packages=${sender.packages}")
            return
        }
        val command = MdmCommandIntentParser.fromIntent(intent, sender.primaryPackage)
        if (command == null) {
            Log.w(TAG, "Ignoring MDM command without type")
            return
        }
        val accepted = dispatcher.dispatch(context, command)
        if (!accepted) {
            Log.w(TAG, "MDM command manager unavailable, dropping ${command.type}")
        }
    }

    private fun resolveSender(
        packageManager: PackageManager,
        allowedPackages: Set<String>
    ): SenderInfo {
        val uid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        val normalizedAllowed = if (allowedPackages.isEmpty()) {
            val fallback = packageManager.getNameForUid(uid)?.lowercase(Locale.US)
            if (fallback != null) setOf(fallback) else emptySet()
        } else {
            allowedPackages.asSequence()
                .map { it.lowercase(Locale.US) }
                .toSet()
        }
        val isAllowed = packages.any { normalizedAllowed.contains(it.lowercase(Locale.US)) }
        return SenderInfo(uid = uid, packages = packages, isAllowed = isAllowed, primaryPackage = packages.firstOrNull())
    }

    fun interface CommandDispatcher {
        fun dispatch(context: Context, command: MdmCommand): Boolean
    }

    private data class SenderInfo(
        val uid: Int,
        val packages: List<String>,
        val isAllowed: Boolean,
        val primaryPackage: String?
    )

    companion object {
        private const val TAG = "MdmCommandReceiver"
    }
}
