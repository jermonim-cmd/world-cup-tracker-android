#!/usr/bin/env kotlin

// Standalone test script for VividSeats scraping
// Run with: kotlinc -script test_vivid_scraper.kt

@file:Repository("https://repo1.maven.org/maven2")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.11.0")
@file:DependsOn("org.jsoup:jsoup:1.15.3")
@file:DependsOn("org.json:json:20230227")

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray

fun main() {
    println("🔍 VividSeats World Cup Scraper Test")
    println("=" * 60)

    val client = OkHttpClient()
    val url = "https://www.vividseats.com/world-cup-soccer-tickets--sports-soccer/performer/944"

    try {
        println("\n📡 Fetching $url...")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            println("❌ HTTP Error: ${response.code}")
            return
        }

        val html = response.body?.string() ?: run {
            println("❌ Empty response body")
            return
        }

        println("✓ Got HTML response (${html.length} bytes)")

        // Try JSON extraction
        println("\n📋 Attempting JSON extraction...")
        val jsonListings = extractFromJson(html)
        if (jsonListings.isNotEmpty()) {
            println("✓ JSON extraction SUCCESS: Found ${jsonListings.size} games")
            jsonListings.take(5).forEach { (name, venue, price, date) ->
                println("  - $name @ $venue on $date ($$$price)")
            }
            if (jsonListings.size > 5) {
                println("  ... and ${jsonListings.size - 5} more")
            }
            return
        }

        println("✗ JSON extraction failed or empty, trying HTML fallback...")

        // Try HTML parsing
        println("\n🔎 Attempting HTML parsing...")
        val htmlListings = parseFromHtml(html)
        println("✓ HTML parsing complete: Found ${htmlListings.size} games")
        htmlListings.take(5).forEach { (name, venue, price, date) ->
            println("  - $name @ $venue on $date ($$$price)")
        }
        if (htmlListings.size > 5) {
            println("  ... and ${htmlListings.size - 5} more")
        }

    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    }
}

data class Listing(val name: String, val venue: String, val price: Int, val date: String)

