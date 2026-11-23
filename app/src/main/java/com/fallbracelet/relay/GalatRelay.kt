package com.fallbracelet.relay

import java.io.IOException

/**
 * What went wrong talking to the relay, in terms the patient can act on. The UI turns each
 * kind into an Indonesian sentence from `strings.xml`; the raw exception text is never shown.
 */
enum class GalatRelay {
    /** No answer: relay off, wrong address, or the phone on another WiFi. */
    TIDAK_TERHUBUNG,

    /** The relay only accepts phones on its own network (403 from `LAN_ONLY`). */
    BEDA_JARINGAN,

    /** The relay answered with an error of its own (5xx). */
    RELAY_BERMASALAH,

    /** The relay refused the request (other 4xx, e.g. this phone not registered). */
    DITOLAK,

    /** Anything else, e.g. a reply the app could not read. */
    LAIN;

    companion object {
        fun dari(e: Throwable): GalatRelay = when {
            e is RelayException && e.status == 0 -> TIDAK_TERHUBUNG
            e is RelayException && e.status == 403 -> BEDA_JARINGAN
            e is RelayException && e.status >= 500 -> RELAY_BERMASALAH
            e is RelayException -> DITOLAK
            e is IOException -> TIDAK_TERHUBUNG
            else -> LAIN
        }
    }
}
