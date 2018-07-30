import $ivy.`org.typelevel::cats-core:1.1.0`
import $ivy.`org.typelevel::cats-effect:0.10.1`
import $ivy.`io.circe::circe-core:0.10.0-M1`
import $ivy.`io.circe::circe-yaml:0.8.0`
import $ivy.`com.github.pathikrit::better-files:3.6.0`
import $ivy.`com.functortech::thera:0.0.1-SNAPSHOT`

import java.util.Date
import java.text.SimpleDateFormat

import better.files._, File._, java.io.{ File => JFile }
import cats._, cats.implicits._, io.circe._
import thera._

case class Post(inFile: File, date: Date) {
  lazy val htmlName: String = s"${inFile.nameWithoutExtension}.html"

  lazy val url: String = s"/posts/$htmlName"
  
  lazy val dateStr: String = Post.dateFormatter.format(date)


  /** WARNING: this is a config, not variables! Variables are a part of config. */
  def localConfig: Ef[Json] =
    templates.parseConfig(inFile.contentAsString).map(_._1)

  def title: Ef[String] =
    for {
      config <- localConfig
      title  <- exn { config.hcursor.downField("variables").get[String]("title") }
    } yield title

  def asJson: Ef[Json] = title.map { t => Json.obj(
    "date"  -> Json.fromString(dateStr)
  , "url"   -> Json.fromString(url    )
  , "title" -> Json.fromString(t      ) ) }
}

object Post {
  val dateParser    = new SimpleDateFormat("yyyy-MM-dd"    )
  val dateFormatter = new SimpleDateFormat("MMMMM dd, yyyy")
  
  def fromFile(f: File): Post = {
    val postName = """(\d{4}-\d{2}-\d{2})-.*\.md""".r
    f.name match { case postName(dateStr) => Post(
      inFile = f
    , date   = dateParser.parse(dateStr)) }
  }
}
