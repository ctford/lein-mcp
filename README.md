# lein-mcp

A Leiningen plugin that integrates the Model Context Protocol (MCP) with nREPL, allowing AI assistants like Claude to interact with your Clojure development environment.

## Features

- **Native Leiningen Integration**: Start MCP-enabled REPL with a single `lein mcp` command
- **nREPL Middleware**: Automatically starts MCP HTTP server alongside nREPL
- **MCP Tools**: Provides AI assistants with tools to evaluate code, load files, switch namespaces, and search symbols
- **MCP Resources**: Exposes session state, namespaces, documentation, and source code

## Installation

Add to your `~/.lein/profiles.clj`:

```clojure
{:user {:plugins [[lein-mcp "0.1.0-SNAPSHOT"]]}}
```

Or add to your project's `project.clj`:

```clojure
:plugins [[lein-mcp "0.1.0-SNAPSHOT"]]
```

## Usage

### Quick Start

Start an MCP-enabled REPL:

```bash
lein mcp
```

This will:
1. Start an nREPL server (default port: auto-assigned or from `:repl-options`)
2. Start an MCP HTTP server (default port: 8787)
3. Print connection information for both servers

### Custom Ports

```bash
# Custom nREPL port
lein mcp :nrepl-port 7888

# Custom MCP HTTP port
lein mcp :mcp-port 9000

# Both custom ports
lein mcp :nrepl-port 7888 :mcp-port 9000
```

### Headless Mode

Run without an interactive REPL:

```bash
lein mcp :headless
```

### Configuration

Add to your `project.clj`:

```clojure
:repl-options {
  :mcp-port 8787  ; Custom MCP HTTP port
  :nrepl-middleware [lein-mcp.middleware/wrap-mcp]  ; Explicit middleware (optional)
}
```

## Using with Claude Code

1. Start the MCP server in your project directory:
   ```bash
   cd /path/to/your/project
   lein mcp :headless
   ```

   This creates a `.mcp-port` file containing the port number (useful for scripts and tooling).

2. Configure Claude Code by adding `.mcp.json` to your project:
   ```json
   {
     "mcpServers": {
       "lein-mcp": {
         "url": "http://localhost:8787",
         "transport": "http"
       }
     }
   }
   ```

3. Claude Code will connect to the MCP server when working in this directory

## Using with Claude Desktop

Add to your `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "clojure": {
      "command": "curl",
      "args": [
        "-X", "POST",
        "-H", "Content-Type: application/json",
        "--data-binary", "@-",
        "http://localhost:8787"
      ]
    }
  }
}
```

Or use a dedicated MCP client that supports HTTP transport.

## MCP Tools

The plugin provides these tools to AI assistants:

### eval-clojure
Evaluate Clojure code in the REPL session.

**Parameters:**
- `code` (string, required): The Clojure code to evaluate

**Example:**
```json
{
  "tool": "eval-clojure",
  "arguments": {
    "code": "(+ 1 2 3)"
  }
}
```

### load-file
Load and evaluate a Clojure file.

**Parameters:**
- `file-path` (string, required): Path to the Clojure file

**Example:**
```json
{
  "tool": "load-file",
  "arguments": {
    "file-path": "src/myapp/core.clj"
  }
}
```

### set-ns
Switch to a different namespace.

**Parameters:**
- `namespace` (string, required): The namespace to switch to

**Example:**
```json
{
  "tool": "set-ns",
  "arguments": {
    "namespace": "myapp.core"
  }
}
```

### apropos
Search for symbols matching a pattern.

**Parameters:**
- `query` (string, required): Search pattern (string or regex)

**Example:**
```json
{
  "tool": "apropos",
  "arguments": {
    "query": "map"
  }
}
```

## MCP Resources

The plugin exposes these resources:

- `clojure://session/current-ns` - Current namespace name
- `clojure://session/namespaces` - All loaded namespaces
- `clojure://doc/{symbol}` - Documentation for a symbol (e.g., `clojure://doc/map`)
- `clojure://source/{symbol}` - Source code for a symbol (e.g., `clojure://source/defn`)

## Architecture

lein-mcp consists of several components:

1. **Leiningen Task** (`leiningen.mcp`): Entry point for `lein mcp` command
2. **nREPL Middleware** (`lein-mcp.middleware`): Integrates MCP with nREPL lifecycle
3. **MCP Server** (`lein-mcp.server`): HTTP server for MCP JSON-RPC requests
4. **MCP Protocol** (`lein-mcp.protocol`): Handles MCP protocol and translates to nREPL ops
5. **nREPL Client** (`lein-mcp.client`): Connects MCP server back to nREPL

### How It Works

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Claude    │   HTTP  │  MCP Server  │  nREPL  │   nREPL     │
│  (AI Tool)  ├────────►│  (port 8787) ├────────►│(auto-assign)│
│             │  JSON-  │              │ BEncode │             │
└─────────────┘   RPC   └──────────────┘         └─────────────┘
                               │
                               │ Middleware Hook
                               ▼
                        ┌──────────────┐
                        │ Clojure REPL │
                        │   Session    │
                        └──────────────┘
```

When you run `lein mcp`:
1. Leiningen starts nREPL with the MCP middleware
2. The middleware starts an HTTP server on port 8787
3. The HTTP server connects back to nREPL as a client
4. MCP JSON-RPC requests arrive via HTTP
5. Requests are translated to nREPL operations
6. Results are returned as JSON-RPC responses

## Development

### Building

```bash
lein install  # Install to local Maven repo
```

### Testing

```bash
# In a test project
lein mcp

# In another terminal, test with curl
curl -X POST http://localhost:8787 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {}
    }
  }'
```

## Roadmap

### Phase 2: Deep Integration
- [ ] Single-port operation (protocol multiplexing)
- [ ] STDIO transport option
- [ ] Better integration with cider-nrepl middleware
- [ ] Support for notifications (file changes, etc.)

### Phase 3: Community Features
- [ ] Publish to Clojars
- [ ] Additional MCP tools (refactoring, testing)
- [ ] Configuration hot-reload
- [ ] Integration tests
- [ ] Documentation and examples
- [ ] CI/CD pipeline

## License

Copyright © 2024 Chris Ford

Distributed under the Eclipse Public License version 1.0.

## Contributing

Pull requests welcome! Please ensure:
- Code follows Clojure style conventions
- Documentation is updated
- Changes are backwards compatible
