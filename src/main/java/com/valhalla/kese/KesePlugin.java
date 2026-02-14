package com.valhalla.kese;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KesePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int EIGHTHS_PER_NUGGET = 1;
    private static final int EIGHTHS_PER_INGOT = 8;
    private static final int EIGHTHS_PER_BLOCK = 72;

    private Economy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault Economy provider bulunamadı. Plugin devre dışı bırakılıyor.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("kese") != null) {
            getCommand("kese").setExecutor(this);
            getCommand("kese").setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        registerRecipes();
        getLogger().info("Kese aktif.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    private void registerRecipes() {
        NamespacedKey keyIngotToNugget = new NamespacedKey(this, "gold_ingot_to_8_nuggets");
        ShapelessRecipe ingotToNugget = new ShapelessRecipe(keyIngotToNugget, new ItemStack(Material.GOLD_NUGGET, 8));
        ingotToNugget.addIngredient(Material.GOLD_INGOT);
        Bukkit.addRecipe(ingotToNugget);

        NamespacedKey keyTotem = new NamespacedKey(this, "custom_totem_of_undying");
        ShapedRecipe totemRecipe = new ShapedRecipe(keyTotem, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        totemRecipe.shape("GBG", "BHB", "GBG");
        totemRecipe.setIngredient('G', Material.GOLD_BLOCK);
        totemRecipe.setIngredient('B', Material.BLAZE_ROD);
        totemRecipe.setIngredient('H', new RecipeChoice.MaterialChoice(Material.HEART_OF_THE_SEA));
        Bukkit.addRecipe(totemRecipe);
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.getSource() != null && event.getSource().getType() == Material.NETHER_GOLD_ORE) {
            event.setResult(new ItemStack(Material.GOLD_NUGGET, 2));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komut yalnızca oyuncular tarafından kullanılabilir.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("Kullanım: /kese <koy|al> <miktar>");
            return true;
        }

        String action = args[0].toLowerCase();
        Integer amountEighths = parseMoneyToEighths(args[1]);
        if (amountEighths == null || amountEighths <= 0) {
            player.sendMessage("Geçersiz miktar. Miktar 0.125 katları olmalıdır.");
            return true;
        }

        if ("koy".equals(action)) {
            handleDeposit(player, amountEighths);
            return true;
        }
        if ("al".equals(action)) {
            handleWithdraw(player, amountEighths);
            return true;
        }

        player.sendMessage("Geçersiz alt komut. Kullanım: /kese <koy|al> <miktar>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = List.of("koy", "al");
            String prefix = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String option : options) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private void handleDeposit(Player player, int requestEighths) {
        PlayerInventory inv = player.getInventory();
        ItemCounts counts = countGold(inv);

        ConsumePlan plan = chooseConsumePlan(counts, requestEighths);
        if (plan == null) {
            player.sendMessage("Envanterinizde yeterli altın yok.");
            return;
        }

        List<ItemStack> changeItems = toChangeItems(plan.overpayEighths());
        ItemStack[] snapshot = cloneStorage(inv.getStorageContents());

        ItemStack[] simulated = simulatePostDeposit(snapshot, plan, changeItems);
        if (simulated == null) {
            player.sendMessage("Bozuk para iadesi için yeterli envanter alanı yok.");
            return;
        }

        inv.setStorageContents(simulated);

        double amount = requestEighths / 8.0D;
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            inv.setStorageContents(snapshot);
            player.sendMessage("Para yatırma başarısız: " + response.errorMessage);
            return;
        }

        player.sendMessage("Başarılı: " + formatMoney(amount) + " para hesabınıza yatırıldı.");
    }

    private void handleWithdraw(Player player, int requestEighths) {
        double amount = requestEighths / 8.0D;
        if (economy.getBalance(player) + 1.0E-9 < amount) {
            player.sendMessage("Yetersiz bakiye.");
            return;
        }

        List<ItemStack> payoutItems = toPayoutItems(requestEighths);
        PlayerInventory inv = player.getInventory();
        ItemStack[] snapshot = cloneStorage(inv.getStorageContents());

        if (!canFitItems(snapshot, payoutItems)) {
            player.sendMessage("Envanterinizde yeterli boş alan yok.");
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage("Para çekme başarısız: " + response.errorMessage);
            return;
        }

        ItemStack[] afterAdd = addItemsToContents(snapshot, payoutItems);
        if (afterAdd == null) {
            economy.depositPlayer(player, amount);
            inv.setStorageContents(snapshot);
            player.sendMessage("Envantere ekleme başarısız oldu, işlem geri alındı.");
            return;
        }

        inv.setStorageContents(afterAdd);
        player.sendMessage("Başarılı: " + formatMoney(amount) + " para karşılığı altın verildi.");
    }

    private ItemCounts countGold(PlayerInventory inv) {
        int nuggets = 0;
        int ingots = 0;
        int blocks = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (stack.getType() == Material.GOLD_NUGGET) {
                nuggets += stack.getAmount();
            } else if (stack.getType() == Material.GOLD_INGOT) {
                ingots += stack.getAmount();
            } else if (stack.getType() == Material.GOLD_BLOCK) {
                blocks += stack.getAmount();
            }
        }
        return new ItemCounts(nuggets, ingots, blocks);
    }

    private ConsumePlan chooseConsumePlan(ItemCounts counts, int requestEighths) {
        ConsumePlan best = null;

        for (int b = 0; b <= counts.blocks(); b++) {
            int bValue = b * EIGHTHS_PER_BLOCK;
            for (int i = 0; i <= counts.ingots(); i++) {
                int base = bValue + i * EIGHTHS_PER_INGOT;
                int neededNuggets = Math.max(0, requestEighths - base);
                if (neededNuggets > counts.nuggets()) {
                    continue;
                }
                int total = base + neededNuggets;
                if (total < requestEighths) {
                    continue;
                }
                int overpay = total - requestEighths;
                ConsumePlan current = new ConsumePlan(neededNuggets, i, b, overpay);
                if (best == null
                        || current.overpayEighths() < best.overpayEighths()
                        || (current.overpayEighths() == best.overpayEighths() && current.totalItemsConsumed() < best.totalItemsConsumed())) {
                    best = current;
                }
            }
        }
        return best;
    }

    private List<ItemStack> toChangeItems(int changeEighths) {
        List<ItemStack> items = new ArrayList<>();
        if (changeEighths <= 0) {
            return items;
        }

        int ingots = changeEighths / EIGHTHS_PER_INGOT;
        int nuggets = changeEighths % EIGHTHS_PER_INGOT;
        appendMaterial(items, Material.GOLD_INGOT, ingots);
        appendMaterial(items, Material.GOLD_NUGGET, nuggets);
        return items;
    }

    private List<ItemStack> toPayoutItems(int amountEighths) {
        List<ItemStack> items = new ArrayList<>();
        int blocks = amountEighths / EIGHTHS_PER_BLOCK;
        int rem = amountEighths % EIGHTHS_PER_BLOCK;
        int ingots = rem / EIGHTHS_PER_INGOT;
        int nuggets = rem % EIGHTHS_PER_INGOT;

        appendMaterial(items, Material.GOLD_BLOCK, blocks);
        appendMaterial(items, Material.GOLD_INGOT, ingots);
        appendMaterial(items, Material.GOLD_NUGGET, nuggets);
        return items;
    }

    private void appendMaterial(List<ItemStack> items, Material material, int amount) {
        int max = material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(max, remaining);
            items.add(new ItemStack(material, give));
            remaining -= give;
        }
    }

    private ItemStack[] simulatePostDeposit(ItemStack[] original, ConsumePlan plan, List<ItemStack> change) {
        ItemStack[] contents = cloneStorage(original);
        if (!removeFromContents(contents, Material.GOLD_NUGGET, plan.nuggetsConsumed())) {
            return null;
        }
        if (!removeFromContents(contents, Material.GOLD_INGOT, plan.ingotsConsumed())) {
            return null;
        }
        if (!removeFromContents(contents, Material.GOLD_BLOCK, plan.blocksConsumed())) {
            return null;
        }
        return addItemsToContents(contents, change);
    }

    private boolean removeFromContents(ItemStack[] contents, Material material, int amount) {
        int remaining = amount;
        if (remaining <= 0) {
            return true;
        }
        for (int idx = 0; idx < contents.length; idx++) {
            ItemStack stack = contents[idx];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int newAmount = stack.getAmount() - take;
            if (newAmount <= 0) {
                contents[idx] = null;
            } else {
                stack.setAmount(newAmount);
            }
            remaining -= take;
            if (remaining == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canFitItems(ItemStack[] contents, List<ItemStack> items) {
        return addItemsToContents(cloneStorage(contents), items) != null;
    }

    private ItemStack[] addItemsToContents(ItemStack[] contents, List<ItemStack> items) {
        ItemStack[] copy = cloneStorage(contents);

        for (ItemStack add : items) {
            int remaining = add.getAmount();

            for (ItemStack stack : copy) {
                if (stack == null || stack.getType() != add.getType()) {
                    continue;
                }
                int max = stack.getMaxStackSize();
                int room = max - stack.getAmount();
                if (room <= 0) {
                    continue;
                }
                int moved = Math.min(room, remaining);
                stack.setAmount(stack.getAmount() + moved);
                remaining -= moved;
                if (remaining == 0) {
                    break;
                }
            }

            while (remaining > 0) {
                int emptySlot = firstEmpty(copy);
                if (emptySlot < 0) {
                    return null;
                }
                int moved = Math.min(add.getMaxStackSize(), remaining);
                copy[emptySlot] = new ItemStack(add.getType(), moved);
                remaining -= moved;
            }
        }

        return copy;
    }

    private int firstEmpty(ItemStack[] contents) {
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null || contents[i].getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack[] cloneStorage(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }
        return clone;
    }

    private Integer parseMoneyToEighths(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            BigDecimal eighths = value.multiply(BigDecimal.valueOf(8));
            if (eighths.stripTrailingZeros().scale() > 0) {
                return null;
            }
            return eighths.intValueExact();
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(3, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    private record ItemCounts(int nuggets, int ingots, int blocks) {
    }

    private record ConsumePlan(int nuggetsConsumed, int ingotsConsumed, int blocksConsumed, int overpayEighths) {
        int totalItemsConsumed() {
            return nuggetsConsumed + ingotsConsumed + blocksConsumed;
        }
    }
}
