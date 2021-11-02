package me.wojnowski.googlecloud4s.auth

import java.time.Instant

case class IdentityToken(value: String, targetAudience: TargetAudience, expires: Instant)
