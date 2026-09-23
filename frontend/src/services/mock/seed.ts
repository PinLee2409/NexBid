import type {
  AppNotification,
  Auction,
  AuditLog,
  AutoBid,
  Bid,
  Category,
  Order,
  Payment,
  Product,
  SellerSummary,
  User,
  WatchlistItem,
} from "@/types";

import { CATEGORY_IMAGES, PRODUCT_IMAGES } from "./images";

/**
 * Seed data for the mock backend.
 *
 * Timestamps are generated relative to process start so the marketplace always
 * has genuinely live, upcoming and finished auctions no matter when the app is
 * opened. Nothing outside `src/services/mock` should import this file.
 */

export const SEED_EPOCH = Date.now();

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

function at(offsetMs: number): string {
  return new Date(SEED_EPOCH + offsetMs).toISOString();
}

const minutes = (n: number) => n * MINUTE;
const hours = (n: number) => n * HOUR;
const days = (n: number) => n * DAY;

/* -------------------------------------------------------------------------- */
/*                                   Users                                    */
/* -------------------------------------------------------------------------- */

export const CURRENT_USER_ID = "user-pin";

export const USERS: User[] = [
  {
    id: CURRENT_USER_ID,
    fullName: "Pin Le",
    email: "pin@nexbid.com",
    displayName: "pin***",
    roles: ["BUYER", "SELLER"],
    status: "ACTIVE",
    createdAt: at(-days(420)),
  },
  {
    id: "user-alex",
    fullName: "Alex Turner",
    email: "alex@nexbid.com",
    displayName: "alex***",
    roles: ["BUYER"],
    status: "ACTIVE",
    createdAt: at(-days(310)),
  },
  {
    id: "user-john",
    fullName: "John Mercer",
    email: "john@nexbid.com",
    displayName: "john***",
    roles: ["BUYER"],
    status: "ACTIVE",
    createdAt: at(-days(260)),
  },
  {
    id: "user-mika",
    fullName: "Mika Tanaka",
    email: "mika@nexbid.com",
    displayName: "mika***",
    roles: ["BUYER", "SELLER"],
    status: "ACTIVE",
    createdAt: at(-days(540)),
  },
  {
    id: "user-sara",
    fullName: "Sara Novak",
    email: "sara@nexbid.com",
    displayName: "sara***",
    roles: ["BUYER"],
    status: "ACTIVE",
    createdAt: at(-days(190)),
  },
  {
    id: "user-dmitri",
    fullName: "Dmitri Volkov",
    email: "dmitri@nexbid.com",
    displayName: "dmit***",
    roles: ["BUYER"],
    status: "BLOCKED",
    createdAt: at(-days(95)),
  },
  {
    id: "seller-atelier",
    fullName: "Atelier Nord",
    email: "hello@ateliernord.com",
    displayName: "atelier***",
    roles: ["SELLER"],
    status: "ACTIVE",
    createdAt: at(-days(880)),
  },
  {
    id: "seller-vault",
    fullName: "The Vault Collective",
    email: "team@vaultcollective.com",
    displayName: "vault***",
    roles: ["SELLER"],
    status: "ACTIVE",
    createdAt: at(-days(620)),
  },
  {
    id: "seller-lumen",
    fullName: "Lumen Optics",
    email: "sales@lumenoptics.com",
    displayName: "lumen***",
    roles: ["SELLER"],
    status: "ACTIVE",
    createdAt: at(-days(410)),
  },
  {
    id: "admin-root",
    fullName: "Nora Adams",
    email: "admin@nexbid.com",
    displayName: "nora***",
    roles: ["ADMIN"],
    status: "ACTIVE",
    createdAt: at(-days(900)),
  },
];

export const SELLERS: SellerSummary[] = [
  {
    id: "seller-atelier",
    displayName: "Atelier Nord",
    rating: 4.9,
    totalSales: 312,
    memberSince: at(-days(880)),
    verified: true,
  },
  {
    id: "seller-vault",
    displayName: "The Vault Collective",
    rating: 4.8,
    totalSales: 184,
    memberSince: at(-days(620)),
    verified: true,
  },
  {
    id: "seller-lumen",
    displayName: "Lumen Optics",
    rating: 4.7,
    totalSales: 96,
    memberSince: at(-days(410)),
    verified: true,
  },
  {
    id: "user-mika",
    displayName: "Mika Tanaka",
    rating: 4.6,
    totalSales: 41,
    memberSince: at(-days(540)),
    verified: false,
  },
  {
    id: CURRENT_USER_ID,
    displayName: "Pin Le",
    rating: 4.8,
    totalSales: 23,
    memberSince: at(-days(420)),
    verified: true,
  },
];

