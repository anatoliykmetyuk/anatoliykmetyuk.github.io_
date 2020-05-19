// TODO
// 1. Implement Thera's pipe and mapBody methods
// 2. Move the Python scripts into this repo
// 3. Each step of the generation should have its
//    own context to prevent one giant shared state
// 4. Move the config out of this file, make this file
//    all about the logic
// 5. Move the Scala sources to a separate dir

import $ivy.`com.github.pathikrit::better-files:3.6.0`
import $ivy.`com.akmetiuk::thera:0.2.0-M1`
import $file.post, post._

import better.files._, File._, java.io.{ File => JFile }
import thera._


val src      = file"src/"
val compiled = file"_site/"
val allPosts: List[Post] = (src/"posts")
  .collectChildren(_.extension.contains(".md"))
  .map(Post.fromFile).toList

implicit val copyOptions: CopyOptions =
  File.CopyOptions(overwrite = true)
implicit val openOptions: OpenOptions =
  List(java.nio.file.StandardOpenOption.CREATE)
implicit val ctx: ValueHierarchy =
  ValueHierarchy.yaml((src/"data/data.yml").contentAsString) +
  names(
    "cssAsset" -> Function.function[Text] { name =>
      (src/s"private-assets/css/${name}.css").contentAsString },
    "allPosts" -> Arr(allPosts.sortBy(_.date)
      .reverse.map(_.asValue))
  )


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
  val css = Thera(src/"private-assets/css/all.css").mkString
  write(compiled/"assets/all.css", css)
}

def genPosts(): Unit = {
  println(s"Processing ${allPosts.length} posts...")
  (compiled/"posts").createDirectoryIfNotExists()

  for ( (post, idx) <- allPosts.zipWithIndex ) {
    println(s"[ $idx / ${allPosts.length} ] Processing ${post.inFile}")

    implicit val ctx = post.thera.context +
      postTemplate.context + defaultTemplate.context

    val result = pipeThera(post, postTemplate, defaultTemplate)

    write(compiled/"posts"/post.htmlName, result)
  }
}

def genIndex(): Unit = {
  println("Generating index.html")
  val res = Thera((src/"index.html").contentAsString).mkString
  write(compiled/"index.html", res)
}

def cleanup(): Unit =
  (compiled/"code").delete()

build()
