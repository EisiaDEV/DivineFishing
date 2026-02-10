package eisiadev.enceladus.fishing

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: FishingPlugin) {
    
    private val fishingRodFile = File(plugin.dataFolder, "fishing_rod.yml")
    private val fishingRodConfig: FileConfiguration = YamlConfiguration.loadConfiguration(fishingRodFile)
    
    val waterRods: List<String> = fishingRodConfig.getStringList("water")
    val lavaRods: List<String> = fishingRodConfig.getStringList("lava")
    
    val minigameDuration: Double = plugin.config.getDouble("minigame.duration", 3.0)
    val updateInterval: Double = plugin.config.getDouble("minigame.update-interval", 0.1)
    
    fun getMessage(key: String): String {
        return plugin.config.getString("messages.$key", "")?.replace("&", "§") ?: ""
    }
    
    fun getSoundConfig(key: String): Map<String, Any> {
        val section = plugin.config.getConfigurationSection("sounds.$key")
        if (section != null && !section.contains("sound")) {
            return emptyMap()
        }
        return mapOf(
            "sound" to (section?.getString("sound") ?: ""),
            "volume" to (section?.getDouble("volume") ?: 1.0),
            "pitch" to (section?.getDouble("pitch") ?: 1.0)
        )
    }
    
    fun getSoundConfigList(key: String): List<Map<String, Any>> {
        val list = plugin.config.getMapList("sounds.$key")
        @Suppress("UNCHECKED_CAST")
        return if (list.isEmpty()) {
            val single = getSoundConfig(key)
            if (single.isEmpty()) emptyList() else listOf(single)
        } else {
            list.map { it as Map<String, Any> }
        }
    }
}
