# PowerShell JavaBytes Development Workflow
# For solo maintainer: build, test, publish cycle

param(
    [string]$Command = "dev"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path $MyInvocation.MyCommand.Path
$projectRoot = Split-Path $scriptDir
$jarPath = "$projectRoot\target\javabytes.jar"
$npmRoot = "$projectRoot\npm"

function Write-Log($message) {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $message"
}

function Build-Jar {
    Write-Log "Building JavaBytes jar..."
    Set-Location $projectRoot
    mvn clean package -DskipTests
    
    if (-not (Test-Path $jarPath)) {
        Write-Error "JAR build failed: $jarPath not found"
        exit 1
    }
    
    $sizeMB = [math]::Round((Get-Item $jarPath).Length / 1024 / 1024, 1)
    Write-Log "✅ Jar built: $jarPath (${sizeMB}MB)"
}

function Test-Jar {
    Write-Log "Running tests..."
    Set-Location $projectRoot
    mvn test
    Write-Log "✅ All tests passed"
}

function Build-Npm {
    Write-Log "Building npm package..."
    Set-Location $npmRoot
    npm run prepack
    Write-Log "✅ npm package ready"
}

function Publish-Npm {
    Write-Log "Publishing npm package..."
    Set-Location $npmRoot
    
    # Check if logged into npm
    try {
        npm whoami | Out-Null
    } catch {
        Write-Warning "Not logged into npm. Run 'npm login' first."
        return
    }
    
    # Check package availability
    try {
        npm view "@javabytes/mcp" | Out-Null
    } catch {
        Write-Warning "Package @javabytes/mcp not found on npm (this is OK for first publish)"
    }
    
    npm publish --access public
    Write-Log "✅ Published to npm: @javabytes/mcp"
}

function Verify-Team-Workflow {
    Write-Log "Verifying team workflow..."
    Set-Location $npmRoot
    
    # Test npm package installation
    $tempDir = [io.path]::GetTempPath() + "javabytes-test-" + [guid]::NewGuid()
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    Set-Location $tempDir
    
    npm install -y @javabytes/mcp | Out-Null
    
    if (-not (Test-Path "node_modules\@javabytes/mcp\bin\javabytes.js")) {
        Write-Error "npm package installation failed"
        Remove-Item -Recurse $tempDir
        exit 1
    }
    
    Remove-Item -Recurse $tempDir
    Write-Log "✅ npm package installs correctly"
}

function Show-Team-Instructions {
    @"
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
"@
}

# Main workflow
$command = $Command.ToLower()
if ($command -eq "build") {
    Build-Jar
} elseif ($command -eq "test") {
    Test-Jar
} elseif ($command -eq "npm") {
    Build-Npm
} elseif ($command -eq "dev") {
    Build-Jar
    Test-Jar
    Build-Npm
} elseif ($command -eq "publish") {
    Build-Jar
    Test-Jar
    Build-Npm
    Publish-Npm
    Verify-Team-Workflow
    Show-Team-Instructions
} else {
    Write-Host "Usage: $PSCommandPath <command>"
    Write-Host "Commands: build, test, npm, dev, publish"
    exit 1
}