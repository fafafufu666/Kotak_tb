package com.fallbracelet.relay

import com.fallbracelet.data.AppSettings
import com.fallbracelet.data.SettingsRepository
import org.json.JSONObject

data class LinkedCaregiver(val id: Long, val name: String, val linkedAt: Long)
data class Invite(val code: String, val expiresAt: Long)
/** An ESKALASI on the relay and who saw it ("Tandai dilihat"); [by] is null while unseen. */
data class StatusEskalasi(val deviceAddress: String, val seq: Long, val by: String?, val at: Long?)

/** The patient phone's side of the relay: registration, heartbeats, invites, ESKALASI and STATUS_HARIAN. */
class WearerRelay(
    private val settings: SettingsRepository,
    private val current: () -> AppSettings,
) {

    val configured: Boolean
        get() = current().relayUrl.isNotBlank() && current().wearerToken != null

    private fun client(): RelayClient {
        val s = current()
        return RelayClient(s.relayUrl, s.wearerToken ?: throw RelayException(401, "not registered with the relay"))
    }

    /** Registers this phone as a wearer the first time a relay address is set. */
    suspend fun ensureRegistered() {
        val s = current()
        if (s.relayUrl.isBlank() || s.wearerToken != null) return
        val r = RelayClient(s.relayUrl, null).post("/v1/wearers", JSONObject().put("name", s.wearerName.ifBlank { "Pasien" }))
        settings.setWearerToken(r.getString("token"))
    }

    suspend fun rename(name: String) {
        if (configured && name.isNotBlank()) client().put("/v1/wearers/me", JSONObject().put("name", name))
    }

    /** Tells the relay this phone is alive (it warns the PMO after 15 min of silence); returns the PMO count. */
    suspend fun heartbeat(): Int =
        client().post("/v1/heartbeat", JSONObject().put("status", JSONObject())).optInt("caregivers")

    suspend fun invite(): Invite {
        val r = client().post("/v1/invites")
        return Invite(r.getString("code"), r.getLong("expiresAt"))
    }

    suspend fun caregivers(): List<LinkedCaregiver> {
        val a = client().get("/v1/caregivers").getJSONArray("caregivers")
        return List(a.length()) { i ->
            val c = a.getJSONObject(i)
            LinkedCaregiver(c.getLong("id"), c.getString("name"), c.getLong("linkedAt"))
        }
    }

    suspend fun removeCaregiver(id: Long) {
        client().delete("/v1/caregivers/$id")
    }

    /** Uploads one ESKALASI (an event with `detail` and the re-send timing); returns its relay id. */
    suspend fun kirimEskalasi(event: JSONObject): Long = client().post("/v1/events", event).getLong("id")

    /** Uploads the daily dose summary; the reply says which ESKALASI the PMO has seen. */
    suspend fun kirimStatusHarian(ringkasan: JSONObject): List<StatusEskalasi> {
        val a = client().post("/v1/status-harian", ringkasan).optJSONArray("eskalasi") ?: return emptyList()
        return List(a.length()) { i ->
            val e = a.getJSONObject(i)
            StatusEskalasi(
                deviceAddress = e.getString("deviceAddress"),
                seq = e.getLong("seq"),
                by = if (e.isNull("ackedBy")) null else e.getString("ackedBy"),
                at = if (e.isNull("ackedAt")) null else e.getLong("ackedAt"),
            )
        }
    }
}
