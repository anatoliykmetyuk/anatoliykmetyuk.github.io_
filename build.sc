import $ivy.`org.typelevel::cats-core:1.1.0`
import $ivy.`org.typelevel::cats-effect:0.10.1`

import $ivy.`io.circe::circe-core:0.10.0-M1`
import $ivy.`io.circe::circe-yaml:0.8.0`

import $ivy.`commons-io:commons-io:2.6`
import $ivy.`com.functortech::thera:0.0.1-SNAPSHOT`

import scala.collection.JavaConverters._

import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import org.apache.commons.io.FileUtils

import cats._, cats.implicits._
import thera._

import io.circe._

val srcRoot      = new File("site-src/")
val compiledRoot = new File("_site/")

val postsIn  = new File(srcRoot     , "posts/")
val postsOut = new File(compiledRoot, "posts/")

val data   = new File(srcRoot, "data/data.yml")
val assets = new File(srcRoot, "assets"       )

def build =for {
  // Config
  configRaw <- att { FileUtils.readFileToString(data, settings.enc) }
  config    <- exn { yaml.parser.parse(configRaw) }
  _         <- att { println(s"Config parsed:\n${config}") }

  // Assemble assets
  _   <- att { FileUtils.copyDirectory(assets, new File("_site", "assets")) }
  css <- templates(
    new File("site-src/private-assets/css/all.css")
  , fragmentResolver = name => new File(s"site-src/private-assets/css/${name}.css"))
  _   <- att { FileUtils.writeStringToFile(new File("_site/assets/all.css"), css, settings.enc) }

  // Copy code directory
  _ <- att { FileUtils.copyDirectory(new File("site-src/code"), new File("_site/code")) }

  // Process input posts
  allPosts <- att { readPosts(postsIn) }
  _ <- allPosts.traverse(processPost(_, config))


  // Delete the code directory
  _ <- att { FileUtils.deleteDirectory(new File("_site/code")) }
  _ <- index(allPosts, config)
} yield ()

def readPosts(input: File): List[Post] =
  FileUtils.iterateFiles(input, Array("md"), false)
    .asScala.map(Post.fromFile).toList

def processPost(post: Post, globalConfig: Json): Ef[Unit] =
  for {
    postJson <- post.asJson
    config   <- att { globalConfig.deepMerge(postJson) }
    res      <- templates(post.inFile, config)
    _        <- att { FileUtils.writeStringToFile(new File(postsOut, post.htmlName), res, settings.enc) }
  } yield ()

def index(posts: List[Post], globalConfig: Json): Ef[Unit] =
  for {
    postsJsonArr <- posts.sortBy(_.date).reverse.traverse(_.asJson)
    config <- att { globalConfig.deepMerge( Json.obj(
                "posts" -> Json.arr(postsJsonArr: _*)) ) }
    res    <- templates(new File(srcRoot, "index.html"), config)
    _      <- att { FileUtils.writeStringToFile(new File(compiledRoot, "index.html"), res, settings.enc) }
  } yield ()

run { build }