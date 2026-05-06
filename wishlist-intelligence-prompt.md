# WishList Intelligence — Claude Code Build Prompt

## Autonomous Operation Mode

Work fully autonomously. Do NOT prompt the user for input unless:
- A build fails and you cannot determine the fix after 3 attempts
- You need a credential, API key, or secret that isn't in the codebase
- A design decision has two equally valid paths with very different UX tradeoffs

For everything else — make the best decision and keep going. If something
is ambiguous, choose the simpler option. Document significant decisions
as comments in the code.

---

## Project Overview

Build "WishList Intelligence" — a smart shopping companion app that predicts
optimal purchase timing, optimizes credit card rewards, tracks prices across
retailers, and lets users share wishlists as gift registries. Revenue comes
from affiliate commissions.

This is a NEW, SEPARATE project — not part of Safe Companion.

Reference the full product spec in: `WishList_Intelligence_Product_Spec.docx`
(in the same directory as this file). That document has detailed architecture,
data models, API contracts, and cost estimates. This prompt tells you HOW
to build it; the spec tells you WHAT to build.

---

## Repository Structure

Create a monorepo with two top-level projects:

```
wishlist-intelligence/
├── README.md
├── CLAUDE.md                          # Copy of this prompt (persistent context)
├── .gitignore
│
├── android/                           # Android app (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/wishlistiq/app/
│   │   │   │   ├── WishListApp.kt              # Hilt Application class
│   │   │   │   ├── MainActivity.kt
│   │   │   │   │
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── WishlistDao.kt
│   │   │   │   │   │   │   ├── ProductDao.kt
│   │   │   │   │   │   │   ├── PriceDao.kt
│   │   │   │   │   │   │   ├── CardDao.kt
│   │   │   │   │   │   │   ├── AlertDao.kt
│   │   │   │   │   │   │   └── CharityDao.kt
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── WishlistItemEntity.kt
│   │   │   │   │   │       ├── ProductEntity.kt
│   │   │   │   │   │       ├── PriceHistoryEntity.kt
│   │   │   │   │   │       ├── CreditCardEntity.kt
│   │   │   │   │   │       ├── PriceAlertEntity.kt
│   │   │   │   │   │       ├── SharedListEntity.kt
│   │   │   │   │   │       ├── AffiliateClickEntity.kt
│   │   │   │   │   │       └── CharityEntity.kt
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   ├── WishListApiService.kt     # Our Azure backend
│   │   │   │   │   │   ├── KeepaApiService.kt        # Price history
│   │   │   │   │   │   └── AffiliateRedirectService.kt
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── WishlistRepository.kt
│   │   │   │   │   │   ├── ProductRepository.kt
│   │   │   │   │   │   ├── PriceRepository.kt
│   │   │   │   │   │   ├── CardRewardsRepository.kt
│   │   │   │   │   │   ├── SharedListRepository.kt
│   │   │   │   │   │   ├── DealRepository.kt
│   │   │   │   │   │   └── CharityRepository.kt
│   │   │   │   │   ├── datastore/
│   │   │   │   │   │   └── UserPreferences.kt
│   │   │   │   │   └── model/
│   │   │   │   │       ├── Product.kt
│   │   │   │   │       ├── PricePrediction.kt
│   │   │   │   │       ├── CreditCard.kt
│   │   │   │   │       ├── CardRecommendation.kt
│   │   │   │   │       ├── Retailer.kt
│   │   │   │   │       ├── Deal.kt
│   │   │   │   │       └── CharityChoice.kt
│   │   │   │   │
│   │   │   │   ├── di/
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   ├── DatabaseModule.kt
│   │   │   │   │   └── NetworkModule.kt
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── wishlist/
│   │   │   │   │   │   ├── WishlistScreen.kt
│   │   │   │   │   │   ├── WishlistViewModel.kt
│   │   │   │   │   │   ├── WishlistItemCard.kt
│   │   │   │   │   │   └── ItemDetailScreen.kt
│   │   │   │   │   ├── explore/
│   │   │   │   │   │   ├── ExploreScreen.kt
│   │   │   │   │   │   ├── ExploreViewModel.kt
│   │   │   │   │   │   └── DealCard.kt
│   │   │   │   │   ├── scan/
│   │   │   │   │   │   ├── ScanScreen.kt
│   │   │   │   │   │   ├── ScanViewModel.kt
│   │   │   │   │   │   ├── BarcodeScannerView.kt
│   │   │   │   │   │   └── UrlInputSheet.kt
│   │   │   │   │   ├── shared/
│   │   │   │   │   │   ├── SharedListsScreen.kt
│   │   │   │   │   │   ├── SharedListViewModel.kt
│   │   │   │   │   │   ├── CreateListScreen.kt
│   │   │   │   │   │   └── PublicListScreen.kt
│   │   │   │   │   ├── profile/
│   │   │   │   │   │   ├── ProfileScreen.kt
│   │   │   │   │   │   ├── ProfileViewModel.kt
│   │   │   │   │   │   ├── CreditCardsScreen.kt
│   │   │   │   │   │   ├── CharityScreen.kt
│   │   │   │   │   │   └── NotificationSettingsScreen.kt
│   │   │   │   │   ├── price/
│   │   │   │   │   │   ├── PriceChartView.kt
│   │   │   │   │   │   └── PredictionOverlay.kt
│   │   │   │   │   ├── cards/
│   │   │   │   │   │   ├── CardOptimizerView.kt
│   │   │   │   │   │   └── CardRecommendationCard.kt
│   │   │   │   │   ├── onboarding/
│   │   │   │   │   │   ├── OnboardingScreen.kt
│   │   │   │   │   │   └── OnboardingViewModel.kt
│   │   │   │   │   ├── components/
│   │   │   │   │   │   ├── RetailerPriceRow.kt
│   │   │   │   │   │   ├── BuyNowVsWaitBadge.kt
│   │   │   │   │   │   ├── PriceDropIndicator.kt
│   │   │   │   │   │   ├── CharityRoundUpToggle.kt
│   │   │   │   │   │   └── ProductImage.kt
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── NavGraph.kt
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt
│   │   │   │   │       ├── Theme.kt
│   │   │   │   │       └── Type.kt
│   │   │   │   │
│   │   │   │   └── util/
│   │   │   │       ├── AffiliateManager.kt
│   │   │   │       ├── BarcodeAnalyzer.kt
│   │   │   │       ├── ProductMatcher.kt
│   │   │   │       ├── NotificationManager.kt
│   │   │   │       └── CharityManager.kt
│   │   │   │
│   │   │   └── res/  (standard Android resources)
│   │   │
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/
│
├── backend/                           # Azure Functions (Python)
│   ├── function_app.py                # Main function app entry
│   ├── requirements.txt
│   ├── host.json
│   ├── local.settings.json            # Local dev settings (gitignored)
│   │
│   ├── api/
│   │   ├── wishlist.py                # CRUD wishlist items
│   │   ├── products.py                # Product search, identify, prices
│   │   ├── predictions.py             # Price prediction endpoints
│   │   ├── card_optimizer.py          # Credit card recommendation
│   │   ├── shared_lists.py            # Shared wishlist management
│   │   ├── redirect.py                # Affiliate redirect service
│   │   ├── alerts.py                  # Price alert configuration
│   │   ├── deals.py                   # Personalized deal feed
│   │   ├── charity.py                 # Charity/GoFundMe endpoints
│   │   └── user.py                    # User profile, cards
│   │
│   ├── jobs/
│   │   ├── price_collector.py         # Timer: fetch prices every 6h
│   │   ├── prediction_refresh.py      # Timer: re-run ML model daily
│   │   ├── alert_processor.py         # Timer: check alerts every 15min
│   │   ├── card_category_sync.py      # Timer: update card rewards weekly
│   │   ├── product_matcher.py         # Timer: cross-retailer matching daily
│   │   ├── deal_scanner.py            # Timer: scan deal feeds every 2h
│   │   ├── charity_disbursement.py    # Timer: process charity round-ups monthly
│   │   └── data_cleanup.py            # Timer: archive old data weekly
│   │
│   ├── services/
│   │   ├── keepa_client.py            # Keepa API wrapper
│   │   ├── amazon_api.py              # Amazon Creators API
│   │   ├── walmart_api.py             # Walmart Affiliate API
│   │   ├── target_api.py              # Target/Partnerize API
│   │   ├── bestbuy_api.py             # Best Buy Affiliate API
│   │   ├── google_shopping.py         # Google Shopping / SerpAPI
│   │   ├── card_rewards_db.py         # Credit card rewards database
│   │   ├── charity_service.py         # Charity/GoFundMe integration
│   │   └── product_identifier.py      # Azure Computer Vision wrapper
│   │
│   ├── ml/
│   │   ├── price_predictor.py         # Prophet + LightGBM ensemble
│   │   ├── feature_engineering.py     # Feature extraction for ML
│   │   ├── decision_engine.py         # Buy Now vs Wait logic
│   │   └── model_training.py          # Training pipeline (Azure ML)
│   │
│   ├── db/
│   │   ├── cosmos_client.py           # Cosmos DB connection + helpers
│   │   └── models.py                  # Data models for all containers
│   │
│   └── tests/
│       ├── test_price_predictor.py
│       ├── test_card_optimizer.py
│       ├── test_affiliate_redirect.py
│       └── test_charity_roundup.py
│
└── infra/                             # Azure infrastructure-as-code
    ├── main.bicep                     # Azure Bicep (or Terraform)
    ├── parameters.json
    └── README.md
```

