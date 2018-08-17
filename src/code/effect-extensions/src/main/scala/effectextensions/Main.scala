package effectextensions

import cats._, cats.implicits._, cats.data._, cats.effect._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import better.files._, better.files.File._, java.io.{ File => JFile }

trait Base {
  def requestHandler(requestBody: String): Ef[Unit] =
    for {
      bodyJson <- parse(requestBody)                   .etr
      _        <- println(s"Parsed body: $bodyJson")   .sus
      fileName <- bodyJson.hcursor.get[String]("file") .etr
      fileBody <- File(fileName).contentAsString       .exn
      _        <- println(s"Parsed file: $fileBody")   .sus
    } yield ()
}

object Successful extends Base with App {
  requestHandler("""{"file": "foo.txt"}""") .run
}

object CirceParserFailed extends Base with App {
  requestHandler("""{"file": "foo.txt}""") .run
}

object CirceKeyFailed extends Base with App {
  requestHandler("""{"stuff": "foo.txt"}""") .run
}

object FileFailed extends Base with App {
  requestHandler("""{"file": "stuff"}""") .run
}
