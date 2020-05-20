import $ivy.`com.akmetiuk::thera:0.2.0-M1`

import $file.post, post._
import $file.util, util._

import os._
import thera._, ValueHierarchy.names


val allPosts: List[Post] = (src/"posts")
  .collectChildren(_.extension.contains(".md"))
  .map(Post.fromFile).toList
val defaultCtx: ValueHierarchy =
  ValueHierarchy.yaml((src/"data/data.yml").contentAsString)


def htmlFragmentCtx(implicit ctx: => ValueHierarchy): ValueHierarchy =
  names("htmlFragment" ->
    Function.function[Str] { name =>
      Thera((src/s"fragments/${name.value}.html").contentAsString)
        .mkValue.asStr
    }
  )

val postTemplate = Thera((src/"templates"/"post.html").contentAsString)
val defaultTemplate = Thera((src/"templates"/"default.html").contentAsString)


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
    os.copy.over(src/f, compiled/f)
}

def genCss(): Unit = {
  println("Processing CSS assets")
  implicit val ctx = defaultCtx + names(
    "cssAsset" -> Function.function[Str] { name =>
      Str((src/s"private-assets/css/${name.value}.css").contentAsString) }
  )

  val css = Thera((src/"private-assets/css/all.css").contentAsString).mkString
  os.write(compiled/"assets/all.css", css)
}

def genPosts(): Unit = {
  println(s"Processing ${allPosts.length} posts...")
  (compiled/"posts").createDirectoryIfNotExists()

  for ( (post, idx) <- allPosts.zipWithIndex ) {
    println(s"[ $idx / ${allPosts.length} ] Processing ${post.file}")
    implicit lazy val ctx: ValueHierarchy = defaultCtx + post.thera.context +
      postTemplate.context + defaultTemplate.context +
      htmlFragmentCtx

    val result = pipeThera(post.thera, postTemplate, defaultTemplate)

    write(compiled/"posts"/post.htmlName, result)
  }
}

def genIndex(): Unit = {
  println("Generating index.html")
  implicit lazy val ctx: ValueHierarchy = defaultCtx + htmlFragmentCtx + names(
    "allPosts" -> Arr(allPosts.sortBy(_.date)
      .reverse.map(_.asValue))
  )

  val res = pipeThera(Thera((src/"index.html")
    .contentAsString), defaultTemplate)

  write(compiled/"index.html", res)
}

def cleanup(): Unit =
  (compiled/"code").delete()

build()