---

## Tech Stack

### Android App
| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository + Clean Architecture |
| DI | Hilt |
| Local DB | Room |
| Networking | Retrofit + OkHttp + Kotlin Serialization |
| Images | Coil |
| Barcode | ML Kit Barcode Scanning |
| Charts | Vico (Compose-native charting library) |
| Auth | Firebase Auth |
| Push | Firebase Cloud Messaging |
| Analytics | Firebase Analytics + Crashlytics |
| Min SDK | 26 |
| Target SDK | 35 |

### Azure Backend
| Component | Technology |
|-----------|-----------|
| Runtime | Azure Functions v4 (Python 3.11) |
| Database | Azure Cosmos DB (Serverless, NoSQL) |
| Cache | Azure Redis Cache (Basic C0) |
| ML | LightGBM + Prophet (in-Function, no separate endpoint) |
| Image/OCR | Azure Computer Vision |
| Secrets | Azure Key Vault |
| Monitoring | Azure Application Insights |
| IaC | Azure Bicep |
| Auth | Firebase Admin SDK (validate tokens) |

---

## Build Order

Implement in this exact sequence. Run `./gradlew assembleDebug` for the
Android app after every 3 features. For the backend, run `func start` locally
to test after each endpoint.

### Phase 1 — Backend Foundation (do this FIRST)

