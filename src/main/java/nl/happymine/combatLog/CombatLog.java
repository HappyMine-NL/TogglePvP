package nl.happymine.combatLog;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import nl.happymine.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public class CombatLog implements org.bukkit.event.Listener {

    //private ArrayList<String> antilog = new ArrayList<String>();
    private ArrayList<PlayerTagExtension> tagList = new ArrayList<PlayerTagExtension>();

    private Main pl;
    private File configFile;

    private boolean enabled;

    public CombatLog(Main pl) {
        this.pl = pl;

        configFile = new File(pl.getDataFolder(), "combatlog.yml");
        if (!configFile.exists()) {
            pl.saveResource("combatlog.yml", false);
        }

        enabled = getMyConfig().getBoolean("enabled");

        if (enabled) {
            pl.getLogger().info("Combat log enabled!");
        }
    }

    public Configuration getMyConfig() {
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public PlayerTagExtension getPlayerTag(Player player) {
        for (PlayerTagExtension p : tagList) {
            if (p.getMyPlayer() == player) {
                return p;
            }
        }
        return null;
    }

    public ArrayList<PlayerTagExtension> getTagList() {
        return tagList;
    }

    public void onJoin(Player p) {
        if (getPlayerTag(p) == null) {
            tagList.add(new PlayerTagExtension(p));
        }
    }

    public void onAttack(Player attacker, Player target) {
        if (!enabled) return;

        final PlayerTagExtension pAttacker = getPlayerTag(attacker);
        final PlayerTagExtension pTarget = getPlayerTag(target);

        if (!pAttacker.isAttacker(target.getName())) {
            pAttacker.addAttacker(target.getName());
            pTarget.addAttacker(attacker.getName());

            sendAction(attacker, getMyConfig().getString("inFight").replace("{player}", target.getName()));
            sendAction(target, getMyConfig().getString("inFight").replace("{player}", attacker.getName()));

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(pl, new Runnable() {
                public void run() {
                    if (pAttacker.isAttacker(pTarget.getMyPlayer().getName())) {
                        pAttacker.removeAttacker(pTarget.getMyPlayer().getName());
                        if (pAttacker.getAttackerList().size() == 0) {
                            sendAction(pAttacker.getMyPlayer(), getMyConfig().getString("noLongerInFight"));
                        } else {
                            String attackers = "";
                            if (pAttacker.getAttackerList().size() > 1) {
                                for (int i = 0; i < pAttacker.getAttackerList().size() - 1; i++) {
                                    attackers += pAttacker.getAttackerList().get(i);

                                    if ((pAttacker.getAttackerList().size() - 1) > i) {
                                        attackers += " ,";
                                    }
                                }
                                attackers += " " + getMyConfig().getString("and") + " " + pAttacker.getAttackerList().get(pAttacker.getAttackerList().size() - 1);
                            } else {
                                attackers = pAttacker.getAttackerList().get(pAttacker.getAttackerList().size() - 1);
                            }

                            sendAction(pAttacker.getMyPlayer(), getMyConfig().getString("stillInFight").replace("{players}", attackers));
                        }
                    }

                    if (pTarget.isAttacker(pAttacker.getMyPlayer().getName())) {
                        pTarget.removeAttacker(pAttacker.getMyPlayer().getName());
                        if (pTarget.getAttackerList().size() == 0) {
                            sendAction(pTarget.getMyPlayer(), getMyConfig().getString("noLongerInFight"));
                        } else {
                            String attackers = "";

                            if (pTarget.getAttackerList().size() > 1) {
                                for (int i = 0; i < pTarget.getAttackerList().size() - 1; i++) {
                                    attackers += pTarget.getAttackerList().get(i) + ", ";
                                }
                                attackers += " " + getMyConfig().getString("and") + " " + pTarget.getAttackerList().get(pTarget.getAttackerList().size() - 1);
                            } else {
                                attackers = pTarget.getAttackerList().get(pTarget.getAttackerList().size() - 1);
                            }
                            //pTarget.getMyPlayer().sendMessage(ChatColor.GRAY + "You are still in attack with " + attackers);
                            sendAction(pTarget.getMyPlayer(), getMyConfig().getString("stillInFight").replace("{players}", attackers));
                        }
                    }
                }
            }, 10 * 20);
        }
    }

    public void onPlayerDeath(Player p) {
        PlayerTagExtension victum = getPlayerTag(p);

        Iterator<String> str = victum.getAttackerList().iterator();
        while (str.hasNext()) {
            str.next();
            str.remove();
        }
    }

    public void sendAction(Player p, String message) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
    }

    public boolean isInFight(Player p) {
        return !getPlayerTag(p).getAttackerList().isEmpty();
    }
}
