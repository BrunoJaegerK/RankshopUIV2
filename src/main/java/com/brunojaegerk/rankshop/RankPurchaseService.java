package com.brunojaegerk.rankshop;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.lldv.llamaeconomy.LlamaEconomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contiene la lógica de negocio para comprar rangos.
 * Main conserva la coordinación del plugin y el bloqueo de compras.
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

    /**
     * Procesa la compra de un rango.
     * El bloqueo se administra en Main.
     */
    public void purchaseRank(Player player, String id) {
        if (player == null || id == null || id.trim().isEmpty()) {
            if (player != null) {
                player.sendMessage(plugin.message("messages.rank-error"));
            }
            return;
        }

        double price = plugin.getConfig().getDouble(id + ".price");
        String group = plugin.getConfig().getString(id + ".lp-user");
        String rankName = plugin.getConfig().getString(id + ".name", id);

        if (!Double.isFinite(price)
                || price < 0
                || group == null
                || !SAFE_GROUP.matcher(group).matches()) {

            player.sendMessage(
                    plugin.message("messages.rank-config-error")
            );

            plugin.getLogger().warning(
                    "Configuración inválida para el rango " + id
            );
            return;
        }

        // Si no se puede verificar el grupo, se falla de forma segura.
        if (hasRankGroup(player, group)) {
            player.sendMessage(plugin.text(
                    "messages.rank-already-owned",
                    "§eYa tienes el rango §d{rank}§e.",
                    Collections.singletonMap("rank", rankName)
            ));
            return;
        }

        if (LlamaEconomy.getAPI().getMoney(player) < price) {
            player.sendMessage(plugin.message("messages.no-money"));
            return;
        }

        boolean dispatched = plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "lp user " + player.getName() + " parent add " + group
        );

        if (!dispatched) {
            player.sendMessage(plugin.message("messages.rank-error"));

            plugin.getLogger().warning(
                    "No se pudo ejecutar LuckPerms para "
                            + player.getName()
            );
            return;
        }

        LlamaEconomy.getAPI().reduceMoney(player.getName(), price);

        // Conserva los kits anteriores y añade los del rango comprado.
        Set<String> unlocked =
                new LinkedHashSet<>(plugin.unlockedKits(player));

        for (String kitId : plugin.getConfig().getStringList(id + ".kits")) {
            if (kits.exists(kitId)) {
                unlocked.add(kitId);
            } else {
                plugin.getLogger().warning(
                        "El rango " + id
                                + " referencia el kit inexistente "
                                + kitId
                );
            }
        }

        plugin.setUnlockedKits(player, new ArrayList<>(unlocked));

        player.sendMessage(plugin.text(
                "messages.purchase-success",
                "§aHas comprado el rango {rank}.",
                Collections.singletonMap("rank", rankName)
        ));
    }

    private boolean hasRankGroup(Player player, String groupName) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager()
                    .getUser(player.getUniqueId());

            if (user == null) {
                plugin.getLogger().warning(
                        "No se pudieron consultar los rangos de "
                                + player.getName()
                );

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
                    "Error al verificar el rango de "
                            + player.getName()
                            + ": " + exception.getMessage()
            );

            // Si no se puede comprobar el rango, no se permite cobrar.
            return true;
        }
    }
}

