package eisiadev.enceladus.fishing

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class FishingSuccessEvent(
    val player: Player,
    val location: Location,
    val rod: ItemStack,
    val fishingType: FishingType
) : Event() {
    
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = Companion.handlers
}