/* -------------------------------------------------------------------------- */
/*                                 Categories                                 */
/* -------------------------------------------------------------------------- */

export const CATEGORIES: Category[] = [
  {
    id: "cat-technology",
    slug: "technology",
    name: "Technology",
    description: "Flagship machines, rare silicon and hard-to-find hardware.",
    imageUrl: CATEGORY_IMAGES.technology,
    auctionCount: 128,
  },
  {
    id: "cat-watches",
    slug: "watches",
    name: "Watches",
    description: "Swiss references, vintage dials and collector pieces.",
    imageUrl: CATEGORY_IMAGES.watches,
    auctionCount: 94,
  },
  {
    id: "cat-collectibles",
    slug: "collectibles",
    name: "Collectibles",
    description: "Graded cards, sealed sets and cultural artefacts.",
    imageUrl: CATEGORY_IMAGES.collectibles,
    auctionCount: 212,
  },
  {
    id: "cat-sneakers",
    slug: "sneakers",
    name: "Sneakers",
    description: "Deadstock grails and limited collaborations.",
    imageUrl: CATEGORY_IMAGES.sneakers,
    auctionCount: 167,
  },
  {
    id: "cat-fashion",
    slug: "fashion",
    name: "Fashion",
    description: "Archive pieces, leather goods and runway rarities.",
    imageUrl: CATEGORY_IMAGES.fashion,
    auctionCount: 143,
  },
  {
    id: "cat-art",
    slug: "art",
    name: "Art",
    description: "Editions, originals and signed prints from working artists.",
    imageUrl: CATEGORY_IMAGES.art,
    auctionCount: 76,
  },
  {
    id: "cat-cameras",
    slug: "cameras",
    name: "Cameras",
    description: "Digital bodies, cult optics and film classics.",
    imageUrl: CATEGORY_IMAGES.cameras,
    auctionCount: 88,
  },
];

/* -------------------------------------------------------------------------- */
/*                                  Products                                  */
/* -------------------------------------------------------------------------- */

interface ProductSeed {
  id: string;
  sellerId: string;
  categoryId: string;
  name: string;
  description: string;
  condition: Product["condition"];
  status: Product["status"];
  images: readonly string[];
  createdOffsetDays: number;
}

