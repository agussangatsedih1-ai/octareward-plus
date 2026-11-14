package id.tempestaria.dailyreward.db;

/** Result of an atomic claim guarded by a rolling cooldown. */
public record CooldownClaimResult(boolean claimed, boolean coolingDown, String periodKey, long nextAvailableAt) {
    public static CooldownClaimResult cooldown(long nextAvailableAt) {
        return new CooldownClaimResult(false, true, "", nextAvailableAt);
    }

    public static CooldownClaimResult claimed(String periodKey, long nextAvailableAt) {
        return new CooldownClaimResult(true, false, periodKey, nextAvailableAt);
    }
}
