(ns lein-mcp.protocol
  "MCP protocol implementation - handles MCP JSON-RPC requests"
  (:require [clojure.string :as str]
            [cheshire.core :as json])
  (:import [java.io StringWriter]))

;; MCP Protocol constants
(def MCP-VERSION "2024-11-05")
(def SERVER-INFO {:name "lein-mcp" :version "0.1.0"})

;; Helper functions for direct evaluation
(defn eval-code-direct
  "Evaluate code directly using clojure.core/eval, capturing output"
  [current-ns code]
  (let [out-writer (StringWriter.)
        err-writer (StringWriter.)]
    (binding [*out* out-writer
              *err* err-writer
              *ns* (the-ns @current-ns)]
      (try
        (let [result (eval (read-string code))]
          {:value (pr-str result)
           :out (str out-writer)
           :err (str err-writer)})
        (catch Exception e
          {:err (str "Error: " (.getMessage e) "\n" (str err-writer))
           :out (str out-writer)})))))

;; MCP Protocol handlers
(defn handle-initialize [params]
  "Handle MCP initialize request"
  (let [client-version (get params "protocolVersion")
        capabilities (get params "capabilities" {})]
    {"protocolVersion" MCP-VERSION
     "capabilities" {"tools" {}
                     "resources" {}}
     "serverInfo" SERVER-INFO}))

(defn handle-tools-list []
  "Return list of available MCP tools"
  {"tools"
   [{"name" "eval-clojure"
     "description" "Evaluate Clojure code"
     "inputSchema"
     {"type" "object"
      "properties"
      {"code" {"type" "string"
               "description" "The Clojure code to evaluate"}}
      "required" ["code"]}}
    {"name" "load-file"
     "description" "Load and evaluate a Clojure file"
     "inputSchema"
     {"type" "object"
      "properties"
      {"file-path" {"type" "string"
                    "description" "The path to the Clojure file to load"}}
      "required" ["file-path"]}}
    {"name" "set-ns"
     "description" "Switch to a different namespace"
     "inputSchema"
     {"type" "object"
      "properties"
      {"namespace" {"type" "string"
                    "description" "The namespace to switch to"}}
      "required" ["namespace"]}}
    {"name" "apropos"
     "description" "Search for symbols matching a pattern"
     "inputSchema"
     {"type" "object"
      "properties"
      {"query" {"type" "string"
                "description" "Search pattern to match against symbol names"}}
      "required" ["query"]}}]})

(defn handle-tools-call [params current-ns]
  "Handle MCP tool call request"
  (let [tool-name (get params "name")
        arguments (get params "arguments" {})]
    (case tool-name
      "eval-clojure"
      (let [code (get arguments "code")]
        (if (str/blank? code)
          {"isError" true
           "content" [{"type" "text"
                      "text" "Error: Code parameter is required and cannot be empty"}]}
          (try
            (let [result (eval-code-direct current-ns code)
                  output (:out result "")
                  errors (:err result "")
                  value (:value result "")
                  result-text (str/join "\n"
                                        (filter (complement str/blank?)
                                               [output errors value]))]
              {"content" [{"type" "text"
                          "text" (if (str/blank? result-text)
                                   "nil"
                                   result-text)}]})
            (catch Exception e
              {"isError" true
               "content" [{"type" "text"
                          "text" (str "Error evaluating Clojure code: " (.getMessage e))}]}))))

      "load-file"
      (let [file-path (get arguments "file-path")]
        (if (str/blank? file-path)
          {"isError" true
           "content" [{"type" "text"
                      "text" "Error: file-path parameter is required and cannot be empty"}]}
          (try
            (if (.exists (java.io.File. file-path))
              (let [code (str "(load-file \"" file-path "\")")
                    result (eval-code-direct current-ns code)
                    output (:out result "")
                    errors (:err result "")
                    value (:value result "")
                    result-text (str/join "\n"
                                          (filter (complement str/blank?)
                                                 [output errors value]))]
                {"content" [{"type" "text"
                            "text" (if (str/blank? result-text)
                                     (str "Successfully loaded file: " file-path)
                                     result-text)}]})
              {"isError" true
               "content" [{"type" "text"
                          "text" (str "Error: File not found: " file-path)}]})
            (catch Exception e
              {"isError" true
               "content" [{"type" "text"
                          "text" (str "Error loading file: " (.getMessage e))}]}))))

      "set-ns"
      (let [namespace (get arguments "namespace")]
        (if (str/blank? namespace)
          {"isError" true
           "content" [{"type" "text"
                      "text" "Error: namespace parameter is required and cannot be empty"}]}
          (try
            (let [ns-sym (symbol namespace)]
              (require ns-sym)
              (reset! current-ns ns-sym)
              {"content" [{"type" "text"
                          "text" (str "Successfully switched to namespace: " namespace)}]})
            (catch Exception e
              {"isError" true
               "content" [{"type" "text"
                          "text" (str "Error switching namespace: " (.getMessage e))}]}))))

      "apropos"
      (let [query (get arguments "query")]
        (if (str/blank? query)
          {"isError" true
           "content" [{"type" "text"
                      "text" "Error: query parameter is required and cannot be empty"}]}
          (try
            (let [code (str "(require 'clojure.repl) (clojure.repl/apropos \"" query "\")")
                  result (eval-code-direct current-ns code)
                  output (:out result "")
                  errors (:err result "")
                  value (:value result "")
                  result-text (str/join "\n"
                                        (filter (complement str/blank?)
                                               [output errors value]))]
              {"content" [{"type" "text"
                          "text" (if (or (str/blank? result-text) (= result-text "nil"))
                                   "No matches found"
                                   result-text)}]})
            (catch Exception e
              {"isError" true
               "content" [{"type" "text"
                          "text" (str "Error searching symbols: " (.getMessage e))}]}))))

      {"isError" true
       "content" [{"type" "text"
                  "text" (str "Unknown tool: " tool-name)}]})))

