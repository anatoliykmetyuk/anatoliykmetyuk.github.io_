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
import           Hakyll.Core.Metadata
import           Text.Pandoc

--------------------------------------------------------------------------------
main :: IO ()
main = hakyll $ do
  create ["index.html"] $ do
    route idRoute
    compile $ do
      rawDataIds <- getMatches "data/*.yml"
    
      let posts :: Compiler [Item String]
          posts = recentFirst =<< loadAll "posts/*"
          dataCtx = mconcat $ fmap ymlContext rawDataIds
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
          (pandocCompilerWithTransformM defaultHakyllReaderOptions defaultHakyllWriterOptions $
            graphvizFilter >=> plantumlFilter)

      >>= loadAndApplyTemplate "templates/post.html"    postCtx
      >>= loadAndApplyTemplate "templates/default.html" postCtx
      >>= relativizeUrls

  create ["assets/all.css"] $ do
    route idRoute
    compile $ do
      frags <- loadAll "private-assets/css/*.css" :: Compiler [Item String]
      makeItem $ concatMap itemBody frags

  ["templates/*", "fragments/*"] `forM_` \f -> match f $ compile templateCompiler
  match "private-assets/css/*.css" $ compile getResourceBody

  ["graphviz-images/*", "plantuml-images/*"] `forM_` \f -> match f $ do
    route idRoute
    compile copyFileCompiler

  match "data/*.yml" $ compile getResourceBody

--------------------------------------------------------------------------------
postCtx :: Context String
postCtx =
  dateField "date" "%B %e, %Y" `mappend`
  defaultContext


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
  scriptFilterWith scr defaultHakyllReaderOptions defaultHakyllWriterOptions pdc

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
