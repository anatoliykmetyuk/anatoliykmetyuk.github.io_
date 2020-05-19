import $ivy.`com.github.pathikrit::better-files:3.6.0`
import $ivy.`commons-io:commons-io:2.6`

import better.files.File
import org.apache.commons.io.IOUtils


/**
 * Command line pipe. Invokes an external application, obtains its
 * input and output streams and feeds
 * the `input` to the output stream. Returns the contents of the
 * input stream of the command.
 */
def pipeIntoCommand(cmd: String, input: String, workdir: File,
  encoding: String = "utf8"): String = {
  val proc = sys.runtime.exec(cmd, null, workdir.toJava)
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
    --filter /pandoc-filters/pandocfilters/examples/graphviz.py
    --filter /pandoc-filters/pandocfilters/examples/plantuml.py
    --filter /pandoc-filters/include-code/include-code.py""",
    str, compiled)

def pandocRaw(str: String): String =
  pipeIntoCommand("pandoc", str, compiled)


def write(f: File, str: String): Unit = {
  f.delete(true)
  f.write(str)
}
