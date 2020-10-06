package me.settingdust.multispawn.network

import com.flowpowered.math.vector.Vector3d
import me.settingdust.laven.sponge.readVector3d
import me.settingdust.laven.sponge.writeVector3d
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.network.Message

data class MessageEditWaystone(
    var pos: Vector3d,
    var name: String,
    var isGlobal: Boolean,
    var fromSelectionGui: Boolean
) : Message {
    override fun readFrom(buf: ChannelBuf) {
        pos = buf.readVector3d()
        name = buf.readUTF()
        isGlobal = buf.readBoolean()
        fromSelectionGui = buf.readBoolean()
    }

    override fun writeTo(buf: ChannelBuf) {
        buf.writeVector3d(pos)
        buf.writeUTF(name)
        buf.writeBoolean(isGlobal)
        buf.writeBoolean(fromSelectionGui)
    }
}
