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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spark.Spark
import spark.kotlin.ignite
import xyz.jmullin.sadrobot.model.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit

object SadRobot {
    private val logger: Logger = LoggerFactory.getLogger(SadRobot::class.java)

    private const val ScryfallBaseUrl = "https://api.scryfall.com"
    private const val SlackBaseUrl = "https://slack.com/api/"
    private const val GoogleAnalyticsBaseUrl = "https://www.google-analytics.com"

    private fun delimiterMatcher(allowSingleDelimiter: Boolean) = if (allowSingleDelimiter) "{1,2}" else "{2}"
    private fun cardPattern(allowSingleDelimiter: Boolean) = """\[${delimiterMatcher(allowSingleDelimiter)}(.{3,}?)]${delimiterMatcher(allowSingleDelimiter)}(?::(\w+))?""".toRegex()
    private fun queryPattern(allowSingleDelimiter: Boolean) = """\{${delimiterMatcher(allowSingleDelimiter)}(.{3,}?)}${delimiterMatcher(allowSingleDelimiter)}([<>]\w+)?(?::(\w+))?""".toRegex()

    private val PriceFormatter = DecimalFormat.getCurrencyInstance()!!
    private const val AuthTokensDatabase = "Tokens.sqlite"

    private val SlackClientId = System.getenv("SLACK_CLIENT_ID")
    private val SlackClientSecret = System.getenv("SLACK_CLIENT_SECRET")
    private val OAuthRedirectUrl = System.getenv("OAUTH_REDIRECT_URL")

    private const val AnalyticsVersion = "1"
    private val AnalyticsTrackingId = System.getenv("GOOGLE_ANALYTICS_TRACKING_ID")

