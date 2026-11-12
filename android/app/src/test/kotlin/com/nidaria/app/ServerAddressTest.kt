package com.nidaria.app

import com.nidaria.app.data.Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun acceptsWhatAPersonWouldType() {
        assertEquals("http://192.168.1.20:3000", Api.cleanServer("192.168.1.20:3000"))
        assertEquals("http://192.168.1.20:3000", Api.cleanServer("  http://192.168.1.20:3000/ "))
        assertEquals("https://nidaria.example", Api.cleanServer("https://nidaria.example"))
        assertEquals("http://mi-ordenador.local:3000", Api.cleanServer("mi-ordenador.local:3000"))
    }

    @Test
    fun rejectsBrokenAddresses() {
        assertNull(Api.cleanServer(""))
        assertNull(Api.cleanServer("   "))
        assertNull(Api.cleanServer("192.168.1.20:"))
        assertNull(Api.cleanServer("192.168.1.20:99999"))
        assertNull(Api.cleanServer("http://"))
        assertNull(Api.cleanServer("ftp//nada:xx"))
    }
}
