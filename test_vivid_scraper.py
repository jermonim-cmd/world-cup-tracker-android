#!/usr/bin/env python3
"""
Standalone test script for VividSeats World Cup scraping
Run with: python3 test_vivid_scraper.py
"""

import requests
import json
from bs4 import BeautifulSoup
import re
import sys

STADIUMS = [
    "Toronto", "Vancouver", "Mexico City", "Guadalajara", "Monterrey",
    "Atlanta", "Boston", "Dallas", "Houston", "Kansas City", "Los Angeles",
    "Miami", "New York", "Philadelphia", "San Francisco", "Seattle",
    "Washington", "Orlando", "Charlotte", "Chicago", "Las Vegas",
    "Phoenix", "Denver"
]

VENUES = {
    "Toronto": ["toronto", "bmo"],
    "Vancouver": ["vancouver", "bc place"],
    "Mexico City": ["mexico city", "azteca", "cdmx"],
    "Guadalajara": ["guadalajara", "akron"],
    "Monterrey": ["monterrey", "bbva"],
    "Atlanta": ["atlanta", "mercedes"],
    "Boston": ["boston", "gillette"],
    "Dallas": ["dallas", "at&t"],
    "Houston": ["houston", "nrg"],
    "Kansas City": ["kansas city", "arrowhead"],
    "Los Angeles": ["los angeles", "sofi"],
    "Miami": ["miami", "hard rock"],
    "New York": ["new york", "metlife"],
    "Philadelphia": ["philadelphia", "lincoln"],
    "San Francisco": ["san francisco", "levi"],
    "Seattle": ["seattle", "lumen"],
    "Washington": ["washington", "audi field"],
    "Orlando": ["orlando", "camping"],
    "Charlotte": ["charlotte"],
    "Chicago": ["chicago"],
    "Las Vegas": ["vegas", "allegiant"],
    "Phoenix": ["phoenix"],
    "Denver": ["denver"]
}

def match_stadium(text):
    """Match stadium name from text"""
    text_lower = text.lower()

    # Try venue names first
    for stadium, venues in VENUES.items():
        for venue in venues:
            if venue in text_lower:
                return stadium

    # Try stadium names
    for stadium in STADIUMS:
        if stadium.lower() in text_lower:
            return stadium

    return None

def extract_from_json(html):
    """Extract listings from __NEXT_DATA__ script"""
    soup = BeautifulSoup(html, 'html.parser')
    script = soup.find('script', {'id': '__NEXT_DATA__'})

    if not script:
        print("  ! __NEXT_DATA__ script not found")
        return []

    print(f"  ✓ Found __NEXT_DATA__ script ({len(script.string)} bytes)")

    try:
        data = json.loads(script.string)
        events = data['props']['pageProps']['initialData']['events']
        print(f"  ✓ Parsed JSON structure, found {len(events)} events")
    except Exception as e:
        print(f"  ✗ JSON structure error: {e}")
        return []

    results = []
    skipped = 0

    for event in events:
        venue = event.get('venue', {})
        venue_name = venue.get('name', 'Unknown')
        venue_city = venue.get('city', 'Unknown')
        venue_text = f"{venue_name} {venue_city}"

        stadium = match_stadium(venue_text)
        if not stadium:
            print(f"    ! Could not match stadium: '{venue_text}'")
            skipped += 1
            continue

        name = event.get('name', 'World Cup Match').replace('Tickets', '').strip()
        price = event.get('minPrice', 0)
        date = event.get('date', '')

        results.append({
            'name': name,
            'stadium': stadium,
            'price': price,
            'date': date
        })

    print(f"  ✓ JSON parsed: {len(results)} matched, {skipped} skipped")
    return results

def parse_from_html(html):
    """Parse listings from HTML elements"""
    soup = BeautifulSoup(html, 'html.parser')
    elements = soup.find_all(attrs={'data-testid': re.compile(r'^production-listing')})

    print(f"  ✓ Found {len(elements)} HTML elements with [data-testid^=production-listing]")

    results = []
    skipped = 0

    for el in elements:
        text = el.get_text()
        stadium = match_stadium(text)

        if not stadium:
            print(f"    ! Skipped element (no stadium match): {text[:60]}...")
            skipped += 1
            continue

        # Extract price
        price_match = re.search(r'\$(\d[\d,]*)', text)
        price = int(price_match.group(1).replace(',', '')) if price_match else 0

        # Extract date
        date_match = re.search(r'\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2}', text, re.IGNORECASE)
        date = date_match.group(0) if date_match else ''

        results.append({
            'name': text[:40],
            'stadium': stadium,
            'price': price,
            'date': date
        })

    print(f"  ✓ HTML parsed: {len(results)} matched, {skipped} skipped")
    return results

def main():
    print("🔍 VividSeats World Cup Scraper Test")
    print("=" * 60)

    url = "https://www.vividseats.com/world-cup-soccer-tickets--sports-soccer/performer/944"
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5',
        'Referer': 'https://www.vividseats.com/',
        'DNT': '1',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1'
    }

    try:
        print(f"\n📡 Fetching {url}...")
        response = requests.get(url, headers=headers, timeout=10)

        if response.status_code != 200:
            print(f"❌ HTTP Error: {response.status_code}")
            return

        html = response.text
        print(f"✓ Got HTML response ({len(html)} bytes)")

        # Try JSON extraction
        print("\n📋 Attempting JSON extraction...")
        json_listings = extract_from_json(html)

        if json_listings:
            print(f"✓ JSON extraction SUCCESS: Found {len(json_listings)} games")
            for listing in json_listings[:5]:
                print(f"  - {listing['name']} @ {listing['stadium']} on {listing['date']} (${listing['price']})")
            if len(json_listings) > 5:
                print(f"  ... and {len(json_listings) - 5} more")
            return

        # Try HTML parsing
        print("\n🔎 Attempting HTML parsing...")
        html_listings = parse_from_html(html)

        print(f"✓ HTML parsing complete: Found {len(html_listings)} games")
        for listing in html_listings[:5]:
            print(f"  - {listing['name']} @ {listing['stadium']} on {listing['date']} (${listing['price']})")
        if len(html_listings) > 5:
            print(f"  ... and {len(html_listings) - 5} more")

    except requests.exceptions.RequestException as e:
        print(f"❌ Network error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