    private val Mapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy())
        .registerModule(KotlinModule())!!

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { message ->
            logger.trace(message)
            // Message
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val request = chain.request()

            var response =
                chain.proceed(request.newBuilder().addHeader("User-Agent", "Linux").build())

            var tryCount = 0
            while (response.code in 500..599 && tryCount < 3) {
                logger.trace("Unsuccessful request to Scryfall; retrying (attempt ${tryCount + 1})...")

                tryCount++

                response = chain.proceed(request)
            }

            response
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .client(client)
        .addConverterFactory(JacksonConverterFactory.create(Mapper))

    private val scryfall = retrofit.baseUrl(ScryfallBaseUrl).build().create(ScryFallApi::class.java)!!
    private val slackAuth = retrofit.baseUrl(SlackBaseUrl).build().create(SlackOAuthApi::class.java)!!
    private val analytics = retrofit.baseUrl(GoogleAnalyticsBaseUrl).build().create(AnalyticsApi::class.java)!!

    val responseHistory = linkedMapOf<MessageContext, MutableList<MessageContext>>().withDefault { mutableListOf() }

    @JvmStatic
    fun main(args: Array<String>) {
        Class.forName("org.sqlite.JDBC")

        Spark.exception(Exception::class.java) { exception, _, _ ->
            exception.printStackTrace()
        }

        val http = ignite()
        http.notFound {
            status(404)
            ""
        }
        http.get("/auth", "application/json") {
            val code = queryMap()["code"].value()

            if (code == null) {
                status(404)
                ""
            } else {
                val response = slackAuth.oauth(SlackClientId, SlackClientSecret, OAuthRedirectUrl, code).execute()
                val authentication = response.body()!!
                withDatabaseConnection { connection ->
                    val statement = connection.prepareStatement("insert into authenticated_workspaces (enabled, workspaceType, token, botToken, botUserId, name, workspaceId, allowSingleDelimiter) values (?,?,?,?,?,?,?,?);")
                    statement.setBoolean(1, true)
                    statement.setString(2, "Slack")
                    statement.setString(3, authentication.accessToken)
                    statement.setString(4, authentication.bot.botAccessToken)
                    statement.setString(5, authentication.bot.botUserId)
                    statement.setString(6, authentication.teamName)
                    statement.setString(7, authentication.teamId)
                    statement.setBoolean(8, false)
                    statement.execute()
                }
                SessionManager.startupWorkspace(Workspace("Slack", authentication.teamId, authentication.teamName, authentication.bot.botAccessToken, false))
                "Successfully added Sad Robot to the ${authentication.teamName} workspace!"
            }
        }

        withDatabaseConnection { connection ->
            val statement = connection.prepareStatement("select enabled, botToken, workspaceId, name, workspaceType, allowSingleDelimiter from authenticated_workspaces;")
            val result = statement.executeQuery()

            while (result.next()) {
                val enabled = result.getBoolean(1)
                val token = result.getString(2)
                val id = result.getString(3)
                val name = result.getString(4)
                val workspaceType = result.getString(5)
                val allowSingleDelimiter = result.getBoolean(6)

                if (enabled) {
                    SessionManager.startupWorkspace(Workspace(workspaceType, id, name, token, allowSingleDelimiter))
                }
            }
        }
    }

    private const val ImageFormat = "image"
    private const val OracleFormat = "oracle"
    private const val ReservedListFormat = "reserved"
    private const val PriceFormat = "price"
    private const val ArtFormat = "art"
    private const val FlavorFormat = "flavor"
    private const val FormatsFormat = "legality"

    private val formatLabels = mapOf(
        "standard" to "Standard",
        "brawl" to "Brawl",
        "pioneer" to "Pioneer",
        "historic" to "Historic",
        "modern" to "Modern",
        "pauper" to "Pauper",
        "legacy" to "Legacy",
        "penny" to "Penny",
        "vintage" to "Vintage",
        "commander" to "Commander"
    )

    private const val helpMessage = """To use Sad Robot, first invite the app to any channels where you'd like to be able to fetch card information. You can also fetch cards privately from the comfort of this direct message. To fetch a card, simply post a message like *[[Card Name]]*, with square brackets, to fetch by name. You can also use full <https://scryfall.com/docs/syntax|Scryfall search syntax> to find cards by criteria by using curly braces, as in *{{t:planeswalker set:WAR ci=ubr}}*.

By default, cards will be returned as full card images. If you want to change what information to display for your result, you can add a *:command* to the end, as in *[[Lightning Bolt]]:art* to fetch the cropped art only. The full list of supported commands is as follows:

  • *:art*  Image of the cropped art for this card.
  • *:oracle*  The oracle text for this card.
  • *:price*  Current price for this card, summarized across all relevant printings (currently paper prices only).
  • *:reserved*  This card's status on the reserved list (either reserved or not reserved).
  • *:flavor*  This card's flavor text.
  • *:legality*  Display a table of formats this card is legal or banned in.

*[[Card Name]]* style fetches must resolve to a single card; if your query matches multiple cards, you will need to clarify your request. *{{Scryfall Query}}* style requests will return the first hit from Scryfall, by default using EDHREC rating sort.

If you wish to change how results are sorted for query requests, you can append a sort criteria to your query, as in {{Lightning Bolt}}>usd to fetch the most expensive printing of Lightning Bolt. Valid sort keys are defined by Scryfall; at present they are artist, cmc, power, toughness, set, name, usd, tix, eur, rarity, color, released, spoiled, and edhrec (default). Using a greater than sign *&gt;* will sort ascending, while *<* will sort descending. If you are combining a sort and a *:command*, the sort must come before the command, as in *{{Lightning Bolt}}<released:flavor* to find the flavor text for the oldest printing of Llanowar Elves."""

    private fun event(workspace: Workspace, clientId: String, category: String, action: String? = null) {
        if (AnalyticsTrackingId != null) {
            analytics.event(AnalyticsVersion, AnalyticsTrackingId, clientId, category, action, "${workspace.name} (${workspace.id})").execute()
        }
    }

    fun messagesToPost(message: String, workspace: Workspace, userId: String, isPrivateChat: Boolean): List<Message> {
        if (isPrivateChat && message.trim().lowercase(Locale.getDefault()) == "help") {
            event(workspace, userId, "help")
            return listOf(Message(helpMessage))
        }

        val fuzzyMatches = cardPattern(workspace.allowSingleDelimiter).findAll(message)
        val queryMatches = queryPattern(workspace.allowSingleDelimiter).findAll(message)

        try {
            val nameResults = fuzzyMatches.map { match ->
                val query = match.groupValues[1]
                val format = match.groupValues.getOrNull(2)

                event(workspace, userId, "byName", match.groupValues[0])

                try {
                    listOf(query)
                        .map(::formatCardRequest)
                        .map { byName(it) }
                        .map { card ->
                            card?.let { respondWithCard(format, null, it) }
                                ?: Message("I'm sorry. I couldn't find any results for `$query`.")
                        }
                } catch (e: TooAmbiguous) {
                    listOf(Message("Multiple cards match `$query`. Can you be more specific?"))
                } catch (e: NoCardFound) {
                    listOf(Message("I'm sorry. I couldn't find any cards named `$query`."))
                }
            }
            val queryResults = queryMatches.map { match ->
                val query = match.groupValues[1]
                val sort = match.groupValues.getOrNull(2)
                val format = match.groupValues.getOrNull(3)

                event(workspace, userId, "bySearch", match.groupValues[0])

                val dir = when(sort?.firstOrNull()) {
                    '<' -> "asc"
                    '>' -> "desc"
                    else -> null
                }
                val order = sort?.drop(1)?.ifEmpty { null } ?: "edhrec"

                listOf(query)
                    .map(::formatCardRequest)
                    .map { search(it, dir, order) }
                    .map { cards ->
                        cards?.let { card ->
                            card.data.firstOrNull()?.let {
                                respondWithCard(format, query, it)
                            } ?: Message("I'm sorry. I couldn't find any results for `$query`.")
                        } ?: Message("I'm sorry. I couldn't find any results for `$query`.")
                    }
            }
            return (nameResults.flatten() + queryResults.flatten()).toList()
        } catch (e: Exception) {
            logger.error("Failed to process message.", e)
            return emptyList()
        }
    }

    private fun respondWithCard(format: String?, query: String?, card: Card): Message? {
        return when (format?.lowercase(Locale.getDefault())) {
            null, "", ImageFormat -> respondWithImage(card)
            OracleFormat -> respondWithOracle(card)
            ReservedListFormat -> respondWithReserved(card)
            PriceFormat -> respondWithPrice(query, card)
            ArtFormat -> respondWithArt(card)
            FlavorFormat -> respondWithFlavor(card)
            FormatsFormat -> respondWithFormats(card)
            else -> null
        }
    }

    private fun respondWithImage(card: Card): Message {
        val images = card.flattenedFaces.mapNotNull { it.images?.normal?.let { image -> it to image } }.takeIf { it.isNotEmpty() } ?: listOfNotNull(card.images?.normal?.let { image -> card to image })
        return Message("",
            images.map { (face, image) ->
                ImageAttachment(face.name, image)
            }
        )
    }

    private fun respondWithOracle(card: Card): Message {
        val oracleTexts = card.flattenedFaces.mapNotNull { it.oracleText?.let { oracle -> it to oracle } }
        return Message("",
            oracleTexts.map { (face, oracle) ->
                TextAttachment(face.name, oracle)
            })
    }

    private fun respondWithReserved(card: Card): Message {
        return Message(if(card.reserved == true) {
            "${card.name} is *on the reserved list*."
        } else {
            "${card.name} is *not* on the reserved list."
        })
    }

    private fun respondWithPrice(query: String?, card: Card): Message {
        val searchResult = query?.let(::searchAllPrintings)

        val initialPrintings = if(searchResult?.data?.size == 1) {
            searchResult.data
        } else {
            allPrintings(card)
        }

        return initialPrintings?.let { printings ->
            val attachments = mutableListOf<TableAttachment>()

            val nonFoils = printings.mapNotNull { printing ->
                printing.prices?.usd?.toDoubleOrNull()?.let { printing.setName to it }
            }

            val foils = printings.mapNotNull { printing ->
                printing.prices?.usdFoil?.toDoubleOrNull()?.let { printing.setName to it }
            }

            if (nonFoils.isNotEmpty()) {
                val cheapest = nonFoils.minByOrNull { it.second }!!
                val priciest = nonFoils.maxByOrNull { it.second }!!
                val attachment = TableAttachment()
                if (nonFoils.size > 1) {
                    listOf("Cheapest Printing" to cheapest, "Priciest Printing" to priciest)
                } else {
                    listOf("Price" to cheapest)
                }.forEach { (tag, data) ->
                    val (setName, price) = data
                    attachment.addField(tag, "*${PriceFormatter.format(price)}* - _${setName}_")
                }
                attachments += listOf(attachment)
            }

            if (foils.isNotEmpty()) {
                val cheapest = foils.minByOrNull { it.second }!!
                val priciest = foils.maxByOrNull { it.second }!!
                val attachment = TableAttachment()
                if (foils.size > 1) {
                    listOf("Cheapest Printing (foil)" to cheapest, "Priciest Printing (foil)" to priciest)
                } else {
                    listOf("Price (foil)" to cheapest)
                }.forEach { (tag, data) ->
                    val (setName, price) = data
                    attachment.addField(tag, "*${PriceFormatter.format(price)}* - _${setName}_")
                }
                attachments += listOf(attachment)
            }

            if (nonFoils.isEmpty() && foils.isEmpty()) {
                Message("I'm sorry, Scryfall doesn't appear to have any price information available for ${card.name}.")
            } else {
                Message("", attachments)
            }
        } ?: (card.prices?.usd?.toDoubleOrNull()?.let { price ->
            Message("", TextAttachment(card.name, PriceFormatter.format(price)))
        } ?: Message("I'm sorry, Scryfall doesn't appear to have any price information available for ${card.name}."))
    }

    private fun respondWithArt(card: Card): Message {
        val arts = card.flattenedFaces.mapNotNull { it.images?.let { images -> it to images.art } }
        return Message("",
            arts.map { (face, image) ->
                ImageAttachment(face.name, image).apply {
                    face.artist?.let { footer = "art by $it" }
                }
            })
    }

    private fun respondWithFlavor(card: Card): Message {
        val flavors = card.flattenedFaces.mapNotNull { it.flavorText?.let { flavor -> it to flavor } }
        return if (flavors.isEmpty()) {
            Message("Sorry, ${card.name} doesn't have any flavor text.")
        } else {
            Message("",
                flavors.map { (face, flavor) ->
                    TextAttachment(face.name, flavor)
                })
        }
    }

    private fun respondWithFormats(card: Card): Message {
        return if (card.legalities == null || card.legalities.isEmpty()) {
            Message("Sorry, ${card.name} doesn't have any format legality information available.")
        } else {
            val attachment = TableAttachment()
            formatLabels.forEach { (format, label) ->
                card.legalities[format]?.let { legal ->
                    val legalLabel = when (legal) {
                        "legal" -> "Legal"
                        else -> "Not Legal"
                    }
                    attachment.addField(label, legalLabel)
                }
            }
            Message("", attachment)
        }
    }

    private fun formatCardRequest(request: String) = request.trim()

    private fun byName(name: String): Card? {
        logger.trace("Fuzzy-finding from ScryFall: $name")
        return scryfall.cardByName(name).go().body()
    }

    private fun search(query: String, dir: String?, order: String?): CardSearchResults? {
        logger.trace("Querying ScryFall: $query, dir: $dir, order: $order")
        return try {
            scryfall.cardsBySearch(query, dir, order).go().body()
        } catch (e: Exception) {
            logger.error("Failed to fetch cards via search query: $query / dir: $dir / order: $order.", e)
            null
        }
    }

    private fun searchAllPrintings(query: String): CardSearchResults? {
        logger.trace("Querying ScryFall: $query")
        return try {
            scryfall.cardsBySearchAllPrints(query).go().body()
        } catch (e: Exception) {
            logger.error("Failed to fetch all printings for query $query.", e)
            null
        }
    }

    private fun allPrintings(card: Card): List<Card>? {
        return card.printingsUrl?.let { url ->
            scryfall.fetchPrintings(url).go().body()?.data
        }
    }

    private fun <T> Call<T>.go(): retrofit2.Response<T> {
        val result = execute()
        if(result.isSuccessful) {
            return result
        }
        val errorBody = result.errorBody()
        val parsed = try {
            Mapper.readValue(errorBody?.string(), SearchError::class.java)
        } catch (e: Exception) {
            logger.error("Error parsing response body from Scryfall.", e)
            throw e
        }

        parsed.let { (code, type) ->
            if(code == "not_found" && type == "ambiguous") {
                throw TooAmbiguous
            } else throw NoCardFound
        }
    }

    private fun <T> withDatabaseConnection(f: (Connection) -> T): T {
        val connection = DriverManager.getConnection("jdbc:sqlite:$AuthTokensDatabase")
        try {
            connection.autoCommit = false
            val result = f(connection)
            connection.commit()
            return result
        } catch (e: Exception) {
            logger.error("Exception when interacting with database.", e)
            e.printStackTrace()
            try {
                logger.error("Attempting to rollback database transaction.", e)
                connection.rollback()
            } catch (e: SQLException) {
                logger.error("Failed to rollback database transaction.", e)
                e.printStackTrace()
            }
            throw e
        } finally {
            connection.close()
        }
    }
}
