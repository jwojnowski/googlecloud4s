package me.wojnowski.googlecloud4s.auth

import java.time.Instant

case class Token(value: String, scope: Scope, expires: Instant)
