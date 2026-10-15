"""
Synthetic, labeled transaction descriptions for training and evaluating the category model.

There is no real user data yet, so descriptions are generated the way banks actually print them:
processor prefixes ("SQ *", "TST*"), "PURCHASE AUTHORIZED ON 10/21 ... CARD 1234", store numbers,
city/state suffixes. On top of that come the messy cases that break whole-word keyword rules:
words glued together ("DOMINOSPIZZA"), descriptions cut off at about 22 characters (card networks
truncate), and dropped vowels ("WALGRNS").

Merchants are split, not rows: every description of a merchant is either in train or in test. So
the test measures what matters in the app, guessing merchants the model has never seen.
Everything is seeded, so the same data comes out every run.
"""

from __future__ import annotations

import random
from dataclasses import dataclass

# The spending categories the model predicts (default category names, see DefaultCategories.java).
# Income and Payments & Transfers are left to rules and the money-in direction; "Other" isn't a
# category anyone wants predicted.
CATEGORIES = [
    "Food & Dining",
    "Groceries",
    "Shopping",
    "Transportation",
    "Travel",
    "Entertainment",
    "Subscriptions",
    "Bills & Utilities",
    "Education",
    "Health & Personal Care",
    "Fees & Interest",
]

# Merchant names per category: well-known brands plus invented local businesses. Some contain a
# dictionary word (PIZZA, PHARMACY), many don't, which is the realistic mix.
MERCHANTS: dict[str, list[str]] = {
    "Food & Dining": [
        "Chick-fil-A", "Domino's Pizza", "McDonald's", "Chipotle Mexican Grill", "Starbucks", "Dunkin",
        "Panera Bread", "Subway", "Taco Bell", "Wendy's", "Five Guys", "Cook Out", "Bojangles",
        "Raising Cane's", "Jersey Mike's", "Insomnia Cookies", "Sweetgreen", "Cava", "Waffle House",
        "Golden Dragon", "Lotus Garden", "Sakura Hibachi", "Mama Rosa's", "Top of the Hill",
        "Linda's Bar", "Sup Dogs", "Med Deli", "Time-Out", "Al's Burger Shack", "Lantern",
        "Mediterranean Bites", "Pho Saigon", "Seoul Food", "Bangkok Thai Kitchen", "Cosmic Cantina",
        "Sunrise Biscuit", "Krispy Kreme", "Cold Stone Creamery", "Moge Tee", "Ye Olde Waffle Shoppe",
        "Spicy 9", "Mandolin", "Bonchon", "Zaxby's", "Wingstop", "Shake Shack", "Little Caesars",
    ],
    "Groceries": [
        "Harris Teeter", "Food Lion", "Trader Joe's", "Whole Foods", "Publix", "Kroger", "Aldi",
        "Wegmans", "Lidl", "Sprouts Farmers Market", "Weaver Street Market", "H Mart", "Costco Wholesale",
        "Sam's Club", "Piggly Wiggly", "Lowes Foods", "Fresh Market", "Carrboro Farmers Market",
        "Compare Foods", "Grocery Outlet", "Patel Brothers", "Li Ming's Global Mart", "Super G Mart",
        "Save A Lot", "Safeway", "Giant Food", "Stop & Shop", "Hannaford", "Meijer", "H-E-B",
        "Winn-Dixie", "Ingles Markets", "Food 4 Less", "Fiesta Mart", "Bravo Supermarket",
    ],
    "Shopping": [
        "Amazon Mktplace", "Target", "Walmart", "Best Buy", "IKEA", "Old Navy", "H&M", "Zara",
        "Uniqlo", "Nike", "Foot Locker", "TJ Maxx", "Marshalls", "Ross Stores", "Bed Bath",
        "Five Below", "Dollar Tree", "Etsy", "eBay", "Poshmark", "Depop", "Shein", "Temu",
        "Barnes & Noble", "UNC Student Stores", "Urban Outfitters", "American Eagle", "Lululemon",
        "Apple Store", "GameStop", "Michaels", "Hobby Lobby", "Home Depot", "Lowe's", "Staples",
        "Office Depot", "Ulta Beauty", "Sephora", "Kohl's", "Macy's", "Nordstrom Rack", "Ace Hardware",
    ],
    "Transportation": [
        "Uber Trip", "Lyft Ride", "Shell Oil", "Exxon Mobil", "BP", "Sunoco", "Sheetz", "Wawa",
        "QuikTrip", "Circle K", "Speedway", "Valero", "Chevron", "Citgo", "GoTriangle", "Chapel Hill Transit",
        "UNC Parking", "ParkMobile", "SpotHero", "NC Quick Pass", "EZPass", "Jiffy Lube", "Valvoline",
        "Midas", "Firestone Complete Auto", "Discount Tire", "AutoZone", "O'Reilly Auto Parts",
        "NCDMV", "Lime Scooter", "Bird Rides", "Zipcar", "Enterprise Rent-A-Car",
    ],
    "Travel": [
        "Delta Air Lines", "American Airlines", "United Airlines", "Southwest Air", "JetBlue",
        "Frontier Airlines", "Spirit Airlines", "Amtrak", "Greyhound", "FlixBus", "Marriott",
        "Hilton Hotels", "Hampton Inn", "Holiday Inn Express", "Airbnb", "Vrbo", "Expedia",
        "Booking.com", "Hotels.com", "Kayak", "Hertz", "Avis", "Budget Rent A Car", "RDU Airport",
        "TSA PreCheck", "Carolina Inn", "Graduate Hotel", "Motel 6", "Best Western", "Priceline",
    ],
    "Entertainment": [
        "AMC Theatres", "Regal Cinemas", "The Varsity Theatre", "Ticketmaster", "StubHub", "SeatGeek",
        "Eventbrite", "Dave & Buster's", "Topgolf", "Bowlero", "Steam Games", "PlayStation Store",
        "Xbox Store", "Nintendo eShop", "Cat's Cradle", "Carolina Performing Arts", "Kenan Stadium",
        "Smith Center", "Main Event", "Escape Room Durham", "Museum of Life", "NC Museum of Art",
        "Durham Bulls", "Sky Zone", "Round One", "Fandango", "Mini Golf Raleigh", "Laser Quest",
    ],
    "Subscriptions": [
        "Netflix", "Spotify", "Hulu", "Disney Plus", "HBO Max", "YouTube Premium", "Apple Music",
        "Apple iCloud", "Google One", "Amazon Prime", "Paramount Plus", "Peacock", "Crunchyroll",
        "Audible", "Chegg Study", "Quizlet Plus", "Grammarly", "Notion Labs", "Duolingo Plus",
        "Adobe Creative Cloud", "Microsoft 365", "Dropbox", "Patreon", "Twitch", "Nord VPN",
        "ChatGPT Plus", "Canva Pro", "Planet Fitness", "Classpass", "Headspace", "Calm App",
    ],
    "Bills & Utilities": [
        "Duke Energy", "Dominion Energy", "PSNC Energy", "OWASA Water", "Piedmont Natural Gas",
        "Spectrum Internet", "Xfinity", "AT&T Wireless", "Verizon Wireless", "T-Mobile", "Mint Mobile",
        "Google Fi", "Visible Wireless", "Cricket Wireless", "Republic Services", "Waste Management",
        "Lemonade Renters", "State Farm Insurance", "Geico", "Progressive Insurance", "Allstate",
        "Ting Internet", "Frontier Communications", "Town of Chapel Hill Utilities", "Durham Water",
        "Orange County Solid Waste", "Apartment Rent Payment", "Bell Tower Apartments Rent",
    ],
    "Education": [
        "UNC Cashier", "UNC Tuition", "Pearson Education", "McGraw Hill", "Cengage", "Wiley",
        "zyBooks", "Gradescope", "Coursera", "Udemy", "edX", "Khan Academy Donation", "Kaplan Test Prep",
        "Princeton Review", "College Board", "ETS GRE", "LSAC", "AAMC MCAT", "Common App",
        "Chegg Textbooks", "VitalSource", "RedShelf", "iClicker", "Tophat", "Brilliant Org",
        "Codecademy", "LeetCode", "DataCamp",
    ],
    "Health & Personal Care": [
        "CVS Pharmacy", "Walgreens", "Rite Aid", "Campus Health Pharmacy", "UNC Health", "Duke Health",
        "Planned Parenthood", "MinuteClinic", "LabCorp", "Quest Diagnostics", "Smile Direct",
        "Aspen Dental", "Chapel Hill Dentistry", "Warby Parker", "LensCrafters", "1-800 Contacts",
        "Great Clips", "Supercuts", "Sport Clips", "Floyd's Barbershop", "Cut Above Salon",
        "European Wax Center", "Massage Envy", "Hand & Stone", "GoodRx", "Hims", "Hers", "Curology",
        "Teladoc", "BetterHelp", "Orangetheory Fitness", "YMCA Chapel Hill", "Pure Barre",
    ],
    "Fees & Interest": [
        "Overdraft Fee", "Monthly Service Fee", "Late Payment Fee", "Interest Charge Purchases",
        "Foreign Transaction Fee", "ATM Withdrawal Fee", "Non-Network ATM Fee", "Cash Advance Fee",
        "Returned Payment Fee", "Annual Membership Fee", "Wire Transfer Fee", "Paper Statement Fee",
        "Minimum Interest Charge", "Balance Transfer Fee", "Stop Payment Fee", "Card Replacement Fee",
    ],
}

