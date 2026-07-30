package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

public class LightningGuardianConvertListener implements Listener {

    private final StarMSkyblockGamePlay plugin;

    public LightningGuardianConvertListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightningHitGuardian(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("lightning-converter.enabled", true)) return;

        // Check damager is lightning
        if (!(event.getDamager() instanceof LightningStrike)) return;

        // Check damaged entity is a Guardian (not Elder Guardian)
        if (!(event.getEntity() instanceof Guardian guardian)) return;
        if (guardian.getType() != EntityType.GUARDIAN) return;

        // Cancel lightning damage so the guardian doesn't die
        event.setCancelled(true);

        // Spawn Elder Guardian at the same location
        guardian.getWorld().spawn(guardian.getLocation(), ElderGuardian.class);

        // Remove the original Guardian
        guardian.remove();
    }
}
