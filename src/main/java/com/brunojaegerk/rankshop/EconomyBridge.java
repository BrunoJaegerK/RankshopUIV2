package com.brunojaegerk.rankshop;

import cn.nukkit.Player;
import cn.nukkit.plugin.Plugin;
import net.lldv.llamaeconomy.LlamaEconomy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adaptador para la economía heredada de LlamaEconomy y las monedas
 * secundarias opcionales de EconomyExpandNK.
 *
 * EconomyExpandNK se consulta mediante su API pública por reflexión para
 * mantenerlo como dependencia opcional y evitar problemas de classloader.
 */
public final class EconomyBridge {

    public static final String LEGACY_CURRENCY = "llamaeconomy";

    private final Main plugin;

    public EconomyBridge(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable(String currencyId) {
        String id = normalize(currencyId);
        if (LEGACY_CURRENCY.equals(id)) {
            return plugin.getServer().getPluginManager()
                    .getPlugin("LlamaEconomy") != null;
        }
        return getSecondaryCurrency(id) != null;
    }

    public String getSymbol(String currencyId) {
        String id = normalize(currencyId);
        if (LEGACY_CURRENCY.equals(id)) {
            return plugin.getConfig().getString("currency-symbol", "$");
        }

        Object currency = getSecondaryCurrency(id);
        Object symbol = invoke(currency, "getSymbol");
        return symbol == null ? "" : String.valueOf(symbol);
    }

    public String formatPrice(String currencyId, double amount) {
        int decimals = 2;
        String id = normalize(currencyId);
        if (!LEGACY_CURRENCY.equals(id)) {
            Object currency = getSecondaryCurrency(id);
            Object configuredDecimals = invoke(currency, "getDecimals");
            if (configuredDecimals instanceof Number) {
                decimals = Math.max(0, Math.min(8,
                        ((Number) configuredDecimals).intValue()));
            }
        }

        BigDecimal value = BigDecimal.valueOf(amount)
                .setScale(decimals, java.math.RoundingMode.DOWN);
        return value.stripTrailingZeros().toPlainString();
    }

    public boolean hasBalance(Player player, String currencyId, double amount) {
        String id = normalize(currencyId);
        if (LEGACY_CURRENCY.equals(id)) {
            return LlamaEconomy.getAPI().getMoney(player) >= amount;
        }

        Object economy = getSecondaryEconomy();
        Object balance = invokeSecondary(economy, "getBalance",
                new Class<?>[]{UUID.class, String.class},
                player.getUniqueId(), id);
        return balance instanceof BigDecimal
                && ((BigDecimal) balance).compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    public boolean withdraw(Player player, String currencyId, double amount) {
        String id = normalize(currencyId);
        if (LEGACY_CURRENCY.equals(id)) {
            if (LlamaEconomy.getAPI().getMoney(player) < amount) {
                return false;
            }
            LlamaEconomy.getAPI().reduceMoney(player.getName(), amount);
            return true;
        }

        Object result = invokeSecondary(getSecondaryEconomy(), "withdraw",
                new Class<?>[]{UUID.class, String.class, BigDecimal.class},
                player.getUniqueId(), id, BigDecimal.valueOf(amount));
        return Boolean.TRUE.equals(result);
    }

    public boolean deposit(Player player, String currencyId, double amount) {
        String id = normalize(currencyId);
        if (LEGACY_CURRENCY.equals(id)) {
            try {
                Object api = LlamaEconomy.getAPI();
                java.lang.reflect.Method method = api.getClass().getMethod(
                        "addMoney", String.class, double.class);
                method.invoke(api, player.getName(), amount);
                return true;
            } catch (Throwable error) {
                plugin.getLogger().error("No se pudo reembolsar LlamaEconomy.", error);
                return false;
            }
        }

        Object result = invokeSecondary(getSecondaryEconomy(), "deposit",
                new Class<?>[]{UUID.class, String.class, BigDecimal.class},
                player.getUniqueId(), id, BigDecimal.valueOf(amount));
        return Boolean.TRUE.equals(result);
    }

    private Object getSecondaryCurrency(String id) {
        Object economy = getSecondaryEconomy();
        Object currency = invokeSecondary(economy, "getCurrency",
                new Class<?>[]{String.class}, id);
        return Boolean.TRUE.equals(invoke(currency, "isEnabled"))
                ? currency : null;
    }

    private Object getSecondaryEconomy() {
        try {
            Plugin dependency = plugin.getServer().getPluginManager()
                    .getPlugin("EconomyExpandNK");
            if (dependency == null || !dependency.isEnabled()) {
                return null;
            }

            ClassLoader loader = dependency.getClass().getClassLoader();
            Class<?> mainClass = Class.forName(
                    "com.bruno.economyexpand.EconomyExpandNK", true, loader);
            Object instance = mainClass.getMethod("getInstance").invoke(null);
            return mainClass.getMethod("getEconomy").invoke(instance);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private Object invokeSecondary(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        if (target == null) {
            return null;
        }

        try {
            ClassLoader loader = target.getClass().getClassLoader();
            Class<?> api = Class.forName(
                    "com.bruno.economyexpand.api.SecondaryEconomy",
                    true, loader);
            Method method = api.getMethod(methodName, parameterTypes);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning(
                    "No se pudo ejecutar " + methodName
                            + " en EconomyExpandNK: " + exception.getMessage());
            return null;
        }
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private String normalize(String currencyId) {
        return currencyId == null || currencyId.trim().isEmpty()
                ? LEGACY_CURRENCY
                : currencyId.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
