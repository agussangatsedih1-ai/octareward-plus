package id.tempestaria.dailyreward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DailyRewardHolder implements InventoryHolder {
    private final String page;
    private final int pageIndex;
    private Inventory inventory;

    public DailyRewardHolder(String page) { this(page, 0); }
    public DailyRewardHolder(String page, int pageIndex) {
        this.page = page;
        this.pageIndex = Math.max(0, pageIndex);
    }
    public String page() { return page; }
    public int pageIndex() { return pageIndex; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
