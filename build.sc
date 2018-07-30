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


// print banner
println("Hello World!!")