```
1.1  Scaffold Azure Functions project with Python 3.11
     - function_app.py, host.json, requirements.txt
     - Cosmos DB client with connection helper
     - Data models for: users, products, wishlists, prices, shared-lists,
       affiliate-clicks, card-rewards, charity-donations

1.2  Implement product search + identification endpoints
     - POST /api/v1/product/search (by name, URL, UPC, ASIN)
     - POST /api/v1/product/identify (image upload → Azure Computer Vision)
     - Product matching logic: UPC primary, fuzzy title secondary

1.3  Implement price collection pipeline
     - Keepa API client (product history, current prices)
     - Amazon PA-API / Creators API client
     - Walmart Affiliate API client
     - Target (Partnerize) API client
     - Best Buy Affiliate API client
     - Timer function: PriceCollector (every 6 hours)

1.4  Implement affiliate redirect service
     - GET /api/v1/redirect/{retailer}/{product}
     - Dynamically append affiliate tags per retailer
     - Log clicks to Cosmos DB for attribution
     - Return 302 redirect to retailer product page

1.5  Implement credit card rewards database
     - Static JSON file with top 50 cards (loaded into Cosmos at startup)
     - Data model: card name, issuer, base rate, rotating categories by quarter,
       retailer-specific bonuses, annual fee, sign-up bonus
     - GET /api/v1/card-rewards/{cardId}
     - GET /api/v1/card-optimizer?productId=X&retailer=Y&cards=A,B,C

1.6  Implement charity round-up infrastructure
     - See CHARITY ROUND-UP FEATURE section below for full spec
     - POST /api/v1/user/charity (set charity preference)
     - GET /api/v1/user/charity/history (donation history)
     - Timer: monthly disbursement aggregation
```

