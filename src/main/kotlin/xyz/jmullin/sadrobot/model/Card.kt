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

package xyz.jmullin.sadrobot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class Card(
    val id: UUID,
    val scryfallUri: String?,
    @JsonProperty("image_uris") val images: CardImages?,
    val oracleId: String?,
    val oracleText: String?,
    val flavorText: String?,
    val legalities: Map<String, String>?,
    val prices: Prices?,
    @JsonProperty("prints_search_uri") val printingsUrl: String?,
    @JsonProperty("rulings_uri") val rulingsUrl: String?,
    val reserved: Boolean?,
    val name: String,
    val artist: String?,
    val setName: String,
    val rarity: Rarity,
    @JsonProperty("card_faces") private val faceData: List<CardFace>?
) {
    val flattenedFaces: List<Card> get() {
        return faceData?.map { face ->
            this.copy(
                name = face.name,
                images = face.imageUris,
                artist = face.artist ?: artist,
                oracleText = face.oracleText,
                flavorText = face.flavorText
            )
        } ?: listOf(this)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CardFace(
    val imageUris: CardImages?,
    val oracleText: String?,
    val flavorText: String?,
    val artist: String?,
    val name: String
)

data class CardImages(
    val small: String,
    val normal: String,
    val large: String,
    @JsonProperty("art_crop") val art: String
)

data class Prices(
    val usd: String?,
    val usdFoil: String?
)
