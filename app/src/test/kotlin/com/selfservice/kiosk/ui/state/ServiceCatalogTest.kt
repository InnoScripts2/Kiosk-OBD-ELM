package com.selfservice.kiosk.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCatalogTest {
    @Test
    fun `primary services list preserves thickness first`() {
        val services = ServiceCatalog.primaryServices()
        assertEquals(ServiceCatalog.ServiceId.THICKNESS, services.first().id)
        assertEquals(2, services.size)
    }

    @Test
    fun `thickness descriptor contains compliance note`() {
        val descriptor = ServiceCatalog.descriptor(ServiceCatalog.ServiceId.THICKNESS)
        assertTrue(descriptor.complianceNote.contains("оплат"))
        assertTrue(descriptor.priceLabel.contains("₽"))
    }

    @Test
    fun `obd descriptor requires real adapter`() {
        val descriptor = ServiceCatalog.descriptor(ServiceCatalog.ServiceId.OBD)
        assertTrue(descriptor.complianceNote.contains("симуляции"))
        assertTrue(descriptor.sellingPoints.any { it.contains("Clear DTC", ignoreCase = true) })
    }
}
