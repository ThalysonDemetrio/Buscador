CREATE TABLE IF NOT EXISTS cached_offer (
    source           TEXT    NOT NULL,
    external_id      TEXT    NOT NULL,
    category_path    TEXT    NOT NULL,
    title            TEXT    NOT NULL,
    title_normalized TEXT    NOT NULL,
    effective_cost   TEXT    NOT NULL,
    reference_price  TEXT    NOT NULL,
    available        INTEGER NOT NULL,
    seller           TEXT,
    warranty         TEXT,
    third_party      INTEGER NOT NULL,
    url              TEXT    NOT NULL,
    PRIMARY KEY (source, external_id)
);

CREATE TABLE IF NOT EXISTS category_refresh (
    category_path TEXT PRIMARY KEY,
    refreshed_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS price_observation (
    source         TEXT NOT NULL,
    external_id    TEXT NOT NULL,
    observed_at    TEXT NOT NULL,
    effective_cost TEXT NOT NULL,
    PRIMARY KEY (source, external_id, observed_at)
);
