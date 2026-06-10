package com.worldcuptracker.core.network

import android.util.Log
import com.worldcuptracker.core.model.LivePrice
import com.worldcuptracker.core.model.StadiumKey
import com.worldcuptracker.core.model.TicketListing
import com.worldcuptracker.core.model.WorldCupTeams
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VividSeatsDataSource @Inject constructor(
    private val client: OkHttpClient,
) {
    companion object {
        private const val TAG = "VividSeatsDataSource"
        private const val URL = "https://www.vividseats.com/world-cup-soccer-tickets--sports-soccer/performer/944"
    }

    fun fetchListings(): List<TicketListing> {
        val request = Request.Builder()
            .url(URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Referer", "https://www.vividseats.com/")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Server returned error: ${response.code}")
                return emptyList()
            }

            val html = response.body?.string() ?: return emptyList()
            Log.d(TAG, "✓ Fetched HTML (${html.length} bytes)")

            // 1. Primary Strategy: Internal JSON Data (Contains all matches)
            val jsonListings = extractFromJson(html)
            if (jsonListings.isNotEmpty()) {
                Log.d(TAG, "✓ JSON extraction SUCCESS: Found ${jsonListings.size} games")
                jsonListings.forEach { listing ->
                    Log.d(TAG, "  - ${listing.match} @ ${listing.stadiumKey.displayName} ($${listing.minPrice})")
                }
                return jsonListings
            }

            Log.d(TAG, "✗ JSON extraction failed or empty, falling back to HTML parser")

            // 2. Fallback Strategy: HTML Scraper
            val htmlListings = parseFromHtml(html)
            Log.d(TAG, "✓ HTML parsing complete: Found ${htmlListings.size} games")
            htmlListings.forEach { listing ->
                Log.d(TAG, "  - ${listing.match} @ ${listing.stadiumKey.displayName} ($${listing.minPrice})")
            }
            htmlListings
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e)
            e.printStackTrace()
            emptyList()
        }
    }

    private fun extractFromJson(html: String): List<TicketListing> {
        return try {
            val doc = Jsoup.parse(html)
            val script = doc.select("script#__NEXT_DATA__").firstOrNull()?.data()
            if (script == null) {
                Log.d(TAG, "  ! __NEXT_DATA__ script not found")
                return emptyList()
            }

            Log.d(TAG, "  ✓ Found __NEXT_DATA__ script (${script.length} bytes)")

            val items = JSONObject(script)
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("initialProductionListData")
                .getJSONArray("items")

            Log.d(TAG, "  ✓ Parsed JSON structure, found ${items.length()} items")

            val results = mutableListOf<TicketListing>()
            var skipped = 0

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val venueObj = item.optJSONObject("venue")
                val venueText = "${venueObj?.optString("name")} ${venueObj?.optString("city")}"

                val stadium = StadiumKey.fromText(venueText)
                if (stadium == null) {
                    Log.d(TAG, "    ! Could not match stadium: '$venueText'")
                    skipped++
                    continue
                }

                var name = item.optString("name", "World Cup Match")
                    .replace(Regex("(?i)tickets$"), "").trim()

                if (name.contains("Maroc", ignoreCase = true)) name = name.replace("Maroc", "Morocco", ignoreCase = true)

                val webPath = item.optString("webPath", "")
                val url = if (webPath.isNotEmpty()) "https://www.vividseats.com$webPath" else ""
                val listingCount = item.optInt("listingCount", 0)

                results.add(TicketListing(
                    match = name,
                    date = formatIsoDate(item.optString("localDate")),
                    minPrice = item.optInt("minPrice", 0),
                    source = "VividSeats",
                    stadiumKey = stadium,
                    rawDate = item.optString("localDate"),
                    url = url,
                    listingCount = listingCount
                ))
            }

            Log.d(TAG, "  ✓ JSON parsed: ${results.size} matched, $skipped skipped (no stadium match)")
            results
        } catch (e: Exception) {
            Log.e(TAG, "  ✗ JSON extraction error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseFromHtml(html: String): List<TicketListing> {
        Log.d(TAG, "  Starting HTML parsing...")
        val doc = Jsoup.parse(html)
        val elements = doc.select("[data-testid^=production-listing]")

        Log.d(TAG, "  ✓ Found ${elements.size} HTML elements with [data-testid^=production-listing]")

        val results = mutableListOf<TicketListing>()
        var skipped = 0

        elements.forEach { el ->
            val text = el.text()
            val stadium = StadiumKey.fromText(text)
            if (stadium == null) {
                Log.d(TAG, "    ! Skipped element (no stadium match): ${text.take(60)}...")
                skipped++
                return@forEach
            }

            val teams = WorldCupTeams.ALL.filter { text.contains(it, ignoreCase = true) || (it == "Morocco" && text.contains("Maroc", ignoreCase = true)) }
            val matchName = if (teams.size >= 2) "${teams[0]} vs ${teams[1]}" else if (teams.size == 1) "${teams[0]} vs TBD" else "World Cup Match"

            val price = Regex("\\$(\\d[\\d,]*)").find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
            val date = Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2}", RegexOption.IGNORE_CASE).find(text)?.value ?: ""
            val rawDate = convertDisplayDateToIso(date)

            results.add(TicketListing(matchName, date, price, "VividSeats", stadium, rawDate))
        }

        Log.d(TAG, "  ✓ HTML parsed: ${results.size} matched, $skipped skipped")
        return results
    }

    private fun formatIsoDate(iso: String): String {
        return try {
            val parts = iso.split("T")[0].split("-")
            val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            "${months[parts[1].toInt()]} ${parts[2]}"
        } catch (_: Exception) { iso }
    }

    private fun convertDisplayDateToIso(displayDate: String): String {
        return try {
            val monthNames = mapOf(
                "Jan" to "01", "Feb" to "02", "Mar" to "03", "Apr" to "04",
                "May" to "05", "Jun" to "06", "Jul" to "07", "Aug" to "08",
                "Sep" to "09", "Oct" to "10", "Nov" to "11", "Dec" to "12"
            )
            val parts = displayDate.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val month = monthNames[parts[0]] ?: "01"
                val day = parts[1].padStart(2, '0')
                "2026-$month-$day"
            } else {
                ""
            }
        } catch (_: Exception) { "" }
    }

    fun fetchLivePrice(url: String): LivePrice? {
        if (url.isEmpty()) return null

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Live price fetch failed: ${response.code}")
                return null
            }

            val html = response.body?.string() ?: return null
            Log.d(TAG, "✓ Fetched live price HTML (${html.length} bytes)")

            // Extract prices from HTML listings
            val prices = extractPricesFromHtml(html)
            if (prices.isEmpty()) {
                Log.d(TAG, "✗ No prices found in live fetch")
                return null
            }

            val minPrice = prices.minOrNull() ?: 0
            val maxPrice = prices.maxOrNull() ?: 0
            val avgPrice = prices.average().toInt()
            val estimatedTotal = (minPrice * 1.15).toInt() // Add 15% fee estimate

            Log.d(TAG, "✓ Live prices: min=$minPrice, avg=$avgPrice, max=$maxPrice, count=${prices.size}")

            LivePrice(
                minPrice = minPrice,
                maxPrice = maxPrice,
                averagePrice = avgPrice,
                listingCount = prices.size,
                estimatedTotal = estimatedTotal
            )
        } catch (e: Exception) {
            Log.e(TAG, "Live price fetch error", e)
            null
        }
    }

    private fun extractPricesFromHtml(html: String): List<Int> {
        val prices = mutableListOf<Int>()
        val doc = Jsoup.parse(html)

        // Try to find prices in listing elements
        val elements = doc.select("[data-testid^=production-listing]")

        elements.forEach { el ->
            val text = el.text()
            val priceMatch = Regex("\\$(\\d[\\d,]*)").find(text)
            priceMatch?.let {
                val price = it.groupValues[1].replace(",", "").toIntOrNull()
                if (price != null && price > 0) {
                    prices.add(price)
                }
            }
        }

        // If HTML parsing fails, try JSON extraction
        if (prices.isEmpty()) {
            try {
                val script = Jsoup.parse(html).select("script#__NEXT_DATA__").firstOrNull()?.data()
                if (script != null) {
                    val items = JSONObject(script)
                        .getJSONObject("props")
                        .getJSONObject("pageProps")
                        .getJSONObject("initialProductionListData")
                        .getJSONArray("items")

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val price = item.optInt("minPrice", 0)
                        if (price > 0) {
                            prices.add(price)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "JSON extraction for live prices failed: ${e.message}")
            }
        }

        return prices
    }
}
