
package com.brunojaegerk.rankshop;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.PluginBase;
import net.lldv.llamaeconomy.LlamaEconomy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends PluginBase implements Listener {

    // Guarda el identificador seleccionado por cada jugador.
    private final Map<String, String> pendingPurchases = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("LlamaEconomy") == null) {
            getLogger().error("No se encontró LlamaEconomy. Deshabilitando Rankshop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().error("No se encontró LuckPerms. Deshabilitando Rankshop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Rankshop habilitado correctamente.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("ranks")
                && !command.getName().equalsIgnoreCase("rangos")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando solo puede usarse dentro del juego.");
            return true;
        }

        Player player = (Player) sender;
        List<String> identifiers = getConfig().getStringList("identifiers");

        if (identifiers.isEmpty()) {
            player.sendMessage("§cNo hay rangos configurados en Rankshop.");
            getLogger().warning("La lista identifiers está vacía en config.yml.");
            return true;
        }

        FormWindowSimple form = new FormWindowSimple(
                getConfig().getString("form-name", "RankShop"),
                "§7Selecciona el rango que deseas comprar."
        );

        for (String identifier : identifiers) {
            String name = getConfig().getString(
                    identifier + ".name",
                    identifier
            );

            double price = getConfig().getDouble(identifier + ".price");

            form.addButton(new ElementButton(
                    "§8" + name
                            + "\n§d$§e" + price
                            + "\n§d§l»§r §7Toca para seleccionar!"
            ));
        }

        player.showFormWindow(form);
        return true;
    }

    @EventHandler
    public void onFormResponded(PlayerFormRespondedEvent event) {
        if (event.wasClosed() || event.getResponse() == null) {
            return;
        }

        FormWindow window = event.getWindow();

        if (!(window instanceof FormWindowSimple)
                || !(event.getResponse() instanceof FormResponseSimple)) {
            return;
        }

        Player player = event.getPlayer();
        String title = ((FormWindowSimple) window).getTitle();
        int button = ((FormResponseSimple) event.getResponse())
                .getClickedButtonId();

        String shopTitle = getConfig().getString("form-name", "RankShop");
        String confirmationTitle = getConfig().getString(
                "confirmation-form-name",
                "Confirm your purchase"
        );

        if (shopTitle.equals(title)) {
            List<String> identifiers = getConfig().getStringList("identifiers");

            if (button < 0 || button >= identifiers.size()) {
                return;
            }

            // El botón es un índice: lo convertimos al identificador real.
            String identifier = identifiers.get(button);

            pendingPurchases.put(player.getName(), identifier);

            String name = getConfig().getString(
                    identifier + ".name",
                    identifier
            );
            double price = getConfig().getDouble(identifier + ".price");

            FormWindowSimple confirmation = new FormWindowSimple(
                    confirmationTitle,
                    "¿Estás seguro de que quieres comprar el rango §d"
                            + name + " §7por §d$§e" + price + "§7?"
            );

            confirmation.addButton(new ElementButton("§aComprar"));
            confirmation.addButton(new ElementButton("§cCancelar"));

            player.showFormWindow(confirmation);
            return;
        }

        if (confirmationTitle.equals(title)) {
            String identifier = pendingPurchases.remove(player.getName());

            if (identifier == null) {
                return;
            }

            // El botón 0 confirma; el botón 1 cancela.
            if (button != 0) {
                player.sendMessage("§7Compra cancelada.");
                return;
            }

            double price = getConfig().getDouble(identifier + ".price");
            String rankName = getConfig().getString(
                    identifier + ".name",
                    identifier
            );
            String group = getConfig().getString(identifier + ".lp-user");

            if (group == null || group.trim().isEmpty()) {
                getLogger().warning(
                        "No está configurado lp-user para el rango " + identifier
                );
                player.sendMessage("§cEste rango está mal configurado.");
                return;
            }

            if (LlamaEconomy.getAPI().getMoney(player) < price) {
                player.sendMessage(getConfig().getString(
                        "no-money",
                        "§cNo tienes suficiente dinero."
                ));
                return;
            }

            LlamaEconomy.getAPI().reduceMoney(player.getName(), price);

            getServer().dispatchCommand(
                    getServer().getConsoleSender(),
                    "lp user " + player.getName() + " parent add " + group
            );

            player.sendMessage(getConfig().getString(
                    "rank-purchased",
                    "§aHas comprado el rango " + rankName + "."
            ));
        }
    }
}
