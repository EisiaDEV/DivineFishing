package eisiadev.enceladus.fishing

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

class FishingListener(private val plugin: FishingPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerInteract(event: PlayerInteractEvent) {

        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return

        val player = event.player
        val item = event.item ?: return
        val clickedBlock = event.clickedBlock

        val itemName = item.itemMeta?.displayName()?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""

        val isWaterRod = plugin.configManager.waterRods.contains(itemName)
        val isLavaRod = plugin.configManager.lavaRods.contains(itemName)

        if (!isWaterRod && !isLavaRod) return

        event.isCancelled = true

        val targetBlock = player.getTargetBlockExact(7, org.bukkit.FluidCollisionMode.ALWAYS)

        val blockToCheck = targetBlock ?: clickedBlock

        if (blockToCheck == null) {
            return
        }

        val blockType = blockToCheck.type

        val fishingType = when (blockType) {
            Material.WATER -> if (isWaterRod) FishingType.WATER else null
            Material.LAVA -> if (isLavaRod) FishingType.LAVA else null
            else -> null
        }

        if (fishingType == null) {
            if (plugin.fishingManager.hasActiveSession(player)) {
                plugin.fishingManager.failFishing(player, plugin.configManager.getMessage("fishing-failed"))
            } else {
                player.sendMessage(plugin.configManager.getMessage("invalid-rod"))
            }
            return
        }

        if (plugin.fishingManager.hasActiveSession(player)) {
            plugin.fishingManager.moveCursor(player, 1, blockToCheck.location)
        } else {
            plugin.fishingManager.startFishing(player, fishingType, blockToCheck.location, item)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerInteractLeft(event: PlayerInteractEvent) {

        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.LEFT_CLICK_BLOCK && event.action != Action.LEFT_CLICK_AIR) return

        val player = event.player
        val item = event.item ?: return

        val itemName = item.itemMeta?.displayName()?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""

        val isWaterRod = plugin.configManager.waterRods.contains(itemName)
        val isLavaRod = plugin.configManager.lavaRods.contains(itemName)

        if (!isWaterRod && !isLavaRod) return

        event.isCancelled = true

        if (plugin.fishingManager.hasActiveSession(player)) {
            val targetBlock = player.getTargetBlockExact(7, org.bukkit.FluidCollisionMode.ALWAYS)
            val clickedBlock = event.clickedBlock
            val blockToCheck = targetBlock ?: clickedBlock
            
            if (blockToCheck != null) {
                plugin.fishingManager.moveCursor(player, -1, blockToCheck.location)
            } else {
                plugin.fishingManager.moveCursor(player, -1, null)
            }
        }
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (!plugin.fishingManager.hasActiveSession(player)) return

        plugin.fishingManager.failFishing(player, plugin.configManager.getMessage("fishing-failed"))
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.fishingManager.endSession(event.player)
    }
}