package fin

import Types._

import java.util.UUID
import cats.effect.IO

object Types {
  case class QueriesBooksArgs(searchFilter: SearchFilter, results: Option[Int])
  case class QueriesBookArgs(isbn: String)
  case class QueriesCollectionArgs(id: UUID)
  case class MutationsCreateCollectionArgs(
      name: String,
      books: Option[List[Book]]
  )
  case class MutationsDeleteCollectionArgs(id: UUID)
  case class MutationsChangeCollectionNameArgs(id: UUID, name: String)
  case class MutationsAddBookArgs(id: UUID, book: Book)
  case class Book(
      title: String,
      authors: List[String],
      description: String,
      isbn: String,
      thumbnailUri: String
  )
  case class Collection(id: UUID, name: String, books: List[String])
  case class Mutations(
      createCollection: MutationsCreateCollectionArgs => Collection,
      deleteCollection: MutationsDeleteCollectionArgs => Collection,
      changeCollectionName: MutationsChangeCollectionNameArgs => Collection,
      addBook: MutationsAddBookArgs => Collection
  )

  sealed trait SearchFilter extends scala.Product with scala.Serializable

  object SearchFilter {
    case class Keywords(titleKeywords: String, authorKeywords: String)
        extends SearchFilter
    case class AuthorKeywords(keywords: String) extends SearchFilter
    case class TitleKeywords(keywords: String)  extends SearchFilter
  }

}

object Operations {

  case class Queries(
      books: QueriesBooksArgs => IO[List[Book]],
      book: QueriesBookArgs => IO[Book],
      collections: IO[List[Collection]],
      collection: QueriesCollectionArgs => IO[Collection]
  )

}