### Phase 2 — Android App Foundation

```
2.1  Scaffold Android project
     - Package: com.wishlistiq.app
     - App name: "WishList IQ" (or "WishList Intelligence")
     - Hilt setup, Room database, Retrofit client, Navigation
     - Material 3 theme:
       - Primary: #2E7D32 (green — money/savings theme)
       - Secondary: #1565C0 (blue — trust/reliability)
       - Tertiary: #F57C00 (orange — deals/urgency)
       - Background: #FAFAFA (clean white)
       - Surface: #FFFFFF
       - Error: #D32F2F
     - Bottom nav: Wishlist | Explore | Scan | Lists | Profile

2.2  Wishlist screen + Add Item flow
     - WishlistScreen: scrollable list of items with price, retailer logos,
       and "Buy Now" vs "Wait" badge
     - Add item via: URL paste, manual search, barcode scan, share sheet
     - WishlistItemCard composable: product image, name, current best price,
       retailer pill, prediction badge, card recommendation pill
     - Room entity: WishlistItemEntity (productId, addedAt, targetPrice,
       alertEnabled, notes)

2.3  Product detail screen
     - ItemDetailScreen: full product info with price comparison table
     - Price across all tracked retailers (sorted by effective price
       after credit card rewards)
     - Price history chart (Vico library) with prediction overlay
     - "Buy Now" vs "Wait" badge with explanation text
     - Credit card recommendation section
     - "Set Price Alert" button
     - "Add to Shared List" button
     - "Buy" button → affiliate redirect

2.4  Barcode scanner
     - ML Kit Barcode Scanning integration
     - Camera preview with scan frame overlay
     - On scan → product lookup → show detail or add to wishlist
     - Handle: UPC-A, UPC-E, EAN-13, EAN-8, QR codes with URLs

2.5  Credit card management
     - CreditCardsScreen: user's registered cards
     - Card picker from catalog (search by name/issuer)
     - NEVER collect card numbers — only card type selection
     - Show current quarter's bonus categories per card
     - CardOptimizerView: per-product, ranked list of user's cards
       with effective discount percentage
```

### Phase 3 — Intelligence Layer

```
3.1  Price prediction ML pipeline (backend)
     - Prophet decomposition: trend + seasonal + holiday effects
     - LightGBM ensemble with Prophet features + additional signals
     - Decision engine: Buy Now / Wait / Strong Buy thresholds
     - GET /api/v1/product/{id}/prediction → 90-day forecast
     - Timer: PredictionRefresh (daily at 3 AM)

3.2  Price chart + prediction UI (Android)
     - PriceChartView: historical prices as solid line
     - PredictionOverlay: predicted prices as dashed line with
       confidence band (shaded region)
     - Known sale events marked (Prime Day, Black Friday, etc.)
     - User's target price as horizontal dashed line
     - Tap interaction: show price + date + confidence on touch

3.3  Price alerts
     - AlertDao + PriceAlertEntity in Room
     - Backend: Timer AlertProcessor (every 15 min)
     - Push notification via FCM when price drops below threshold
     - Notification includes: product name, new price, retailer,
       "Buy Now" deep link (with affiliate tag)

3.4  Deal feed
     - ExploreScreen: personalized deals relevant to wishlist categories
     - Backend: DealScanner timer (every 2 hours)
     - DealCard composable: product image, price drop %, retailer,
       time remaining (for flash deals), "Add to Wishlist" button
```