const PRODUCT_SEEDS: ProductSeed[] = [
  {
    id: "prod-macbook-m3",
    sellerId: "seller-atelier",
    categoryId: "cat-technology",
    name: 'MacBook Pro 16" M3 Max',
    description:
      "Space Black, 48GB unified memory, 1TB SSD. Purchased new in March and used in a smoke-free studio for colour grading. Battery health reports 97% with 41 cycles. Ships in the original box with the 140W adapter, braided USB-C cable and unused documentation. AppleCare+ remains active until next spring and is transferable to the winning bidder.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.macbook,
    createdOffsetDays: 18,
  },
  {
    id: "prod-rolex-sub",
    sellerId: "seller-vault",
    categoryId: "cat-watches",
    name: "Rolex Submariner Date 126610LN",
    description:
      "2023 production, 41mm Oystersteel case on an Oyster bracelet with Glidelock clasp. Unpolished with crisp lugs and a clean bezel insert. Runs +2 seconds per day on a recent timegrapher check. Complete set: inner and outer boxes, warranty card dated to the original purchase, swing tags and booklets.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.rolex,
    createdOffsetDays: 12,
  },
  {
    id: "prod-leica-q3",
    sellerId: "seller-lumen",
    categoryId: "cat-cameras",
    name: "Leica Q3",
    description:
      "60MP full-frame compact with the fixed Summilux 28mm f/1.7 ASPH. Shutter count just under 3,200. Body and lens barrel are free of brassing; the tilting screen has never been separated from its protector. Includes two batteries, the original charger, strap, thumb rest and a Peak Design quick-release plate.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.leica,
    createdOffsetDays: 9,
  },
  {
    id: "prod-jordan-chicago",
    sellerId: "seller-vault",
    categoryId: "cat-sneakers",
    name: 'Air Jordan 1 Retro High OG "Chicago"',
    description:
      "US 10, deadstock and never laced. Stored flat in a climate-controlled case since release day. Original box is intact with a clean label and no shipping scars. Includes both lace sets and the original tissue. Authenticated in-house against the release-week production markers.",
    condition: "NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.jordan,
    createdOffsetDays: 7,
  },
  {
    id: "prod-sony-a7rv",
    sellerId: "seller-lumen",
    categoryId: "cat-cameras",
    name: "Sony α7R V Body",
    description:
      "61MP sensor with the AI processing unit, roughly 6,400 actuations. Light handling marks on the base plate, sensor professionally cleaned last month. Bundled with three genuine NP-FZ100 batteries, dual charger, cage-free L-bracket and a 128GB CFexpress Type A card.",
    condition: "GOOD",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.sony,
    createdOffsetDays: 15,
  },
  {
    id: "prod-omega-seamaster",
    sellerId: "seller-vault",
    categoryId: "cat-watches",
    name: "Vintage Omega Seamaster 300",
    description:
      "1966 reference with a beautifully even tropical dial and original tritium plots that have aged to warm cream. Serviced two years ago by an independent Omega specialist; movement runs strong within COSC tolerance. Case retains sharp bevels. Presented on a period-correct leather strap with the original bracelet included.",
    condition: "GOOD",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.omega,
    createdOffsetDays: 22,
  },
  {
    id: "prod-charizard",
    sellerId: "seller-vault",
    categoryId: "cat-collectibles",
    name: "Charizard Base Set Holo — PSA 8",
    description:
      "1999 unlimited print, graded PSA 8 NM-MT with excellent centring and a clean holofoil surface. Minor edge wear on the reverse consistent with the grade. Slab is unblemished and the certification number verifies against the PSA registry. Ships double-boxed with a card saver and signature confirmation.",
    condition: "GOOD",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.charizard,
    createdOffsetDays: 5,
  },
  {
    id: "prod-keyboard",
    sellerId: CURRENT_USER_ID,
    categoryId: "cat-technology",
    name: "Tokyo60 Limited Mechanical Keyboard",
    description:
      "Hand-assembled 60% board in anodised silver, one of 300 units from the original group buy. Lubed and filmed linear switches, brass weight, FR4 plate and a foam-dampened case. Typing test video available on request. Includes the aluminium carry case, spare switches and a coiled aviator cable.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.keyboard,
    createdOffsetDays: 4,
  },
  {
    id: "prod-birkin",
    sellerId: "seller-atelier",
    categoryId: "cat-fashion",
    name: "Hermès Birkin 30 Togo Leather",
    description:
      "Gold Togo leather with palladium hardware, blind stamp confirms recent production. Corners are sharp with no rubbing and the hardware retains its factory film. Complete with dust bag, lock, keys, clochette, rain cover and box. Independently authenticated with a certificate included in the lot.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.handbag,
    createdOffsetDays: 11,
  },
  {
    id: "prod-art-print",
    sellerId: CURRENT_USER_ID,
    categoryId: "cat-art",
    name: 'Mara Ellis — "Northfall No. 7"',
    description:
      "Six-colour screen print on 300gsm Somerset Satin, 70 × 100cm. Numbered 14/40 in pencil and signed by the artist. Never framed, stored flat in archival tissue since acquisition directly from the studio. Includes the original certificate of authenticity and studio invoice.",
    condition: "NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.artPrint,
    createdOffsetDays: 8,
  },
  {
    id: "prod-vision-pro",
    sellerId: "seller-atelier",
    categoryId: "cat-technology",
    name: "Apple Vision Pro 1TB",
    description:
      "1TB configuration with both the Solo Knit and Dual Loop bands, sizes W/M included. Under 20 hours of total use; light seal and cushions are pristine. Ships with the travel case, battery, 30W adapter and polishing cloth. Zeiss optical inserts are not included.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.visionPro,
    createdOffsetDays: 6,
  },
  {
    id: "prod-film-camera",
    sellerId: "seller-lumen",
    categoryId: "cat-cameras",
    name: "Canon AE-1 Program + FD 50mm f/1.4",
    description:
      "Fully serviced film body with a fresh light seal replacement and a silent, accurate shutter across all speeds. The famous squeal is gone. Paired with a clean FD 50mm f/1.4 showing no haze, fungus or separation. Includes strap, cap and a fresh battery.",
    condition: "GOOD",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.filmCamera,
    createdOffsetDays: 14,
  },
  {
    id: "prod-sneaker-collab",
    sellerId: CURRENT_USER_ID,
    categoryId: "cat-sneakers",
    name: "Sacai x Nike LDWaffle — US 9.5",
    description:
      "Worn twice indoors with original insoles intact and no creasing on the toe box. Midsoles are bright with no yellowing. Comes with the original box, both lace options and the hangtag. Cleaned and deodorised before listing.",
    condition: "GOOD",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.sneakerCollab,
    createdOffsetDays: 3,
  },
  {
    id: "prod-headphones",
    sellerId: "seller-atelier",
    categoryId: "cat-technology",
    name: "Focal Utopia 2022 Headphones",
    description:
      "Reference open-back headphones with pure beryllium M-shaped domes. Pads and headband show no flaking. Includes the hard carry case, both the 1.2m unbalanced and 3m balanced XLR cables, plus the original documentation.",
    condition: "LIKE_NEW",
    status: "IN_AUCTION",
    images: PRODUCT_IMAGES.headphones,
    createdOffsetDays: 20,
  },
  {
    id: "prod-guitar",
    sellerId: "seller-vault",
    categoryId: "cat-collectibles",
    name: "1974 Fender Stratocaster — Olympic White",
    description:
      "All-original hardware and electronics with a lightly checked nitro finish that has aged to a warm cream. Frets show honest play wear with plenty of life remaining. Neck date and pot codes are consistent. Ships in the original hard case with the tremolo arm and bridge cover.",
    condition: "FAIR",
    status: "AVAILABLE",
    createdOffsetDays: 2,
    images: PRODUCT_IMAGES.guitar,
  },
  {
    id: "prod-lens",
    sellerId: CURRENT_USER_ID,
    categoryId: "cat-cameras",
    name: "Voigtländer 35mm f/1.2 Nokton",
    description:
      "Manual focus M-mount lens with clean glass throughout — no haze, fungus or separation. Aperture blades are snappy and oil-free, focus is smooth from infinity to the close limit. Includes both caps, the original hood and the box.",
    condition: "LIKE_NEW",
    status: "AVAILABLE",
    images: PRODUCT_IMAGES.leica,
    createdOffsetDays: 3,
  },
  {
    id: "prod-tube-amp",
    sellerId: CURRENT_USER_ID,
    categoryId: "cat-technology",
    name: "Vintage Tube Amplifier",
    description:
      "Integrated valve amplifier, recapped two years ago. Warm output with no hum at idle. Tubes tested and matched.",
    condition: "GOOD",
    status: "AVAILABLE",
    images: PRODUCT_IMAGES.headphones,
    createdOffsetDays: 6,
  },
  {
    id: "prod-vinyl",
    sellerId: CURRENT_USER_ID,
    categoryId: "cat-collectibles",
    name: "First Pressing Vinyl Archive — 12 LPs",
    description:
      "Twelve first-pressing LPs spanning jazz and soul, all graded VG+ to NM with original inner sleeves. Jackets show light shelf wear only. Full track-by-track condition notes are provided in the lot documentation.",
    condition: "GOOD",
    status: "AVAILABLE",
    createdOffsetDays: 1,
    images: PRODUCT_IMAGES.vinyl,
  },
];

