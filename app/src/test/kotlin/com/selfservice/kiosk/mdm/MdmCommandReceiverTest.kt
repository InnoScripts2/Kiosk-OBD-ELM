package com.selfservice.kiosk.mdm

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MdmCommandReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dispatched = mutableListOf<MdmCommand>()
    private val dispatcher = MdmCommandReceiver.CommandDispatcher { _, command ->
        dispatched += command
        true
    }

    @AfterTest
    fun tearDown() {
        dispatched.clear()
        ShadowBinder.setCallingUid(0)
    }

    @Test
    fun `allowed sender dispatches command`() {
        val receiver = MdmCommandReceiver(dispatcher) { setOf("com.vendor.mdm") }
        prepareSender(uid = 42, packages = arrayOf("com.vendor.mdm"))
        val intent = Intent(MdmCommandIntents.ACTION_MANAGED_COMMAND).apply {
            putExtra(MdmCommandIntents.EXTRA_COMMAND_ID, "cmd-100")
            putExtra(MdmCommandIntents.EXTRA_COMMAND_TYPE, "heartbeat")
            putExtra(MdmCommandIntents.EXTRA_COMMAND_PAYLOAD, "{\"force\":true}")
            putExtra(MdmCommandIntents.EXTRA_COMMAND_ISSUED_AT, 1234L)
        }

        receiver.onReceive(context, intent)

        assertEquals(1, dispatched.size)
        val command = dispatched.first()
        assertEquals("cmd-100", command.id)
        assertEquals("heartbeat", command.type)
        assertEquals(1234L, command.issuedAtMillis)
        assertEquals(true, command.payload["force"])
    }

    @Test
    fun `unauthorized sender is rejected`() {
        val receiver = MdmCommandReceiver(dispatcher) { setOf("com.vendor.mdm") }
        prepareSender(uid = 7, packages = arrayOf("com.attacker.app"))
        val intent = Intent(MdmCommandIntents.ACTION_MANAGED_COMMAND).apply {
            putExtra(MdmCommandIntents.EXTRA_COMMAND_TYPE, "heartbeat")
        }

        receiver.onReceive(context, intent)

        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `missing type does not dispatch`() {
        val receiver = MdmCommandReceiver(dispatcher) { setOf(context.packageName) }
        prepareSender(uid = 8, packages = arrayOf(context.packageName))
        val intent = Intent(MdmCommandIntents.ACTION_MANAGED_COMMAND)

        receiver.onReceive(context, intent)

        assertTrue(dispatched.isEmpty())
    }

    private fun prepareSender(uid: Int, packages: Array<String>) {
        ShadowBinder.setCallingUid(uid)
        check(android.os.Binder.getCallingUid() == uid) {
            "Binder calling uid ${android.os.Binder.getCallingUid()} does not match expected $uid"
        }
        val pm = context.packageManager
        val shadowPm = Shadows.shadowOf(pm)
        shadowPm.setPackagesForUid(uid, *packages)
        val registered = pm.getPackagesForUid(uid)?.toSet()
        check(registered != null) { "packages for uid $uid not registered" }
        check(packages.all { registered.contains(it) }) {
            "expected ${packages.toList()} for uid $uid but was $registered"
        }
    }
}
