package eisiadev.enceladus.fishing

import org.bukkit.plugin.java.JavaPlugin

class FishingPlugin : JavaPlugin() {
    
    lateinit var configManager: ConfigManager
        private set
    
    lateinit var fishingManager: FishingManager
        private set
    
    override fun onEnable() {
        saveDefaultConfig()
        saveResource("fishing_rod.yml", false)
        
        configManager = ConfigManager(this)
        fishingManager = FishingManager(this)
        
        server.pluginManager.registerEvents(FishingListener(this), this)
        
        logger.info("Enceladus Fishing Plugin has been enabled!")
    }
    
    override fun onDisable() {
        fishingManager.clearAllSessions()
        logger.info("Enceladus Fishing Plugin has been disabled!")
    }
}
