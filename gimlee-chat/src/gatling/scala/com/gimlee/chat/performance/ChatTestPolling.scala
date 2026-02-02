package com.gimlee.chat.performance

import io.gatling.core.Predef._
import io.gatling.core.config.GatlingFiles
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps

class ChatTestPolling extends Simulation {

  private val httpProtocol = http.baseUrl("http://localhost:12060")

  private val usersFeed = csv("resources/users.csv").circular
  private val randomMessagesFeed = jsonFile("resources/messages.json").random
  private val randomChatsFeed = csv("resources/chats.csv").random

  private val baseHeaders = Map(
    "Content-Type" -> "application/json")

  private val scn = scenario("MessagesStream")
    .feed(usersFeed)
    .feed(randomMessagesFeed)
    .feed(randomChatsFeed)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(2 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(2 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(1 seconds)

    .exec(http("User is typing")
      .post("/api/chat/${chatId}/typing")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders)
      .body(StringBody("""""")).asJson)

    .pause(1 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(2 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(1 seconds)

    .exec(http("User is typing")
      .post("/api/chat/${chatId}/typing")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders)
      .body(StringBody("""""")).asJson)

    .pause(1 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(1 seconds)

    .exec(http("User sends message")
      .post("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders)
      .body(StringBody("""{ "message": "${message}" }""")).asJson)

    .pause(1 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(1)

    .exec(http("User is typing")
      .post("/api/chat/${chatId}/typing")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders)
      .body(StringBody("""""")).asJson)

    .pause(200 millis)

    .exec(http("User sends message")
      .post("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders)
      .body(StringBody("""{ "message": "${message}" }""")).asJson)

    .pause(1800 millis)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(2 seconds)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(1900 millis)

    .exec(http("User scrolls up to get older messages")
      .get("/api/chat/${chatId}/messages?beforeId=5c27a39f1e80ed8160a76d10")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(100 millis)

    .exec(http("[polling] User gets latest messages from chat")
      .get("/api/chat/${chatId}/messages")
      .header("Cookie", "JWT=${jwt}")
      .header("Accept", "application/json")
      .headers(baseHeaders))

    .pause(1 seconds)

  before {
    Helper.initChats(GatlingFiles.resourcesDirectory)
  }

  setUp(
    scn.inject(
      rampConcurrentUsers(0) to 2000 during (120 seconds)
    )
  ).assertions(
    global.responseTime.percentile4.lt(1000),
    global.responseTime.percentile3.lt(700),
    global.responseTime.percentile2.lt(700),
    global.responseTime.percentile1.lt(700),
    global.successfulRequests.percent.gt(99.95)
  ).protocols(httpProtocol)
}
