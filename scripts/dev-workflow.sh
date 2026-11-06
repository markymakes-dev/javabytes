#!/bin/bash
# JavaBytes Development Workflow Script
# For solo maintainer: build, test, publish cycle

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAR_PATH="$PROJECT_ROOT/target/javabytes.jar"
NPM_ROOT="$PROJECT_ROOT/npm"

function log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

function build_jar() {
    log "Building JavaBytes jar..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests
    if [[ ! -f "$JAR_PATH" ]]; then
        echo "❌ JAR build failed: $JAR_PATH not found"
        exit 1
    fi
    local size_mb=$(($(stat -c%s "$JAR_PATH") / 1024 / 1024))
    log "✅ Jar built: $JAR_PATH (${size_mb}MB)"
}

function test_jar() {
    log "Running tests..."
    cd "$PROJECT_ROOT"
    mvn test
    log "✅ All tests passed"
}

function build_npm() {
    log "Building npm package..."
    cd "$NPM_ROOT"
    npm run prepack
    log "✅ npm package ready"
}

function publish_npm() {
    log "Publishing npm package..."
    cd "$NPM_ROOT"
    
    # Check if user is logged in
    npm whoami > /dev/null 2>&1 || {
        echo "⚠️  Not logged into npm. Run 'npm login' first."
        return 1
    }
    
    # Check package availability
    local package_name="@javabytes/mcp"
    npm view "$package_name" > /dev/null 2>&1 || {
        echo "⚠️  Package $package_name not found on npm (this is OK for first publish)"
    }
    
    npm publish --access public
    log "✅ Published to npm: $package_name"
}

function verify_team_workflow() {
    log "Verifying team workflow..."
    cd "$NPM_ROOT"
    
    # Test npm package installation
    local temp_dir="$(mktemp -d)"
    cd "$temp_dir"
    npm install -y @javabytes/mcp > /dev/null 2>&1
    if [[ ! -f "node_modules/@javabytes/mcp/bin/javabytes.js" ]]; then
        echo "❌ npm package installation failed"
        rm -rf "$temp_dir"
        exit 1
    fi
    rm -rf "$temp_dir"
    
    log "✅ npm package installs correctly"
}

function show_team_instructions() {
    cat << EOF
=================================================================
TEAM INSTALLATION INSTRUCTIONS
=================================================================

Team members can now use JavaBytes with ONE command:

1. OpenCode Configuration (copy to ~/.config/opencode/opencode.json):
{
  "mcpServers": {
    "javabytes": {
      "command": ["npx", "-y", "@javabytes/mcp"],
      "env": {
        "JAVABYTES_DB_URL": "jdbc:mysql://localhost:3306/appdb",
        "JAVABYTES_DB_USER": "app",
        "JAVABYTES_DB_PASSWORD": "change-me"
      }
    }
  }
}

2. For Claude Desktop (claude_desktop_config.json):
{
  "mcpServers": {
    "javabytes": {
      "command": "npx",
      "args": ["-y", "@javabytes/mcp"],
      "env": {
        "JAVABYTES_DB_URL": "jdbc:mysql://localhost:3306/appdb",
        "JAVABYTES_DB_USER": "app",
        "JAVABYTES_DB_PASSWORD": "change-me"
      }
    }
  }
}

Team members can now just run:
  npx -y @javabytes/mcp

=================================================================
EOF
}

# Main workflow
case "${1:-}" in
    "build")
        build_jar
        ;;
    "test")
        test_jar
        ;;
    "npm")
        build_npm
        ;;
    "publish")
        build_jar
        test_jar
        build_npm
        publish_npm
        verify_team_workflow
        show_team_instructions
        ;;
    "dev")
        build_jar
        test_jar
        build_npm
        ;;
    *)
        echo "Usage: $0 {build|test|npm|publish|dev}"
        echo ""
        echo "Commands:"
        echo "  build    - Build the jar only"
        echo "  test     - Run tests only"
        echo "  npm      - Build npm package only"
        echo "  dev      - Full development cycle (build + test + npm)"
        echo "  publish  - Complete release (dev + npm publish + verify)"
        exit 1
        ;;
esac