package me.wojnowski.googlecloud4s.firestore

object Helpers {

  implicit class ShortNameString(string: String) {
    def toDocumentId: DocumentId = DocumentId.parse(string).toOption.get
  }

  implicit class CollectionIdString(string: String) {
    def toCollectionId: CollectionId = CollectionId.parse(string).toOption.get
  }

}
