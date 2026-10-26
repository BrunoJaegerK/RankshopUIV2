package com.brunojaegerk.rankshop;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestiona la validación, entrega y cooldown de los kits.
 * Main conserva la apertura de formularios y el manejo de sus respuestas.
 */
public final class KitService {

    private final Main plugin;
    private final Config kits;
    private final Config playerData;

    public KitService(Main plugin, Config kits, Config playerData) {
        if (plugin == null || kits == null || playerData == null) {
            throw new IllegalArgumentException(
                    "plugin, kits y playerData no pueden ser null"
            );
        }

        this.plugin = plugin;
        this.kits = kits;
        this.playerData = playerData;
    }

    /**
     * Reclama un kit si el jugador lo tiene desbloqueado y está disponible.
     */
    public void claimKit(Player player, String id) {
        if (player == null || id == null || id.trim().isEmpty()) {
            return;
        }

        if (!plugin.unlockedKits(player).contains(id)) {
            player.sendMessage(plugin.message("messages.kit-no-permission"));
            return;
        }

        if (!kits.exists(id)) {
            player.sendMessage(plugin.message("messages.kit-config-error"));
            return;
        }

        long remaining = cooldownRemaining(player, id);

        if (remaining > 0) {
            player.sendMessage(
                    plugin.text(
                            "messages.kit-cooldown",
                            "§cDisponible en {time}.",
                            Collections.singletonMap(
                                    "time",
                                    duration(remaining)
                            )
                    )
            );
            return;
        }

        List<String> entries = kits.getStringList(id + ".items");

        if (entries.isEmpty()) {
            player.sendMessage(plugin.message("messages.kit-empty"));
            return;
        }

        List<Item> items = new ArrayList<>();

        try {
            for (String entry : entries) {
                items.add(parseKitItem(entry));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Objeto inválido en el kit " + id
                            + ": " + ex.getMessage()
            );
            player.sendMessage(plugin.message("messages.kit-config-error"));
            return;
        }

        // Verifica que quepan todos los objetos antes de entregarlos.
        for (Item item : items) {
            if (!player.getInventory().canAddItem(item)) {
                player.sendMessage(plugin.message("messages.inventory-full"));
                return;
            }
        }

        for (Item item : items) {
            player.getInventory().addItem(item);
        }

        Object raw = kits.get(id + ".cooldown-seconds");

        long cooldown = raw instanceof Number
                ? ((Number) raw).longValue()
                : 86400L;

        cooldown = Math.max(0L, cooldown);

        playerData.set(
                "players." + plugin.playerKey(player)
                        + ".cooldowns." + id,
                System.currentTimeMillis() / 1000L + cooldown
        );

        playerData.save();

        player.sendMessage(
                plugin.text(
                        "messages.kit-claimed",
                        "§aHas reclamado el kit {kit}.",
                        Collections.singletonMap(
                                "kit",
                                kits.getString(id + ".name", id)
                        )
                )
        );
    }

    /**
     * Devuelve los segundos restantes del cooldown.
     * Devuelve cero si el kit está disponible.
     */
    public long cooldownRemaining(Player player, String id) {
        if (player == null || id == null) {
            return 0L;
        }

        Object value = playerData.get(
                "players." + plugin.playerKey(player)
                        + ".cooldowns." + id
        );

        long until = value instanceof Number
                ? ((Number) value).longValue()
                : 0L;

        return Math.max(
                0L,
                until - System.currentTimeMillis() / 1000L
        );
    }

    /*
     * Formatos admitidos:
     *
     * ID:META:CANTIDAD
     * ID:META:CANTIDAD:NOMBRE
     * ID:META:CANTIDAD:default:ENCANTAMIENTO:NIVEL
     * ID:META:CANTIDAD:NOMBRE:ENCANTAMIENTO:NIVEL
     *
     * Se pueden agregar varios pares ENCANTAMIENTO:NIVEL.
     */
    private Item parseKitItem(String entry) {
        String[] parts = entry.split(":", -1);

        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Formato esperado ID:META:CANTIDAD: " + entry
            );
        }

        int itemId = Integer.parseInt(parts[0].trim());
        int meta = Integer.parseInt(parts[1].trim());
        int amount = Integer.parseInt(parts[2].trim());

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero: " + entry
            );
        }

        Item item = Item.get(itemId, meta, amount);
        int enchantmentStart = 3;

        if (parts.length >= 4) {
            String customName = parts[3];

            if (!customName.equalsIgnoreCase("default")
                    && !customName.isEmpty()) {
                item.setCustomName(customName.replace('&', '§'));
            }

            enchantmentStart = 4;
        }

        int remainingParts = parts.length - enchantmentStart;

        if (remainingParts % 2 != 0) {
            throw new IllegalArgumentException(
                    "Cada encantamiento necesita un ID y un nivel: "
                            + entry
            );
        }

        for (int i = enchantmentStart; i < parts.length; i += 2) {
            int enchantmentId = Integer.parseInt(parts[i].trim());
            int level = Integer.parseInt(parts[i + 1].trim());

            if (level < 1) {
                throw new IllegalArgumentException(
                        "El nivel del encantamiento debe ser positivo: "
                                + entry
                );
            }

            Enchantment enchantment =
                    Enchantment.getEnchantment(enchantmentId);

            if (enchantment == null) {
                throw new IllegalArgumentException(
                        "ID de encantamiento no reconocido: "
                                + enchantmentId
                );
            }

            enchantment.setLevel(level, false);
            item.addEnchantment(enchantment);
        }

        return item;
    }

    private String duration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }

        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }

        return remainingSeconds + "s";
    }
}


