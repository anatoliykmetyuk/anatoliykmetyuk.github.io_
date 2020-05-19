import $ivy.`com.github.pathikrit::better-files:3.9.0`
import $ivy.`com.akmetiuk::thera:0.2.0-M1`

import java.util.Date
import java.text.SimpleDateFormat

import better.files._
import thera._

case class Post(file: File, date: Date) {
  lazy val htmlName: String = s"${file.nameWithoutExtension}.html"
  lazy val url: String = s"/posts/$htmlName"
  lazy val dateStr: String = Post.dateFormatter.format(date)
  lazy val src: String = file.contentAsString
  lazy val thera: Template = {
    val tml = Thera(src)
    tml.copy(context = tml.context + ValueHierarchy.names(
      "date" -> Str(dateStr),
      "url" -> Str(url)
    ))
  }
  lazy val title: String = thera.context("variables.title").asStr.value
  lazy val asValue: Value = ValueHierarchy.names(
    "date"  -> Str(dateStr),
    "url"   -> Str(url),
    "title" -> Str(title),
  )
}

object Post {
  val dateParser    = new SimpleDateFormat("yyyy-MM-dd"    )
  val dateFormatter = new SimpleDateFormat("MMM dd, yyyy")

  def fromFile(f: File): Post = {
    val postName = """(\d{4}-\d{2}-\d{2})-.*\.md""".r
    f.name match { case postName(dateStr) => Post(
      file = f
    , date   = dateParser.parse(dateStr)) }
  }
}
