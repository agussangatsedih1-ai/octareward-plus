package id.tempestaria.dailyreward.service;

import id.tempestaria.dailyreward.config.RewardDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Collection;
import java.util.UUID;

/** Permission and optional LuckPerms group checks for premium daily rewards. */
public final class SupporterAccess {
    private SupporterAccess() {}

    public static boolean hasAccess(Player player, RewardDefinition reward) {
        if (player == null || reward == null) return false;

        String permission = reward.permission();
        if (!permission.isBlank() && player.hasPermission(permission)) return true;

        String group = reward.luckPermsGroup();
        // Backward compatibility for older configs using octarewardplus.supporter.<group>.
        if (group.isBlank() && permission.startsWith("octarewardplus.supporter.")) {
            group = permission.substring("octarewardplus.supporter.".length());
        }
        if (group.isBlank()) return false;

        String normalizedGroup = group.trim().toLowerCase(Locale.ROOT);
        if (player.hasPermission("octarewardplus.claim.support." + normalizedGroup)) return true;
        return isLuckPermsMember(player.getUniqueId(), group);
    }

    private static boolean isLuckPermsMember(UUID uuid, String expectedGroup) {
        Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin == null || !luckPermsPlugin.isEnabled()) return false;

        try {
            ClassLoader loader = luckPermsPlugin.getClass().getClassLoader();
            Class<?> providerApi = Class.forName("net.luckperms.api.LuckPermsProvider", true, loader);
            Object luckPerms = providerApi.getMethod("get").invoke(null);

            Class<?> luckPermsApi = Class.forName("net.luckperms.api.LuckPerms", true, loader);
            Object userManager = luckPermsApi.getMethod("getUserManager").invoke(luckPerms);
            Class<?> userManagerApi = Class.forName("net.luckperms.api.model.user.UserManager", true, loader);
            Object user = userManagerApi.getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user == null) return false;

            Class<?> userApi = Class.forName("net.luckperms.api.model.user.User", true, loader);
            Object primaryGroup = userApi.getMethod("getPrimaryGroup").invoke(user);
            if (primaryGroup != null && expectedGroup.equalsIgnoreCase(primaryGroup.toString())) return true;

            Class<?> queryOptionsApi = Class.forName("net.luckperms.api.query.QueryOptions", true, loader);
            Object options = userApi.getMethod("getQueryOptions").invoke(user);
            Object inherited = userApi.getMethod("getInheritedGroups", queryOptionsApi).invoke(user, options);
            if (inherited instanceof Collection<?> groups) {
                Class<?> groupApi = Class.forName("net.luckperms.api.model.group.Group", true, loader);
                for (Object group : groups) {
                    Object name = groupApi.getMethod("getName").invoke(group);
                    if (name != null && expectedGroup.equalsIgnoreCase(name.toString())) return true;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The octarewardplus.claim.support.<group> permission route remains available.
        }
        return false;
    }
}