fun extractFromJson(html: String): List<Listing> {
    return try {
        val doc = Jsoup.parse(html)
        val script = doc.select("script#__NEXT_DATA__").firstOrNull()?.data() ?: run {
            println("  ! __NEXT_DATA__ script not found")
            return emptyList()
        }

        println("  ✓ Found __NEXT_DATA__ script (${script.length} bytes)")

        val events = JSONObject(script)
            .getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("initialData")
            .getJSONArray("events")

        println("  ✓ Parsed JSON structure, found ${events.length()} events")

        val results = mutableListOf<Listing>()
        var skipped = 0

        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            val venueObj = event.optJSONObject("venue")
            val venueName = venueObj?.optString("name") ?: "Unknown"
            val venueCity = venueObj?.optString("city") ?: "Unknown"

            // Basic stadium matching (simplified from actual)
            val stadiumMatch = when {
                venueName.contains("Toronto", ignoreCase = true) -> "Toronto"
                venueName.contains("Vancouver", ignoreCase = true) -> "Vancouver"
                venueName.contains("Mexico", ignoreCase = true) -> "Mexico City"
                venueCity.contains("Guadalajara", ignoreCase = true) -> "Guadalajara"
                venueCity.contains("Monterrey", ignoreCase = true) -> "Monterrey"
                venueName.contains("Atlanta", ignoreCase = true) -> "Atlanta"
                venueName.contains("Boston", ignoreCase = true) -> "Boston"
                venueName.contains("Dallas", ignoreCase = true) -> "Dallas"
                venueName.contains("Houston", ignoreCase = true) -> "Houston"
                venueName.contains("Kansas", ignoreCase = true) -> "Kansas City"
                venueName.contains("Los Angeles", ignoreCase = true) || venueName.contains("SoFi", ignoreCase = true) -> "Los Angeles"
                venueName.contains("Miami", ignoreCase = true) -> "Miami"
                venueCity.contains("East Rutherford", ignoreCase = true) || venueName.contains("MetLife", ignoreCase = true) -> "New York"
                venueName.contains("Philadelphia", ignoreCase = true) -> "Philadelphia"
                venueName.contains("San Francisco", ignoreCase = true) -> "San Francisco"
                venueName.contains("Seattle", ignoreCase = true) -> "Seattle"
                venueCity.contains("Washington", ignoreCase = true) -> "Washington D.C."
                venueName.contains("Orlando", ignoreCase = true) -> "Orlando"
                venueName.contains("Charlotte", ignoreCase = true) -> "Charlotte"
                venueName.contains("Chicago", ignoreCase = true) -> "Chicago"
                venueCity.contains("Las Vegas", ignoreCase = true) || venueName.contains("Allegiant", ignoreCase = true) -> "Las Vegas"
                venueName.contains("Phoenix", ignoreCase = true) -> "Phoenix"
                venueName.contains("Denver", ignoreCase = true) -> "Denver"
                else -> null
            }

            if (stadiumMatch == null) {
                println("    ! Could not match stadium: $venueName, $venueCity")
                skipped++
                continue
            }

            val name = event.optString("name", "World Cup Match")
                .replace(Regex("(?i)tickets$"), "").trim()

            results.add(Listing(
                name = name,
                venue = stadiumMatch,
                price = event.optInt("minPrice", 0),
                date = event.optString("date", "")
            ))
        }

        println("  ✓ JSON parsed: ${results.size} matched, $skipped skipped")
        results
    } catch (e: Exception) {
        println("  ✗ JSON extraction error: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}

fun parseFromHtml(html: String): List<Listing> {
    println("  Starting HTML parsing...")
    val doc = Jsoup.parse(html)
    val elements = doc.select("[data-testid^=production-listing]")

    println("  ✓ Found ${elements.size} HTML elements with [data-testid^=production-listing]")

    val results = mutableListOf<Listing>()
    var skipped = 0

    elements.forEach { el ->
        val text = el.text()

        // Simplified stadium detection
        val stadium = when {
            text.contains("Toronto", ignoreCase = true) -> "Toronto"
            text.contains("Vancouver", ignoreCase = true) -> "Vancouver"
            text.contains("Mexico", ignoreCase = true) -> "Mexico City"
            text.contains("Guadalajara", ignoreCase = true) -> "Guadalajara"
            text.contains("Monterrey", ignoreCase = true) -> "Monterrey"
            text.contains("Atlanta", ignoreCase = true) -> "Atlanta"
            text.contains("Boston", ignoreCase = true) || text.contains("Gillette", ignoreCase = true) -> "Boston"
            text.contains("Dallas", ignoreCase = true) -> "Dallas"
            text.contains("Houston", ignoreCase = true) -> "Houston"
            text.contains("Kansas", ignoreCase = true) -> "Kansas City"
            text.contains("Los Angeles", ignoreCase = true) || text.contains("SoFi", ignoreCase = true) -> "Los Angeles"
            text.contains("Miami", ignoreCase = true) -> "Miami"
            text.contains("MetLife", ignoreCase = true) || text.contains("New York", ignoreCase = true) -> "New York"
            text.contains("Philadelphia", ignoreCase = true) -> "Philadelphia"
            text.contains("San Francisco", ignoreCase = true) -> "San Francisco"
            text.contains("Seattle", ignoreCase = true) -> "Seattle"
            text.contains("Washington", ignoreCase = true) -> "Washington D.C."
            text.contains("Orlando", ignoreCase = true) -> "Orlando"
            text.contains("Charlotte", ignoreCase = true) -> "Charlotte"
            text.contains("Chicago", ignoreCase = true) -> "Chicago"
            text.contains("Vegas", ignoreCase = true) -> "Las Vegas"
            text.contains("Phoenix", ignoreCase = true) -> "Phoenix"
            text.contains("Denver", ignoreCase = true) -> "Denver"
            else -> null
        }

        if (stadium == null) {
            println("    ! Skipped element (no stadium match): ${text.take(60)}...")
            skipped++
            return@forEach
        }

        val price = Regex("\\$(\\d[\\d,]*)").find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        val date = Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2}", RegexOption.IGNORE_CASE).find(text)?.value ?: ""

        results.add(Listing(text.take(40), stadium, price, date))
    }

    println("  ✓ HTML parsed: ${results.size} matched, $skipped skipped")
    return results
}

operator fun String.times(count: Int) = this.repeat(count)

main()
