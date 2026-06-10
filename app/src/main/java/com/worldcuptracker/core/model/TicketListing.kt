package com.worldcuptracker.core.model

data class TicketListing(
    val match: String,
    val date: String,
    val minPrice: Int,
    val source: String,
    val stadiumKey: StadiumKey,
    val rawDate: String = "", // ISO date for sorting (e.g., "2024-06-12")
    val url: String = "", // VividSeats URL for this game
    val listingCount: Int = 0, // Number of available listings for this game
    val currency: String = "USD", // Currency code (USD or CAD)
)

enum class StadiumKey(
    val displayName: String,
    val venue: String,
    val aliases: List<String>,
) {
    TORONTO("Toronto", "BMO Field", listOf("toronto", "bmo")),
    VANCOUVER("Vancouver", "BC Place", listOf("vancouver", "bc place")),
    MEXICO_CITY("Mexico City", "Estadio Azteca", listOf("mexico city", "azteca", "cdmx", "mexico df")),
    GUADALAJARA("Guadalajara", "Estadio Akron", listOf("guadalajara", "akron", "zapopan")),
    MONTERREY("Monterrey", "Estadio BBVA", listOf("monterrey", "bbva")),
    ATLANTA("Atlanta", "Mercedes-Benz Stadium", listOf("atlanta", "mercedes", "mercedes-benz")),
    BOSTON("Boston", "Gillette Stadium", listOf("boston", "gillette", "foxborough", "foxboro")),
    DALLAS("Dallas", "AT&T Stadium", listOf("dallas", "at&t", "att", "arlington")),
    HOUSTON("Houston", "NRG Stadium", listOf("houston", "nrg")),
    KANSAS_CITY("Kansas City", "Arrowhead Stadium", listOf("kansas city", "arrowhead", "kc")),
    LOS_ANGELES("Los Angeles", "SoFi Stadium", listOf("los angeles", "sofi", "inglewood")),
    MIAMI("Miami", "Hard Rock Stadium", listOf("miami", "hard rock", "miami gardens")),
    NEW_YORK("New York", "MetLife Stadium", listOf("new york", "metlife", "east rutherford", "nj", "ny/nj")),
    PHILADELPHIA("Philadelphia", "Lincoln Financial Field", listOf("philadelphia", "lincoln financial", "philly")),
    SAN_FRANCISCO("San Francisco", "Levi's Stadium", listOf("san francisco", "levi's", "santa clara", "bay area")),
    SEATTLE("Seattle", "Lumen Field", listOf("seattle", "lumen field")),
    WASHINGTON_DC("Washington D.C.", "Audi Field", listOf("washington", "dc", "d.c.", "landover", "baltimore")),
    ORLANDO("Orlando", "Camping World Stadium", listOf("orlando", "camping world")),
    CHARLOTTE("Charlotte", "Bank of America Stadium", listOf("charlotte")),
    CHICAGO("Chicago", "Soldier Field", listOf("chicago")),
    LAS_VEGAS("Las Vegas", "Allegiant Stadium", listOf("vegas", "allegiant")),
    PHOENIX("Phoenix", "State Farm Stadium", listOf("phoenix", "glendale")),
    DENVER("Denver", "Empower Field", listOf("denver", "mile high"));

    companion object {
        fun fromText(text: String): StadiumKey? {
            val lower = text.lowercase()
            
            // Priority 1: Exact venue match
            entries.forEach { stadium ->
                val v = stadium.venue.lowercase()
                if (lower.contains(v) || lower.contains(v.replace("-", " "))) return stadium
            }

            // Priority 2: City/Alias match
            // We use word boundaries to avoid matching "York" in "Yorkshire"
            return entries.firstOrNull { stadium ->
                lower.contains(stadium.displayName.lowercase()) || 
                stadium.aliases.any { lower.contains(it.lowercase()) }
            }
        }
    }
}

object WorldCupTeams {
    val ALL = listOf(
        "Algeria", "Argentina", "Australia", "Belgium", "Brazil", "Cameroon", "Canada", 
        "Chile", "China", "Colombia", "Costa Rica", "Croatia", "Denmark", "Ecuador", 
        "Egypt", "England", "France", "Germany", "Ghana", "Greece", "Haiti", "India", "Iran", "Italy", 
        "Ivory Coast", "Japan", "Mexico", "Morocco", "Netherlands", "New Zealand", 
        "Nigeria", "Norway", "Panama", "Paraguay", "Peru", "Poland", "Portugal", 
        "Qatar", "Saudi Arabia", "Scotland", "Senegal", "Serbia", "South Africa", "South Korea", "Spain",
        "Sweden", "Switzerland", "Tunisia", "Turkey", "Ukraine", "Uruguay", "USA", 
        "Venezuela", "Vietnam"
    ).sorted()
}
