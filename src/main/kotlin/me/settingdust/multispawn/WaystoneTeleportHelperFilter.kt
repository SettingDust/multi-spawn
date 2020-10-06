package me.settingdust.multispawn

import com.google.inject.Singleton
import me.settingdust.multispawn.MultiSpawn.Companion.pluginId
import org.spongepowered.api.block.BlockState
import org.spongepowered.api.world.teleport.TeleportHelperFilter

@Singleton
@ExperimentalStdlibApi
class WaystoneTeleportHelperFilter : TeleportHelperFilter {
    companion object {
        const val waystoneBlockId = "waystones:waystone"
    }

    override fun isSafeFloorMaterial(blockState: BlockState) = blockState.type.name != waystoneBlockId

    override fun isSafeBodyMaterial(blockState: BlockState) = blockState.type.name != waystoneBlockId

    override fun getId(): String {
        return "$pluginId:waystone"
    }

    override fun getName(): String {
        return "Waystone Teleport Helper Filter"
    }
}
