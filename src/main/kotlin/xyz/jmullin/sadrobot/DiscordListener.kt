/*
 * Sad Robot MTG Card Fetcher
 * Copyright (C) 2021 Justin Mullin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.jmullin.sadrobot

import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class DiscordListener(private val workspace: Workspace) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.author.isBot) {
            val channel = event.channel
            val decodedMessage = event.message.contentRaw.replace("&gt;", ">").replace("&lt;", "<")
            SadRobot.messagesToPost(decodedMessage, workspace, event.author.id, channel.type == ChannelType.PRIVATE).forEach { message ->
                if (message.attachments.isNotEmpty()) {
                    message.attachments.forEach { attachment ->
                        when(attachment) {
                            is TextAttachment -> channel.sendMessage("**${attachment.title}**\n${attachment.text}")
                            is ImageAttachment -> channel.sendMessage(MessageEmbed(null, "**${attachment.title}**", "", null, null, 0, null, null, null, null, attachment.footer?.let { MessageEmbed.Footer(it, null, null) }, MessageEmbed.ImageInfo(attachment.imageUrl, null, 0, 0), null))
                            is TableAttachment -> channel.sendMessage(attachment.fields.joinToString("\n") { field ->
                                "**${field.name}**: ${field.value}"
                            })
                            else -> null
                        }?.complete()
                    }
                } else {
                    channel.sendMessage(message.text).complete()
                }
            }
        }
    }
}
