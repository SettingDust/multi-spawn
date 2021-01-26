package me.settingdust.multispawn.locale

import com.google.inject.ImplementedBy
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.serializer.TextSerializers
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@ExperimentalStdlibApi
@ImplementedBy(LocaleServiceImpl::class)
interface LocaleService {
    companion object {
        private const val keyIsMissing = "missingKey"
        private const val keyIsMissingString = "[Missing language key: {{path}}]"
        private val ketIsMissingText: Text = Text.of(keyIsMissingString)
    }

    operator fun get(
        path: String,
        source: CommandSource = Sponge.getServer().console,
        vararg tokens: Pair<String, (CommandSource) -> Text?>
    ): Text?

    fun getString(
        path: String,
        source: CommandSource = Sponge.getServer().console,
        vararg tokens: Pair<String, (CommandSource) -> Text?>
    ): String? =
        get(path, source, *tokens)?.let { TextSerializers.FORMATTING_CODE.serialize(it) }

    fun getTextUnsafe(
        path: String,
        source: CommandSource = Sponge.getServer().console,
        vararg tokens: Pair<String, (CommandSource) -> Text?>
    ): Text =
        get(path, source, *tokens) ?: get(keyIsMissing, source, *tokens) ?: ketIsMissingText

    fun getStringUnsafe(
        path: String,
        source: CommandSource = Sponge.getServer().console,
        vararg tokens: Pair<String, (CommandSource) -> Text?>
    ): String =
        getString(path, source, *tokens) ?: getString(keyIsMissing, source, *tokens)
            ?: keyIsMissingString
}