export const PRODUCTS: Product[] = PRODUCT_SEEDS.map((seed) => ({
  id: seed.id,
  sellerId: seed.sellerId,
  categoryId: seed.categoryId,
  name: seed.name,
  description: seed.description,
  condition: seed.condition,
  status: seed.status,
  images: seed.images.map((url, index) => ({
    id: `${seed.id}-img-${index}`,
    url,
    alt: `${seed.name} — view ${index + 1}`,
    sortOrder: index,
  })),
  createdAt: at(-days(seed.createdOffsetDays)),
  updatedAt: at(-days(Math.max(0, seed.createdOffsetDays - 1))),
}));

/* -------------------------------------------------------------------------- */
/*                                  Auctions                                  */
/* -------------------------------------------------------------------------- */

interface AuctionSeed {
  id: string;
  productId: string;
  status: Auction["status"];
  startingPrice: number;
  currentPrice: number;
  minimumIncrement: number;
  /** Offsets in ms relative to process start. Negative is in the past. */
  startOffset: number;
  endOffset: number;
  bidCount: number;
  viewerCount: number;
  winnerId?: string | null;
  antiSnipingEnabled?: boolean;
  extensionCount?: number;
  rejectionReason?: string;
}

const AUCTION_SEEDS: AuctionSeed[] = [
  // ---- Live, ending within minutes: the hero of the marketplace -----------
  {
    id: "auction-rolex-sub",
    productId: "prod-rolex-sub",
    status: "ACTIVE",
    startingPrice: 9500,
    currentPrice: 13450,
    minimumIncrement: 250,
    startOffset: -hours(20),
    endOffset: minutes(8) + 21_000,
    bidCount: 42,
    viewerCount: 318,
    antiSnipingEnabled: true,
    extensionCount: 1,
  },
  {
    id: "auction-jordan-chicago",
    productId: "prod-jordan-chicago",
    status: "ACTIVE",
    startingPrice: 900,
    currentPrice: 2380,
    minimumIncrement: 50,
    startOffset: -hours(11),
    endOffset: minutes(6) + 12_000,
    bidCount: 37,
    viewerCount: 204,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-macbook-m3",
    productId: "prod-macbook-m3",
    status: "ACTIVE",
    startingPrice: 1800,
    currentPrice: 2940,
    minimumIncrement: 60,
    startOffset: -hours(30),
    endOffset: minutes(46),
    bidCount: 28,
    viewerCount: 142,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-leica-q3",
    productId: "prod-leica-q3",
    status: "ACTIVE",
    startingPrice: 3900,
    currentPrice: 5120,
    minimumIncrement: 80,
    startOffset: -hours(8),
    endOffset: hours(2) + minutes(14),
    bidCount: 19,
    viewerCount: 96,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-charizard",
    productId: "prod-charizard",
    status: "ACTIVE",
    startingPrice: 1200,
    currentPrice: 2650,
    minimumIncrement: 75,
    startOffset: -hours(26),
    endOffset: hours(5) + minutes(38),
    bidCount: 33,
    viewerCount: 187,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-omega-seamaster",
    productId: "prod-omega-seamaster",
    status: "ACTIVE",
    startingPrice: 4200,
    currentPrice: 6300,
    minimumIncrement: 150,
    startOffset: -days(1) - hours(4),
    endOffset: hours(9) + minutes(5),
    bidCount: 24,
    viewerCount: 73,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-keyboard",
    productId: "prod-keyboard",
    status: "ACTIVE",
    startingPrice: 320,
    currentPrice: 615,
    minimumIncrement: 25,
    startOffset: -hours(14),
    endOffset: hours(18) + minutes(40),
    bidCount: 16,
    viewerCount: 54,
    antiSnipingEnabled: false,
  },
  {
    id: "auction-birkin",
    productId: "prod-birkin",
    status: "ACTIVE",
    startingPrice: 11000,
    currentPrice: 14800,
    minimumIncrement: 400,
    startOffset: -hours(19),
    endOffset: days(1) + hours(3),
    bidCount: 12,
    viewerCount: 128,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-sony-a7rv",
    productId: "prod-sony-a7rv",
    status: "ACTIVE",
    startingPrice: 2400,
    currentPrice: 3080,
    minimumIncrement: 70,
    startOffset: -hours(6),
    endOffset: days(2) + hours(1),
    bidCount: 11,
    viewerCount: 61,
    antiSnipingEnabled: true,
  },

  // ---- Upcoming ------------------------------------------------------------
  {
    id: "auction-vision-pro",
    productId: "prod-vision-pro",
    status: "SCHEDULED",
    startingPrice: 2600,
    currentPrice: 2600,
    minimumIncrement: 100,
    startOffset: hours(3) + minutes(30),
    endOffset: days(1) + hours(3),
    bidCount: 0,
    viewerCount: 0,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-art-print",
    productId: "prod-art-print",
    status: "SCHEDULED",
    startingPrice: 850,
    currentPrice: 850,
    minimumIncrement: 50,
    startOffset: hours(9),
    endOffset: days(2) + hours(9),
    bidCount: 0,
    viewerCount: 0,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-film-camera",
    productId: "prod-film-camera",
    status: "SCHEDULED",
    startingPrice: 280,
    currentPrice: 280,
    minimumIncrement: 20,
    startOffset: days(1) + hours(2),
    endOffset: days(3) + hours(2),
    bidCount: 0,
    viewerCount: 0,
    antiSnipingEnabled: false,
  },
  {
    id: "auction-sneaker-collab",
    productId: "prod-sneaker-collab",
    status: "SCHEDULED",
    startingPrice: 420,
    currentPrice: 420,
    minimumIncrement: 30,
    startOffset: days(2) + hours(5),
    endOffset: days(4) + hours(5),
    bidCount: 0,
    viewerCount: 0,
    antiSnipingEnabled: true,
  },

  // ---- Finished ------------------------------------------------------------
  {
    id: "auction-headphones",
    productId: "prod-headphones",
    status: "ENDED",
    startingPrice: 2200,
    currentPrice: 3450,
    minimumIncrement: 100,
    startOffset: -days(4),
    endOffset: -hours(5),
    bidCount: 21,
    viewerCount: 0,
    winnerId: CURRENT_USER_ID,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-guitar-past",
    productId: "prod-guitar",
    status: "COMPLETED",
    startingPrice: 6000,
    currentPrice: 9200,
    minimumIncrement: 200,
    startOffset: -days(12),
    endOffset: -days(6),
    bidCount: 29,
    viewerCount: 0,
    winnerId: CURRENT_USER_ID,
    antiSnipingEnabled: true,
  },

  // ---- Seller / admin workflow states -------------------------------------
  {
    id: "auction-vinyl-pending",
    productId: "prod-vinyl",
    status: "PENDING_APPROVAL",
    startingPrice: 640,
    currentPrice: 640,
    minimumIncrement: 40,
    startOffset: days(1),
    endOffset: days(4),
    bidCount: 0,
    viewerCount: 0,
    antiSnipingEnabled: true,
  },
  {
    id: "auction-amp-rejected",
    productId: "prod-tube-amp",
    status: "REJECTED",
    startingPrice: 900,
    currentPrice: 900,
    minimumIncrement: 50,
    startOffset: days(2),
    endOffset: days(5),
    bidCount: 0,
    viewerCount: 0,
    antiSnipingEnabled: true,
    rejectionReason:
      "The photos do not show the serial number plate, and the description is missing the service history. Add both and resubmit.",
  },
];

