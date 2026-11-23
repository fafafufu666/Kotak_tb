package com.fallbracelet

import com.fallbracelet.relay.GalatRelay
import com.fallbracelet.relay.RelayException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException

class GalatRelayTest {
    @Test fun tidakTerjangkau() {
        assertEquals(GalatRelay.TIDAK_TERHUBUNG, GalatRelay.dari(RelayException(0, "Failed to connect to /192.168.0.104:8787")))
        assertEquals(GalatRelay.TIDAK_TERHUBUNG, GalatRelay.dari(ConnectException("refused")))
    }

    @Test fun relayHanyaJaringanRumah() =
        assertEquals(GalatRelay.BEDA_JARINGAN, GalatRelay.dari(RelayException(403, "this relay only accepts phones on the home network")))

    @Test fun relayBermasalah() {
        assertEquals(GalatRelay.RELAY_BERMASALAH, GalatRelay.dari(RelayException(500, "boom")))
        assertEquals(GalatRelay.RELAY_BERMASALAH, GalatRelay.dari(RelayException(503, "busy")))
    }

    @Test fun ditolak() {
        assertEquals(GalatRelay.DITOLAK, GalatRelay.dari(RelayException(401, "wearer token required")))
        assertEquals(GalatRelay.DITOLAK, GalatRelay.dari(RelayException(404, "not found")))
    }

    @Test fun lainnya() {
        assertEquals(GalatRelay.LAIN, GalatRelay.dari(JSONException("No value for code")))
        assertEquals(GalatRelay.LAIN, GalatRelay.dari(IllegalStateException()))
    }
}
