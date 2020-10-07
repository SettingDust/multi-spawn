@file:Suppress("UnstableApiUsage")

package me.settingdust.multispawn.api

import me.settingdust.laven.unwrap
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.kotlin.contains
import ninja.leaping.configurate.kotlin.get
import ninja.leaping.configurate.kotlin.set
import org.spongepowered.api.Sponge
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.UUID

interface MultiSpawnService {
    /**
     * All spawns.
     */
    val spawns: ConfigurationNode

    fun all(): Sequence<Pair<String, Location<World>>>

    operator fun set(name: String, location: Location<World>) {
        spawns[name]["worldId"] = location.extent.uniqueId.toString()
        spawns[name]["x"] = location.x
        spawns[name]["y"] = location.y
        spawns[name]["z"] = location.z
    }

    operator fun contains(name: String): Boolean = name in spawns

    @ExperimentalStdlibApi
    operator fun get(name: String): Location<World>? {
        val value = spawns[name]
        value["worldId"].string
            ?.let { UUID.fromString(it) }
            ?.let { Sponge.getServer().getWorld(it).unwrap() }
            ?.let {
                if ("x" in value && "y" in value && "z" in value)
                    return Location(it, value["x"].double, value["y"].double, value["z"].double)
            }
        return null
    }

    fun remove(name: String) {
        spawns.removeChild(name)
    }

    /**
     * @return The closest point.
     */
    fun getClosest(location: Location<World>): Pair<String, Location<World>>

    /**
     * @return The closest point to the user. The first point if nothing is activated.
     */
    fun getClosest(location: Location<World>, uuid: UUID): Pair<String, Location<World>>

    fun getClosest(player: Player) = getClosest(player.location, player.uniqueId)
}
