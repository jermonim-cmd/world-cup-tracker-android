package com.worldcuptracker.core.network

import android.util.Log
import com.worldcuptracker.core.model.CurrencyConverter
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
        // Vivid Seats prices are in the currency of where you're accessing from:
        // - Canadian stadiums (Toronto, Vancouver): Prices already in CAD
        // - US stadiums: Prices in USD, need to convert to CAD (1 USD = 1.36 CAD)
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

                val priceFromApi = item.optInt("minPrice", 0)
                // Toronto and Vancouver prices are already in CAD from Vivid Seats
                // US stadium prices are in USD and need conversion
                val priceCad = if (isCanadianStadium(stadium)) {
                    priceFromApi // Already in CAD
                } else {
                    CurrencyConverter.usdToCad(priceFromApi) // Convert USD to CAD
                }

                results.add(TicketListing(
                    match = name,
                    date = formatIsoDate(item.optString("localDate")),
                    minPrice = priceCad,
                    source = "VividSeats",
                    stadiumKey = stadium,
                    rawDate = item.optString("localDate"),
                    url = url,
                    listingCount = listingCount,
                    currency = "CAD"
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

    fun fetchLivePrice(url: String, stadium: StadiumKey? = null): LivePrice? {
        if (url.isEmpty()) {
            Log.e(TAG, "Live price fetch: Empty URL provided")
            return null
        }

        Log.d(TAG, "Fetching live price from: $url (stadium: ${stadium?.displayName})")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Connection", "keep-alive")
            .build()

        return try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "Live price response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "Live price fetch failed: ${response.code}")
                return null
            }

            val html = response.body?.string()
            if (html == null) {
                Log.e(TAG, "Live price fetch: Empty response body")
                return null
            }

            Log.d(TAG, "✓ Fetched live price HTML (${html.length} bytes)")
            if (html.length < 100) {
                Log.e(TAG, "⚠️ HTML response suspiciously small: ${html.take(200)}")
            }

            // Try multiple strategies to extract prices
            val prices = extractPricesFromJson(html)
                ?: extractPricesFromHtml(html)
                ?: emptyList()

            if (prices.isEmpty()) {
                Log.d(TAG, "✗ No prices found in live fetch from both JSON and HTML")
                return null
            }

            Log.d(TAG, "Extracted prices (count=${prices.size}): $prices")

            val minPriceRaw = prices.minOrNull() ?: 0
            val maxPriceRaw = prices.maxOrNull() ?: 0
            val avgPriceRaw = prices.average().toInt()

            Log.d(TAG, "Price stats before conversion: min=$minPriceRaw, max=$maxPriceRaw, avg=$avgPriceRaw")

            // Canadian stadiums: prices already in CAD, don't convert
            // US stadiums: prices in USD, convert to CAD
            val isCanadian = isCanadianStadium(stadium)
            val minPrice = if (isCanadian) minPriceRaw else CurrencyConverter.usdToCad(minPriceRaw)
            val maxPrice = if (isCanadian) maxPriceRaw else CurrencyConverter.usdToCad(maxPriceRaw)
            val avgPrice = if (isCanadian) avgPriceRaw else CurrencyConverter.usdToCad(avgPriceRaw)

            Log.d(TAG, "Live prices (${if (isCanadian) "CAD - no conversion" else "USD converted"}): min=$minPrice, avg=$avgPrice, max=$maxPrice, isCanadian=$isCanadian")

            // Try to extract actual fees from the page (in USD, then convert to CAD)
            val feesUsd = extractActualFees(html, minPriceRaw)
            val serviceFee = CurrencyConverter.usdToCad(feesUsd.serviceFee)
            val facilityFee = CurrencyConverter.usdToCad(feesUsd.facilityFee)
            val tax = CurrencyConverter.usdToCad(feesUsd.tax)

            Log.d(TAG, "✓ Live prices (CAD): min=$minPrice, avg=$avgPrice, max=$maxPrice, count=${prices.size}")
            Log.d(TAG, "  Fees (CAD): service=$serviceFee, facility=$facilityFee, tax=$tax, hasActual=${feesUsd.hasActualFees}")

            val estimatedTotal = minPrice + serviceFee + facilityFee + tax

            LivePrice(
                minPrice = minPrice,
                maxPrice = maxPrice,
                averagePrice = avgPrice,
                listingCount = prices.size,
                estimatedTotal = estimatedTotal,
                serviceFeeAmount = serviceFee,
                facilityFeeAmount = facilityFee,
                taxAmount = tax,
                hasActualFees = feesUsd.hasActualFees,
                originalCurrency = "USD"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Live price fetch error: ${e.message}", e)
            null
        }
    }

    private fun extractPricesFromJson(html: String): List<Int>? {
        return try {
            val doc = Jsoup.parse(html)

            // Look for __NEXT_DATA__ script
            var script = doc.select("script#__NEXT_DATA__").firstOrNull()?.data()

            if (script == null) {
                Log.d(TAG, "  __NEXT_DATA__ not found, searching for alternative scripts...")
                // Try alternative patterns
                script = doc.select("script").find {
                    val content = it.data()
                    content.contains("initialProductionListData") ||
                    content.contains("minPrice") ||
                    content.contains("Event data")
                }?.data()
            }

            if (script == null) {
                Log.d(TAG, "  No JSON data scripts found")
                return null
            }

            Log.d(TAG, "  Attempting JSON extraction (${script.length} bytes)")

            val prices = mutableListOf<Int>()

            // Try the exact path from the current structure
            try {
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
            } catch (e: Exception) {
                Log.d(TAG, "  JSON path structure different, trying alternative...")
                // Try alternative structure
                return null
            }

            if (prices.isNotEmpty()) {
                Log.d(TAG, "  ✓ JSON extraction found ${prices.size} prices")
                prices
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "  JSON extraction failed: ${e.message}")
            null
        }
    }

    private fun extractActualFees(html: String, basePrice: Int): FeeBreakdown {
        return try {
            val doc = Jsoup.parse(html)

            // Try to find fee information in various possible locations
            var serviceFee = 0
            var facilityFee = 0
            var taxAmount = 0

            // Look for fee text patterns in the page
            val pageText = doc.text()

            // Try to extract service fees (usually 15-20% of ticket price)
            val serviceFeePattern = Regex("""Service Fee[:\s]+\$(\d+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
            serviceFeePattern.find(pageText)?.let {
                serviceFee = it.groupValues[1].toDoubleOrNull()?.toInt() ?: 0
            }

            // Try to extract facility fees
            val facilityFeePattern = Regex("""Facility Charge[:\s]+\$(\d+(?:\.\d{2})?)|Facility Fee[:\s]+\$(\d+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
            facilityFeePattern.find(pageText)?.let {
                facilityFee = (it.groupValues[1].ifEmpty { it.groupValues[2] }).toDoubleOrNull()?.toInt() ?: 0
            }

            // Try to extract tax/sales tax
            val taxPattern = Regex("""(?:Sales\s+)?Tax[:\s]+\$(\d+(?:\.\d{2})?)|Taxes[:\s]+\$(\d+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
            taxPattern.find(pageText)?.let {
                taxAmount = (it.groupValues[1].ifEmpty { it.groupValues[2] }).toDoubleOrNull()?.toInt() ?: 0
            }

            // If we found actual fees, return them
            if (serviceFee > 0 || facilityFee > 0 || taxAmount > 0) {
                Log.d(TAG, "  ✓ Extracted actual fees from page")
                return FeeBreakdown(serviceFee, facilityFee, taxAmount, hasActualFees = true)
            }

            // Try JSON extraction for fees
            try {
                val script = doc.select("script#__NEXT_DATA__").firstOrNull()?.data()
                    ?: doc.select("script").filter { it.data().contains("serviceFee") }.firstOrNull()?.data()

                if (script != null) {
                    val json = JSONObject(script)
                    // Look for fee information in various JSON paths
                    val items = json.getJSONObject("props")
                        .getJSONObject("pageProps")
                        .getJSONObject("initialProductionListData")
                        .getJSONArray("items")

                    if (items.length() > 0) {
                        val firstItem = items.getJSONObject(0)
                        serviceFee = firstItem.optInt("serviceFee", 0)
                        facilityFee = firstItem.optInt("facilityFee", 0)
                        taxAmount = firstItem.optInt("tax", 0)

                        if (serviceFee > 0 || facilityFee > 0 || taxAmount > 0) {
                            Log.d(TAG, "  ✓ Extracted fees from JSON data")
                            return FeeBreakdown(serviceFee, facilityFee, taxAmount, hasActualFees = true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "  JSON fee extraction failed: ${e.message}")
            }

            // Fallback: estimate based on typical Vivid Seats fee structure
            // Service fee is typically 15-20% of ticket price
            serviceFee = (basePrice * 0.18).toInt() // 18% service fee estimate
            facilityFee = 10 // Typical facility fee
            taxAmount = (basePrice * 0.10).toInt() // 10% tax estimate (varies by location)

            Log.d(TAG, "  Using estimated fees (15-20% service + facility + tax)")
            FeeBreakdown(serviceFee, facilityFee, taxAmount, hasActualFees = false)
        } catch (e: Exception) {
            Log.d(TAG, "Fee extraction error: ${e.message}")
            // Safe defaults
            val estimatedService = (basePrice * 0.18).toInt()
            FeeBreakdown(estimatedService, 10, 0, hasActualFees = false)
        }
    }

    private data class FeeBreakdown(
        val serviceFee: Int,
        val facilityFee: Int,
        val tax: Int,
        val hasActualFees: Boolean = false,
    )

    private fun isCanadianStadium(stadium: StadiumKey?): Boolean {
        return stadium == StadiumKey.TORONTO || stadium == StadiumKey.VANCOUVER
    }

    private fun extractPricesFromHtml(html: String): List<Int>? {
        return try {
            val prices = mutableListOf<Int>()
            val doc = Jsoup.parse(html)

            Log.d(TAG, "  Attempting HTML extraction")

            // Try multiple selectors for price elements
            val selectors = listOf(
                "[data-testid^=production-listing]",
                "[data-testid*=listing]",
                "[class*='listing']",
                "[class*='price']",
                "[class*='Price']",
                "div[role='button']",
                "span[class*='price']",
                "div[class*='amount']"
            )

            for (selector in selectors) {
                val elements = doc.select(selector)
                if (elements.isEmpty()) continue

                Log.d(TAG, "    Found ${elements.size} elements with selector: $selector")

                elements.forEach { el ->
                    val text = el.text()
                    if (text.contains("$")) {
                        // Look for dollar amounts
                        val priceMatches = Regex("""\$(\d{1,5}(?:,\d{3})*(?:\.\d{2})?)""").findAll(text)
                        priceMatches.forEach { match ->
                            val priceStr = match.groupValues[1].replace(",", "")
                            val price = priceStr.toDoubleOrNull()?.toInt()
                            if (price != null && price > 0 && price < 10000) {
                                prices.add(price)
                            }
                        }
                    }
                }

                if (prices.isNotEmpty()) {
                    Log.d(TAG, "    ✓ Found prices with selector: $selector")
                    break
                }
            }

            if (prices.isNotEmpty()) {
                Log.d(TAG, "  ✓ HTML extraction found ${prices.size} unique prices")
                prices.distinct()
            } else {
                Log.d(TAG, "  ✗ HTML extraction found no prices")
                // Log a sample of the HTML for debugging
                Log.d(TAG, "  HTML sample: ${html.take(500).replace("\n", " ")}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "  HTML extraction error: ${e.message}", e)
            null
        }
    }
}