### Phase 4 — Social & Sharing

```
4.1  Shared wishlists (gift registry)
     - CreateListScreen: name, event type (birthday/holiday/wedding/baby/other),
       event date, privacy (public link / invite-only)
     - Add items from personal wishlist to shared list
     - PublicListScreen: grid of items, "Buy This Gift" buttons (affiliate),
       purchased items marked (anonymous purchase tracking)
     - Share via: deep link, QR code, SMS, email
     - App Links for instant open: wishlistiq.com/list/{id}

4.2  Charity round-up feature (Android)
     - CharityScreen: choose charity or GoFundMe campaign
     - CharityRoundUpToggle composable: on item detail screen
       "Round up to nearest dollar for [charity name]"
     - Purchase summary shows: item price + round-up amount + charity name
     - Profile shows: total donated, charity history
     - See CHARITY ROUND-UP FEATURE section for full details

4.3  Onboarding flow
     - 4 screens: Welcome → Add first item (URL/search) → Pick your cards
       → Enable notifications
     - Skip-friendly — every screen has "I'll do this later"
     - Clean, modern design with illustrations
     - Charity opt-in on card setup screen: "Round up spare change for
       a cause you care about?"
```

### Phase 5 — Polish & Scale

```
5.1  Screenshot product identification
     - Share sheet integration: share screenshot → app identifies product
     - Azure Computer Vision for OCR + product matching
     - Fallback: manual search from extracted text

5.2  Push notification optimization
     - Smart send timing (not at 3 AM)
     - Relevance scoring: don't spam low-value alerts
     - Weekly digest option: "Your Wishlist This Week" summary

5.3  Referral program
     - Share invite code → friend signs up → both get featured deal highlights
     - Track referrals in Cosmos DB

5.4  Analytics dashboard
     - Profile screen: "You've saved $X this month"
     - "X items on your wishlist are near their lowest price"
     - "You've donated $X to [charity] this year"
```

---

## CHARITY ROUND-UP FEATURE — Full Spec

### Concept
When a user clicks "Buy" on a product, offer to round up the purchase to the
nearest dollar (or a custom amount) and donate the difference to a charity or
GoFundMe campaign of their choice. This is opt-in, transparent, and creates
an emotional hook that drives adoption and retention.

### How It Works

1. User selects a charity or GoFundMe in their profile (one-time setup)
2. On every purchase recommendation, a small toggle shows:
   "Round up $0.37 for [Charity Name]?" — default OFF
3. If enabled, the round-up amount is tracked in the app
4. The app does NOT process payments — it tracks the INTENT and shows a
   running tally. Actual donation happens one of two ways:
   - Method A (Phase 1 — simple): Monthly summary email with a "Donate Now"
     link to the charity's website. User donates directly. Honor system.
   - Method B (Phase 2 — automated): Stripe integration. User pre-authorizes
     a monthly charge. App batches round-ups and processes a single monthly
     donation. Tax receipt generated. This requires significant compliance
     work — do Method A first.

### Architecture

```kotlin
// Android
data class CharityChoice(
    val id: String,
    val name: String,
    val type: CharityType,  // NONPROFIT_501C3, GOFUNDME, CUSTOM
    val url: String,
    val logoUrl: String?,
    val ein: String?,        // IRS EIN for 501(c)(3) — for tax receipts later
    val isActive: Boolean
)

enum class CharityType { NONPROFIT_501C3, GOFUNDME, CUSTOM }

data class RoundUpRecord(
    val id: String,
    val purchaseProductId: String,
    val purchaseAmount: Double,
    val roundUpAmount: Double,   // e.g., $0.37
    val charityId: String,
    val timestamp: Long,
    val donated: Boolean         // true after user confirms donation
)
```

