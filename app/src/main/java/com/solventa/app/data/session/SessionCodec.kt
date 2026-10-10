package com.solventa.app.data.session

import com.solventa.app.data.auth.ClienteResumen
import com.solventa.app.data.auth.EstadoKyc
import com.solventa.app.data.auth.Session
import org.json.JSONObject

/** Serializa la sesión para cifrarla con la llave biométrica. */
object SessionCodec {

    fun encode(session: Session): ByteArray = JSONObject()
        .put("accessToken", session.accessToken)
        .put("refreshToken", session.refreshToken)
        .put("expiresInSeconds", session.expiresInSeconds)
        .put(
            "cliente",
            JSONObject()
                .put("id", session.cliente.id)
                .put("nombre", session.cliente.nombre)
                .put("estadoKyc", session.cliente.estadoKyc.name)
                .put("dispositivoVinculado", session.cliente.dispositivoVinculado),
        )
        .toString()
        .toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Session {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val cliente = json.getJSONObject("cliente")
        return Session(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresInSeconds = json.getLong("expiresInSeconds"),
            cliente = ClienteResumen(
                id = cliente.getString("id"),
                nombre = cliente.getString("nombre"),
                estadoKyc = EstadoKyc.valueOf(cliente.getString("estadoKyc")),
                dispositivoVinculado = cliente.getBoolean("dispositivoVinculado"),
            ),
        )
    }
}
