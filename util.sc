import $ivy.`commons-io:commons-io:2.6`
import $ivy.`com.akmetiuk::thera:0.2.0-M1`

import thera._

import os._
import org.apache.commons.io.IOUtils


val src      = os.pwd/"src"
val compiled = os.pwd/"_site"

/**
 * Command line pipe. Invokes an external application, obtains its
 * input and output streams and feeds
 * the `input` to the output stream. Returns the contents of the
 * input stream of the command.
 */
def pipeIntoCommand(cmd: String, input: String, workdir: Path,
  encoding: String = "utf8"): String = {
  val proc = sys.runtime.exec(cmd, null, workdir.toIO)
  val is   = proc.getInputStream
  val os   = proc.getOutputStream
  val es   = proc.getErrorStream

  def closeAll(): Unit = {
    os.close()
    is.close()
    es.close()
  }

  try {
    IOUtils.write(input, os, encoding)
    os.close()
    val res = IOUtils.toString(is, encoding)
    println(IOUtils.toString(es, encoding))
    res
  }
  finally closeAll()
}

def postMarkdownToHtml(str: String): String =
  pipeIntoCommand("""pandoc
    --toc
    --webtex
    --template=../src/templates/pandoc-post.html
    --filter ../pandoc-filters/graphviz.py
    --filter ../pandoc-filters/plantuml.py
    --filter ../pandoc-filters/include-code.py""",
    str, compiled)

def pandocRaw(str: String): String =
  pipeIntoCommand("pandoc", str, compiled)


def write(f: File, str: String): Unit = {
  f.delete(true)
  f.write(str)
}

def pipeThera(tmls: Template*)(
  implicit ctx: ValueHierarchy): String =
  tmls.tail.foldLeft(tmls.head.mkValue) { (v, tml) =>
    tml.mkValue.asFunction(v :: Nil)
  }.asStr.value
