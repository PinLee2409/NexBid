/**
 * Remote product imagery used by the mock catalogue.
 *
 * Every id below was checked visually against the product it represents.
 * When the real media service arrives only `Product.images[].url` changes —
 * nothing else in the app references these ids.
 */

const UNSPLASH = "https://images.unsplash.com";

function photo(id: string, width = 1400): string {
  return `${UNSPLASH}/${id}?auto=format&fit=crop&w=${width}&q=80`;
}

export const PRODUCT_IMAGES = {
  macbook: [
    photo("photo-1517336714731-489689fd1ca8"),
    photo("photo-1611186871348-b1ce696e52c9"),
    photo("photo-1531297484001-80022131f5a1"),
  ],
  rolex: [
    photo("photo-1587836374828-4dbafa94cf0e"),
    photo("photo-1614164185128-e4ec99c436d7"),
    photo("photo-1548171915-e79a380a2a4b"),
  ],
  leica: [
    photo("photo-1510127034890-ba27508e9f1c"),
    photo("photo-1452780212940-6f5c0d14d848"),
    photo("photo-1516035069371-29a1b244cc32"),
  ],
  jordan: [
    photo("photo-1556906781-9a412961c28c"),
    photo("photo-1552346154-21d32810aba3"),
    photo("photo-1584735175315-9d5df23860e6"),
  ],
  sony: [
    photo("photo-1502982720700-bfff97f2ecac"),
    photo("photo-1516035069371-29a1b244cc32"),
    photo("photo-1500634245200-e5245c7574ef"),
  ],
  omega: [
    photo("photo-1524805444758-089113d48a6d"),
    photo("photo-1622434641406-a158123450f9"),
    photo("photo-1594576722512-582bcd46fba3"),
  ],
  // Only one dependable photo exists for this lot; the gallery handles it.
  charizard: [photo("photo-1613771404784-3a5686aa2be3")],
  keyboard: [
    photo("photo-1587829741301-dc798b83add3"),
    photo("photo-1618384887929-16ec33fab9ef"),
    photo("photo-1595225476474-87563907a212"),
  ],
  handbag: [
    photo("photo-1584917865442-de89df76afd3"),
    photo("photo-1548036328-c9fa89d128fa"),
    photo("photo-1594223274512-ad4803739b7c"),
  ],
  artPrint: [
    photo("photo-1579783902614-a3fb3927b6a5"),
    photo("photo-1549289524-06cf8837ace5"),
    photo("photo-1577720580479-7d839d829c73"),
  ],
  visionPro: [
    photo("photo-1622979135225-d2ba269cf1ac"),
    photo("photo-1592478411213-6153e4ebc07d"),
    photo("photo-1617802690992-15d93263d3a9"),
  ],
  filmCamera: [
    photo("photo-1452780212940-6f5c0d14d848"),
    photo("photo-1500634245200-e5245c7574ef"),
  ],
  sneakerCollab: [
    photo("photo-1514989940723-e8e51635b782"),
    photo("photo-1465453869711-7e174808ace9"),
    photo("photo-1600269452121-4f2416e55c28"),
  ],
  guitar: [
    photo("photo-1550985616-10810253b84d"),
    photo("photo-1516924962500-2b4b3b99ea02"),
  ],
  headphones: [
    photo("photo-1505740420928-5e560c06d30e"),
    photo("photo-1583394838336-acd977736f90"),
    photo("photo-1484704849700-f032a568e944"),
  ],
  vinyl: [
    photo("photo-1603048588665-791ca8aea617"),
    photo("photo-1461360370896-922624d12aa1"),
    photo("photo-1483412033650-1015ddeb83d1"),
  ],
} as const;

export const CATEGORY_IMAGES = {
  technology: photo("photo-1518770660439-4636190af475", 900),
  watches: photo("photo-1524592094714-0f0654e20314", 900),
  collectibles: photo("photo-1608889175123-8ee362201f81", 900),
  sneakers: photo("photo-1543508282-6319a3e2621f", 900),
  fashion: photo("photo-1445205170230-053b83016050", 900),
  art: photo("photo-1513519245088-0e12902e5a38", 900),
  cameras: photo("photo-1502920917128-1aa500764cbd", 900),
} as const;
