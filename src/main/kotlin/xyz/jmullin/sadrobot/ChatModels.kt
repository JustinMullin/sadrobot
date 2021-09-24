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

interface Attachment
data class ImageAttachment(val title: String, val imageUrl: String) : Attachment {
    var footer: String? = null
}
data class TextAttachment(val title: String, val text: String) : Attachment
data class TableField(val name: String, val value: String)
class TableAttachment : Attachment {
    val fields = mutableListOf<TableField>()

    fun addField(label: String, value: String) {
        fields.add(TableField(label, value))
    }
}
