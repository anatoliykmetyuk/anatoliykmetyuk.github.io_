import $ivy.`com.github.pathikrit::better-files:3.9.0`
import $ivy.`com.akmetiuk::thera:0.2.0-M1`

import $file.post, post._
import $file.util, util._

import better.files._, File._, java.io.{ File => JFile }
import thera._


val src      = file"src/"
val compiled = file"_site/"
val allPosts: List[Post] = (src/"posts")
  .collectChildren(_.extension.contains(".md"))
  .map(Post.fromFile).toList
val defaultCtx: ValueHierarchy =
  ValueHierarchy.yaml((src/"data/data.yml").contentAsString)

implicit val copyOptions: CopyOptions =
  File.CopyOptions(overwrite = true)
implicit val openOptions: OpenOptions =
  List(java.nio.file.StandardOpenOption.CREATE)

def htmlFragment(implicit ctx: => ValueHierarchy): Function =
  names("htmlFragment" ->
    Function.function[Text] { name =>
      Thera((src/s"fragments/${name}.html").contentAsString)
        .mkString
    }
  )


// === Build procedure ===
def build(): Unit = {
  genStaticAssets()
  genCss()
  genPosts()
  genIndex()
  cleanup()
}

def genStaticAssets(): Unit = {
  println("Copying static assets")
  for (f <- List("assets", "code", "CNAME", "favicon.png"))
    src/f copyTo compiled/f
}

def genCss(): Unit = {
  println("Processing CSS assets")
  implicit val ctx = defaultCtx + names(
    "cssAsset" -> Function.function[Text] { name =>
      (src/s"private-assets/css/${name}.css").contentAsString }
  )

  val css = Thera(src/"private-assets/css/all.css").mkString
  write(compiled/"assets/all.css", css)
}

def genPosts(): Unit = {
  println(s"Processing ${allPosts.length} posts...")
  (compiled/"posts").createDirectoryIfNotExists()

  for ( (post, idx) <- allPosts.zipWithIndex ) {
    println(s"[ $idx / ${allPosts.length} ] Processing ${post.inFile}")
    implicit val ctx = defaultCtx + post.thera.context +
      postTemplate.context + defaultTemplate.context +
      htmlFragmentCtx

    val result = pipeThera(post, postTemplate, defaultTemplate)

    write(compiled/"posts"/post.htmlName, result)
  }
}

def genIndex(): Unit = {
  println("Generating index.html")
  implicit val ctx =  defaultCtx + htmlFragmentCtx + names(
    "allPosts" -> Arr(allPosts.sortBy(_.date)
      .reverse.map(_.asValue))
  )

  val res = Thera((src/"index.html").contentAsString).mkString
  write(compiled/"index.html", res)
}

def cleanup(): Unit =
  (compiled/"code").delete()

build()
