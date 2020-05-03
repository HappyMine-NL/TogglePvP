package nl.happymine;

import nl.happymine.combatLog.CombatLog;
import nl.happymine.combatLog.PlayerTagExtension;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by DeStilleGast on 7-6-2017.
 */
public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private ArrayList<UUID> pvpEnabled = new ArrayList<>();
    private Connector SQL;
    private String prefix = "&bPvP &8>&7";

    private File configFile = new File(getDataFolder(), "config.yml");
    private final HashMap<Player, Integer> disableMap = new HashMap<>();

    private boolean defaultPvP = getMyConfig().getBoolean("defaultPVPon");

    private ArrayList<Player> lastKnownTagPlayers = new ArrayList<>();
    private CombatLog cl;
    private BossBar dontLeaveBas;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        if (!configFile.exists()) {
            this.saveResource("config.yml", false);
        }

        YamlConfiguration sqlConfig = YamlConfiguration.loadConfiguration(configFile);

        String host, database, table, user, password;

        host = sqlConfig.getString("Server");
        database = sqlConfig.getString("Database");
        user = sqlConfig.getString("User");
        password = sqlConfig.getString("Password");
        table = sqlConfig.getString("Table", "PvpEnabledPlayers");

        prefix = sqlConfig.getString("prefix", prefix);

        SQL = new Connector(this.getLogger(), host, database, user, password);
        SQL.open();
        if (!SQL.hasConnection()) {
            this.getLogger().warning("There is no connection to the database...");
            this.getLogger().info("Database required, disabling...");

            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        SQL.createTable("`" + table + "` ( `id` INT(5) NOT NULL AUTO_INCREMENT , `UUID` VARCHAR(36) NOT NULL , `Enabled` BOOLEAN NOT NULL , PRIMARY KEY (`id`), UNIQUE(`UUID`))");
        Bukkit.getPluginManager().registerEvents(this, this);

        this.getCommand("pvp").setExecutor(this);
        cl = new CombatLog(this);

        dontLeaveBas = getServer().createBossBar("Don't leave, you are in combat !!", BarColor.RED, BarStyle.SOLID);

        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (PlayerTagExtension pte : cl.getTagList()) {
                    if (pte.getAttackerList().size() > 0) {
                        dontLeaveBas.addPlayer(pte.getMyPlayer());
                    } else {
                        dontLeaveBas.removePlayer(pte.getMyPlayer());
                    }
                }
            }
        }, 20, 10);
    }

    public Configuration getMyConfig() {
        return YamlConfiguration.loadConfiguration(configFile);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Runnable setPvP = () -> {
            Player p = e.getPlayer();

            EhasPvpEnabled result = hasPvPEnabled(p);

            if (result == EhasPvpEnabled.YES) {
                if (this.togglePvpOn(p)) {
                    sendMessage(p, "pvpEnabled", p);
                }
            } else if (result == EhasPvpEnabled.NO) {
                if (this.togglePvpOff(p, true)) {
                    sendMessage(p, "pvpDisabled", p);
                }
            } else if (result == EhasPvpEnabled.UNKNOWN) {
                if (defaultPvP) {
                    if (this.togglePvpOn(p))
                        sendMessage(p, "pvpEnabled", p);
                } else {
                    if (this.togglePvpOff(p, true))
                        sendMessage(p, "pvpDisabled", p);
                }
            }
        };

        getServer().getScheduler().runTaskLater(this, setPvP, 5);

        cl.onJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;

        Entity entityAttacker = e.getDamager();

        Player attacker = null;
        Player target = null;

        if (entityAttacker instanceof Player) {
            attacker = (Player) e.getDamager();
        }

        if (e.getEntity() instanceof Player) {
            target = (Player) e.getEntity();
        }

        if (entityAttacker instanceof Projectile) {
            Projectile pj = (Projectile) entityAttacker;
            if (pj.getShooter() instanceof Player) {

                attacker = (Player) pj.getShooter();
                if (pj.getType() == EntityType.ENDER_PEARL && target == attacker) return;
            }
        }

        if (entityAttacker instanceof AreaEffectCloud) {
            AreaEffectCloud aec = (AreaEffectCloud) entityAttacker;
            if (aec.getSource() instanceof Player) {
                attacker = (Player) aec.getSource();
            }
        }

        if (attacker == null) return;
        if (target == null) return;
        if (attacker == target) return;

        boolean targetPvpEnabled = pvpEnabled.contains(target.getUniqueId());
        boolean attackerPvpEnabled = pvpEnabled.contains(attacker.getUniqueId());

        if (!targetPvpEnabled && attackerPvpEnabled) {
            sendMessage(attacker, "pvpDisabledMessage", target);
            e.setCancelled(true);
        } else if (!attackerPvpEnabled && targetPvpEnabled) {
            sendMessage(attacker, "pvpDisabledOwnMessage", attacker);
            e.setCancelled(true);
        } else if (!attackerPvpEnabled && !targetPvpEnabled) {
            e.setCancelled(true);
        }

        if (!e.isCancelled()) {
            cl.onAttack(attacker, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void setPlayerOnFire(EntityCombustByEntityEvent e) {
        if (e.getCombuster() instanceof Arrow && e.getEntity() instanceof Player) {
            Arrow arrow = (Arrow) e.getCombuster();
            if (arrow.getShooter() instanceof Player) {

                Player attacker = (Player) arrow.getShooter();
                Player target = (Player) e.getEntity();

                if (isPvpDisabledForPlayers(target, attacker)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    public boolean isPvpDisabledForPlayers(Player target, Player attacker) {
        boolean targetPvpEnabled = pvpEnabled.contains(target.getUniqueId());
        boolean attackerPvpEnabled = pvpEnabled.contains(attacker.getUniqueId());

        if (!targetPvpEnabled && attackerPvpEnabled) {
            return true;
        } else if (!attackerPvpEnabled && targetPvpEnabled) {
            sendMessage(attacker, "pvpDisabledOwnMessage", attacker);
            return true;
        } else if (!attackerPvpEnabled && !targetPvpEnabled) {
            return true;
        }

        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;

            if (args.length == 0) {
                if (pvpEnabled.contains(p.getUniqueId())) {
                    if (togglePvpOff(p, false)) sendMessage(p, "pvpDeactivateTimeout", p);
                } else {
                    if (togglePvpOn(p)) sendMessage(p, "pvpEnabled", p);
                }
            } else if (args.length == 1 && args[0].equalsIgnoreCase("others")) {
                for (Player onlineSpeler : getServer().getOnlinePlayers()) {
                    if (pvpEnabled.contains(onlineSpeler.getUniqueId())) {
                        p.sendMessage(onlineSpeler.getName() + ChatColor.RED + " [PvP]");
                    } else {
                        p.sendMessage(onlineSpeler.getName() + ChatColor.GREEN + " [No PvP]");
                    }
                }

                /*Player otherPlayer = getServer().getPlayer(args[1]);
                if (otherPlayer != null) {
                    if (pvpEnabled.contains(otherPlayer.getUniqueId())) {
                        if (togglePvpOff(otherPlayer, false)) sendMessage(p, "pvpDisabledForOther", otherPlayer);
                    } else {
                        if (togglePvpOn(otherPlayer)) sendMessage(p, "pvpEnabledForOther", otherPlayer);
                    }
                }*/
            }
        } else {
            sender.sendMessage("You need to be a player do to that.");
        }
        return true;
    }

    public boolean togglePvpOn(Player p) {
        if (!pvpEnabled.contains(p.getUniqueId())) {
            pvpEnabled.add(p.getUniqueId());

            try {
                SQL.open();
                PreparedStatement ps = SQL.prepareStatement("INSERT INTO `" + getMyConfig().getString("Table") + "` VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE Enabled=?");
                ps.setInt(1, 0);
                ps.setString(2, p.getUniqueId().toString());
                ps.setBoolean(3, true);
                ps.setBoolean(4, true);

                ps.execute();

            } catch (SQLException e) {
                e.printStackTrace();

                return false;
            } finally {
                SQL.close();
            }
        }
        getLogger().info("Enabled pvp for " + p.getName());

        return true;
    }


    public boolean togglePvpOff(Player p, boolean skipTimer) {
        Runnable disableTask = new Runnable() {

            private final Location playerPos = p.getLocation();

            @Override
            public void run() {
                if (p.getLocation().getX() != playerPos.getX() || p.getLocation().getZ() != playerPos.getZ() || p.getLocation().getY() != playerPos.getY()) {
                    sendMessage(p, "pvpDisabledFailedMove", p);
                    return;
                }

                try {
                    SQL.open();
                    PreparedStatement ps = SQL.prepareStatement("INSERT INTO `" + getMyConfig().getString("Table") + "` VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE Enabled=?");
                    ps.setInt(1, 0);
                    ps.setString(2, p.getUniqueId().toString());
                    ps.setBoolean(3, false);
                    ps.setBoolean(4, false);

                    ps.execute();

                    getLogger().info("Disabled pvp for " + p.getName());
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    SQL.close();
                }

                if (pvpEnabled.contains(p.getUniqueId())) {
                    pvpEnabled.remove(p.getUniqueId());
                    sendMessage(p, "pvpDisabled", p);
                }

                disableMap.remove(p);
            }
        };

        if (!skipTimer) {
            if (cl.isInFight(p))
                return false;

            if (disableMap.containsKey(p)) {
                Bukkit.getScheduler().cancelTask(disableMap.get(p));
            }

            disableMap.put(p, getServer().getScheduler().scheduleSyncDelayedTask(this, disableTask, 20 * 10));
        } else {
            disableTask.run();
        }

//        DisablePvpTask dpt = new DisablePvpTask(this, p) {
//            @Override
//            public void finish(boolean success) {
//                //Bukkit.getScheduler().cancelTask(disableMap.get(this));
//                if (success) {
//                    try {
//                        SQL.open();
//                        PreparedStatement ps = SQL.prepareStatement("INSERT INTO `" + getMyConfig().getString("Table") + "` VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE Enabled=?");
//                        ps.setInt(1, 0);
//                        ps.setString(2, p.getUniqueId().toString());
//                        ps.setBoolean(3, false);
//                        ps.setBoolean(4, false);
//
//                        ps.execute();
//
//                        getLogger().info("Disabled pvp for " + p.getName());
//                    } catch (SQLException e) {
//                        e.printStackTrace();
//                    }finally {
//                        SQL.close();
//                    }
//
//                    if (pvpEnabled.contains(p.getUniqueId())) {
//                        pvpEnabled.remove(p.getUniqueId());
//                        sendMessage(p, "pvpDisabled", p);
//                    }
//                }else{
//                    getLogger().info("Success = false, something went wrong or someone moved!");
//                }
//            }
//        };
//
//        if (!skipTimer) {
//            disableMap.put(dpt, getServer().getScheduler().scheduleSyncDelayedTask(this, dpt,20*10));
//        } else {
//            dpt.finish(true);
//        }
        return true;
    }

    public EhasPvpEnabled hasPvPEnabled(Player p) {
        try {
            SQL.open();
            PreparedStatement ps = Main.this.SQL.prepareStatement("SELECT Enabled FROM `" + getMyConfig().getString("Table") + "` WHERE UUID=?");
            ps.setString(1, p.getUniqueId().toString());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (rs.getBoolean("Enabled")) {
                    return EhasPvpEnabled.YES;
                } else {
                    return EhasPvpEnabled.NO;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            SQL.close();
        }

        return EhasPvpEnabled.UNKNOWN;
    }

    public void sendMessage(CommandSender sender, String message, Player p) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + config.getString(message).replace("{player}", p.getName()).replace("{time}", 10 + "")));
    }

    public ArrayList<UUID> getPvpEnabled() {
        return pvpEnabled;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        cl.onPlayerDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTeleport(PlayerTeleportEvent e) {
        if (cl.getPlayerTag(e.getPlayer()) != null) {
            if (cl.getPlayerTag(e.getPlayer()).getAttackerList().size() > 0) {
                if (e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || e.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN || e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMoveWhileDisabling(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (disableMap.containsKey(p)) {
            if (event.getFrom() != event.getTo()) {
                Bukkit.getScheduler().cancelTask(disableMap.get(p));
                sendMessage(p, "pvpDisabledFailedMove", p);
                togglePvpOn(p);
                disableMap.remove(p);
            }
        }
    }

    public CombatLog getCombatLogger() {
        return cl;
    }

    enum EhasPvpEnabled {
        YES,
        NO,
        UNKNOWN
    }
}
