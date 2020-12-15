package me.drawethree.ultraprisoncore.enchants.api.events;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.Getter;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.List;

@Getter
public abstract class BlockEnchantEvent extends Event implements Cancellable {

    protected final Player player;
    protected final ProtectedRegion mineRegion;
    protected final List<BlockState> blocksAffected;

    public BlockEnchantEvent(Player p, ProtectedRegion mineRegion, List<BlockState> blocksAffected) {
        this.player = p;
        this.mineRegion = mineRegion;
        this.blocksAffected = blocksAffected;
    }
}