```python
# Backend — Cosmos DB container: charity-donations
{
    "id": "uuid",
    "userId": "user123",
    "charityId": "charity456",
    "charityName": "St. Jude Children's Research Hospital",
    "charityType": "NONPROFIT_501C3",
    "roundUpAmount": 0.37,
    "purchaseProductId": "prod789",
    "purchaseRetailer": "amazon",
    "purchaseAmount": 42.63,
    "timestamp": "2026-04-01T10:30:00Z",
    "monthlyBatchId": "2026-04",
    "donationStatus": "pending"  // pending | reminded | confirmed | donated
}
```

### Charity Discovery
- Pre-loaded list of 50 popular charities (501(c)(3) verified):
  St. Jude, Salvation Army, Red Cross, Habitat for Humanity, ASPCA,
  Feeding America, United Way, Doctors Without Borders, etc.
- GoFundMe integration: paste any GoFundMe URL to add it
- Search by name or cause category (health, animals, education, disaster relief, etc.)
- "Trending on WishList IQ" section: most-selected charities by other users

### UI Components

**CharityScreen (Profile tab):**
- "Your Cause" card: selected charity with logo, name, total donated
- "Change Charity" button → charity picker
- "Donation History" list: monthly totals, per-purchase breakdown
- "Year-to-Date: $47.23 donated to [Charity Name]" — prominent, feels good

**CharityRoundUpToggle (on ItemDetailScreen, near Buy button):**
- Small row below the "Buy at [Retailer]" button
- Charity logo (small) + "Round up $0.37 for St. Jude?" + toggle switch
- Remembers user's preference (default: matches their global setting)
- Tapping charity name → quick-change to different charity

**Monthly Summary Notification (Method A):**
- Push notification at end of month: "You saved $34 in round-ups for St. Jude
  this month! Tap to donate."
- Opens a screen with: total amount, itemized list, "Donate Now" button
  (deep link to charity website with amount pre-suggested)
- "Mark as Donated" button (user confirms they donated)

### Future: Automated Donations (Method B)
- Stripe Connect integration
- User adds payment method specifically for donations
- Monthly batch processing: single charge for all round-ups
- Tax receipt generation (for 501(c)(3) charities)
- IRS compliance: issue 1099 if >$250/year donated through platform
- This is a Phase 5+ feature — DO NOT build this now, just architect
  the data model to support it later

### Privacy & Compliance
- Charity selection is private — not shared with other users
- Round-up amounts are not included in affiliate tracking
- If GoFundMe: clearly state "WishList IQ is not affiliated with GoFundMe.
  Donations are made directly through GoFundMe's platform."
