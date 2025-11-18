(ns lein-mcp.middleware
  "nREPL middleware for MCP integration"
  (:require [lein-mcp.server :as server]))

(def default-config
  {:mcp-port 8787})

;; Start MCP server in background when middleware namespace is loaded
;; This runs automatically when nREPL loads the middleware
(defonce ^:private mcp-server
  (future
    (try
      (println "\nMCP: Starting HTTP server...")
      (Thread/sleep 2000) ; Give nREPL time to fully initialize
      (server/start-server default-config)
      (println "MCP: HTTP server ready on http://localhost:8787\n")
      (catch Exception e
        (println (str "\nMCP: ERROR starting server: " (.getMessage e)))
        (.printStackTrace e)))))

(defn wrap-mcp
  "nREPL middleware for MCP integration.
   Server starts automatically when this namespace is loaded."
  [handler]
  ;; Just pass through - server starts automatically above
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
