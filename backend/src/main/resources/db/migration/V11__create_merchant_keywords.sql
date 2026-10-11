-- A built-in dictionary of merchant keywords -> default categories, so transactions get a category
-- even when the bank's file has no category column. Order of precedence (CategoryAssigner):
-- the user's manual choice > the user's rules > the bank's label > this dictionary > Uncategorized.
--
-- Matching is on whole words: the description and the keyword are both reduced to uppercase
-- letters/digits separated by single spaces ("APPLE.COM/BILL" -> "APPLE COM BILL"), so a keyword
-- never matches inside a longer word ("MART" doesn't match "WALMART"). The longest matching
-- keyword wins ("UBER EATS" -> Food beats "UBER" -> Transportation).

ALTER TABLE transactions DROP CONSTRAINT ck_transactions_category_source;
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_category_source
        CHECK (category_source IN ('BANK', 'RULE', 'MANUAL', 'KEYWORD'));

CREATE TABLE merchant_keywords (
    -- Already normalized: uppercase words of letters/digits separated by single spaces.
    keyword       VARCHAR(60) PRIMARY KEY
        CONSTRAINT ck_merchant_keywords_normalized CHECK (keyword ~ '^[A-Z0-9]+( [A-Z0-9]+)*$'),
    category_name VARCHAR(50) NOT NULL,
    -- Only for money coming in (e.g. PAYROLL): a refund from a store is still that store's category.
    money_in_only BOOLEAN     NOT NULL DEFAULT FALSE
);

INSERT INTO merchant_keywords (keyword, category_name) VALUES
    -- ===== Food & Dining =====
    ('RESTAURANT', 'Food & Dining'), ('RESTAURANTS', 'Food & Dining'), ('CAFE', 'Food & Dining'),
    ('CAFFE', 'Food & Dining'), ('COFFEE', 'Food & Dining'), ('ESPRESSO', 'Food & Dining'),
    ('PIZZA', 'Food & Dining'), ('PIZZERIA', 'Food & Dining'), ('GRILL', 'Food & Dining'),
    ('DINER', 'Food & Dining'), ('BURGER', 'Food & Dining'), ('BURGERS', 'Food & Dining'),
    ('TACO', 'Food & Dining'), ('TACOS', 'Food & Dining'), ('TAQUERIA', 'Food & Dining'),
    ('SUSHI', 'Food & Dining'), ('BBQ', 'Food & Dining'), ('BARBECUE', 'Food & Dining'),
    ('BAKERY', 'Food & Dining'), ('KITCHEN', 'Food & Dining'), ('BISTRO', 'Food & Dining'),
    ('DELI', 'Food & Dining'), ('NOODLE', 'Food & Dining'), ('NOODLES', 'Food & Dining'),
    ('RAMEN', 'Food & Dining'), ('PHO', 'Food & Dining'), ('WINGS', 'Food & Dining'),
    ('BAGEL', 'Food & Dining'), ('BAGELS', 'Food & Dining'), ('DONUT', 'Food & Dining'),
    ('DONUTS', 'Food & Dining'), ('DOUGHNUT', 'Food & Dining'), ('ICE CREAM', 'Food & Dining'),
    ('CREAMERY', 'Food & Dining'), ('GELATO', 'Food & Dining'), ('SMOOTHIE', 'Food & Dining'),
    ('BOBA', 'Food & Dining'), ('BREWING', 'Food & Dining'), ('BREWERY', 'Food & Dining'),
    ('TAVERN', 'Food & Dining'), ('PUB', 'Food & Dining'), ('CANTINA', 'Food & Dining'),
    ('STEAKHOUSE', 'Food & Dining'), ('EATERY', 'Food & Dining'), ('DINING', 'Food & Dining'),
    ('CATERING', 'Food & Dining'), ('FOOD TRUCK', 'Food & Dining'), ('TST', 'Food & Dining'),
    ('STARBUCKS', 'Food & Dining'), ('DUNKIN', 'Food & Dining'), ('MCDONALD', 'Food & Dining'),
    ('MCDONALDS', 'Food & Dining'), ('CHICK FIL A', 'Food & Dining'), ('CHICKFILA', 'Food & Dining'),
    ('CHIPOTLE', 'Food & Dining'), ('PANERA', 'Food & Dining'), ('SUBWAY', 'Food & Dining'),
    ('DOMINO', 'Food & Dining'), ('DOMINOS', 'Food & Dining'), ('PAPA JOHN', 'Food & Dining'),
    ('PAPA JOHNS', 'Food & Dining'), ('WENDY', 'Food & Dining'), ('WENDYS', 'Food & Dining'),
    ('BURGER KING', 'Food & Dining'), ('TACO BELL', 'Food & Dining'), ('KFC', 'Food & Dining'),
    ('POPEYES', 'Food & Dining'), ('FIVE GUYS', 'Food & Dining'), ('SHAKE SHACK', 'Food & Dining'),
    ('SONIC DRIVE', 'Food & Dining'), ('ARBYS', 'Food & Dining'), ('CAVA', 'Food & Dining'),
    ('SWEETGREEN', 'Food & Dining'), ('JIMMY JOHN', 'Food & Dining'), ('JIMMY JOHNS', 'Food & Dining'),
    ('JERSEY MIKE', 'Food & Dining'), ('JERSEY MIKES', 'Food & Dining'), ('FIREHOUSE SUBS', 'Food & Dining'),
    ('APPLEBEE', 'Food & Dining'), ('APPLEBEES', 'Food & Dining'), ('OLIVE GARDEN', 'Food & Dining'),
    ('IHOP', 'Food & Dining'), ('WAFFLE HOUSE', 'Food & Dining'), ('CRACKER BARREL', 'Food & Dining'),
    ('RAISING CANE', 'Food & Dining'), ('RAISING CANES', 'Food & Dining'), ('CULVERS', 'Food & Dining'),
    ('ZAXBY', 'Food & Dining'), ('ZAXBYS', 'Food & Dining'), ('BOJANGLES', 'Food & Dining'),
    ('CHEESECAKE FACTORY', 'Food & Dining'), ('PANDA EXPRESS', 'Food & Dining'), ('QDOBA', 'Food & Dining'),
    ('MOES', 'Food & Dining'), ('BUFFALO WILD', 'Food & Dining'), ('CARIBOU COFFEE', 'Food & Dining'),
    ('PEETS', 'Food & Dining'), ('TIM HORTONS', 'Food & Dining'), ('KRISPY KREME', 'Food & Dining'),
    ('BASKIN', 'Food & Dining'), ('DAIRY QUEEN', 'Food & Dining'), ('JAMBA', 'Food & Dining'),
    ('TROPICAL SMOOTHIE', 'Food & Dining'), ('INSOMNIA COOKIES', 'Food & Dining'), ('CRUMBL', 'Food & Dining'),
    ('WINGSTOP', 'Food & Dining'), ('DOORDASH', 'Food & Dining'), ('UBER EATS', 'Food & Dining'),
    ('UBEREATS', 'Food & Dining'), ('GRUBHUB', 'Food & Dining'), ('POSTMATES', 'Food & Dining'),
    ('SEAMLESS', 'Food & Dining'), ('EZCATER', 'Food & Dining'),

    -- ===== Groceries =====
    ('GROCERY', 'Groceries'), ('GROCERIES', 'Groceries'), ('GROCER', 'Groceries'),
    ('SUPERMARKET', 'Groceries'), ('MART', 'Groceries'), ('HARRIS TEETER', 'Groceries'),
    ('FOOD LION', 'Groceries'), ('KROGER', 'Groceries'), ('PUBLIX', 'Groceries'),
    ('ALDI', 'Groceries'), ('LIDL', 'Groceries'), ('TRADER JOE', 'Groceries'),
    ('TRADER JOES', 'Groceries'), ('WHOLE FOODS', 'Groceries'), ('WHOLEFDS', 'Groceries'),
    ('WEGMANS', 'Groceries'), ('SAFEWAY', 'Groceries'), ('GIANT FOOD', 'Groceries'),
    ('HEB', 'Groceries'), ('H E B', 'Groceries'), ('SPROUTS', 'Groceries'),
    ('COSTCO', 'Groceries'), ('SAMS CLUB', 'Groceries'), ('BJS WHOLESALE', 'Groceries'),
    ('INSTACART', 'Groceries'), ('FOOD MART', 'Groceries'), ('MEIJER', 'Groceries'),
    ('ALBERTSONS', 'Groceries'), ('STOP SHOP', 'Groceries'), ('FRESH MARKET', 'Groceries'),
    ('WINN DIXIE', 'Groceries'), ('PIGGLY WIGGLY', 'Groceries'), ('LOWES FOODS', 'Groceries'),

    -- ===== Transportation =====
    ('UBER', 'Transportation'), ('LYFT', 'Transportation'), ('RIDESHARE', 'Transportation'),
    ('TAXI', 'Transportation'), ('CAB', 'Transportation'), ('SHELL', 'Transportation'),
    ('EXXON', 'Transportation'), ('EXXONMOBIL', 'Transportation'), ('MOBIL', 'Transportation'),
    ('CHEVRON', 'Transportation'), ('BP', 'Transportation'), ('SUNOCO', 'Transportation'),
    ('CITGO', 'Transportation'), ('SPEEDWAY', 'Transportation'), ('SHEETZ', 'Transportation'),
    ('WAWA', 'Transportation'), ('QUIKTRIP', 'Transportation'), ('CIRCLE K', 'Transportation'),
    ('VALERO', 'Transportation'), ('ARCO', 'Transportation'), ('TEXACO', 'Transportation'),
    ('GAS', 'Transportation'), ('GASOLINE', 'Transportation'), ('FUEL', 'Transportation'),
    ('PARKING', 'Transportation'), ('PARKMOBILE', 'Transportation'), ('TOLL', 'Transportation'),
    ('TOLLS', 'Transportation'), ('E ZPASS', 'Transportation'), ('EZPASS', 'Transportation'),
    ('QUICK PASS', 'Transportation'), ('SUNPASS', 'Transportation'), ('TRANSIT', 'Transportation'),
    ('CAR WASH', 'Transportation'), ('AUTOZONE', 'Transportation'), ('JIFFY LUBE', 'Transportation'),
    ('ADVANCE AUTO', 'Transportation'), ('OREILLY AUTO', 'Transportation'), ('O REILLY AUTO', 'Transportation'),
    ('VALVOLINE', 'Transportation'), ('FIRESTONE', 'Transportation'), ('DMV', 'Transportation'),

    -- ===== Travel =====
    ('AIRLINES', 'Travel'), ('AIRLINE', 'Travel'), ('AIRWAYS', 'Travel'), ('AIR LINES', 'Travel'),
    ('DELTA AIR', 'Travel'), ('AMERICAN AIR', 'Travel'), ('UNITED AIRLINES', 'Travel'),
    ('SOUTHWEST AIR', 'Travel'), ('SOUTHWES', 'Travel'), ('JETBLUE', 'Travel'), ('SPIRIT AIRL', 'Travel'),
    ('FRONTIER AIRLINES', 'Travel'), ('ALASKA AIR', 'Travel'), ('HOTEL', 'Travel'), ('HOTELS', 'Travel'),
    ('MOTEL', 'Travel'), ('MARRIOTT', 'Travel'), ('HILTON', 'Travel'), ('HYATT', 'Travel'),
    ('HOLIDAY INN', 'Travel'), ('AIRBNB', 'Travel'), ('VRBO', 'Travel'), ('EXPEDIA', 'Travel'),
    ('BOOKING COM', 'Travel'), ('PRICELINE', 'Travel'), ('HOTELS COM', 'Travel'), ('AMTRAK', 'Travel'),
    ('GREYHOUND', 'Travel'), ('MEGABUS', 'Travel'), ('HERTZ', 'Travel'), ('AVIS', 'Travel'),
    ('ENTERPRISE RENT', 'Travel'), ('BUDGET RENT', 'Travel'), ('NATIONAL CAR', 'Travel'),
    ('CAR RENTAL', 'Travel'), ('TURO', 'Travel'), ('TSA PRECHECK', 'Travel'), ('AIRPORT', 'Travel'),

    -- ===== Subscriptions =====
    ('NETFLIX', 'Subscriptions'), ('SPOTIFY', 'Subscriptions'), ('HULU', 'Subscriptions'),
    ('DISNEY PLUS', 'Subscriptions'), ('DISNEYPLUS', 'Subscriptions'), ('HBO MAX', 'Subscriptions'),
    ('HBOMAX', 'Subscriptions'), ('MAX COM', 'Subscriptions'), ('PARAMOUNT PLUS', 'Subscriptions'),
    ('PEACOCK', 'Subscriptions'), ('YOUTUBE PREMIUM', 'Subscriptions'), ('YOUTUBEPREMIUM', 'Subscriptions'),
    ('YOUTUBE TV', 'Subscriptions'), ('APPLE COM BILL', 'Subscriptions'), ('ITUNES', 'Subscriptions'),
    ('ICLOUD', 'Subscriptions'), ('AMAZON PRIME', 'Subscriptions'), ('PRIME VIDEO', 'Subscriptions'),
    ('PRIMEVIDEO', 'Subscriptions'), ('AUDIBLE', 'Subscriptions'), ('KINDLE UNLIMITED', 'Subscriptions'),
    ('OPENAI', 'Subscriptions'), ('CHATGPT', 'Subscriptions'), ('ANTHROPIC', 'Subscriptions'),
    ('CLAUDE AI', 'Subscriptions'), ('ADOBE', 'Subscriptions'), ('MICROSOFT 365', 'Subscriptions'),
    ('GOOGLE ONE', 'Subscriptions'), ('GOOGLE STORAGE', 'Subscriptions'), ('DROPBOX', 'Subscriptions'),
    ('NOTION', 'Subscriptions'), ('CANVA', 'Subscriptions'), ('GRAMMARLY', 'Subscriptions'),
    ('DUOLINGO', 'Subscriptions'), ('CRUNCHYROLL', 'Subscriptions'), ('PATREON', 'Subscriptions'),
    ('SIRIUSXM', 'Subscriptions'), ('PELOTON', 'Subscriptions'), ('CLASSPASS', 'Subscriptions'),
    ('APPLE MUSIC', 'Subscriptions'), ('PANDORA', 'Subscriptions'), ('TIDAL', 'Subscriptions'),
    ('SUBSCRIPTION', 'Subscriptions'), ('MEMBERSHIP', 'Subscriptions'),

    -- ===== Entertainment =====
    ('CINEMA', 'Entertainment'), ('CINEMAS', 'Entertainment'), ('THEATRE', 'Entertainment'),
    ('THEATER', 'Entertainment'), ('THEATRES', 'Entertainment'), ('THEATERS', 'Entertainment'),
    ('AMC', 'Entertainment'), ('REGAL', 'Entertainment'), ('FANDANGO', 'Entertainment'),
    ('TICKETMASTER', 'Entertainment'), ('STUBHUB', 'Entertainment'), ('SEATGEEK', 'Entertainment'),
    ('EVENTBRITE', 'Entertainment'), ('LIVE NATION', 'Entertainment'), ('STEAM', 'Entertainment'),
    ('STEAMGAMES', 'Entertainment'), ('STEAMPOWERED', 'Entertainment'), ('PLAYSTATION', 'Entertainment'),
    ('XBOX', 'Entertainment'), ('NINTENDO', 'Entertainment'), ('EPIC GAMES', 'Entertainment'),
    ('ROBLOX', 'Entertainment'), ('RIOT GAMES', 'Entertainment'), ('BOWLING', 'Entertainment'),
    ('TOPGOLF', 'Entertainment'), ('MUSEUM', 'Entertainment'), ('CONCERT', 'Entertainment'),
    ('ARCADE', 'Entertainment'), ('MINI GOLF', 'Entertainment'), ('ESCAPE ROOM', 'Entertainment'),

    -- ===== Shopping =====
    ('AMAZON', 'Shopping'), ('AMZN', 'Shopping'), ('AMAZON COM', 'Shopping'), ('AMZN MKTP', 'Shopping'),
    ('TARGET', 'Shopping'), ('WALMART', 'Shopping'), ('WAL MART', 'Shopping'), ('BEST BUY', 'Shopping'),
    ('BESTBUY', 'Shopping'), ('EBAY', 'Shopping'), ('ETSY', 'Shopping'), ('POSHMARK', 'Shopping'),
    ('DEPOP', 'Shopping'), ('MERCARI', 'Shopping'), ('WHATNOT', 'Shopping'), ('SHEIN', 'Shopping'),
    ('TEMU', 'Shopping'), ('NIKE', 'Shopping'), ('ADIDAS', 'Shopping'), ('IKEA', 'Shopping'),
    ('HOME DEPOT', 'Shopping'), ('LOWES', 'Shopping'), ('MACYS', 'Shopping'), ('MACY S', 'Shopping'),
    ('NORDSTROM', 'Shopping'), ('TJ MAXX', 'Shopping'), ('TJMAXX', 'Shopping'), ('MARSHALLS', 'Shopping'),
    ('ROSS STORES', 'Shopping'), ('OLD NAVY', 'Shopping'), ('GAP', 'Shopping'), ('ZARA', 'Shopping'),
    ('UNIQLO', 'Shopping'), ('URBAN OUTFITTERS', 'Shopping'), ('ULTA', 'Shopping'), ('SEPHORA', 'Shopping'),
    ('APPLE STORE', 'Shopping'), ('DOLLAR TREE', 'Shopping'), ('DOLLAR GENERAL', 'Shopping'),
    ('FIVE BELOW', 'Shopping'), ('BARNES NOBLE', 'Shopping'), ('MICHAELS', 'Shopping'),
    ('HOBBY LOBBY', 'Shopping'), ('DICKS SPORTING', 'Shopping'), ('ACADEMY SPORTS', 'Shopping'),
    ('GAMESTOP', 'Shopping'), ('KOHLS', 'Shopping'), ('BED BATH', 'Shopping'), ('WAYFAIR', 'Shopping'),
    ('ALIEXPRESS', 'Shopping'), ('STOCKX', 'Shopping'), ('THRIFT', 'Shopping'), ('GOODWILL', 'Shopping'),

    -- ===== Health & Personal Care =====
    ('PHARMACY', 'Health & Personal Care'), ('CVS', 'Health & Personal Care'),
    ('WALGREENS', 'Health & Personal Care'), ('RITE AID', 'Health & Personal Care'),
    ('DENTAL', 'Health & Personal Care'), ('DENTIST', 'Health & Personal Care'),
    ('MEDICAL', 'Health & Personal Care'), ('CLINIC', 'Health & Personal Care'),
    ('HOSPITAL', 'Health & Personal Care'), ('URGENT CARE', 'Health & Personal Care'),
    ('OPTOMETRY', 'Health & Personal Care'), ('VISION', 'Health & Personal Care'),
    ('PHYSICIAN', 'Health & Personal Care'), ('THERAPY', 'Health & Personal Care'),
    ('GYM', 'Health & Personal Care'), ('FITNESS', 'Health & Personal Care'),
    ('PLANET FITNESS', 'Health & Personal Care'), ('YMCA', 'Health & Personal Care'),
    ('BARBER', 'Health & Personal Care'), ('BARBERS', 'Health & Personal Care'),
    ('BARBERSHOP', 'Health & Personal Care'), ('SALON', 'Health & Personal Care'),
    ('HAIR', 'Health & Personal Care'), ('NAILS', 'Health & Personal Care'),

    -- ===== Bills & Utilities =====
    ('VERIZON', 'Bills & Utilities'), ('AT T', 'Bills & Utilities'), ('ATT', 'Bills & Utilities'),
    ('T MOBILE', 'Bills & Utilities'), ('TMOBILE', 'Bills & Utilities'), ('SPRINT', 'Bills & Utilities'),
    ('MINT MOBILE', 'Bills & Utilities'), ('VISIBLE', 'Bills & Utilities'), ('CRICKET WIRELESS', 'Bills & Utilities'),
    ('GOOGLE FI', 'Bills & Utilities'), ('COMCAST', 'Bills & Utilities'), ('XFINITY', 'Bills & Utilities'),
    ('SPECTRUM', 'Bills & Utilities'), ('COX COMM', 'Bills & Utilities'), ('DUKE ENERGY', 'Bills & Utilities'),
    ('DOMINION ENERGY', 'Bills & Utilities'), ('ELECTRIC', 'Bills & Utilities'), ('UTILITIES', 'Bills & Utilities'),
    ('UTILITY', 'Bills & Utilities'), ('WATER', 'Bills & Utilities'), ('SEWER', 'Bills & Utilities'),
    ('INTERNET', 'Bills & Utilities'), ('WIFI', 'Bills & Utilities'), ('PHONE', 'Bills & Utilities'),
    ('WIRELESS', 'Bills & Utilities'), ('RENT', 'Bills & Utilities'), ('APARTMENTS', 'Bills & Utilities'),
    ('APTS', 'Bills & Utilities'), ('PROPERTY MGMT', 'Bills & Utilities'), ('PROPERTY MANAGEMENT', 'Bills & Utilities'),
    ('INSURANCE', 'Bills & Utilities'), ('GEICO', 'Bills & Utilities'), ('PROGRESSIVE', 'Bills & Utilities'),
    ('STATE FARM', 'Bills & Utilities'), ('ALLSTATE', 'Bills & Utilities'), ('LEMONADE', 'Bills & Utilities'),

    -- ===== Education =====
    ('TUITION', 'Education'), ('BURSAR', 'Education'), ('BOOKSTORE', 'Education'),
    ('TEXTBOOK', 'Education'), ('TEXTBOOKS', 'Education'), ('CHEGG', 'Education'),
    ('COURSERA', 'Education'), ('UDEMY', 'Education'), ('PEARSON', 'Education'),
    ('MCGRAW HILL', 'Education'), ('CENGAGE', 'Education'), ('WILEY', 'Education'),
    ('QUIZLET', 'Education'), ('EDX', 'Education'), ('LEETCODE', 'Education'),
    ('STUDENT STORES', 'Education'), ('COLLEGE BOARD', 'Education'), ('PRINCETON REVIEW', 'Education'),

    -- ===== Fees & Interest =====
    ('INTEREST CHARGE', 'Fees & Interest'), ('PURCHASE INTEREST', 'Fees & Interest'),
    ('LATE FEE', 'Fees & Interest'), ('ANNUAL FEE', 'Fees & Interest'),
    ('FOREIGN TRANSACTION', 'Fees & Interest'), ('OVERDRAFT', 'Fees & Interest'),
    ('SERVICE FEE', 'Fees & Interest'), ('SERVICE CHARGE', 'Fees & Interest'),
    ('MONTHLY FEE', 'Fees & Interest'), ('MAINTENANCE FEE', 'Fees & Interest'),
    ('ATM FEE', 'Fees & Interest'), ('NSF FEE', 'Fees & Interest'), ('RETURNED PAYMENT FEE', 'Fees & Interest'),

    -- ===== Payments & Transfers (not spending) =====
    ('PAYMENT THANK YOU', 'Payments & Transfers'), ('PAYMENT THANK', 'Payments & Transfers'),
    ('AUTOPAY PYMT', 'Payments & Transfers'), ('AUTOPAY PAYMENT', 'Payments & Transfers'),
    ('AUTOMATIC PAYMENT', 'Payments & Transfers'), ('ONLINE PAYMENT', 'Payments & Transfers'),
    ('MOBILE PAYMENT', 'Payments & Transfers'), ('INTERNET PAYMENT', 'Payments & Transfers'),
    ('ELECTRONIC PAYMENT', 'Payments & Transfers'), ('CREDIT CARD PAYMENT', 'Payments & Transfers'),
    ('CARD PAYMENT', 'Payments & Transfers'), ('EPAYMENT', 'Payments & Transfers'),
    ('TRANSFER', 'Payments & Transfers'), ('XFER', 'Payments & Transfers'), ('ZELLE', 'Payments & Transfers'),
    ('VENMO', 'Payments & Transfers'), ('CASH APP', 'Payments & Transfers'), ('CASHAPP', 'Payments & Transfers'),
    ('WIRE', 'Payments & Transfers'), ('PAYPAL TRANSFER', 'Payments & Transfers'),

    -- ===== Other =====
    ('ATM WITHDRAWAL', 'Other'), ('CASH WITHDRAWAL', 'Other'), ('ATM', 'Other');

INSERT INTO merchant_keywords (keyword, category_name, money_in_only) VALUES
    -- ===== Income (money in only) =====
    ('PAYROLL', 'Income', TRUE), ('DIR DEP', 'Income', TRUE), ('DIRECT DEP', 'Income', TRUE),
    ('DIRECT DEPOSIT', 'Income', TRUE), ('SALARY', 'Income', TRUE), ('PAYCHECK', 'Income', TRUE),
    ('INTEREST PAID', 'Income', TRUE), ('INTEREST EARNED', 'Income', TRUE), ('INTEREST PAYMENT', 'Income', TRUE),
    ('DIVIDEND', 'Income', TRUE), ('TAX REFUND', 'Income', TRUE), ('IRS TREAS', 'Income', TRUE),
    ('STIPEND', 'Income', TRUE), ('FINANCIAL AID', 'Income', TRUE), ('SCHOLARSHIP', 'Income', TRUE),
    ('DEPOSIT', 'Income', TRUE);

-- Categorize existing uncategorized transactions (never touches manual choices or anything already
-- categorized). Same matching as Java: whole words, longest keyword wins.
UPDATE transactions t
SET category_id = best.category_id,
    category_source = 'KEYWORD'
FROM (
    SELECT DISTINCT ON (t2.id) t2.id AS transaction_id, c.id AS category_id
    FROM transactions t2
    JOIN merchant_keywords k
      ON (' ' || btrim(regexp_replace(upper(t2.description), '[^A-Z0-9]+', ' ', 'g')) || ' ')
             LIKE ('% ' || k.keyword || ' %')
     AND (NOT k.money_in_only OR t2.amount > 0)
    JOIN categories c ON c.user_id = t2.user_id AND lower(c.name) = lower(k.category_name)
    WHERE t2.category_id IS NULL AND t2.category_source IS NULL
    ORDER BY t2.id, length(k.keyword) DESC, k.keyword
) best
WHERE t.id = best.transaction_id;
