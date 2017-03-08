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
            dataCtx <>
            defaultContext

      makeItem ""
        >>= loadAndApplyTemplate "templates/index.html"   indexCtx
        >>= loadAndApplyTemplate "templates/default.html" indexCtx

  match "posts/*" $ do
    route $ setExtension "html"
    compile $
          (pandocCompilerWithTransformM readerOpts writerOpts $
            graphvizFilter >=> plantumlFilter)

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
  return $ either (error . show) id $ readJSON readerOpts outJson


--------------------------------------------------------------------------------
-- Actual filters
pyPandocPlugin :: String -> Pandoc -> Compiler Pandoc
pyPandocPlugin str = scriptFilter $ "./plugins/pandocfilters/examples/" ++ str ++ ".py"

graphvizFilter = pyPandocPlugin "graphviz"
plantumlFilter = pyPandocPlugin "plantuml"


--------------------------------------------------------------------------------
highlightStyle = pygments

writerOpts = defaultHakyllWriterOptions {
  writerHighlightStyle = highlightStyle
, writerHTMLMathMethod = WebTeX "https://latex.codecogs.com/png.latex?"
}
readerOpts = defaultHakyllReaderOptions {
  readerStandalone = True
, readerExtensions = S.fromList [Ext_tex_math_dollars]
}
