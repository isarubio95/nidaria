// Estado de la partida en el teléfono. Guarda la última foto del servidor
// y, entre visitas, mueve las necesidades con la misma simulación que usa
// el servidor (rules/Needs.kt, igual que shared/needs.js).

package com.nidaria.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nidaria.app.rules.SimResult
import com.nidaria.app.rules.TICKET_REGEN_MS
import com.nidaria.app.rules.Tickets
import com.nidaria.app.rules.pendingIncome
import com.nidaria.app.rules.simulate
import com.nidaria.app.rules.ticketsNow

class Store {
    // Estado de Compose: la interfaz que lo lee se repinta al cambiar.
    var state by mutableStateOf<GameState?>(null)
        private set

    // Diferencia entre el reloj del servidor y el del teléfono.
    var skew = 0.0
        private set

    // Hora del servidor, corregida con el desfase del reloj local.
    fun now(): Double = System.currentTimeMillis() + skew

    fun apply(next: GameState) {
        skew = next.now - System.currentTimeMillis()
        state = next
    }

    fun clear() {
        state = null
    }

    // La criatura adelantada hasta ahora. Si en `events` hay algo
    // (evolución, muerte), conviene confirmarlo con el servidor.
    fun live(): SimResult? {
        val current = state ?: return null
        val creature = current.creature ?: return null
        return simulate(creature, now(), current.timeScale)
    }

    fun tickets(): Tickets {
        val current = state ?: return Tickets(0, 0.0, null)
        val user = current.user
        if (user.tickets >= user.ticketsMax) return Tickets(user.ticketsMax, now(), null)
        val nextAt = user.ticketsNextAt ?: return Tickets(user.tickets, now(), null)
        val at = nextAt - TICKET_REGEN_MS / current.timeScale
        return ticketsNow(user.tickets, at, now(), current.timeScale)
    }

    fun income(): Int {
        val current = state ?: return 0
        return pendingIncome(current.user.incomeAt, now(), current.timeScale)
    }
}