export const AUCTIONS: Auction[] = AUCTION_SEEDS.map((seed, index) => {
  const product = PRODUCTS.find((item) => item.id === seed.productId);
  if (!product) {
    throw new Error(`Mock seed error: unknown product ${seed.productId}`);
  }

  return {
    id: seed.id,
    productId: seed.productId,
    sellerId: product.sellerId,
    // The catalogue is numbered from 001 in seed order.
    lotNumber: index + 1,
    startingPrice: seed.startingPrice,
    currentPrice: seed.currentPrice,
    minimumIncrement: seed.minimumIncrement,
    startTime: at(seed.startOffset),
    endTime: at(seed.endOffset),
    status: seed.status,
    bidCount: seed.bidCount,
    winnerId: seed.winnerId ?? null,
    antiSniping: {
      enabled: seed.antiSnipingEnabled ?? true,
      windowSeconds: 30,
      extensionSeconds: 120,
    },
    extensionCount: seed.extensionCount ?? 0,
    rejectionReason: seed.rejectionReason,
    createdAt: product.createdAt,
    updatedAt: at(-minutes(4)),
  } satisfies Auction;
});

/* -------------------------------------------------------------------------- */
/*                                    Bids                                    */
/* -------------------------------------------------------------------------- */

const BIDDER_ROTATION = [
  CURRENT_USER_ID,
  "user-alex",
  "user-john",
  "user-sara",
  "user-mika",
] as const;

