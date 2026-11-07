
package com.brunojaegerk.rankshop;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.response.FormResponseModal;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowModal;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Main extends PluginBase implements Listener {

    private Config ranks;
    private Config kits;
    private Config playerData;
    private Config lang;

    private PurchaseManager purchaseManager;
    private RankPurchaseService rankPurchaseService;
    private KitService kitService;
    private PurchaseLogger purchaseLogger;

    private final Map<UUID, Integer> pendingPurchaseForms = new HashMap<>();
    private final Map<UUID, String> pendingPurchases = new HashMap<>();
    private final Map<UUID, Integer> pendingKitForms = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("No se pudo crear la carpeta del plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        // Los rangos se encuentran en config.yml.
        ranks = getConfig();

        loadConfiguration("kits.yml");
        loadConfiguration("playerdata.yml");
        loadConfiguration("lang.yml");

        kits = new Config(new File(getDataFolder(), "kits.yml"), Config.YAML);
        playerData = new Config(
                new File(getDataFolder(), "playerdata.yml"),
                Config.YAML
        );
        lang = new Config(
                new File(getDataFolder(), "lang.yml"),
                Config.YAML
        );

        if (getServer().getPluginManager().getPlugin("LlamaEconomy") == null) {
            getLogger().error("No se encontró LlamaEconomy.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().error("No se encontró LuckPerms.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        purchaseLogger = new PurchaseLogger(this);

        purchaseManager = new PurchaseManager();
        rankPurchaseService = new RankPurchaseService(this, kits);
        kitService = new KitService(this, kits, playerData);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("RankshopUI habilitado.");

        List<String> rankIds = getRankIds();
        getLogger().info("Rangos cargados: " + rankIds.size()
                + (rankIds.isEmpty() ? " (ninguno)" : " " + rankIds));
    }

    private void loadConfiguration(String fileName) {
        File file = new File(getDataFolder(), fileName);

        if (!file.exists()) {
            saveResource(fileName, false);
        }
    }

    @Override
    public void onDisable() {
        pendingPurchaseForms.clear();
        pendingPurchases.clear();
        pendingKitForms.clear();

        if (purchaseManager != null) {
            purchaseManager.clear();
        }

        getLogger().info("RankshopUI deshabilitado.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("ranks")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando solo puede usarlo un jugador.");
                return true;
            }

            openRanksMenu((Player) sender);
            return true;
        }

        if (commandName.equals("kits")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando solo puede usarlo un jugador.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                openKitsMenu(player);
            } else {
                kitService.claimKit(player, args[0]);
            }

            return true;
        }

        return false;
    }

    private void openRanksMenu(Player player) {
        List<String> rankIds = getRankIds();

        FormWindowSimple form = new FormWindowSimple(
                text("forms.ranks-title",
                        getConfig().getString("form-name", "§lRankShop")),
                text("forms.ranks-content",
                        "Selecciona el rango que deseas comprar.")
        );

        for (String id : rankIds) {
            String name = ranks.getString(id + ".name", id);
            double price = ranks.getDouble(id + ".price", 0.0);

            form.addButton(new ElementButton(
                    name + "\n§7Precio: §6" + formatPrice(price)
            ));
        }

        if (rankIds.isEmpty()) {
            player.sendMessage("§cRankshop: no se encontraron rangos en config.yml.");
            getLogger().warning(
                    "No hay rangos. Comprueba la lista 'identifiers' de config.yml."
            );
            return;
        }

        int formId = player.showFormWindow(form);
        pendingPurchaseForms.put(player.getUniqueId(), formId);
        pendingPurchases.put(player.getUniqueId(), "");
    }

    private void openPurchaseConfirmation(Player player, String rankId) {
        String rankName = ranks.getString(rankId + ".name", rankId);
        String price = formatPrice(ranks.getDouble(rankId + ".price", 0.0));

        FormWindowModal form = new FormWindowModal(
                text("forms.purchase-title",
                        getConfig().getString(
                                "confirmation-form-name",
                                "§lConfirmar compra"
                        )),
                text(
                        "forms.purchase-content",
                        "¿Deseas comprar el rango {rank} por {price}?",
                        placeholders("rank", rankName, "price", price)
                ),
                text("forms.purchase-confirm", "§aComprar"),
                text("forms.purchase-cancel", "§cCancelar")
        );

        int formId = player.showFormWindow(form);
        pendingPurchaseForms.put(player.getUniqueId(), formId);
        pendingPurchases.put(player.getUniqueId(), rankId);
    }

    private void openKitsMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple(
                text("forms.kits-title", "§l§bKits"),
                text("forms.kits-content",
                        "Selecciona el kit que deseas reclamar.")
        );

        Set<String> unlocked = unlockedKits(player);

        for (String id : getKitIds()) {
            String name = kits.getString(id + ".name", id);
            String status;

            if (!unlocked.contains(id)) {
                status = text("forms.kit-locked", "§cBloqueado");
            } else {
                long remaining = kitService.cooldownRemaining(player, id);
                status = remaining > 0
                        ? text("forms.kit-cooldown", "§eEn cooldown")
                        : text("forms.kit-available", "§aDisponible");
            }

            form.addButton(new ElementButton(name + "\n" + status));
        }

        int formId = player.showFormWindow(form);
        pendingKitForms.put(player.getUniqueId(), formId);
    }

    @EventHandler
    public void onFormResponse(PlayerFormRespondedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        int formId = event.getFormID();
        FormWindow window = event.getWindow();

        if (window == null || window.wasClosed()) {
            clearPendingForm(uuid, formId);
            return;
        }

        Integer purchaseFormId = pendingPurchaseForms.get(uuid);

        if (purchaseFormId != null && purchaseFormId == formId) {
            handlePurchaseForm(player, uuid, window);
            return;
        }

        Integer kitFormId = pendingKitForms.get(uuid);

        if (kitFormId != null && kitFormId == formId) {
            handleKitForm(player, uuid, window);
        }
    }

    private void handlePurchaseForm(
            Player player,
            UUID uuid,
            FormWindow window
    ) {
        String selectedRank = pendingPurchases.get(uuid);

        pendingPurchaseForms.remove(uuid);
        pendingPurchases.remove(uuid);

        if (selectedRank == null) {
            return;
        }

        if (selectedRank.isEmpty()) {
            if (!(window instanceof FormWindowSimple)) {
                return;
            }

            FormResponseSimple response =
                    ((FormWindowSimple) window).getResponse();

            if (response == null) {
                return;
            }

            String rankId = getRankIdAt(response.getClickedButtonId());

            if (rankId != null) {
                openPurchaseConfirmation(player, rankId);
            }

            return;
        }

        if (!(window instanceof FormWindowModal)) {
            return;
        }

        FormResponseModal response =
                ((FormWindowModal) window).getResponse();

        if (response == null || response.getClickedButtonId() != 0) {
            return;
        }

        if (!purchaseManager.tryBeginPurchase(uuid)) {
            player.sendMessage(message("messages.purchase-in-progress"));
            return;
        }

        try {
            rankPurchaseService.purchaseRank(player, selectedRank);
        } catch (Exception exception) {
            getLogger().error(
                    "Error al procesar la compra de rango de "
                            + player.getName(),
                    exception
            );
            player.sendMessage(message("messages.rank-error"));
        } finally {
            purchaseManager.finishPurchase(uuid);
        }
    }

    private void handleKitForm(
            Player player,
            UUID uuid,
            FormWindow window
    ) {
        pendingKitForms.remove(uuid);

        if (!(window instanceof FormWindowSimple)) {
            return;
        }

        FormResponseSimple response =
                ((FormWindowSimple) window).getResponse();

        if (response == null) {
            return;
        }

        String kitId = getKitIdAt(response.getClickedButtonId());

        if (kitId != null) {
            kitService.claimKit(player, kitId);
        }
    }

    private void clearPendingForm(UUID uuid, int formId) {
        Integer purchaseId = pendingPurchaseForms.get(uuid);

        if (purchaseId != null && purchaseId == formId) {
            pendingPurchaseForms.remove(uuid);
            pendingPurchases.remove(uuid);
        }

        Integer kitId = pendingKitForms.get(uuid);

        if (kitId != null && kitId == formId) {
            pendingKitForms.remove(uuid);
        }
    }

    /**
     * Los rangos están en config.yml:
     * identifiers:
     *   - "0"
     *   - "1"
     *
     * "0":
     *   name: "PRUEBA1"
     *   price: 20000
     */
    private List<String> getRankIds() {
        List<String> result = new ArrayList<>();

        for (String id : getConfig().getStringList("identifiers")) {
            if (id == null || id.trim().isEmpty()) {
                continue;
            }

            if (getConfig().exists(id + ".name")
                    && getConfig().exists(id + ".price")) {
                result.add(id);
            } else {
                getLogger().warning(
                        "El identificador de rango '" + id
                                + "' no tiene name o price en config.yml."
                );
            }
        }

        return result;
    }

    private String getRankIdAt(int index) {
        List<String> ids = getRankIds();

        if (index < 0 || index >= ids.size()) {
            return null;
        }

        return ids.get(index);
    }

    private Set<String> getKitIds() {
        Set<String> result = new LinkedHashSet<>();

        if (kits.exists("kits")) {
            result.addAll(kits.getSection("kits").getKeys(false));
        } else {
            result.addAll(kits.getKeys(false));
        }

        result.remove("settings");
        return result;
    }

    private String getKitIdAt(int index) {
        if (index < 0) {
            return null;
        }

        int current = 0;

        for (String id : getKitIds()) {
            if (current++ == index) {
                return id;
            }
        }

        return null;
    }

    public Set<String> unlockedKits(Player player) {
        Set<String> unlocked = new LinkedHashSet<>();
        String path = "players." + playerKey(player) + ".unlocked-kits";
        Object value = playerData.get(path);

        if (value instanceof Collection<?>) {
            for (Object entry : (Collection<?>) value) {
                if (entry != null) {
                    unlocked.add(entry.toString());
                }
            }
        }

        return unlocked;
    }

    public void setUnlockedKits(
            Player player,
            Collection<String> unlocked
    ) {
        playerData.set(
                "players." + playerKey(player) + ".unlocked-kits",
                new ArrayList<>(unlocked)
        );
        playerData.save();
    }

    String playerKey(Player player) {
        return player.getUniqueId().toString();
    }

    public String message(String path) {
        return text(path, "§cMensaje no configurado: " + path);
    }

    public String text(String path, String fallback) {
        return lang.getString(path, fallback).replace('&', '§');
    }

    public String text(
            String path,
            String fallback,
            Map<String, String> placeholders
    ) {
        String result = text(path, fallback);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(
                    "{" + entry.getKey() + "}",
                    entry.getValue()
            );
        }

        return result;
    }

    private Map<String, String> placeholders(
            String key1,
            String value1,
            String key2,
            String value2
    ) {
        Map<String, String> values = new HashMap<>();
        values.put(key1, value1);
        values.put(key2, value2);
        return values;
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.format(
                    java.util.Locale.ROOT,
                    "%.0f",
                    price
            );
        }

        return String.format(
                java.util.Locale.ROOT,
                "%.2f",
                price
        );
    }

    public PurchaseLogger getPurchaseLogger() {
        return purchaseLogger;
    }
}



