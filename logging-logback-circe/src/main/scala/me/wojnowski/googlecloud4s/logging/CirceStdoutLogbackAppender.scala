package me.wojnowski.googlecloud4s.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase

import java.time.Instant
import io.circe.JsonObject
import io.circe.syntax._

class CirceStdoutLogbackAppender extends AppenderBase[ILoggingEvent] {

  override def append(event: ILoggingEvent): Unit = {
    val json = JsonObject(
      "message" -> event.getFormattedMessage.asJson,
      "severity" -> event.getLevel.toString.asJson,
      "timestamp" -> Instant.ofEpochMilli(event.getTimeStamp).asJson,
      "logging.googleapis.com/labels" -> JsonObject
        .fromIterable(
          Seq(
            "levelName" -> event.getLevel.toString.asJson,
            "loggerName" -> event.getLoggerName.asJson,
            "threadName" -> event.getThreadName.asJson
          ) ++ Option(event.getThrowableProxy).map(tp => "stacktrace" -> ThrowableProxyUtil.asString(tp).asJson)
        )
        .asJson
    ).asJson
    println(json.noSpaces)
  }

}
