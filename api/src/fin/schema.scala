package fin

import Types._

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

  case class Queries[F[_]](
      books: QueriesBooksArgs => F[List[Book]],
      book: QueriesBookArgs => F[Book],
      collections: F[List[Collection]],
      collection: QueriesCollectionArgs => F[Collection]
  )

  case class Mutations[F[_]](
      createCollection: MutationsCreateCollectionArgs => F[Collection],
      deleteCollection: MutationsDeleteCollectionArgs => F[Option[Boolean]],
      changeCollectionName: MutationsChangeCollectionNameArgs => F[Collection],
      addBook: MutationsAddBookArgs => F[Collection],
      removeBook: MutationsRemoveBookArgs => F[Option[Boolean]]
  )

}

