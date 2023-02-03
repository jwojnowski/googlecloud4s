package me.wojnowski.googlecloud4s.firestore

object Helpers {

  implicit class ShortNameString(string: String) {
    def toDocumentName: Name = Name.parse(string).toOption.get
  }

  implicit class CollectionIdString(string: String) {
    def toCollectionId: CollectionId = CollectionId.parse(string).toOption.get
  }

}
