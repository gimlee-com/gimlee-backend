package com.gimlee.chat.performance

import java.nio.file.Path
import java.net.http.{HttpClient, HttpRequest}
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import scala.io.Source

object Helper {

  def initChats(resourcesDir: Path): Unit = {
    val chatsFile = Source.fromFile(s"$resourcesDir/resources/chats.csv")
    val lines = chatsFile.getLines().drop(1).toList
    chatsFile.close()

    val numberOfChats = lines.size
    println(s"Creating $numberOfChats chats...")

    val client = HttpClient.newBuilder().build()

    lines.foreach(chat => {
      val request = HttpRequest.newBuilder()
        .uri(URI.create(s"http://localhost:12060/api/chat/$chat/touch"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .header("Cookie", "JWT=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6IjYwZmJmYmZiZmJmYmZiZmJmYmZiZmJmYiIsInVzZXJuYW1lIjoiYWRtaW4iLCJyb2xlcyI6WyJVU0VSIiwiQURNSU4iXX0.some-signature")
        .build()

      client.send(request, BodyHandlers.ofString())
    })

    println("Chats have been initialized")
  }
}
