CREATE TABLE IF NOT EXISTS books(
  isbn          TEXT NOT NULL PRIMARY KEY,
  title         TEXT NOT NULL,
  authors       TEXT NOT NULL,
  description   TEXT NOT NULL,
  thumbnail_uri TEXT NOT NULL,
  added         DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS collections(
  name           TEXT NOT NULL PRIMARY KEY,
  preferred_sort TEXT CHECK( preferred_sort IN ('DateAdded','Title','Author', 'Rating') ) COLLATE NOCASE NOT NULL
);

CREATE TABLE IF NOT EXISTS collection_books(
  collection_name TEXT NOT NULL,
  isbn            TEXT NOT NULL,
  FOREIGN KEY(collection_name) REFERENCES collections(name) ON DELETE CASCADE,
  FOREIGN KEY(isbn)            REFERENCES books(isbn),
  PRIMARY KEY(collection_name, isbn)
);

CREATE TABLE IF NOT EXISTS currently_reading_books(
  isbn     TEXT NOT NULL PRIMARY KEY,
  started  DATE NOT NULL,
  FOREIGN KEY(isbn) REFERENCES books(isbn)
);

CREATE TABLE IF NOT EXISTS read_books(
  isbn     TEXT NOT NULL,
  started  DATE NOT NULL,
  finished DATE NOT NULL,
  FOREIGN KEY(isbn) REFERENCES books(isbn),
  PRIMARY KEY(isbn, started)
);

CREATE TABLE IF NOT EXISTS rated_books(
  isbn   TEXT    NOT NULL PRIMARY KEY,
  rating INTEGER NOT NULL,
  FOREIGN KEY(isbn) REFERENCES books(isbn)
);