CITIES = [
    ("CHAPEL HILL", "NC"), ("CARRBORO", "NC"), ("DURHAM", "NC"), ("RALEIGH", "NC"), ("CARY", "NC"),
    ("CHARLOTTE", "NC"), ("GREENSBORO", "NC"), ("ATLANTA", "GA"), ("NEW YORK", "NY"), ("SEATTLE", "WA"),
    ("SAN FRANCISCO", "CA"), ("AUSTIN", "TX"), ("RICHMOND", "VA"), ("BOSTON", "MA"),
]

# How different banks print the same purchase. {m} = merchant text, {c}/{s} = city/state,
# {n} = store number, {d} = MM/DD, {k} = last 4 card digits, {r} = reference code.
TEMPLATES = [
    "{m}",
    "{m} #{n}",
    "{m} {c} {s}",
    "{m} #{n} {c} {s}",
    "POS DEBIT {m} {c} {s}",
    "PURCHASE AUTHORIZED ON {d} {m} {c} {s} CARD {k}",
    "DEBIT CARD PURCHASE - {m} {c} {s}",
    "CHECKCARD {d} {m} {c} {s} {r}",
    "SQ *{m}",
    "TST* {m}",
    "{m}*{r}",
    "PAYPAL *{m}",
    "RECURRING PAYMENT {m}",
]


