--------------------------------------------------------------------------------
{-# LANGUAGE OverloadedStrings #-}
import           Data.Monoid
import           Control.Monad
import           Control.Applicative           (Alternative (..))
import           Control.Exception.Base
import           Hakyll

import qualified Data.Yaml                     as Yaml
import qualified Data.Text                     as T
import qualified Data.Text.Encoding            as T
import qualified Data.Set                      as S
import           Hakyll.Core.Metadata
import           Text.Pandoc
import           Skylighting.Styles
import           Skylighting.Format.HTML

import           Debug.Trace


--------------------------------------------------------------------------------
main :: IO ()
main = hakyll $ do
  create ["index.html"] $ do
    route idRoute
    compile $ do

      let posts :: Compiler [Item String]
          posts = recentFirst =<< loadAll "posts/*"
          indexCtx =
            listField "posts" postCtx posts <>
            constField "title" "Blog Posts" <>
            boolField  "website" (\_ -> True) <>
            dataCtx <>
            defaultContext

      makeItem ""
        >>= loadAndApplyTemplate "templates/index.html"   indexCtx
        >>= loadAndApplyTemplate "templates/default.html" indexCtx

  match "posts/*" $ do
    route $ setExtension "html"
    compile $
          (pandocCompilerWithTransformM readerOpts writerOpts $
            codeInclude >=> graphvizFilter >=> plantumlFilter)

      >>= saveSnapshot "content"
      >>= loadAndApplyTemplate "templates/post.html"    postCtx
      >>= loadAndApplyTemplate "templates/default.html" postCtx
      >>= relativizeUrls

  create ["assets/all.css"] $ do
    route idRoute
    compile $ do
      frags     <- loadAll "private-assets/css/*.css" >>= (return . fmap itemBody)
      let frags' = frags ++ [styleToCss highlightStyle]
      makeItem $ compressCss $ concat frags'

  match "assets/imgs/**" $ do
    route idRoute
    compile copyFileCompiler

  ["templates/*", "fragments/*"] `forM_` \f -> match f $ compile templateCompiler
  match "private-assets/css/*.css" $ compile getResourceBody

  ["graphviz-images/*", "plantuml-images/*"] `forM_` \f -> match f $ do
    route idRoute
    compile copyFileCompiler

  match "data/*.yml" $ compile getResourceBody
  
  createRedirects brokenLinks

  create ["atom.xml"] $ do
    route idRoute
    compile $ do
      let feedCtx = postCtx `mappend` bodyField "description"
      posts <- fmap (take 10) . recentFirst =<< loadAllSnapshots "posts/*" "content"
      renderAtom feedConfig feedCtx posts

  create ["404.html", "CNAME", "favicon.png"] $ do
    route idRoute
    compile copyFileCompiler


--------------------------------------------------------------------------------
postCtx :: Context String
postCtx =
  dateField "date" "%B %e, %Y" <>
  defaultContext <>
  dataCtx

dataCtx :: Context String
dataCtx = flattenCtx $ do
  rawDataIds <- getMatches "data/*.yml" :: Compiler [Identifier]
  return $ mconcat $ fmap ymlContext rawDataIds

flattenCtx :: Compiler (Context a) -> Context a
flattenCtx cmp = Context $ \k x is -> do
  ctx <- cmp
  (unContext ctx) k x is 


--------------------------------------------------------------------------------
ymlContext :: Identifier -> Context String
ymlContext identifier = Context $ \key _ _ -> do
  body <- loadBody identifier
  let md    = parseYml body
      value = lookupString key md
  maybe empty (return . StringField) value

parseYml :: String -> Metadata
parseYml str = case dRes of
    Left  err -> throw err
    Right res -> res
  where
    dRes = Yaml.decodeEither' . T.encodeUtf8 . T.pack $ str


--------------------------------------------------------------------------------
type Script = String

scriptFilter :: Script -> Pandoc -> Compiler Pandoc
scriptFilter scr pdc =
  scriptFilterWith scr readerOpts writerOpts pdc

scriptFilterWith :: Script
                 -> ReaderOptions
                 -> WriterOptions
                 -> Pandoc
                 -> Compiler Pandoc
scriptFilterWith scr readerOpts writerOpts pdc = do
  let inJson = writeJSON writerOpts pdc
  outJson   <- unixFilter scr [] inJson
  let res    = either (error . show) id $ readJSON readerOpts outJson
  return $ res --trace (show res) res


--------------------------------------------------------------------------------
-- Actual filters
pyPandocPlugin :: String -> Pandoc -> Compiler Pandoc
pyPandocPlugin str = scriptFilter $ "./plugins/pandocfilters/examples/" ++ str ++ ".py"

graphvizFilter = pyPandocPlugin "graphviz"
plantumlFilter = pyPandocPlugin "plantuml"
codeInclude    = scriptFilter   "./plugins/pandoc-include-code/dist/build/pandoc-include-code/pandoc-include-code"


--------------------------------------------------------------------------------
highlightStyle = pygments

writerOpts = defaultHakyllWriterOptions {
  writerHighlightStyle  = highlightStyle
, writerHTMLMathMethod  = WebTeX "https://latex.codecogs.com/png.latex?"
, writerTableOfContents = True
, writerTemplate        = Just "$toc$\n$body$"
}
readerOpts = defaultHakyllReaderOptions

feedConfig = FeedConfiguration {
  feedTitle       = "Blog of Anatolii Kmetiuk"
, feedDescription = "All things functional"
, feedAuthorName  = "Anatolii Kmetiuk"
, feedAuthorEmail = "anatoliykmetyuk@gmail.com"
, feedRoot        = "http://akmetiuk.com"
}

--------------------------------------------------------------------------------
brokenLinks :: [(Identifier, String)]
brokenLinks = [
  ("blog/2017/01/13/rewriting-process-algebra-part-3-freeacp-implementation.html", "/posts/2017-01-13-rewriting-process-algebra-part-3-freeacp-implementation.html"),
  ("blog/2017/01/12/rewriting-process-algebra-part-2-engine-theory.html", "/posts/2017-01-12-rewriting-process-algebra-part-2-engine-theory.html"),
  ("blog/2017/01/11/rewriting-process-algebra-part-1-introduction-to-process-algebra.html", "/posts/2017-01-11-rewriting-process-algebra-part-1-introduction-to-process-algebra.html"),
  ("blog/2016/10/09/dissecting-shapeless-poly.html", "/posts/2016-10-09-dissecting-shapeless-poly.html"),
  ("blog/2016/09/30/dissecting-shapeless-hlists.html", "/posts/2016-09-30-dissecting-shapeless-hlists.html"),
  ("blog/2016/08/10/splitting-monolitic-commits.html", "/posts/2016-08-10-splitting-monolitic-commits.html"),
  ("blog/2016/04/13/subscript-values-in-by-name-calls.html", "/posts/2016-04-13-subscript-values-in-by-name-calls.html"),
  ("blog/2016/04/08/subscript-progress-report.html", "/posts/2016-04-08-subscript-progress-report.html"),
  ("blog/2016/03/29/compiling-caffe.html", "/posts/2016-03-29-compiling-caffe.html")]