/**
 * Rebuilds a plausible bid ladder ending exactly at `currentPrice`, so bid
 * history, bid count and the displayed price are always consistent.
 */
function buildBidLadder(auction: Auction): Bid[] {
  if (auction.bidCount === 0) return [];

  const bids: Bid[] = [];
  const visibleCount = Math.min(auction.bidCount, 12);
  const endMs = new Date(auction.endTime).getTime();
  const startMs = new Date(auction.startTime).getTime();
  const latestBidMs = Math.min(SEED_EPOCH, endMs) - 6_000;

  // A seller can never bid on their own lot (spec §4.2), so they are removed
  // from the rotation rather than filtered out afterwards.
  const eligibleBidders = BIDDER_ROTATION.filter(
    (bidderId) => bidderId !== auction.sellerId,
  );

  for (let index = 0; index < visibleCount; index += 1) {
    const amount = auction.currentPrice - index * auction.minimumIncrement;
    if (amount < auction.startingPrice) break;

    const bidderId =
      eligibleBidders[(index + auction.id.length) % eligibleBidders.length];
    const bidder = USERS.find((user) => user.id === bidderId);
    // Bids get sparser as we look further back in the ladder.
    const createdMs = Math.max(
      startMs,
      latestBidMs - Math.round(index * (12_000 + index * 9_000)),
    );

    bids.push({
      id: `${auction.id}-bid-${index}`,
      auctionId: auction.id,
      bidderId,
      bidderDisplayName: bidder?.displayName ?? "user***",
      amount,
      automatic: index % 5 === 3,
      createdAt: new Date(createdMs).toISOString(),
    });
  }

  return bids;
}

