package eisiadev.enceladus.fishing

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.*
import kotlin.random.Random

class FishingManager(private val plugin: FishingPlugin) {

    private val sessions = mutableMapOf<UUID, FishingSession>()

    fun hasActiveSession(player: Player): Boolean {
        return sessions.containsKey(player.uniqueId)
    }

    private fun playSoundsFromConfig(player: Player, key: String) {
        val soundConfigs = plugin.configManager.getSoundConfigList(key)
        for (config in soundConfigs) {
            val sound = config["sound"] as? String ?: continue
            val volume = (config["volume"] as? Double ?: 1.0).toFloat()
            val pitch = (config["pitch"] as? Double ?: 1.0).toFloat()

            try {
                player.playSound(
                    player.location,
                    Sound.valueOf(sound.uppercase().replace(".", "_")),
                    volume,
                    pitch
                )
            } catch (e: Exception) {
                plugin.logger.warning("Invalid sound: $sound")
            }
        }
    }

    fun startFishing(player: Player, type: FishingType, location: Location, rod: ItemStack) {
        if (hasActiveSession(player)) return

        val efficiency = extractEfficiency(rod)
        val session = FishingSession(
            player = player,
            type = type,
            location = location,
            rod = rod,
            efficiency = efficiency
        )

        sessions[player.uniqueId] = session

        if (efficiency >= 15) {
            immediateSuccess(session)
        } else {
            startCountdown(session)
        }
    }

