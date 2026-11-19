package com.brunojaegerk.rankshop;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contiene la lógica de negocio para comprar rangos.
 * Cada rango utiliza la moneda configurada en config.yml.
 */
public final class RankPurchaseService {

    private static final Pattern SAFE_GROUP =
            Pattern.compile("[A-Za-z0-9_.-]+");

    private final Main plugin;
    private final Config kits;

    public RankPurchaseService(Main plugin, Config kits) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin no puede ser null");
        }
        if (kits == null) {
            throw new IllegalArgumentException("kits no puede ser null");
        }
        this.plugin = plugin;
        this.kits = kits;
    }

    public void purchaseRank(Player player, String id) {
        if (player == null || id == null || id.trim().isEmpty()) {
            if (player != null) {
                player.sendMessage(plugin.message("messages.rank-error"));
            }
            plugin.getPurchaseLogger().log(
                    player == null ? "unknown" : player.getName(),
                    id, id, Double.NaN, "FAILED", "invalid_purchase_request");
            return;
        }

        double price = plugin.getConfig().getDouble(id + ".price");
        String group = plugin.getConfig().getString(id + ".lp-user");
        String rankName = plugin.getConfig().getString(id + ".name", id);
        String currencyId = plugin.getConfig().getString(
                id + ".currency", EconomyBridge.LEGACY_CURRENCY);

        if (!Double.isFinite(price) || price < 0
                || group == null || !SAFE_GROUP.matcher(group).matches()
                || !plugin.getEconomyBridge().isAvailable(currencyId)) {
            player.sendMessage(plugin.message("messages.rank-config-error"));
            plugin.getLogger().warning(
                    "Configuración inválida para el rango " + id
                            + " (currency=" + currencyId + ")");
            plugin.getPurchaseLogger().log(
                    player.getName(), id, rankName, price,
                    "FAILED", "invalid_rank_configuration_or_currency");
            return;
        }

        if (hasRankGroup(player, group)) {
            player.sendMessage(plugin.text(
                    "messages.rank-already-owned",
                    "§eYa tienes el rango §d{rank}§e.",
                    Collections.singletonMap("rank", rankName)));
            plugin.getPurchaseLogger().log(
                    player.getName(), id, rankName, price,
                    "REJECTED", "rank_owned_or_group_check_failed");
            return;
        }

        if (!plugin.getEconomyBridge().hasBalance(player, currencyId, price)) {
            player.sendMessage(plugin.message("messages.no-money"));
            plugin.getPurchaseLogger().log(
                    player.getName(), id, rankName, price,
                    "REJECTED", "insufficient_balance:" + currencyId);
            return;
        }

        // Descontar primero para evitar asignar un rango si el cobro falla.
        if (!plugin.getEconomyBridge().withdraw(player, currencyId, price)) {
            player.sendMessage(plugin.message("messages.no-money"));
            plugin.getPurchaseLogger().log(
                    player.getName(), id, rankName, price,
                    "FAILED", "currency_withdraw_failed:" + currencyId);
            return;
        }

        boolean dispatched = plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "lp user " + player.getName() + " parent add " + group);

        if (!dispatched) {
            boolean refunded = plugin.getEconomyBridge().deposit(
                    player, currencyId, price);
            player.sendMessage(plugin.message("messages.rank-error"));
            plugin.getLogger().warning(
                    "No se pudo ejecutar LuckPerms para " + player.getName()
                            + ". Reembolso " + (refunded ? "realizado" : "fallido")
                            + " (" + currencyId + ").");
            plugin.getPurchaseLogger().log(
                    player.getName(), id, rankName, price,
                    "FAILED", refunded
                            ? "luckperms_dispatch_failed_refunded:" + currencyId
                            : "luckperms_dispatch_failed_refund_failed:" + currencyId);
            return;
        }

        Set<String> unlocked =
                new LinkedHashSet<>(plugin.unlockedKits(player));

        for (String kitId : plugin.getConfig().getStringList(id + ".kits")) {
            if (kits.exists(kitId)) {
                unlocked.add(kitId);
            } else {
                plugin.getLogger().warning(
                        "El rango " + id
                                + " referencia el kit inexistente " + kitId);
            }
        }

        plugin.setUnlockedKits(player, new ArrayList<>(unlocked));
        plugin.getPurchaseLogger().log(
                player.getName(), id, rankName, price,
                "SUCCESS", "purchased:" + currencyId);

        player.sendMessage(plugin.text(
                "messages.purchase-success",
                "§aHas comprado el rango {rank}.",
                Collections.singletonMap("rank", rankName)));
    }

    private boolean hasRankGroup(Player player, String groupName) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());

            if (user == null) {
                plugin.getLogger().warning(
                        "No se pudieron consultar los rangos de " + player.getName());
                return true;
            }

            if (user.getPrimaryGroup().equalsIgnoreCase(groupName)) {
                return true;
            }

            for (Group inheritedGroup : user.getInheritedGroups(
                    QueryOptions.defaultContextualOptions())) {
                if (inheritedGroup.getName().equalsIgnoreCase(groupName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Error al verificar el rango de " + player.getName()
                            + ": " + exception.getMessage());
            return true;
        }
    }
}
