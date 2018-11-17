FROM hseeberger/scala-sbt

# Ammonite to run site build scripts
RUN sh -c '(echo "#!/usr/bin/env sh" && curl -L https://github.com/lihaoyi/Ammonite/releases/download/1.1.2/2.12-1.1.2) > /usr/local/bin/amm && chmod +x /usr/local/bin/amm'

RUN apt-get update
RUN apt-get -y upgrade

# Pandoc, PlantUML, GraphViz
RUN apt-get install -y\
  pandoc python-pip plantuml graphviz\
  libgraphviz-dev graphviz-dev pkg-config
RUN pip install pandocfilters pygraphviz

# Pandoc filters: plantuml, graphviz (under `pandocfilters`) and include-code
WORKDIR /pandoc-filters
RUN git clone https://github.com/anatoliykmetyuk/pandocfilters.git
RUN git clone https://github.com/anatoliykmetyuk/include-code.git

# Start a server to browse the generated site
CMD (mkdir _site; cd _site && python -m SimpleHTTPServer 8888)
