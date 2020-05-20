import $ivy.`com.akmetiuk::thera:0.2.0-M1`

import $file.post, post._
import $file.util, util._

import os._
import thera._, ValueHierarchy.names


val allPosts: List[Post] = walk(
    path = src/"posts",
    skip = p => p.ext !="md"
  ).map(Post.fromPath).toList
val defaultCtx: ValueHierarchy =
  ValueHierarchy.yaml(read(src/"data"/"data.yml"))


def htmlFragmentCtx(implicit ctx: => ValueHierarchy): ValueHierarchy =
  names("htmlFragment" ->
    Function.function[Str] { name =>
      Thera(read(src/s"fragments/${name.value}.html"))
        .mkValue.asStr
    }
  )

val postTemplate = Thera(read(src/"templates"/"post.html"))
val defaultTemplate = Thera(read(src/"templates"/"default.html"))


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
    copy.over(src/f, compiled/f)
}

def genCss(): Unit = {
  println("Processing CSS assets")
  implicit val ctx = defaultCtx + names(
    "cssAsset" -> Function.function[Str] { name =>
      Str(read(src/s"private-assets"/"css"/"${name.value}.css")) }
  )

  val css = Thera(read(src/"private-assets"/"css"/"all.css")).mkString
  writeFile(compiled/"assets"/"all.css", css)
}

def genPosts(): Unit = {
  println(s"Processing ${allPosts.length} posts...")

  for ( (post, idx) <- allPosts.zipWithIndex ) {
    println(s"[ $idx / ${allPosts.length} ] Processing ${post.file}")
    implicit lazy val ctx: ValueHierarchy = defaultCtx + post.thera.context +
      postTemplate.context + defaultTemplate.context +
      htmlFragmentCtx

    val result = pipeThera(post.thera, postTemplate, defaultTemplate)
    writeFile(compiled/"posts"/post.htmlName, result)
  }
}

def genIndex(): Unit = {
  println("Generating index.html")
  implicit lazy val ctx: ValueHierarchy = defaultCtx + htmlFragmentCtx + names(
    "allPosts" -> Arr(allPosts.sortBy(_.date)
      .reverse.map(_.asValue))
  )

  val res = pipeThera(
    Thera(read(src/"index.html")), defaultTemplate)

  writeFile(compiled/"index.html", res)
}

def cleanup(): Unit =
  remove(compiled/"code")

build()
