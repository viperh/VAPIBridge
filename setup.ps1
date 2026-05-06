[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GroupId,

    [Parameter(Mandatory = $true)]
    [string]$PluginName,

    [string]$Description,
    [string]$Author
)

$ErrorActionPreference = 'Stop'

# ---------------- VALIDATION ----------------

if ($GroupId -notmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$') {
    throw "GroupId '$GroupId' must be a dotted lowercase identifier (e.g. com.example)."
}

if ($PluginName -notmatch '^[A-Za-z][A-Za-z0-9]*$') {
    throw "PluginName '$PluginName' must start with a letter and contain only letters/digits."
}

# ---------------- VARIABLES ----------------

$repoRoot = $PSScriptRoot

$oldGroup       = 'org.me.qviperh'
$oldLeaf        = 'mCTemplate'
$oldClass       = 'MCTemplate'
$oldArtifactId  = 'MCTemplate'
$oldFullPkg     = "$oldGroup.$oldLeaf"

$newGroup      = $GroupId
$newLeaf       = $PluginName.Substring(0,1).ToLower() + $PluginName.Substring(1)
$newClass      = $PluginName
$newArtifactId = $PluginName
$newFullPkg    = "$newGroup.$newLeaf"

if ($oldFullPkg -eq $newFullPkg -and $oldClass -eq $newClass) {
    Write-Host "Nothing to rename."
    return
}

Write-Host "Renaming:"
Write-Host "  $oldFullPkg -> $newFullPkg"
Write-Host "  $oldClass   -> $newClass"

# ---------------- PATHS ----------------

$kotlinRoot = Join-Path $repoRoot "src\main\kotlin"
$oldPkgDir  = Join-Path $kotlinRoot ($oldFullPkg -replace '\.', '\')
$newPkgDir  = Join-Path $kotlinRoot ($newFullPkg -replace '\.', '\')

if (!(Test-Path $oldPkgDir)) {
    throw "Old package not found: $oldPkgDir"
}

# ---------------- MOVE PACKAGE ----------------

$newPkgParent = Split-Path $newPkgDir -Parent
New-Item -ItemType Directory -Force -Path $newPkgParent | Out-Null

Copy-Item $oldPkgDir $newPkgDir -Recurse
Remove-Item $oldPkgDir -Recurse -Force

# ---------------- RENAME MAIN CLASS ----------------

$oldMain = Join-Path $newPkgDir "$oldClass.kt"
$newMain = Join-Path $newPkgDir "$newClass.kt"

if (Test-Path $oldMain) {
    Rename-Item $oldMain $newMain
}

# ---------------- UPDATE KOTLIN FILES ----------------

$ktFiles = Get-ChildItem $kotlinRoot -Recurse -Filter *.kt

foreach ($file in $ktFiles) {
    $text = Get-Content $file.FullName -Raw

    # package
    $text = [regex]::Replace(
            $text,
            "^\s*package\s+$([regex]::Escape($oldFullPkg))",
            "package $newFullPkg",
            'Multiline'
    )

    # imports
    $text = $text -replace "import\s+$([regex]::Escape($oldFullPkg))", "import $newFullPkg"

    # class rename (safe)
    $text = [regex]::Replace(
            $text,
            "\b$([regex]::Escape($oldClass))\b",
            $newClass
    )

    [System.IO.File]::WriteAllText($file.FullName, $text, [System.Text.Encoding]::UTF8)
}

# ---------------- UPDATE pom.xml ----------------

$pomPath = Join-Path $repoRoot "pom.xml"

[xml]$pom = Get-Content $pomPath

$pom.project.groupId = $newGroup
$pom.project.artifactId = $newArtifactId
$pom.project.name = $newArtifactId

$pom.Save($pomPath)

# ---------------- UPDATE plugin.yml ----------------

$pluginPath = Join-Path $repoRoot "src\main\resources\plugin.yml"

$lines = Get-Content $pluginPath

$updated = foreach ($line in $lines) {
    if ($line -match '^name:') {
        "name: $PluginName"
    }
    elseif ($line -match '^prefix:') {
        "prefix: $PluginName"
    }
    elseif ($line -match '^main:') {
        "main: $newFullPkg.$newClass"
    }
    elseif ($line -match '^description:' -and $Description) {
        "description: $Description"
    }
    else {
        $line
    }
}

if ($Author) {
    $updated += "authors:"
    $updated += "  - $Author"
}

$updated | Set-Content $pluginPath -Encoding UTF8

# ---------------- CLEANUP ----------------

$iml = Join-Path $repoRoot "MCTemplate.iml"
if (Test-Path $iml) {
    Remove-Item $iml -Force
}

Write-Host ""
Write-Host "Done."

# self delete (safe)
Start-Sleep -Milliseconds 300
Remove-Item $PSCommandPath -Force -ErrorAction SilentlyContinue