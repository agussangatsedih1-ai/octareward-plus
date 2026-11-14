package id.tempestaria.dailyreward.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.util.DateUtil;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.*;

public final class DatabaseManager implements AutoCloseable {
    private final DailyRewardPlugin plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "OctaRewardPlus-Database"); t.setDaemon(true); return t;
    });
    private HikariDataSource dataSource;
    private boolean mysql;

    public DatabaseManager(DailyRewardPlugin plugin) { this.plugin = plugin; }

    public void initialize() throws SQLException {
        String type = plugin.getConfig().getString("storage.type", "SQLITE").toUpperCase();
        HikariConfig cfg = new HikariConfig();
        if (type.equals("MYSQL")) {
            mysql = true;
            String host = plugin.getConfig().getString("storage.mysql.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String db = plugin.getConfig().getString("storage.mysql.database", "octarewardplus");
            boolean ssl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl + "&serverTimezone=UTC&characterEncoding=utf8");
            cfg.setUsername(plugin.getConfig().getString("storage.mysql.username", "root"));
            cfg.setPassword(plugin.getConfig().getString("storage.mysql.password", ""));
            cfg.setMaximumPoolSize(Math.max(2, plugin.getConfig().getInt("storage.mysql.maximum-pool-size", 10)));
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            mysql = false;
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite-file", "data/octarewardplus.db"));
            File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
            cfg.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            cfg.setDriverClassName("org.sqlite.JDBC");
            cfg.setMaximumPoolSize(1);
            cfg.addDataSourceProperty("busy_timeout", "10000");
            cfg.addDataSourceProperty("journal_mode", "WAL");
        }
        cfg.setPoolName("OctaRewardPlus-Pool");
        cfg.setConnectionTimeout(10000);
        cfg.setValidationTimeout(5000);
        dataSource = new HikariDataSource(cfg);
        migrate();
    }

    private void migrate() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dr_players (uuid VARCHAR(36) PRIMARY KEY, playtime_ms BIGINT NOT NULL DEFAULT 0, current_streak INT NOT NULL DEFAULT 0, best_streak INT NOT NULL DEFAULT 0, last_daily_period VARCHAR(16) NOT NULL DEFAULT '')");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dr_claims (uuid VARCHAR(36) NOT NULL, reward_id VARCHAR(96) NOT NULL, claim_type VARCHAR(24) NOT NULL, period_key VARCHAR(96) NOT NULL, claimed_at BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', PRIMARY KEY (uuid, reward_id, claim_type, period_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dr_events (event_id VARCHAR(96) NOT NULL, instance_id VARCHAR(36) PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL DEFAULT '', starts_at BIGINT NOT NULL, ends_at BIGINT NOT NULL, active INT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dr_event_progress (uuid VARCHAR(36) NOT NULL, event_id VARCHAR(96) NOT NULL, progress INT NOT NULL DEFAULT 0, triggered INT NOT NULL DEFAULT 0, PRIMARY KEY(uuid,event_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dr_audit (id VARCHAR(36) PRIMARY KEY, actor VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, action VARCHAR(48) NOT NULL, details VARCHAR(512) NOT NULL, created_at BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dr_meta (meta_key VARCHAR(96) PRIMARY KEY, meta_value VARCHAR(96) NOT NULL)");
            try (PreparedStatement p = c.prepareStatement("SELECT meta_value FROM dr_meta WHERE meta_key='schema_version'"); ResultSet rs = p.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement i = c.prepareStatement("INSERT INTO dr_meta(meta_key,meta_value) VALUES('schema_version','2')")) { i.executeUpdate(); }
                } else {
                    try (PreparedStatement u = c.prepareStatement("UPDATE dr_meta SET meta_value='2' WHERE meta_key='schema_version'")) { u.executeUpdate(); }
                }
            }
        }
    }

    private <T> CompletableFuture<T> async(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try { return task.run(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    @FunctionalInterface private interface SqlTask<T> { T run() throws Exception; }

    private void ensurePlayer(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT uuid FROM dr_players WHERE uuid=?")) {
            q.setString(1, uuid.toString()); try (ResultSet rs = q.executeQuery()) { if (rs.next()) return; }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO dr_players(uuid) VALUES(?)")) {
            p.setString(1, uuid.toString()); p.executeUpdate();
        } catch (SQLException ex) {
            if (!isDuplicate(ex)) throw ex;
        }
    }

    private void lockPlayerRow(Connection c, UUID uuid) throws SQLException {
        if (!mysql) return;
        try (PreparedStatement p = c.prepareStatement("SELECT uuid FROM dr_players WHERE uuid=? FOR UPDATE")) {
            p.setString(1, uuid.toString());
            try (ResultSet ignored = p.executeQuery()) { /* row lock retained until transaction end */ }
        }
    }

    private boolean isDuplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 19 || ex.getErrorCode() == 1062 || (state != null && state.startsWith("23"));
    }

    public CompletableFuture<PlayerStats> getStats(UUID uuid) {
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                ensurePlayer(c, uuid);
                try (PreparedStatement p = c.prepareStatement("SELECT playtime_ms,current_streak,best_streak,last_daily_period FROM dr_players WHERE uuid=?")) {
                    p.setString(1, uuid.toString()); try (ResultSet rs = p.executeQuery()) {
                        return rs.next() ? new PlayerStats(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getString(4)) : PlayerStats.empty();
                    }
                }
            }
        });
    }

    public CompletableFuture<Long> getPlaytime(UUID uuid) { return getStats(uuid).thenApply(PlayerStats::playtimeMillis); }

    public CompletableFuture<Void> addPlaytime(UUID uuid, long millis) {
        if (millis <= 0) return CompletableFuture.completedFuture(null);
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                ensurePlayer(c, uuid);
                try (PreparedStatement p = c.prepareStatement("UPDATE dr_players SET playtime_ms=playtime_ms+? WHERE uuid=?")) {
                    p.setLong(1, millis); p.setString(2, uuid.toString()); p.executeUpdate();
                }
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> hasClaim(UUID uuid, String rewardId, String type, String period) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("SELECT 1 FROM dr_claims WHERE uuid=? AND reward_id=? AND claim_type=? AND period_key=?")) {
                p.setString(1, uuid.toString()); p.setString(2, rewardId); p.setString(3, type); p.setString(4, period);
                try (ResultSet rs = p.executeQuery()) { return rs.next(); }
            }
        });
    }

    public CompletableFuture<Boolean> hasAnyClaim(UUID uuid, String rewardId, String type) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("SELECT 1 FROM dr_claims WHERE uuid=? AND reward_id=? AND claim_type=? LIMIT 1")) {
                p.setString(1, uuid.toString()); p.setString(2, rewardId); p.setString(3, type);
                try (ResultSet rs = p.executeQuery()) { return rs.next(); }
            }
        });
    }

    public CompletableFuture<Boolean> hasReachedClaimLimit(UUID uuid, String rewardId, String type, int limit) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM dr_claims WHERE uuid=? AND reward_id=? AND claim_type=?")) {
                p.setString(1, uuid.toString()); p.setString(2, rewardId); p.setString(3, type);
                try (ResultSet rs = p.executeQuery()) { return rs.next() && rs.getInt(1) >= Math.max(1, limit); }
            }
        });
    }

    public CompletableFuture<ClaimResult> claimDaily(UUID uuid, String rewardId, LocalDate period, String missedPolicy, int graceDays, Set<Integer> milestoneDays) {
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    ensurePlayer(c, uuid);
                    lockPlayerRow(c, uuid);
                    PlayerStats old = readStats(c, uuid);
                    if (!insertClaim(c, uuid, rewardId, "DAILY", period.toString())) {
                        c.rollback(); return ClaimResult.duplicate(old.currentStreak(), old.bestStreak());
                    }
                    boolean firstDailyClaimOfPeriod = insertClaim(c, uuid, "daily-cycle", "DAILY_STREAK", period.toString());
                    int streak = old.currentStreak();
                    int best = old.bestStreak();
                    boolean milestone = false;
                    if (firstDailyClaimOfPeriod) {
                        streak = DateUtil.nextStreak(old.currentStreak(), old.lastDailyPeriod(), period, missedPolicy, graceDays);
                        best = Math.max(old.bestStreak(), streak);
                        try (PreparedStatement p = c.prepareStatement("UPDATE dr_players SET current_streak=?,best_streak=?,last_daily_period=? WHERE uuid=?")) {
                            p.setInt(1, streak); p.setInt(2, best); p.setString(3, period.toString()); p.setString(4, uuid.toString()); p.executeUpdate();
                        }
                        milestone = milestoneDays != null && milestoneDays.contains(streak)
                                && insertClaim(c, uuid, "streak-" + streak, "STREAK", period.toString());
                    }
                    c.commit();
                    return new ClaimResult(true, false, old.currentStreak(), streak, best, milestone);
                } catch (Exception ex) { c.rollback(); throw ex; }
                finally { c.setAutoCommit(true); }
            }
        });
    }

    public CompletableFuture<Boolean> claimOnce(UUID uuid, String rewardId, String type, String period) {
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    ensurePlayer(c, uuid);
                    boolean inserted = insertClaim(c, uuid, rewardId, type, period);
                    if (inserted) c.commit(); else c.rollback();
                    return inserted;
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            }
        });
    }

    private boolean insertClaim(Connection c, UUID uuid, String rewardId, String type, String period) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO dr_claims(uuid,reward_id,claim_type,period_key,claimed_at,status) VALUES(?,?,?,?,?,'PENDING')")) {
            p.setString(1, uuid.toString()); p.setString(2, rewardId); p.setString(3, type); p.setString(4, period); p.setLong(5, System.currentTimeMillis());
            p.executeUpdate(); return true;
        } catch (SQLException ex) { if (isDuplicate(ex)) return false; throw ex; }
    }

    private PlayerStats readStats(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT playtime_ms,current_streak,best_streak,last_daily_period FROM dr_players WHERE uuid=?")) {
            p.setString(1, uuid.toString()); try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? new PlayerStats(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getString(4)) : PlayerStats.empty();
            }
        }
    }

    public CompletableFuture<String> claimEvent(UUID uuid, String eventId, int maxClaims) {
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    ensurePlayer(c, uuid);
                    lockPlayerRow(c, uuid);
                    long now = System.currentTimeMillis();
                    String instance = null;
                    try (PreparedStatement p = c.prepareStatement("SELECT instance_id FROM dr_events WHERE event_id=? AND active=1 AND starts_at<=? AND ends_at>=? AND (owner_uuid='' OR owner_uuid=?) ORDER BY created_at DESC")) {
                        p.setString(1, eventId); p.setLong(2, now); p.setLong(3, now); p.setString(4, uuid.toString());
                        try (ResultSet rs = p.executeQuery()) { if (rs.next()) instance = rs.getString(1); }
                    }
                    if (instance == null) { c.rollback(); return null; }
                    try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM dr_claims WHERE uuid=? AND reward_id=? AND claim_type='EVENT'")) {
                        p.setString(1, uuid.toString()); p.setString(2, eventId);
                        try (ResultSet rs = p.executeQuery()) { if (rs.next() && rs.getInt(1) >= Math.max(1, maxClaims)) { c.rollback(); return null; } }
                    }
                    boolean inserted = insertClaim(c, uuid, eventId, "EVENT", instance);
                    if (inserted) { c.commit(); return instance; }
                    c.rollback(); return null;
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            }
        });
    }

    /** Increments a persisted activity counter once; returns true exactly once at the threshold. */
    public CompletableFuture<Boolean> incrementEventProgress(UUID uuid, String eventId, int requiredCount) {
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    ensurePlayer(c, uuid);
                    lockPlayerRow(c, uuid);
                    int progress = 0; boolean triggered = false; boolean exists = false;
                    try (PreparedStatement q = c.prepareStatement("SELECT progress,triggered FROM dr_event_progress WHERE uuid=? AND event_id=?")) {
                        q.setString(1, uuid.toString()); q.setString(2, eventId);
                        try (ResultSet rs = q.executeQuery()) { if (rs.next()) { exists = true; progress = rs.getInt(1); triggered = rs.getInt(2) != 0; } }
                    }
                    if (triggered) { c.commit(); return false; }
                    progress++;
                    boolean reached = progress >= Math.max(1, requiredCount);
                    if (exists) {
                        try (PreparedStatement u = c.prepareStatement("UPDATE dr_event_progress SET progress=?,triggered=? WHERE uuid=? AND event_id=?")) {
                            u.setInt(1, progress); u.setInt(2, reached ? 1 : 0); u.setString(3, uuid.toString()); u.setString(4, eventId); u.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement i = c.prepareStatement("INSERT INTO dr_event_progress(uuid,event_id,progress,triggered) VALUES(?,?,?,?)")) {
                            i.setString(1, uuid.toString()); i.setString(2, eventId); i.setInt(3, progress); i.setInt(4, reached ? 1 : 0); i.executeUpdate();
                        }
                    }
                    c.commit(); return reached;
                } catch (Exception ex) { c.rollback(); throw ex; }
                finally { c.setAutoCommit(true); }
            }
        });
    }

    public CompletableFuture<String> triggerEvent(String eventId, UUID owner, long startsAt, long endsAt) {
        return async(() -> {
            String instance = UUID.randomUUID().toString();
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO dr_events(event_id,instance_id,owner_uuid,starts_at,ends_at,active,created_at) VALUES(?,?,?,?,?,1,?)")) {
                p.setString(1, eventId); p.setString(2, instance); p.setString(3, owner == null ? "" : owner.toString());
                p.setLong(4, startsAt); p.setLong(5, endsAt); p.setLong(6, System.currentTimeMillis()); p.executeUpdate();
            }
            return instance;
        });
    }

    public CompletableFuture<Void> endEvent(String eventId) {
        return async(() -> { try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE dr_events SET active=0 WHERE event_id=?")) { p.setString(1, eventId); p.executeUpdate(); } return null; });
    }

    public CompletableFuture<Boolean> hasActiveEvent(String eventId, UUID player) {
        return async(() -> {
            long now = System.currentTimeMillis();
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("SELECT 1 FROM dr_events WHERE event_id=? AND active=1 AND starts_at<=? AND ends_at>=? AND (owner_uuid='' OR owner_uuid=?) LIMIT 1")) {
                p.setString(1, eventId); p.setLong(2, now); p.setLong(3, now); p.setString(4, player.toString());
                try (ResultSet rs = p.executeQuery()) { return rs.next(); }
            }
        });
    }

    public CompletableFuture<Boolean> resetClaim(UUID uuid, String rewardId) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM dr_claims WHERE uuid=? AND reward_id=?")) {
                p.setString(1, uuid.toString()); p.setString(2, rewardId); return p.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Void> resetStreak(UUID uuid) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE dr_players SET current_streak=0,last_daily_period='' WHERE uuid=?")) {
                p.setString(1, uuid.toString()); p.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> setPlaytime(UUID uuid, long minutes) {
        return async(() -> {
            try (Connection c = dataSource.getConnection()) {
                ensurePlayer(c, uuid);
                try (PreparedStatement p = c.prepareStatement("UPDATE dr_players SET playtime_ms=? WHERE uuid=?")) { p.setLong(1, Math.max(0, minutes) * 60_000L); p.setString(2, uuid.toString()); p.executeUpdate(); }
            }
            return null;
        });
    }

    public CompletableFuture<Void> markClaimStatus(UUID uuid, String rewardId, String type, String period, String status) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE dr_claims SET status=? WHERE uuid=? AND reward_id=? AND claim_type=? AND period_key=?")) {
                p.setString(1, status); p.setString(2, uuid.toString()); p.setString(3, rewardId); p.setString(4, type); p.setString(5, period); p.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> audit(UUID actor, UUID target, String action, String details) {
        return async(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO dr_audit(id,actor,target_uuid,action,details,created_at) VALUES(?,?,?,?,?,?)")) {
                p.setString(1, UUID.randomUUID().toString()); p.setString(2, actor == null ? "CONSOLE" : actor.toString());
                p.setString(3, target == null ? "UNKNOWN" : target.toString()); p.setString(4, action); p.setString(5, details == null ? "" : details.substring(0, Math.min(512, details.length()))); p.setLong(6, System.currentTimeMillis()); p.executeUpdate();
            }
            return null;
        });
    }

    @Override public void close() {
        executor.shutdown();
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
        if (dataSource != null) dataSource.close();
    }
}