    private fun extractEfficiency(rod: ItemStack): Int {
        val lore = rod.lore() ?: return 0
        val regex = """\[ 낚시 효율 \+(\d+) ]""".toRegex()

        for (component in lore) {
            val plain = component?.let {
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
            } ?: continue

            val match = regex.find(plain)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }

    private fun immediateSuccess(session: FishingSession) {
        completeSuccess(session)
    }

    private fun startCountdown(session: FishingSession) {
        var countdown = 3

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!sessions.containsKey(session.player.uniqueId)) {
                session.countdownTask?.cancel()
                return@Runnable
            }

            if (countdown > 0) {
                session.player.showTitle(
                    Title.title(
                        Component.empty(),
                        Component.text(countdown.toString(), NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ZERO)
                    )
                )
                playSoundFromConfig(session.player, "cursor-move")
                countdown--
            } else {
                session.countdownTask?.cancel()
                startMinigame(session)
            }
        }, 0L, 20L)

        session.countdownTask = task
    }

    private fun startMinigame(session: FishingSession) {
        playSoundFromConfig(session.player, "minigame-start")

        val barSize = 20 - session.efficiency
        session.barSize = barSize

        session.cursorPosition = Random.nextInt(barSize)
        var targetPos = Random.nextInt(barSize)
        while (targetPos == session.cursorPosition) {
            targetPos = Random.nextInt(barSize)
        }
        session.targetPosition = targetPos

        session.timeRemaining = plugin.configManager.minigameDuration

        updateMinigameDisplay(session)

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!sessions.containsKey(session.player.uniqueId)) {
                session.minigameTask?.cancel()
                return@Runnable
            }

            session.timeRemaining -= plugin.configManager.updateInterval

            if (session.timeRemaining <= 0) {
                session.minigameTask?.cancel()
                failFishing(session, plugin.configManager.getMessage("fishing-failed"))
            } else {
                updateMinigameDisplay(session)
            }
        }, 0L, (plugin.configManager.updateInterval * 20).toLong())

        session.minigameTask = task
    }

    private fun updateMinigameDisplay(session: FishingSession) {
        val subtitle = buildMinigameBar(session)
        val title = Component.text(String.format("%.1f초", session.timeRemaining), NamedTextColor.WHITE)

        session.player.showTitle(
            Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(200), Duration.ZERO)
            )
        )
    }

    private fun buildMinigameBar(session: FishingSession): Component {
        val builder = Component.text()

        for (i in 0 until session.barSize) {
            val bar = when (i) {
                session.cursorPosition -> Component.text("▌", NamedTextColor.YELLOW)
                session.targetPosition -> Component.text("▌", NamedTextColor.GREEN)
                else -> Component.text("▌", NamedTextColor.GRAY)
            }

            if (i > 0) {
                builder.append(Component.space())
            }
            builder.append(bar)
        }

        return builder.build()
    }

    fun moveCursor(player: Player, direction: Int, clickLocation: Location? = null) {
        val session = sessions[player.uniqueId] ?: return

        clickLocation?.let {
            session.lastClickLocation = it.clone()
        }

        val newPosition = session.cursorPosition + direction

        if (newPosition < 0 || newPosition >= session.barSize) {
            playSoundFromConfig(player, "cursor-blocked")
            return
        }

        session.cursorPosition = newPosition

        val soundKey = when (session.type) {
            FishingType.WATER -> "cursor-move-water"
            FishingType.LAVA -> "cursor-move-lava"
        }
        playSoundsFromConfig(player, soundKey)

        updateMinigameDisplay(session)
        spawnCursorMoveParticles(session)

        if (session.cursorPosition == session.targetPosition) {
            completeSuccess(session)
        }
    }

    private fun completeSuccess(session: FishingSession) {
        session.cleanup()
        sessions.remove(session.player.uniqueId)

        val soundKey = when (session.type) {
            FishingType.WATER -> "success-water"
            FishingType.LAVA -> "success-lava"
        }

        val soundConfigs = plugin.configManager.getSoundConfigList(soundKey)
        for (config in soundConfigs) {
            val sound = config["sound"] as? String ?: continue
            val volume = (config["volume"] as? Double ?: 1.0).toFloat()
            val pitch = (config["pitch"] as? Double ?: 1.0).toFloat()

            try {
                session.player.playSound(
                    session.player.location,
                    Sound.valueOf(sound.uppercase().replace(".", "_")),
                    volume,
                    pitch
                )
            } catch (e: Exception) {
                plugin.logger.warning("Invalid sound: $sound")
            }
        }

        session.player.clearTitle()

        // 성공 파티클 효과
        spawnSuccessParticles(session)

        val event = FishingSuccessEvent(
            player = session.player,
            location = session.location,
            rod = session.rod,
            fishingType = session.type
        )
        plugin.server.pluginManager.callEvent(event)
    }

    fun failFishing(session: FishingSession, message: String) {
        session.cleanup()
        sessions.remove(session.player.uniqueId)
        
        // 실패 파티클 효과
        spawnFailParticles(session)
        
        session.player.sendMessage(message)
    }

    fun failFishing(player: Player, message: String) {
        val session = sessions[player.uniqueId] ?: return
        failFishing(session, message)
    }

    fun endSession(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        session.cleanup()
    }

    fun clearAllSessions() {
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
    }

    private fun playSoundFromConfig(player: Player, key: String) {
        val config = plugin.configManager.getSoundConfig(key)
        val sound = config["sound"] as? String ?: return
        val volume = (config["volume"] as? Double ?: 1.0).toFloat()
        val pitch = (config["pitch"] as? Double ?: 1.0).toFloat()

        try {
            player.playSound(
                player.location,
                Sound.valueOf(sound.uppercase().replace(".", "_")),
                volume,
                pitch
            )
        } catch (e: Exception) {
            plugin.logger.warning("Invalid sound: $sound")
        }
    }

    private fun spawnSuccessParticles(session: FishingSession) {
        val world = session.lastClickLocation.world ?: return

        val fishingLoc = session.lastClickLocation.clone().add(0.5, 1.0, 0.5)
        world.spawnParticle(Particle.TOTEM, fishingLoc, 30, 0.3, 0.5, 0.3, 0.1)
        world.spawnParticle(Particle.FIREWORKS_SPARK, fishingLoc, 20, 0.3, 0.3, 0.3, 0.05)
        world.spawnParticle(Particle.END_ROD, fishingLoc, 15, 0.2, 0.4, 0.2, 0.02)
    }

    private fun spawnFailParticles(session: FishingSession) {
        val world = session.lastClickLocation.world ?: return

        val fishingLoc = session.lastClickLocation.clone().add(0.5, 1.0, 0.5)
        world.spawnParticle(Particle.SMOKE_LARGE, fishingLoc, 15, 0.3, 0.3, 0.3, 0.02)
    }

    private fun spawnCursorMoveParticles(session: FishingSession) {
        val world = session.lastClickLocation.world ?: return
        val fishingLoc = session.lastClickLocation.clone().add(0.5, 1.0, 0.5)

        when (session.type) {
            FishingType.WATER -> {
                world.spawnParticle(Particle.WATER_SPLASH, fishingLoc, 8, 0.3, 0.2, 0.3, 0.1)
                world.spawnParticle(Particle.WATER_BUBBLE, fishingLoc, 5, 0.2, 0.1, 0.2, 0.05)
            }
            FishingType.LAVA -> {
                world.spawnParticle(Particle.LAVA, fishingLoc, 1, 0.2, 0.2, 0.2, 0.0)
                world.spawnParticle(Particle.FLAME, fishingLoc, 6, 0.2, 0.2, 0.2, 0.02)
            }
        }
    }
}
