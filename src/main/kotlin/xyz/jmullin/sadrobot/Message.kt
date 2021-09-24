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

import com.ullink.slack.simpleslackapi.SlackAttachment

data class Message(val text: String, val attachments: List<Attachment> = emptyList()) {
    constructor(text: String, attachment: Attachment) : this(text, listOf(attachment))

    fun slackAttachments(): List<SlackAttachment> {
        return attachments.mapNotNull { when(it) {
            is TextAttachment -> SlackAttachment(it.title, null, it.text, null)
            is ImageAttachment -> SlackAttachment(it.title, null, "", null).apply {
                addMiscField("image_url", it.imageUrl)
                footer = it.footer
            }
            is TableAttachment -> SlackAttachment().let { attachment ->
                it.fields.forEach { field ->
                    attachment.addField(field.name, field.value, true)
                }
                attachment
            }
            else -> null
        } }
    }
}
