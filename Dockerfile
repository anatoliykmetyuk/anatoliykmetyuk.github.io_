FROM ubuntu:cosmic

ENV PATH=$PATH:/root/.local/bin
ENV LANG=C.UTF-8

# Essentials
RUN apt-get -y update
RUN apt-get -y install\
  git curl ghc pkg-config\
  libghc-pcre-light-dev\
  python-pygraphviz python-pip\
  plantuml

# Haskell Stack
RUN curl -sSL https://get.haskellstack.org/ | sh
RUN stack install cabal-install pandoc
RUN cabal update

# Dependencies
RUN stack install\
  base containers process filepath pcre-heavy\
  pandoc-types hakyll yaml text pandoc skylighting
RUN pip install pandocfilters
