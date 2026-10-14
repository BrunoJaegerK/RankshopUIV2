
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
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

import net.lldv.llamaeconomy.LlamaEconomy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Main extends PluginBase implements Listener {

    private final Map<String, String> pendingPurchases = new HashMap<>();
    private final Map<String, List<String>> pendingKitSelections = new HashMap<>();

    private Config lang;
    private Config kits;
    private Config playerData;

    private static final Pattern SAFE_GROUP =
            Pattern.compile("[A-Za-z0-9_.-]+");

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("No se pudo crear la carpeta de Rankshop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveResource("lang.yml", false);
        saveResource("kits.yml", false);

        lang = new Config(
                new File(getDataFolder(), "lang.yml"),
                Config.YAML
        );

        kits = new Config(
                new File(getDataFolder(), "kits.yml"),
                Config.YAML
        );

        playerData = new Config(
                new File(getDataFolder(), "playerdata.yml"),
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

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(
                "Rankshop habilitado: rangos, kits, nombres y encantamientos."
        );
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("ranks")
                || commandName.equals("rangos")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(message("messages.player-only"));
                return true;
            }

            openRankShop((Player) sender);
            return true;
        }

        if (commandName.equals("kits")
                || commandName.equals("kit")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(message("messages.player-only"));
                return true;
            }

            openKitsMenu((Player) sender);
            return true;
        }

        return false;
    }

    // --------------------------------------------------
    // TIENDA DE RANGOS
    // --------------------------------------------------

    private void openRankShop(Player player) {
        List<String> identifiers =
                getConfig().getStringList("identifiers");

        if (identifiers.isEmpty()) {
            player.sendMessage(message("messages.no-ranks"));
            return;
        }

        FormWindowSimple form = new FormWindowSimple(
                text("forms.shop.title", "§lRankShop"),
                text(
                        "forms.shop.content",
                        "§7Selecciona el rango que deseas comprar."
                )
        );

        for (String id : identifiers) {
            Map<String, String> values = new HashMap<>();

            values.put(
                    "rank",
                    getConfig().getString(id + ".name", id)
            );

            values.put(
                    "price",
                    number(getConfig().getDouble(id + ".price"))
            );

            form.addButton(new ElementButton(
                    text(
                            "forms.shop.rank-button",
                            "§8{rank}\n§d${price}\n§7Toca para seleccionar",
                            values
                    )
            ));
        }

        player.showFormWindow(form);
    }

    private void openConfirmation(Player player, String id) {
        Map<String, String> values = new HashMap<>();

        values.put(
                "rank",
                getConfig().getString(id + ".name", id)
        );

        values.put(
                "price",
                number(getConfig().getDouble(id + ".price"))
        );

        FormWindowSimple form = new FormWindowSimple(
                text(
                        "forms.confirmation.title",
                        "§lConfirmar compra"
                ),
                text(
                        "forms.confirmation.content",
                        "¿Comprar §d{rank} §7por §d${price}§7?",
                        values
                )
        );

        form.addButton(new ElementButton(
                text("forms.confirmation.buy-button", "§aComprar")
        ));

        form.addButton(new ElementButton(
                text("forms.confirmation.cancel-button", "§cCancelar")
        ));

        pendingPurchases.put(playerKey(player), id);
        player.showFormWindow(form);
    }

    private void purchaseRank(Player player, String id) {
        double price = getConfig().getDouble(id + ".price");

        String group = getConfig().getString(id + ".lp-user");

        String rankName =
                getConfig().getString(id + ".name", id);

        if (price < 0
                || group == null
                || !SAFE_GROUP.matcher(group).matches()) {

            player.sendMessage(message("messages.rank-config-error"));

            getLogger().warning(
                    "Configuración inválida para el rango " + id
            );

            return;
        }

        // Comprobar si el jugador ya tiene este rango.
        if (hasRankGroup(player, group)) {
            player.sendMessage(text(
                    "messages.rank-already-owned",
                    "§eYa tienes el rango §d{rank}§e.",
                    Collections.singletonMap("rank", rankName)
            ));
            return;
        }

        if (LlamaEconomy.getAPI().getMoney(player) < price) {
            player.sendMessage(message("messages.no-money"));
            return;
        }

        boolean dispatched = getServer().dispatchCommand(
                getServer().getConsoleSender(),
                "lp user " + player.getName()
                        + " parent add " + group
        );

        if (!dispatched) {
            player.sendMessage(message("messages.rank-error"));

            getLogger().warning(
                    "No se pudo ejecutar LuckPerms para "
                            + player.getName()
            );

            return;
        }

        LlamaEconomy.getAPI().reduceMoney(
                player.getName(),
                price
        );

        // Desbloquea los kits asociados al rango comprado.
        Set<String> unlocked =
                new LinkedHashSet<>(unlockedKits(player));

        for (String kitId :
                getConfig().getStringList(id + ".kits")) {

            if (kits.exists(kitId)) {
                unlocked.add(kitId);
            } else {
                getLogger().warning(
                        "El rango " + id
                                + " referencia el kit inexistente "
                                + kitId
                );
            }
        }

        setUnlockedKits(
                player,
                new ArrayList<>(unlocked)
        );

        player.sendMessage(
                text(
                        "messages.purchase-success",
                        "§aHas comprado el rango {rank}.",
                        Collections.singletonMap("rank", rankName)
                )
        );
    }

    private boolean hasRankGroup(Player player, String groupName) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager()
                    .getUser(player.getUniqueId());

            // Si no podemos comprobar el rango, no permitimos el cobro.
            if (user == null) {
                getLogger().warning(
                        "No se pudieron consultar los rangos de "
                                + player.getName()
                );
                return true;
            }

            // Comprobar el grupo principal.
            if (user.getPrimaryGroup().equalsIgnoreCase(groupName)) {
                return true;
            }

            // Comprobar grupos directos y heredados.
            for (Group inheritedGroup :
                    user.getInheritedGroups(
                            QueryOptions.defaultContextualOptions())) {

                if (inheritedGroup.getName()
                        .equalsIgnoreCase(groupName)) {
                    return true;
                }
            }

            return false;

        } catch (Exception exception) {
            getLogger().warning(
                    "Error al verificar el rango de "
                            + player.getName() + ": "
                            + exception.getMessage()
            );

            // No cobrar cuando no se puede verificar el rango.
            return true;
        }
    }

    // --------------------------------------------------
    // MENÚ DE KITS
    // --------------------------------------------------

    private void openKitsMenu(Player player) {
        List<String> unlocked = unlockedKits(player);

        unlocked.removeIf(id -> !kits.exists(id));

        if (unlocked.isEmpty()) {
            player.sendMessage(message("messages.no-kits"));
            return;
        }

        FormWindowSimple form = new FormWindowSimple(
                text("forms.kits.title", "§lTus kits"),
                text(
                        "forms.kits.content",
                        "§7Selecciona un kit para reclamarlo."
                )
        );

        for (String id : unlocked) {
            long remaining = cooldownRemaining(player, id);

            Map<String, String> values = new HashMap<>();

            values.put(
                    "kit",
                    kits.getString(id + ".name", id)
            );

            String status;

            if (remaining > 0) {
                status = text(
                        "forms.kits.cooldown-status",
                        "Disponible en {time}",
                        Collections.singletonMap(
                                "time",
                                duration(remaining)
                        )
                );
            } else {
                status = text(
                        "forms.kits.available-status",
                        "Disponible"
                );
            }

            values.put("status", status);

            form.addButton(new ElementButton(
                    text(
                            "forms.kits.kit-button",
                            "§b{kit}\n§7{status}",
                            values
                    )
            ));
        }

        pendingKitSelections.put(
                playerKey(player),
                new ArrayList<>(unlocked)
        );

        player.showFormWindow(form);
    }

    @EventHandler
    public void onFormResponded(PlayerFormRespondedEvent event) {
        if (event.wasClosed() || event.getResponse() == null) {
            return;
        }

        if (!(event.getWindow() instanceof FormWindowSimple)
                || !(event.getResponse() instanceof FormResponseSimple)) {
            return;
        }

        Player player = event.getPlayer();

        String title =
                ((FormWindowSimple) event.getWindow()).getTitle();

        int button =
                ((FormResponseSimple) event.getResponse())
                        .getClickedButtonId();

        // Respuesta de la tienda de rangos.
        if (title.equals(text("forms.shop.title", "§lRankShop"))) {
            List<String> identifiers =
                    getConfig().getStringList("identifiers");

            if (button >= 0 && button < identifiers.size()) {
                openConfirmation(player, identifiers.get(button));
            }

            return;
        }

        // Respuesta del formulario de confirmación.
        if (title.equals(
                text("forms.confirmation.title", "§lConfirmar compra")
        )) {
            String id = pendingPurchases.remove(playerKey(player));

            if (id == null) {
                return;
            }

            if (button != 0) {
                player.sendMessage(
                        message("messages.purchase-cancelled")
                );
                return;
            }

            purchaseRank(player, id);
            return;
        }

        // Respuesta del menú de kits.
        if (title.equals(text("forms.kits.title", "§lTus kits"))) {
            List<String> selected =
                    pendingKitSelections.remove(playerKey(player));

            if (selected != null
                    && button >= 0
                    && button < selected.size()) {

                claimKit(player, selected.get(button));
            }
        }
    }

    // --------------------------------------------------
    // RECLAMAR KITS
    // --------------------------------------------------

    private void claimKit(Player player, String id) {
        if (!unlockedKits(player).contains(id)) {
            player.sendMessage(message("messages.kit-no-permission"));
            return;
        }

        if (!kits.exists(id)) {
            player.sendMessage(message("messages.kit-config-error"));
            return;
        }

        long remaining = cooldownRemaining(player, id);

        if (remaining > 0) {
            player.sendMessage(
                    text(
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

        List<String> entries =
                kits.getStringList(id + ".items");

        if (entries.isEmpty()) {
            player.sendMessage(message("messages.kit-empty"));
            return;
        }

        List<Item> items = new ArrayList<>();

        try {
            for (String entry : entries) {
                items.add(parseKitItem(entry));
            }
        } catch (Exception ex) {
            getLogger().warning(
                    "Objeto inválido en el kit " + id
                            + ": " + ex.getMessage()
            );

            player.sendMessage(message("messages.kit-config-error"));
            return;
        }

        // Verifica que quepan todos los objetos antes de entregarlos.
        for (Item item : items) {
            if (!player.getInventory().canAddItem(item)) {
                player.sendMessage(message("messages.inventory-full"));
                return;
            }
        }

        for (Item item : items) {
            player.getInventory().addItem(item);
        }

        Object raw = kits.get(id + ".cooldown-seconds");

        long cooldown =
                raw instanceof Number
                        ? ((Number) raw).longValue()
                        : 86400L;

        cooldown = Math.max(0L, cooldown);

        playerData.set(
                "players." + playerKey(player)
                        + ".cooldowns." + id,
                System.currentTimeMillis() / 1000L + cooldown
        );

        playerData.save();

        player.sendMessage(
                text(
                        "messages.kit-claimed",
                        "§aHas reclamado el kit {kit}.",
                        Collections.singletonMap(
                                "kit",
                                kits.getString(id + ".name", id)
                        )
                )
        );
    }

    // --------------------------------------------------
    // OBJETOS, NOMBRES PERSONALIZADOS Y ENCANTAMIENTOS
    // --------------------------------------------------

    /*
     * Formatos admitidos:
     *
     * ID:META:CANTIDAD
     *
     * ID:META:CANTIDAD:NOMBRE
     *
     * ID:META:CANTIDAD:default:ENCANTAMIENTO:NIVEL
     *
     * ID:META:CANTIDAD:NOMBRE:ENCANTAMIENTO:NIVEL
     *
     * Se pueden agregar varios pares ENCANTAMIENTO:NIVEL.
     *
     * Ejemplo:
     * 276:0:1:&dEspada VIP:9:3:13:2
     *
     * "default" significa que se conserva el nombre normal.
     * Los códigos de color con & se convierten a §.
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

                item.setCustomName(
                        customName.replace('&', '§')
                );
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
            int enchantmentId =
                    Integer.parseInt(parts[i].trim());

            int level =
                    Integer.parseInt(parts[i + 1].trim());

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

    // --------------------------------------------------
    // DATOS DE JUGADORES Y COOLDOWNS
    // --------------------------------------------------

    private List<String> unlockedKits(Player player) {
        List<String> value = playerData.getStringList(
                "players." + playerKey(player) + ".unlocked-kits"
        );

        return value == null
                ? new ArrayList<String>()
                : new ArrayList<>(value);
    }

    private void setUnlockedKits(
            Player player,
            List<String> unlocked
    ) {
        playerData.set(
                "players." + playerKey(player) + ".unlocked-kits",
                unlocked
        );

        playerData.save();
    }

    private long cooldownRemaining(Player player, String id) {
        Object value = playerData.get(
                "players." + playerKey(player)
                        + ".cooldowns." + id
        );

        long until =
                value instanceof Number
                        ? ((Number) value).longValue()
                        : 0L;

        return Math.max(
                0L,
                until - System.currentTimeMillis() / 1000L
        );
    }

    private String playerKey(Player player) {
        return player.getName().toLowerCase(Locale.ROOT);
    }

    // --------------------------------------------------
    // IDIOMA Y UTILIDADES
    // --------------------------------------------------

    private String message(String key) {
        return text(
                key,
                "§cFalta el mensaje de idioma: " + key
        );
    }

    private String text(String key, String fallback) {
        return replace(
                lang.getString(key, fallback),
                Collections.<String, String>emptyMap()
        );
    }

    private String text(
            String key,
            String fallback,
            Map<String, String> replacements
    ) {
        return replace(
                lang.getString(key, fallback),
                replacements
        );
    }

    private String replace(
            String input,
            Map<String, String> values
    ) {
        String output = input == null ? "" : input;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace(
                    "{" + entry.getKey() + "}",
                    entry.getValue()
            );
        }

        return output;
    }

    private String number(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }

        return Double.toString(value);
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