export const BIDS: Bid[] = AUCTIONS.flatMap(buildBidLadder);

/* -------------------------------------------------------------------------- */
/*                                  Auto bids                                 */
/* -------------------------------------------------------------------------- */

export const AUTO_BIDS: AutoBid[] = [
  {
    id: "autobid-1",
    auctionId: "auction-leica-q3",
    userId: CURRENT_USER_ID,
    maxAmount: 5800,
    active: true,
    createdAt: at(-hours(3)),
    updatedAt: at(-hours(3)),
  },
];

/* -------------------------------------------------------------------------- */
/*                                  Watchlist                                 */
/* -------------------------------------------------------------------------- */

export const WATCHLIST_AUCTION_IDS = [
  "auction-rolex-sub",
  "auction-charizard",
  "auction-vision-pro",
  "auction-birkin",
  "auction-art-print",
];

export const WATCHLIST_SEED: Omit<WatchlistItem, "auction">[] =
  WATCHLIST_AUCTION_IDS.map((auctionId, index) => ({
    id: `watch-${index}`,
    userId: CURRENT_USER_ID,
    auctionId,
    createdAt: at(-hours(index + 1)),
  }));

/* -------------------------------------------------------------------------- */
/*                               Notifications                                */
/* -------------------------------------------------------------------------- */

export const NOTIFICATIONS: AppNotification[] = [
  {
    id: "notif-1",
    userId: CURRENT_USER_ID,
    type: "OUTBID",
    title: "You've been outbid",
    message: "Rolex Submariner Date 126610LN is now at $13,450.",
    isRead: false,
    href: "/auctions/auction-rolex-sub",
    createdAt: at(-minutes(2)),
  },
  {
    id: "notif-2",
    userId: CURRENT_USER_ID,
    type: "AUCTION_EXTENDED",
    title: "Auction extended",
    message:
      "Rolex Submariner Date 126610LN was extended by 2 minutes after a last-minute bid.",
    isRead: false,
    href: "/auctions/auction-rolex-sub",
    createdAt: at(-minutes(9)),
  },
  {
    id: "notif-3",
    userId: CURRENT_USER_ID,
    type: "AUCTION_ENDING",
    title: "Ending soon",
    message: "Air Jordan 1 Retro High OG \"Chicago\" ends in under 10 minutes.",
    isRead: false,
    href: "/auctions/auction-jordan-chicago",
    createdAt: at(-minutes(14)),
  },
  {
    id: "notif-4",
    userId: CURRENT_USER_ID,
    type: "PAYMENT_REQUIRED",
    title: "Payment required",
    message: "Complete payment for Focal Utopia 2022 Headphones within 48 hours.",
    isRead: false,
    href: "/orders",
    createdAt: at(-hours(5)),
  },
  {
    id: "notif-5",
    userId: CURRENT_USER_ID,
    type: "AUCTION_WON",
    title: "You won an auction",
    message: "Focal Utopia 2022 Headphones sold to you at $3,450.",
    isRead: true,
    href: "/my-wins",
    createdAt: at(-hours(5) - minutes(1)),
  },
  {
    id: "notif-6",
    userId: CURRENT_USER_ID,
    type: "AUCTION_STARTING",
    title: "Starting soon",
    message: "Apple Vision Pro 1TB opens for bidding in 3 hours.",
    isRead: true,
    href: "/auctions/auction-vision-pro",
    createdAt: at(-hours(8)),
  },
  {
    id: "notif-7",
    userId: CURRENT_USER_ID,
    type: "PAYMENT_SUCCESS",
    title: "Payment confirmed",
    message: "Your payment for 1974 Fender Stratocaster was successful.",
    isRead: true,
    href: "/orders",
    createdAt: at(-days(5)),
  },
];

