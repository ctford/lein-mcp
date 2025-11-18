(ns lein-mcp.server
  "MCP HTTP server that handles JSON-RPC requests"
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [cheshire.core :as json]
            [lein-mcp.protocol :as protocol])
  (:import [org.eclipse.jetty.server Server]))

(defonce server-state (atom {:server nil
                               :current-ns (atom 'user)
                               :initialized? (atom false)}))

(defn json-response
  "Create a JSON HTTP response"
  [data]
  (-> (response/response (json/generate-string data))
      (response/content-type "application/json")))

(defn handle-mcp-request
  "Handle incoming MCP JSON-RPC request"
  [request]
  (let [{:keys [current-ns initialized?]} @server-state
        body (slurp (:body request))]
    (try
      (let [json-request (json/parse-string body)
            response-data (protocol/handle-request json-request current-ns initialized?)]
        (json-response response-data))
      (catch Exception e
        (json-response (protocol/handle-error nil (.getMessage e)))))))

(defn mcp-handler
  "Ring handler for MCP server"
  [request]
  (if (= (:request-method request) :post)
    (handle-mcp-request request)
    (response/not-found "MCP server only accepts POST requests")))

(defn start-server
  "Start the MCP HTTP server"
  [{:keys [port] :or {port 8787}}]
  (try
    (let [current-ns (atom 'user)
          initialized? (atom false)]

      (println (str "MCP: Starting HTTP server on port " port "..."))

      ;; Start HTTP server (localhost only for security)
      (let [server (jetty/run-jetty #(mcp-handler %)
                                     {:port port
                                      :host "127.0.0.1"
                                      :join? false})]
        (swap! server-state assoc
               :server server
               :current-ns current-ns
               :initialized? initialized?)
        ;; Write port to .mcp-port file (similar to .nrepl-port)
        (spit ".mcp-port" (str port))
        (println (str "MCP: HTTP server started successfully on http://localhost:" port))
        server))
    (catch Exception e
      (println (str "MCP: Failed to start server: " (.getMessage e)))
      (throw e))))

(defn stop-server
  "Stop the MCP HTTP server"
  []
  (when-let [server (:server @server-state)]
    (println "MCP: Stopping HTTP server...")
    (.stop ^Server server)
    (reset! server-state {:server nil
                          :current-ns (atom 'user)
                          :initialized? (atom false)})
    (println "MCP: Server stopped")))

(defn restart-server
  "Restart the MCP HTTP server with new configuration"
  [config]
  (stop-server)
  (Thread/sleep 500) ; Give server time to fully stop
  (start-server config))
