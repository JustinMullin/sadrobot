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

import com.ullink.slack.simpleslackapi.*
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply
import xyz.jmullin.sadrobot.SadRobot.messagesToPost
import xyz.jmullin.sadrobot.SadRobot.responseHistory

class SlackListener(private val workspace: Workspace) {
    fun startSession() {
        val session = SlackSessionFactory.createWebSocketSlackSession(workspace.botToken)
        session.connect()

        session.addMessagePostedListener { posted, slack ->
            val decodedMessage = posted.messageContent.replace("&gt;", ">").replace("&lt;", "<")
            if(posted.bot?.isBot != true && posted.user.userName != "sadrobot" && posted.user.realName != "Sad Robot") {
                messagesToPost(decodedMessage, workspace, posted.user.id, posted.channel.isDirect).forEach { message ->
                    slack.sendMessageMulti(posted.toContext, posted.channel, message.text, message.slackAttachments(), posted.threadTimestamp)
                }
            }
        }

        session.addMessageUpdatedListener { edited, slack ->
            val context = MessageContext(edited.messageTimestamp, edited.channel.id)
            responseHistory[context]?.let { responses ->
                val messages = messagesToPost(edited.newMessage, workspace, "", edited.channel.isDirect)
                (0 until responses.size).forEach { i ->
                    val message = messages.getOrNull(i)
                    val response = responses.getOrNull(i)

                    if(message == null && response != null) {
                        slack.deleteMessage(response.timestamp, edited.channel)
                    } else if(response == null && message != null) {
                        slack.sendMessageMulti(context, edited.channel, message.text, message.slackAttachments(), context.threadTimestamp)
                    } else if(response != null && message != null) {
                        slack.updateMessage(response.timestamp, edited.channel, message.text, message.slackAttachments().toTypedArray())
                    }
                }
            }
        }

        session.addMessageDeletedListener { deleted, slack ->
            responseHistory[MessageContext(deleted.messageTimestamp, deleted.channel.id)]?.let { responses ->
                responses.forEachIndexed { _, response ->
                    slack.deleteMessage(response.timestamp, deleted.channel)
                }
            }
        }

    }

    private val SlackMessagePosted.toContext get() = MessageContext(timestamp, channel.id, threadTimestamp)

    private fun SlackSession.sendMessageMulti(respondingTo: MessageContext, channel: SlackChannel, message: String, attachments: List<SlackAttachment>, threadTimestamp: String ?= null): SlackMessageHandle<SlackMessageReply> {
        return sendMessage(channel, SlackPreparedMessage.builder()
            .message(message)
            .unfurl(false)
            .threadTimestamp(threadTimestamp)
            .attachments(attachments)
            .build())
            .also { handle ->
                responseHistory
                    .getOrPut(respondingTo) { mutableListOf() }
                    .add(MessageContext(handle.reply.timestamp, channel.id))
            }
    }
}