(defn handle-resources-list []
  "Return list of available MCP resources"
  {"resources"
   [{"uri" "clojure://session/current-ns"
     "name" "Current Namespace"
     "description" "The current default namespace in the REPL session"
     "mimeType" "text/plain"}
    {"uri" "clojure://session/namespaces"
     "name" "Session Namespaces"
     "description" "Currently loaded namespaces"
     "mimeType" "application/json"}]})

(defn handle-resources-read [params current-ns]
  "Handle MCP resource read request"
  (let [uri (get params "uri")]
    (cond
      (str/starts-with? uri "clojure://doc/")
      (let [symbol-name (subs uri 14)
            ;; Access var metadata directly instead of using doc macro (which doesn't work with eval)
            code (str "(let [v (resolve '" symbol-name ")] "
                      "(when v "
                      "(let [m (meta v)] "
                      "(when (:doc m) "
                      "(str \"-------------------------\\n\" "
                      "(:name m) \"\\n\" "
                      "(pr-str (:arglists m)) \"\\n  \" "
                      "(:doc m))))))")
            result (eval-code-direct current-ns code)
            doc-content (:value result)]
        {"contents" [{"uri" uri
                     "mimeType" "text/plain"
                     "text" (if (or (str/blank? doc-content) (= doc-content "nil"))
                             (str "No documentation found for: " symbol-name)
                             (clojure.string/replace doc-content #"^\"|\"$" ""))}]})

      (str/starts-with? uri "clojure://source/")
      (let [symbol-name (subs uri 17)
            code (str "(require 'clojure.repl) (with-out-str (clojure.repl/source " symbol-name "))")
            result (eval-code-direct current-ns code)
            source-content (str (:value result "") (:out result ""))]
        {"contents" [{"uri" uri
                     "mimeType" "text/clojure"
                     "text" (if (or (str/blank? source-content) (= source-content "nil"))
                             (str "No source found for: " symbol-name)
                             source-content)}]})

      (= uri "clojure://session/current-ns")
      {"contents" [{"uri" uri
                   "mimeType" "text/plain"
                   "text" (str @current-ns)}]}

      (= uri "clojure://session/namespaces")
      (let [code "(pr-str (map str (all-ns)))"
            result (eval-code-direct current-ns code)
            namespaces (:value result "[]")]
        {"contents" [{"uri" uri
                     "mimeType" "application/json"
                     "text" namespaces}]})

      :else
      (throw (Exception. (str "Unknown resource URI: " uri))))))

(defn handle-request [request current-ns initialized?]
  "Handle MCP JSON-RPC request"
  (let [method (get request "method")
        params (get request "params")
        id (get request "id")]

    (when-not @initialized?
      (when-not (= method "initialize")
        (throw (Exception. "Server not initialized"))))

    (let [result
          (case method
            "initialize" (do
                          (reset! initialized? true)
                          (handle-initialize params))
            "tools/list" (handle-tools-list)
            "tools/call" (handle-tools-call params current-ns)
            "resources/list" (handle-resources-list)
            "resources/read" (handle-resources-read params current-ns)
            (throw (Exception. (str "Unknown method: " method))))]

      {"jsonrpc" "2.0"
       "id" id
       "result" result})))

(defn handle-error [id error-msg]
  "Create MCP error response"
  {"jsonrpc" "2.0"
   "id" id
   "error" {"code" -1
            "message" error-msg}})
