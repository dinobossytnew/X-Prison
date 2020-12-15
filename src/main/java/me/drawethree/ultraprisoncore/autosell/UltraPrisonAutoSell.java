package me.drawethree.ultraprisoncore.autosell;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.Getter;
import me.drawethree.ultraprisoncore.UltraPrisonCore;
import me.drawethree.ultraprisoncore.UltraPrisonModule;
import me.drawethree.ultraprisoncore.api.events.player.UltraPrisonAutoSellEvent;
import me.drawethree.ultraprisoncore.autosell.api.UltraPrisonAutoSellAPI;
import me.drawethree.ultraprisoncore.autosell.api.UltraPrisonAutoSellAPIImpl;
import me.drawethree.ultraprisoncore.config.FileManager;
import me.drawethree.ultraprisoncore.enchants.enchants.implementations.LuckyBoosterEnchant;
import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import me.lucko.helper.event.filter.EventFilters;
import me.lucko.helper.text.Text;
import me.lucko.helper.utils.Players;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class UltraPrisonAutoSell implements UltraPrisonModule {

    @Getter
    private FileManager.Config config;

    private HashMap<ProtectedRegion, HashMap<Material, Integer>> regionsAutoSell;
    private HashMap<String, String> messages;
    private HashMap<UUID, Double> lastMinuteEarnings;
    private HashMap<UUID, Integer> lastMinuteItems;
    @Getter
    private UltraPrisonAutoSellAPI api;
    private List<UUID> disabledAutoSell;
    @Getter
    private UltraPrisonCore core;

    public UltraPrisonAutoSell(UltraPrisonCore UltraPrisonCore) {
        this.core = UltraPrisonCore;
        this.config = UltraPrisonCore.getFileManager().getConfig("autosell.yml").copyDefaults(true).save();
        this.disabledAutoSell = new ArrayList<>();
        this.lastMinuteEarnings = new HashMap<>();
        this.lastMinuteItems = new HashMap<>();
    }

    private void loadMessages() {
        messages = new HashMap<>();
        for (String key : this.getConfig().get().getConfigurationSection("messages").getKeys(false)) {
            messages.put(key.toLowerCase(), Text.colorize(this.getConfig().get().getString("messages." + key)));
        }
    }

    private void loadAutoSellRegions() {
        regionsAutoSell = new HashMap<>();
        for (String regName : this.getConfig().get().getConfigurationSection("regions").getKeys(false)) {

            String worldName = this.getConfig().get().getString("regions." + regName + ".world");

            World w = Bukkit.getWorld(worldName);
            ProtectedRegion region = WorldGuardPlugin.inst().getRegionManager(w).getRegion(regName);

            if (region == null || w == null) {
                continue;
            }

            HashMap<Material, Integer> sellPrices = new HashMap<>();
            for (String item : this.getConfig().get().getConfigurationSection("regions." + regName + ".items").getKeys(false)) {
                Material type = Material.valueOf(item);
                int sellPrice = this.getConfig().get().getInt("regions." + regName + ".items." + item);
                sellPrices.put(type, sellPrice);
            }
            regionsAutoSell.put(region, sellPrices);
        }
    }

    @Override
    public void reload() {

    }

    @Override
    public void enable() {
        this.loadAutoSellRegions();
        this.loadMessages();
        this.registerCommands();
        this.registerListeners();
        this.runBroadcastTask();
        this.api = new UltraPrisonAutoSellAPIImpl(this);
    }

    private void runBroadcastTask() {
        Schedulers.async().runRepeating(() -> {
            Players.all().stream().filter(p -> lastMinuteEarnings.containsKey(p.getUniqueId())).forEach(p -> {
                double lastAmount = lastMinuteEarnings.getOrDefault(p.getUniqueId(), 0.0);
                int lastItems = lastMinuteItems.getOrDefault(p.getUniqueId(), 0);
                p.sendMessage(Text.colorize("&e&m-------&f&m-------&e&m--------&f&m--------&e&m--------&f&m-------&e&m-------"));
                p.sendMessage(Text.colorize(" &8&l» &6&lAUTOSELL:"));
                p.sendMessage(Text.colorize(" &8&l➥ &e&lMONEY MADE: &f$" + String.format("%,.0f", lastAmount)));
                p.sendMessage(Text.colorize(" &8&l➥ &e&lITEMS SOLD: &f" + String.format("%,d", lastItems)));
                p.sendMessage(Text.colorize(" &8&l➥ &e&lMULTIPLIER: &fX" + String.format("%,.0f", this.core.getMultipliers().getApi().getPlayerMultiplier(p))));
                p.sendMessage(Text.colorize("&e&m-------&f&m-------&e&m--------&f&m--------&e&m--------&f&m-------&e&m-------"));
                //p.sendMessage(getMessage("last_minute_earn").replace("%amount%", String.format("%,.0f", lastAmount)));
            });
            lastMinuteEarnings.clear();
        }, 0, TimeUnit.SECONDS, 1, TimeUnit.MINUTES);
    }

    private void registerListeners() {
        Events.subscribe(PlayerJoinEvent.class)
                .handler(e -> Schedulers.async().runLater(() -> {
                    if (!disabledAutoSell.contains(e.getPlayer().getUniqueId())) {
                        e.getPlayer().sendMessage(getMessage("autosell_enable"));
                    }
                }, 20));
        Events.subscribe(BlockBreakEvent.class, EventPriority.HIGHEST)
                .filter(EventFilters.ignoreCancelled())
                .filter(e -> !e.isCancelled() && e.getPlayer().getGameMode() == GameMode.SURVIVAL && e.getPlayer().getItemInHand() != null && e.getPlayer().getItemInHand().getType() == Material.DIAMOND_PICKAXE && !e.getPlayer().getWorld().getName().equalsIgnoreCase("pvp") && !e.getPlayer().getWorld().getName().equalsIgnoreCase("plots"))
                .handler(e -> {
                    int fortuneLevel = core.getEnchants().getApi().getEnchantLevel(e.getPlayer().getItemInHand(), 3);
                    if (disabledAutoSell.contains(e.getPlayer().getUniqueId())) {
                        if (e.getBlock().getType() != Material.ENDER_STONE && e.getBlock().getType() != Material.OBSIDIAN) {
                            e.getPlayer().getInventory().addItem(new ItemStack(e.getBlock().getType(), 1 + fortuneLevel));
                        }

                        e.getBlock().getDrops().clear();
                        e.getBlock().setType(Material.AIR);
                    } else {
                        ProtectedRegion reg = getFirstRegionAtLocation(e.getBlock().getLocation());

                        if (reg == null) {
                            return;
                        }

                        if (regionsAutoSell.containsKey(reg) && regionsAutoSell.get(reg).containsKey(e.getBlock().getType())) {
                            int amplifier = fortuneLevel == 0 ? 1 : fortuneLevel + 1;
                            double amount = core.getMultipliers().getApi().getTotalToDeposit(e.getPlayer(), (regionsAutoSell.get(reg).get(e.getBlock().getType()) + 0.0) * amplifier);

                            UltraPrisonAutoSellEvent event = new UltraPrisonAutoSellEvent(e.getPlayer(), reg, e.getBlock(), amount);

                            Events.call(event);

                            if (event.isCancelled()) {
                                return;
                            }

                            amount = event.getMoneyToDeposit();

                            int amountOfItems = 0;
                            for (ItemStack item : e.getBlock().getDrops(e.getPlayer().getItemInHand())) {
                                amountOfItems += item.getAmount() * amplifier;
                            }

                            boolean luckyBooster = LuckyBoosterEnchant.hasLuckyBoosterRunning(e.getPlayer());
                            core.getEconomy().depositPlayer(e.getPlayer(), luckyBooster ? amount * 2 : amount);
                            core.getAutoSell().addToCurrentEarnings(e.getPlayer(), luckyBooster ? amount * 2 : amount);
                            this.lastMinuteItems.put(e.getPlayer().getUniqueId(), this.lastMinuteItems.getOrDefault(e.getPlayer().getUniqueId(), 0) + amountOfItems);

                            e.getBlock().getDrops().clear();
                            e.getBlock().setType(Material.AIR);
                        }
                    }

                }).bindWith(core);
    }

    @Override
    public void disable() {

    }

    @Override
    public String getName() {
        return "Auto Sell";
    }

    private void registerCommands() {
        Commands.create()
                .assertPlayer()
                .handler(c -> {
                    if (c.args().size() == 0) {
                        toggleAutoSell(c.sender());
                    }
                }).registerAndBind(core, "autosell");
        Commands.create()
                .assertPlayer()
                .assertPermission("ultraprison.sellprice")
                .handler(c -> {
                    if (c.args().size() == 1) {

                        if (c.sender().getItemInHand() == null) {
                            c.sender().sendMessage(Text.colorize("&cPlease hold some item!"));
                            return;
                        }

                        int price = c.arg(0).parseOrFail(Integer.class).intValue();
                        Material type = c.sender().getItemInHand().getType();
                        ProtectedRegion region = getFirstRegionAtLocation(c.sender().getLocation());

                        if (region == null) {
                            c.sender().sendMessage(Text.colorize("&cYou must be standing in a region!"));
                            return;
                        }

                        getConfig().set("regions." + region.getId() + ".world", c.sender().getWorld().getName());
                        getConfig().set("regions." + region.getId() + ".items." + type.name(), price);
                        getConfig().save();

                        HashMap<Material, Integer> prices;
                        if (regionsAutoSell.containsKey(region)) {
                            prices = regionsAutoSell.get(region);
                        } else {
                            prices = new HashMap<>();
                        }
                        prices.put(type, price);
                        regionsAutoSell.put(region, prices);

                        c.sender().sendMessage(Text.colorize(String.format("&aSuccessfuly set sell price of &e%s &ato &e$%d &ain region &e%s", type.name(), price, region.getId())));
                    }
                }).registerAndBind(core, "sellprice");
        Commands.create()
                .assertPlayer()
                .handler(c -> {
                    if (c.args().size() == 0) {
                        ProtectedRegion region = this.getFirstRegionAtLocation(c.sender().getLocation());

                        if (region == null) {
                            c.sender().sendMessage(getMessage("not_in_region"));
                            return;
                        }


                        if (regionsAutoSell.containsKey(region)) {

                            double totalPrice = 0;

                            List<ItemStack> toRemove = new ArrayList<>();

                            for (Material m : regionsAutoSell.get(region).keySet()) {
                                for (ItemStack item : Arrays.stream(c.sender().getInventory().getContents()).filter(i -> i != null && i.getType() == m).collect(Collectors.toList())) {
                                    totalPrice += item.getAmount() * regionsAutoSell.get(region).get(m);
                                    toRemove.add(item);
                                }
                            }

                            toRemove.forEach(i -> c.sender().getInventory().removeItem(i));
                            totalPrice = (long) core.getMultipliers().getApi().getTotalToDeposit(c.sender(), totalPrice);
                            core.getEconomy().depositPlayer(c.sender(), totalPrice);
                            c.sender().sendMessage(getMessage("sell_all_complete").replace("%price%", String.format("%,.0f", totalPrice)));
                        }
                    }
                }).registerAndBind(core, "sellall");


    }

    private void toggleAutoSell(Player player) {
        if (disabledAutoSell.contains(player.getUniqueId())) {
            player.sendMessage(getMessage("autosell_enable"));
            disabledAutoSell.remove(player.getUniqueId());
        } else {
            disabledAutoSell.add(player.getUniqueId());
            player.sendMessage(getMessage("autosell_disable"));
        }
    }

    private ProtectedRegion getFirstRegionAtLocation(Location loc) {
        List<ProtectedRegion> regions = new ArrayList<>(WorldGuardPlugin.inst().getRegionContainer().createQuery().getApplicableRegions(loc).getRegions());
        return regions.size() == 0 ? null : regions.get(0);
    }

    public String getMessage(String key) {
        return messages.get(key.toLowerCase());
    }

    public double getCurrentEarnings(Player player) {
        return lastMinuteEarnings.containsKey(player.getUniqueId()) ? lastMinuteEarnings.get(player.getUniqueId()) : 0.0;
    }

    public int getPriceForBrokenBlock(ProtectedRegion region, Block block) {
        return regionsAutoSell.containsKey(region) ? regionsAutoSell.get(region).containsKey(block.getType()) ? regionsAutoSell.get(region).get(block.getType()) : 0 : 0;
    }

    public boolean hasAutoSellEnabled(Player p) {
        return !disabledAutoSell.contains(p.getUniqueId());
    }

    public void addToCurrentEarnings(Player p, double amount) {
        double current = this.lastMinuteEarnings.getOrDefault(p.getUniqueId(), 0.0);

        this.lastMinuteEarnings.put(p.getUniqueId(), current + amount);
    }
}