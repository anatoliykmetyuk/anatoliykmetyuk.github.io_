import $ivy.`com.github.pathikrit::better-files:3.6.0`
import $ivy.`com.functortech::thera:0.2.0-M1`

import java.util.Date
import java.text.SimpleDateFormat

import better.files._
import thera._

case class Post(inFile: File, date: Date) {
  lazy val htmlName: String = s"${inFile.nameWithoutExtension}.html"
  lazy val url: String = s"/posts/$htmlName"
  lazy val dateStr: String = Post.dateFormatter.format(date)
  lazy val src: String = ???
  lazy val template: Template = Thera(src)
  lazy val context: ValueHierarchy = template.context
  lazy val title = context("variables.title").asStr.value
}

object Post {
  val dateParser    = new SimpleDateFormat("yyyy-MM-dd"    )
  val dateFormatter = new SimpleDateFormat("MMM dd, yyyy")

  def fromFile(f: File): Post = {
    val postName = """(\d{4}-\d{2}-\d{2})-.*\.md""".r
    f.name match { case postName(dateStr) => Post(
      inFile = f
    , date   = dateParser.parse(dateStr)) }
  }
}
