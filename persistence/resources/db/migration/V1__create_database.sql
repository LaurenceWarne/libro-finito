CREATE TABLE IF NOT EXISTS books(
  isbn TEXT NOT NULL PRIMARY KEY,
  authors TEXT NOT NULL,
  description TEXT NOT NULL,
  thumbnail_uri TEXT NOT NULL,
  added DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS collections(
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS collection_books(
  collection_id TEXT NOT NULL,
  isbn TEXT NOT NULL,
  FOREIGN KEY(collection_id) REFERENCES collections(id),
  FOREIGN KEY(isbn) REFERENCES books(isbn),
  PRIMARY KEY(collection_id, isbn)
);

CREATE TABLE IF NOT EXISTS read_books(
  isbn TEXT NOT NULL PRIMARY KEY,
  date DATE NOT NULL,
  FOREIGN KEY(isbn) REFERENCES books(isbn)
);

CREATE TABLE IF NOT EXISTS rated_books(
  isbn TEXT NOT NULL PRIMARY KEY,
  rating INTEGER NOT NULL,
  FOREIGN KEY(isbn) REFERENCES books(isbn)
);

