(defproject lein-mcp "0.1.0-SNAPSHOT"
  :description "Leiningen plugin for Model Context Protocol (MCP) integration with nREPL"
  :url "https://github.com/yourusername/lein-mcp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [nrepl "1.3.0"]
                 [bencode "0.2.5"]
                 [cheshire "5.13.0"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-jetty-adapter "1.12.2"]]
  :plugins [[lein-mcp "0.1.0-SNAPSHOT"]]
  :source-paths ["src"])