- No percentage of affiliate revenue goes to charity (that's the USER's
  round-up, not the company's margin) — keep this distinction clear

---

## Key Conventions

- Package name: com.wishlistiq.app
- App display name: "WishList IQ"
- Kotlin + Jetpack Compose only, no XML layouts
- All networking via Retrofit + OkHttp
- All DI via Hilt
- All local storage via Room + DataStore
- All user-facing text should be clean, modern, concise — NOT elderly-focused
  (different from Safe Companion). Target audience: 25-55 year old shoppers
- Price chart library: Vico (com.patrykandpatrick.vico)
- NEVER collect or store credit card numbers. Only card TYPE.
- EVERY "Buy" button must route through the affiliate redirect service
- ALL prices displayed in USD with 2 decimal places
- Dark mode support from day one (Material 3 dynamic colors)

---

## Affiliate Attribution Rules

- Every purchase link MUST go through /api/v1/redirect/{retailer}/{product}
- The redirect service appends the correct affiliate tag per retailer:
  - Amazon: ?tag=wishlistiq-20
  - Walmart: via Impact affiliate link
  - Target: via Partnerize click URL
  - Best Buy: via affiliate network link
  - eBay: via eBay Partner Network campaign ID
- Log every click: userId, productId, retailer, timestamp, referrer
- NEVER show a direct retailer link that bypasses the redirect — this is
  how the business makes money

---

## Credit Card Rewards Data (Initial 20 Cards)

Seed the card rewards database with at least these 20 popular cards.
Store as JSON in backend/services/data/cards.json, loaded into Cosmos DB.

1. Chase Freedom Flex — 5% rotating quarterly, 3% dining/drugstores, 1% all
2. Chase Freedom Unlimited — 1.5% all, 3% dining/drugstores, 5% travel via portal
3. Discover it — 5% rotating quarterly, 1% all
4. Citi Double Cash — 2% all (1% buy + 1% pay)
5. Citi Custom Cash — 5% top spend category (auto), 1% all
6. Capital One SavorOne — 3% dining/entertainment/grocery/streaming, 1% all
7. Capital One Quicksilver — 1.5% all
8. Amex Blue Cash Everyday — 3% grocery (up to $6K/yr), 2% gas/transit, 1% all
9. Amex Blue Cash Preferred — 6% grocery (up to $6K/yr), 6% streaming, 3% gas/transit
10. Amazon Prime Visa — 5% Amazon/Whole Foods, 2% restaurants/gas/transit, 1% all
11. Target RedCard — 5% Target purchases
12. Walmart Rewards Card — 5% Walmart.com, 2% in-store/restaurants/travel, 1% all
13. Costco Anywhere Visa — 4% gas, 3% restaurants/travel, 2% Costco, 1% all
14. Bank of America Customized Cash — 3% choice category, 2% grocery/wholesale, 1% all
15. Wells Fargo Active Cash — 2% all
16. US Bank Cash+ — 5% two choice categories (quarterly), 2% one category, 1% all
17. Apple Card — 3% Apple/select merchants via Apple Pay, 2% Apple Pay, 1% all
18. PayPal Cashback — 3% PayPal purchases, 2% all
19. Venmo Credit Card — 3% top spend category (auto), 2% second, 1% all
20. Chase Sapphire Preferred — 3x dining/streaming/online grocery, 2x travel, 1x all

Include: current quarter's rotating categories, any retailer-specific bonuses,
annual fee, and point valuation (for non-cashback cards).

---

## Status Table

| ID | Feature | Status |
|----|---------|--------|
| 1 | Backend scaffold + Cosmos DB | PENDING |
| 2 | Product search + identification API | PENDING |
| 3 | Price collection pipeline (5 retailers) | PENDING |
| 4 | Affiliate redirect service | PENDING |
| 5 | Credit card rewards database + optimizer | PENDING |
| 6 | Charity round-up backend | PENDING |
| 7 | Android scaffold + navigation + theme | PENDING |
| 8 | Wishlist screen + add item flow | PENDING |
| 9 | Product detail screen + price comparison | PENDING |
| 10 | Barcode scanner | PENDING |
| 11 | Credit card management UI | PENDING |
| 12 | Price prediction ML pipeline | PENDING |
| 13 | Price chart + prediction UI | PENDING |
| 14 | Price drop alerts | PENDING |
| 15 | Deal feed | PENDING |
| 16 | Shared wishlists (gift registry) | PENDING |
| 17 | Charity round-up UI | PENDING |
| 18 | Onboarding flow | PENDING |
| 19 | Screenshot product identification | PENDING |
| 20 | Push notification optimization | PENDING |
| 21 | Referral program | PENDING |
| 22 | Savings analytics dashboard | PENDING |

Mark each DONE as you complete it. Run builds frequently.

---

## Getting Started

```bash
# Clone and enter the project
mkdir wishlist-intelligence && cd wishlist-intelligence
git init

# Backend setup
cd backend
python -m venv .venv
source .venv/bin/activate  # or .venv\Scripts\activate on Windows
pip install azure-functions azure-cosmos lightgbm prophet requests
func init --python

# Android setup (in Android Studio or via CLI)
cd ../android
# Open in Android Studio and let Gradle sync
```

Begin with Phase 1 (backend) items 1-6, then move to Phase 2 (Android) items 7-11.
