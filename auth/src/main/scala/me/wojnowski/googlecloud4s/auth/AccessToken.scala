package me.wojnowski.googlecloud4s.auth

import java.time.Instant

case class AccessToken(value: String, scope: Scopes, expires: Instant)
