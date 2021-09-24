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

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import xyz.jmullin.sadrobot.model.AutocompleteResults
import xyz.jmullin.sadrobot.model.Card
import xyz.jmullin.sadrobot.model.CardSearchResults
import xyz.jmullin.sadrobot.model.RulingsResults

interface ScryFallApi {
    @GET("cards/named")
    fun cardByName(@Query("fuzzy") name: String, @Query("order") order: String = "edhrec"): Call<Card>

    @GET("cards/autocomplete")
    fun cardsByAutoComplete(@Query("q") query: String, @Query("order") order: String = "edhrec"): Call<AutocompleteResults>

    @GET("cards/search")
    fun cardsBySearch(@Query("q") query: String, @Query("dir") dir: String? = null, @Query("order") order: String? = "edhrec", @Query("unique") unique: String? = if(order != "edhrec") "prints" else null): Call<CardSearchResults>

    @GET("cards/search")
    fun cardsBySearchAllPrints(@Query("q") query: String, @Query("order") order: String = "released", @Query("unique") unique: String = "prints"): Call<CardSearchResults>

    @GET fun fetchPrintings(@Url url: String): Call<CardSearchResults>
    @GET fun fetchRulings(@Url url: String): Call<RulingsResults>
}
