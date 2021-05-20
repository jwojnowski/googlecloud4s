package me.wojnowski.googlecloud4s.auth

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uri
import io.circe.Decoder
import io.circe.refined._

case class Credentials(
  privateKey: String,
  clientEmail: String,
  authUri: String Refined Uri,
  tokenUri: String Refined Uri
)

object Credentials {
  implicit val decoder: Decoder[Credentials] =
    Decoder.forProduct4("private_key", "client_email", "auth_uri", "token_uri")(Credentials.apply)
}
