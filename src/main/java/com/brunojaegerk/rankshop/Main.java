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
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import java.util.Iterator;
import java.util.List;
import net.lldv.llamaeconomy.LlamaEconomy;

public class Main extends PluginBase implements Listener {
    public Integer tempval;

    public void onEnable() {
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("LlamaEconomy") == null) {
          getLogger().info("Buscando dependencia (LlamaEconomy). Deshabilitando plugin");
          getServer().getPluginManager().disablePlugin((Plugin)this);
        } else if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
          getLogger().info("Buscando dependencia (LuckPerms). Deshabilitando plugin");
          getServer().getPluginManager().disablePlugin((Plugin)this);
        }
        getServer().getPluginManager().registerEvents(this, (Plugin)this);

    }

    public boolean onCommand (CommandSender sender, Command command, String label, String[] args) {
      FormWindowSimple form;
      List<String> purchasableRanksList;
      Iterator<String> var2;
      Player player;
      switch (command.getName()) {
          case "Rango":
            if (!(sender instanceof Player)) {
              getLogger().info("No puedes usar esto desde la consola");
              return false;
            }
            form = new FormWindowSimple(getConfig().getString("form-name"), "");
            purchasableRanksList = getConfig().getStringList("identifiers");
            var2 = purchasableRanksList.iterator();
            while (var2.hasNext()) {
              String rank = var2.next();
              form.addButton(new ElementButton("§8" + getConfig().getString(rank + ".name") + "\n§d§l»§r §7Toca para seleccionar!"));
            }
            player = (Player) sender;
            player.showFormWindow((FormWindow)form);
            break;
      }
      return true;
    }

    @EventHandler
    public void formRespond(PlayerFormRespondedEvent event) {
      Player player = event.getPlayer();
      FormWindow window = event.getWindow();
      if (event.getResponse() == null)
        return;
      if (window instanceof FormWindowSimple) {
        String title = ((FormWindowSimple)event.getWindow()).getTitle();
        int button = ((FormResponseSimple)event.getResponse()).getClickedButtonId();
        if (!event.wasClosed())
          if (title.equals(getConfig().getString("form-name"))) {
            this.tempval = Integer.valueOf(button);
            FormWindowSimple form = new FormWindowSimple(getConfig().getString("confirmation-form-name"), "¿Estás seguro de que quieres comprar el §l§d?" + getConfig().getString(button+ ".name") + " §7rango por §l§d$§e" + getConfig().getDouble(button + ".precio"));
            form.addButton(new ElementButton("§7Comprar\n§d§l»§r §7Toca para seleccionar!"));
            form.addButton(new ElementButton("§l§7Salir\n§d§l»§r §7Toca para seleccionar!"));
            player.showFormWindow((FormWindow)form);
          } else if (title.equals(getConfig().getString("confirmation-form-name")) &&
            button == 0) {
            if (LlamaEconomy.getAPI().getMoney(player) < getConfig().getDouble(this.tempval + ".price")) {
              player.sendMessage(getConfig().getString("no-money"));
            } else {
              LlamaEconomy.getAPI().reduceMoney(player.getName(), getConfig().getDouble(this.tempval + ".price"));
              getServer().dispatchCommand((CommandSender)getServer().getConsoleSender(), "lp user " + player.getName() + " parent add " + getConfig().getString(this.tempval + ".lp-user"));
              player.sendMessage(getConfig().getString("rank-purchased"));
            }
          }
      }
    }
}