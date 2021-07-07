package fin

import Types._

import cats.effect.IO

object Types {
  case class QueriesBooksArgs(
      titleKeywords: Option[String],
      authorKeywords: Option[String],
      maxResults: Option[Int]
  )
  case class QueriesBookArgs(isbn: String)
  case class QueriesCollectionArgs(name: String)
  case class MutationsCreateCollectionArgs(
      name: String,
      books: Option[List[Book]]
  )
  case class MutationsDeleteCollectionArgs(name: String)
  case class MutationsChangeCollectionNameArgs(
      currentName: String,
      newName: String
  )
  case class MutationsAddBookArgs(collection: String, book: Book)
  case class MutationsRemoveBookArgs(collection: String, isbn: String)
  case class Book(
      title: String,
      authors: List[String],
      description: String,
      isbn: String,
      thumbnailUri: String
  )
  case class Collection(name: String, books: List[Book])

}

object Operations {

  case class Queries(
      books: QueriesBooksArgs => IO[List[Book]],
      book: QueriesBookArgs => IO[Book],
      collections: IO[List[Collection]],
      collection: QueriesCollectionArgs => IO[Collection]
  )

  case class Mutations(
      createCollection: MutationsCreateCollectionArgs => IO[Collection],
      deleteCollection: MutationsDeleteCollectionArgs => IO[Option[Boolean]],
      changeCollectionName: MutationsChangeCollectionNameArgs => IO[Collection],
      addBook: MutationsAddBookArgs => IO[Collection],
      removeBook: MutationsRemoveBookArgs => IO[Option[Boolean]]
  )

}