@dataclass(frozen=True)
class Example:
    description: str
    category: str
    merchant: str


def _messy(rng: random.Random, name: str) -> str:
    """The merchant name as a bank might print it, sometimes mangled."""
    text = name.upper().replace("'", "")
    roll = rng.random()
    if roll < 0.20:
        text = text.replace(" ", "").replace("-", "")  # glued together: DOMINOSPIZZA
    elif roll < 0.30:
        # dropped vowels after the first letter of each word: WALGRNS
        text = " ".join(w[0] + "".join(ch for ch in w[1:] if ch not in "AEIOU") for w in text.split())
    elif roll < 0.38 and len(text) > 6:
        cut = rng.randint(5, len(text) - 1)  # cut short mid-word
        text = text[:cut]
    return text


def _describe(rng: random.Random, merchant: str) -> str:
    template = rng.choice(TEMPLATES)
    city, state = rng.choice(CITIES)
    text = template.format(
        m=_messy(rng, merchant),
        c=city,
        s=state,
        n=rng.randint(100, 99999),
        d=f"{rng.randint(1, 12):02d}/{rng.randint(1, 28):02d}",
        k=rng.randint(1000, 9999),
        r="".join(rng.choice("ABCDEFGHJKLMNPQRSTUVWXYZ0123456789") for _ in range(rng.randint(6, 12))),
    )
    if rng.random() < 0.35:
        text = text[:22]  # card networks often keep only ~22 characters
    return text


# Neutral words to build pretend local businesses around a dictionary keyword: "MAPLE PIZZA".
FILLER_NAMES = [
    "MAPLE", "BLUE RIDGE", "SUNRISE", "OAK STREET", "MAIN ST", "TAR HEEL", "CAROLINA", "PIEDMONT",
    "LIBERTY", "GOLDEN", "RIVERSIDE", "UPTOWN", "FRANKLIN", "EASTGATE", "WILLOW", "HARBOR", "SUMMIT",
    "LAKESIDE", "PINE", "CEDAR", "HILLTOP", "VILLAGE", "METRO", "PARKSIDE", "ROSEWOOD",
]


def keyword_examples(keywords: list[tuple[str, str]], seed: int = 11, per_keyword: int = 6) -> list[Example]:
    """
    Training examples built from the app's own merchant dictionary (keyword -> category), wrapped
    in made-up names and messed up like real descriptions. This teaches the model the dictionary's
    words in the forms whole-word matching misses ("MAPLEPIZZA", "SUNRISE PHRMCY").
    """
    rng = random.Random(seed)
    examples: list[Example] = []
    for keyword, category in keywords:
        if category not in CATEGORIES:
            continue
        for _ in range(per_keyword):
            name = keyword if rng.random() < 0.3 else f"{rng.choice(FILLER_NAMES)} {keyword}"
            examples.append(Example(_describe(rng, name), category, f"keyword:{keyword}"))
    return examples


def descriptions(merchants_by_category: dict[str, list[str]], seed: int, per_merchant: int) -> list[Example]:
    """per_merchant generated descriptions for each merchant."""
    rng = random.Random(seed)
    return [Example(_describe(rng, merchant), category, merchant)
            for category in CATEGORIES
            for merchant in merchants_by_category[category]
            for _ in range(per_merchant)]


def split_merchants(seed: int = 2029, test_share: float = 0.3) -> tuple[dict, dict]:
    """(train merchants, test merchants) per category; the test ones are never used for training."""
    rng = random.Random(seed)
    train, test = {}, {}
    for category in CATEGORIES:
        merchants = sorted(MERCHANTS[category])
        rng.shuffle(merchants)
        n_test = max(2, round(len(merchants) * test_share))
        test[category], train[category] = merchants[:n_test], merchants[n_test:]
    return train, test


def generate(seed: int = 2029, per_merchant: int = 25, test_share: float = 0.3) -> tuple[list[Example], list[Example]]:
    """(train, test), split by merchant so test merchants are never seen in training."""
    rng = random.Random(seed)
    train: list[Example] = []
    test: list[Example] = []
    for category in CATEGORIES:
        merchants = sorted(MERCHANTS[category])
        rng.shuffle(merchants)
        n_test = max(2, round(len(merchants) * test_share))
        for i, merchant in enumerate(merchants):
            target = test if i < n_test else train
            for _ in range(per_merchant):
                target.append(Example(_describe(rng, merchant), category, merchant))
    return train, test
