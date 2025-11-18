(ns lein-mcp.middleware
  "nREPL middleware for MCP integration"
  (:require [lein-mcp.server :as server]))

(def default-config
  {:mcp-port 8787})

;; Track if server has been started
(defonce server-started? (atom false))

(defn wrap-mcp
  "nREPL middleware that starts MCP HTTP server on first call.
   This ensures the server starts when nREPL actually uses the middleware."
  [handler]
  ;; Start server on first middleware invocation
  (when (compare-and-set! server-started? false true)
    (future
      (try
        (println "\nMCP: Starting HTTP server...")
        (Thread/sleep 1000) ; Brief delay to let nREPL fully initialize
        (server/start-server default-config)
        (println "MCP: HTTP server ready on http://localhost:8787\n")
        (catch Exception e
          (println (str "\nMCP: ERROR starting server: " (.getMessage e)))
          (.printStackTrace e)))))

  ;; Pass through to next middleware
  handler)

;; Middleware descriptor for nREPL
(defn mcp-middleware
  "Middleware descriptor for nREPL"
  []
  {:requires #{}
   :expects #{}
   :handles {}
   :describe-fn (fn [] {:mcp {:doc "Model Context Protocol HTTP server"
                              :version "0.1.0"}})})