/* -------------------------------------------------------------------------- */
/*                             Payments and orders                            */
/* -------------------------------------------------------------------------- */

export const PAYMENTS: Payment[] = [
  {
    id: "pay-headphones",
    auctionId: "auction-headphones",
    userId: CURRENT_USER_ID,
    amount: 3450,
    status: "PENDING",
    expiredAt: at(hours(43)),
    createdAt: at(-hours(5)),
    updatedAt: at(-hours(5)),
  },
  {
    id: "pay-guitar",
    auctionId: "auction-guitar-past",
    userId: CURRENT_USER_ID,
    amount: 9200,
    status: "SUCCESS",
    expiredAt: at(-days(4)),
    createdAt: at(-days(6)),
    updatedAt: at(-days(5)),
  },
];

export const ORDERS: Order[] = [
  {
    id: "order-headphones",
    auctionId: "auction-headphones",
    buyerId: CURRENT_USER_ID,
    sellerId: "seller-atelier",
    paymentId: "pay-headphones",
    amount: 3450,
    status: "PENDING_PAYMENT",
    createdAt: at(-hours(5)),
    updatedAt: at(-hours(5)),
  },
  {
    id: "order-guitar",
    auctionId: "auction-guitar-past",
    buyerId: CURRENT_USER_ID,
    sellerId: "seller-vault",
    paymentId: "pay-guitar",
    amount: 9200,
    status: "COMPLETED",
    createdAt: at(-days(6)),
    updatedAt: at(-days(3)),
  },
];

/* -------------------------------------------------------------------------- */
/*                                 Audit logs                                 */
/* -------------------------------------------------------------------------- */

export const AUDIT_LOGS: AuditLog[] = [
  {
    id: "audit-1",
    userId: "admin-root",
    actorDisplayName: "nora***",
    action: "AUCTION_APPROVED",
    entityType: "Auction",
    entityId: "auction-rolex-sub",
    oldValue: "PENDING_APPROVAL",
    newValue: "SCHEDULED",
    ipAddress: "10.4.18.22",
    createdAt: at(-days(1)),
  },
  {
    id: "audit-2",
    userId: CURRENT_USER_ID,
    actorDisplayName: "pin***",
    action: "BID_PLACED",
    entityType: "Auction",
    entityId: "auction-rolex-sub",
    oldValue: "13200",
    newValue: "13450",
    ipAddress: "10.4.18.91",
    createdAt: at(-minutes(2)),
  },
  {
    id: "audit-3",
    userId: null,
    actorDisplayName: "system",
    action: "AUCTION_EXTENDED",
    entityType: "Auction",
    entityId: "auction-rolex-sub",
    oldValue: "21:00:00",
    newValue: "21:02:00",
    ipAddress: "127.0.0.1",
    createdAt: at(-minutes(9)),
  },
  {
    id: "audit-4",
    userId: "admin-root",
    actorDisplayName: "nora***",
    action: "USER_BLOCKED",
    entityType: "User",
    entityId: "user-dmitri",
    oldValue: "ACTIVE",
    newValue: "BLOCKED",
    ipAddress: "10.4.18.22",
    createdAt: at(-days(2)),
  },
  {
    id: "audit-5",
    userId: null,
    actorDisplayName: "system",
    action: "AUCTION_ENDED",
    entityType: "Auction",
    entityId: "auction-headphones",
    oldValue: "ACTIVE",
    newValue: "ENDED",
    ipAddress: "127.0.0.1",
    createdAt: at(-hours(5)),
  },
];
