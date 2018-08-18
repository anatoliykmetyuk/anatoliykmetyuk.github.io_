import $ivy.`org.typelevel::cats-core:1.1.0`
import $ivy.`org.typelevel::cats-effect:0.10.1`
import $ivy.`io.circe::circe-core:0.10.0-M1`
import $ivy.`io.circe::circe-yaml:0.8.0`
import $ivy.`com.github.pathikrit::better-files:3.6.0`
import $ivy.`com.functortech::thera:0.0.3-SNAPSHOT`
import $file.post, post._

import better.files._, File._, java.io.{ File => JFile }
import cats._, cats.implicits._, io.circe._
import thera._


val src      = file"src/"
val compiled = file"_site/"

implicit val copyOptions: CopyOptions = File.CopyOptions(overwrite = true)
implicit val openOptions: OpenOptions = List(
  java.nio.file.StandardOpenOption.CREATE)

val tmlFilters = Map(
  "post" -> filter.command { """pandoc
    --toc
    --webtex
    --template=../src/templates/pandoc-post.html
    --filter /pandoc-filters/pandocfilters/examples/graphviz.py
    --filter /pandoc-filters/pandocfilters/examples/plantuml.py
    --filter /pandoc-filters/include-code/include-code.py"""
  }

, "raw-pandoc" -> filter.command { "pandoc" }
)

def log(msg: String) = att { println(msg) }

def build = for {
  // Config
  _ <- log("Reading configuration")
  config <- exn { yaml.parser parse (src/"data/data.yml").contentAsString }
  // _ <- log(s"Config parsed:\n${config}")

  // Assets, code, some static files
  _ <- log("Copying static assets")
  _    = List("assets", "code", "CNAME", "favicon.png")
          .foreach { f => src/f copyTo compiled/f }

  // CSS
  _ <- log("Processing CSS assets")
  css <- template(src/"private-assets/css/all.css"
    , fragmentResolver = name => src/s"private-assets/css/${name}.css")
  _    = compiled/"assets/all.css" write css

  // Generate posts, create index.html
  allPosts = (src/"posts").collectChildren(_.extension.contains(".md"))
    .map(Post.fromFile).toList
  _ <- log(s"Starting to process ${allPosts.length} posts...")
  _  = compiled/"posts" createDirectoryIfNotExists()
  _ <- allPosts.traverse(processPost(_, config))
  _ <- index(allPosts, config)

  // Delete the code directory
  _  = compiled/"code" delete()
} yield ()
  

def processPost(post: Post, globalConfig: Json): Ef[Unit] =
  for {
    _        <- log(s"Processing ${post.inFile}")
    postJson <- post.asJson
    config    = globalConfig.deepMerge(postJson)
    res      <- template(post.inFile, config, templateFilters = tmlFilters)
    _         = compiled/"posts"/post.htmlName write res
  } yield ()

def index(posts: List[Post], globalConfig: Json): Ef[Unit] =
  for {
    _ <- log("Generating index.html")
    postsJsonArr <- posts.sortBy(_.date).reverse.traverse(_.asJson)
    config = globalConfig.deepMerge( Json.obj(
                "posts" -> Json.arr(postsJsonArr: _*)) )
    res <- template(src/"index.html", config)
    _    = compiled/"index.html" write res
  } yield ()

run { build }