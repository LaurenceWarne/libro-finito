package fin

import Types._

import java.util.UUID
import cats.effect.IO

object Types {
  case class QueriesBooksArgs(
      titleKeywords: Option[String],
      authorKeywords: Option[String],
      maxResults: Option[Int]
  )
  case class QueriesBookArgs(isbn: String)
  case class QueriesCollectionArgs(id: UUID)
  case class MutationsCreateCollectionArgs(
      name: String,
      books: Option[List[Book]]
  )
  case class MutationsDeleteCollectionArgs(id: String)
  case class MutationsChangeCollectionNameArgs(id: String, name: String)
  case class MutationsAddBookArgs(id: String, book: Book)
  case class Book(
      title: String,
      authors: List[String],
      description: String,
      isbn: String,
      thumbnailUri: String
  )
  case class Collection(id: String, name: String, books: List[Book])

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
      deleteCollection: MutationsDeleteCollectionArgs => IO[Collection],
      changeCollectionName: MutationsChangeCollectionNameArgs => IO[Collection],
      addBook: MutationsAddBookArgs => IO[Collection]
  )

}

