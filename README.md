This is the sources for my [blog](http://akmetiuk.com). The credit for the template used goes to [swanson/lagom](https://github.com/swanson/lagom/). It was further adapted, so that it works with [Hakyll](https://jaspervdj.be/hakyll/) now.

# Publish cycle
1. `git checkout develop`
2. Write the article under `posts`
3. `./buildAll.sh` - to build the articles to HTML
4. `./go.sh` - to start the server and try out the work
5. `git ceckout`

# Fixes
[https://github.com/ttuegel/emacs2nix/issues/33](https://github.com/ttuegel/emacs2nix/issues/33)

You will need to run `cabal install` from the root and `plugins/pandoc-include-code` to install all the Haskell dependencies.
