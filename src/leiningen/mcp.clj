(ns leiningen.mcp
  "Leiningen task for starting nREPL with MCP support"
  (:require [leiningen.core.main :as main]
            [leiningen.core.project :as project]
            [leiningen.repl :as repl]))

;; Dependencies required by the MCP middleware
(def mcp-dependencies
  '[[lein-mcp "0.1.0-SNAPSHOT"]  ; Plugin itself (contains middleware)
    [nrepl "1.3.0"]
    [bencode "0.2.5"]
    [cheshire "5.13.0"]
    [ring/ring-core "1.12.2"]
    [ring/ring-jetty-adapter "1.12.2"]])

(defn mcp
  "Start an nREPL server with MCP (Model Context Protocol) support.

   This starts both an nREPL server and an MCP HTTP server that provides
   AI assistants like Claude with tools to interact with your Clojure environment.

   Usage:
     lein mcp                    ; Start MCP-enabled REPL (MCP on port 8787)
     lein mcp :port 7888         ; Start nREPL on custom port
     lein mcp :mcp-port 9000     ; Use custom port for MCP HTTP server
     lein mcp :headless          ; Start in headless mode (no interactive REPL)

   Configuration in project.clj:
     :repl-options {:mcp-port 8787}  ; Custom MCP HTTP port

   The MCP HTTP server accepts JSON-RPC POST requests and provides tools for:
     - eval-clojure: Evaluate Clojure code
     - load-file: Load and evaluate files
     - set-ns: Switch namespaces
     - apropos: Search for symbols"
  [project & args]
  ;; Add MCP middleware to nREPL middleware stack
  (let [mcp-middleware 'lein-mcp.middleware/wrap-mcp
        current-middleware (get-in project [:repl-options :nrepl-middleware] [])
        updated-middleware (if (some #{mcp-middleware} current-middleware)
                            current-middleware
                            (conj (vec current-middleware) mcp-middleware))

        ;; Parse MCP port from args or use default
        mcp-port (or (when-let [idx (.indexOf (vec args) ":mcp-port")]
                       (when (>= idx 0)
                         (parse-long (nth args (inc idx) "8787"))))
                     (get-in project [:repl-options :mcp-port])
                     8787)

        ;; Add MCP dependencies to project
        current-deps (:dependencies project)
        updated-deps (concat current-deps mcp-dependencies)

        ;; Add injection to load middleware namespace early
        current-injections (:injections project [])
        injection '(do
                     (println "MCP: Loading middleware namespace...")
                     (require 'lein-mcp.middleware)
                     (println "MCP: Middleware loaded and HTTP server starting..."))
        updated-injections (conj (vec current-injections) injection)

        ;; Update project with MCP middleware and dependencies
        project (-> project
                    (assoc :dependencies updated-deps)
                    (assoc :injections updated-injections)
                    (assoc-in [:repl-options :nrepl-middleware] updated-middleware)
                    (assoc-in [:repl-options :mcp-config :mcp-port] mcp-port))]

    (println "Starting nREPL with MCP support...")
    (println (str "  MCP HTTP server will be available on port " mcp-port))
    (println "  Send JSON-RPC POST requests to http://localhost:" mcp-port)
    (println)

    ;; Start nREPL with MCP middleware
    (apply repl/repl project args)))
