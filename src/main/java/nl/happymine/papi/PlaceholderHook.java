package nl.happymine.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.happymine.EhasPvpEnabled;
import nl.happymine.Main;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Created by DeStilleGast 11-10-2020
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final Main main;

    public PlaceholderHook(Main main) {
        this.main = main;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hasPVPB";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DeStilleGast";
    }

    @Override
    public @NotNull String getVersion() {
        return "1";
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        boolean hasPvp = main.hasPvPEnabled(player) == EhasPvpEnabled.YES;
        return hasPvp + "";
    }
}
