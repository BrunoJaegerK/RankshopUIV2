package com.brunojaegerk.rankshop;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Evita que un jugador tenga más de una compra de rango activa a la vez.
 *
 * Esta clase protege el ciclo de ejecución; la validación del formulario
 * pendiente se realiza en Main antes de invocar la compra.
 */
public final class PurchaseManager {

    private final Set<UUID> purchasesInProgress = new HashSet<>();

    /**
     * Reserva la compra para el jugador.
     *
     * @return true si se pudo reservar; false si ya tenía una compra activa.
     */
    public synchronized boolean tryBeginPurchase(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        return purchasesInProgress.add(playerId);
    }

    /**
     * Indica si el jugador tiene una compra activa.
     */
    public synchronized boolean isPurchaseInProgress(UUID playerId) {
        return playerId != null && purchasesInProgress.contains(playerId);
    }

    /**
     * Libera la reserva. Debe ejecutarse siempre, incluso si la compra falla.
     */
    public synchronized void finishPurchase(UUID playerId) {
        if (playerId != null) {
            purchasesInProgress.remove(playerId);
        }
    }

    /**
     * Limpia reservas si el plugin se deshabilita.
     */
    public synchronized void clear() {
        purchasesInProgress.clear();
    }
}
