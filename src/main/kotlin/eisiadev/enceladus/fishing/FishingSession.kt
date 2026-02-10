package eisiadev.enceladus.fishing

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

data class FishingSession(
    val player: Player,
    val type: FishingType,
    val location: Location,
    val rod: ItemStack,
    val efficiency: Int,
    var cursorPosition: Int = 0,
    var targetPosition: Int = 0,
    var barSize: Int = 0,
    var countdownTask: BukkitTask? = null,
    var minigameTask: BukkitTask? = null,
    var timeRemaining: Double = 0.0,
    var lastClickLocation: Location = location.clone()
) {
    fun cleanup() {
        countdownTask?.cancel()
        minigameTask?.cancel()
        player.clearTitle()
    }
}
