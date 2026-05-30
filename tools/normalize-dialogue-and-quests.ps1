$ErrorActionPreference = 'Stop'

$nodeScript = Join-Path $PSScriptRoot 'normalize-dialogue-and-quests.mjs'
$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
if ($nodeCommand -and (Test-Path $nodeScript)) {
    & $nodeCommand.Source $nodeScript
    exit $LASTEXITCODE
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$dialogueRoot = Join-Path $repoRoot 'neoforge\src\main\resources\data\villagerretaliation\dialogue\en_us'
$dialogueTreeRoot = Join-Path $repoRoot 'neoforge\src\main\resources\data\villagerretaliation\dialogue_trees\en_us'
$forcedDialogueRoot = Join-Path $repoRoot 'neoforge\src\main\resources\data\villagerretaliation\forced_dialogue'
$questRoot = Join-Path $repoRoot 'neoforge\src\main\resources\data\villagerretaliation\quests'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Has-Property {
    param($Object, [string]$Name)

    return $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
}

function Normalize-Token {
    param($Value)

    if ($null -eq $Value) {
        return ''
    }

    $normalized = [string]$Value
    $normalized = $normalized.Trim().ToLowerInvariant()
    $normalized = $normalized -replace '[:/\\]+', '.'
    $normalized = $normalized -replace '[^a-z0-9_.-]+', '_'
    while ($normalized.Contains('..')) {
        $normalized = $normalized.Replace('..', '.')
    }
    return ($normalized -replace '^[._-]+|[._-]+$', '')
}

function Unique-Strings {
    param([string[]]$Values)

    return @($Values | Where-Object { $_ } | Sort-Object -Unique)
}

function Ensure-Metadata {
    param($Object)

    if (-not (Has-Property $Object 'metadata') -or $Object.metadata -isnot [pscustomobject]) {
        $Object | Add-Member -NotePropertyName metadata -NotePropertyValue ([pscustomobject]@{}) -Force
    }

    return $Object.metadata
}

function Set-PropertyValue {
    param($Object, [string]$Name, $Value)

    if ($null -eq $Object) {
        return
    }

    $existing = $Object.PSObject.Properties.Match($Name)
    if ($existing.Count -eq 0) {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
        return
    }

    $Object.$Name = $Value
}

function Read-Json {
    param([string]$Path)

    return Get-Content $Path -Raw | ConvertFrom-Json
}

function Write-Json {
    param([string]$Path, $Data)

    $json = $Data | ConvertTo-Json -Depth 100
    [System.IO.File]::WriteAllText($Path, $json + [Environment]::NewLine, $utf8NoBom)
}

function Get-DialogueSection {
    param([string[]]$RelativeSegments, $Data)

    foreach ($segment in $RelativeSegments) {
        switch ($segment) {
            'option' { return 'options' }
            'options' { return 'options' }
            'line' { return 'lines' }
            'lines' { return 'lines' }
            'message' { return 'messages' }
            'messages' { return 'messages' }
            'opening' { return 'openings' }
            'openings' { return 'openings' }
            'closing' { return 'closings' }
            'closings' { return 'closings' }
            'pacify' { return 'pacify' }
            'pacification' { return 'pacify' }
        }
    }

    if (Has-Property $Data 'type' -and $Data.type -eq 'dialogue_option') {
        return 'options'
    }
    if (Has-Property $Data 'label') {
        return 'options'
    }
    if (Has-Property $Data 'key') {
        return 'messages'
    }
    if (Has-Property $Data 'request') {
        return 'lines'
    }
    if (Has-Property $Data 'outcomes') {
        return 'pacify'
    }

    return 'lines'
}

function Get-ScopeTags {
    param([string[]]$RelativeSegments, [string]$Section)

    $sectionIndex = -1
    for ($index = 0; $index -lt $RelativeSegments.Length; $index++) {
        $segment = $RelativeSegments[$index]
        $matchesSection = ($Section -eq 'options' -and ($segment -eq 'option' -or $segment -eq 'options'))
        $matchesSection = $matchesSection -or ($Section -eq 'lines' -and ($segment -eq 'line' -or $segment -eq 'lines'))
        $matchesSection = $matchesSection -or ($Section -eq 'messages' -and ($segment -eq 'message' -or $segment -eq 'messages'))
        $matchesSection = $matchesSection -or ($Section -eq 'openings' -and ($segment -eq 'opening' -or $segment -eq 'openings'))
        $matchesSection = $matchesSection -or ($Section -eq 'closings' -and ($segment -eq 'closing' -or $segment -eq 'closings'))
        $matchesSection = $matchesSection -or ($Section -eq 'pacify' -and ($segment -eq 'pacify' -or $segment -eq 'pacification'))
        if ($matchesSection) {
            $sectionIndex = $index
            break
        }
    }

    $scopeSegments = @()
    if ($sectionIndex -gt 0) {
        $scopeSegments = @($RelativeSegments[0..($sectionIndex - 1)])
    }
    $scopeSegments = @($scopeSegments | ForEach-Object { Normalize-Token $_ } | Where-Object { $_ })

    if ($scopeSegments.Count -gt 0 -and $scopeSegments[0] -eq 'professions') {
        $professionSegments = @($scopeSegments | Select-Object -Skip 1)
        if ($professionSegments.Count -gt 0) {
            return @("scope.profession.$(($professionSegments -join '.'))")
        }
        return @('scope.profession')
    }

    if ($scopeSegments.Count -gt 0 -and $scopeSegments[0] -eq 'groups') {
        $groupSegments = @($scopeSegments | Select-Object -Skip 1)
        if ($groupSegments.Count -gt 0) {
            return @("scope.group.$(($groupSegments -join '.'))")
        }
        return @('scope.group')
    }

    if ($scopeSegments.Count -gt 0 -and $scopeSegments[0] -eq 'global') {
        return @('scope.global')
    }

    return @()
}

$forcedByQuestline = @{}
Get-ChildItem $forcedDialogueRoot -Recurse -Filter *.json | ForEach-Object {
    $relative = $_.FullName.Substring($forcedDialogueRoot.Length).TrimStart('\').Replace('\', '/')
    if (-not $relative.StartsWith('quest/')) {
        return
    }

    $questline = Normalize-Token $_.BaseName
    $data = Read-Json $_.FullName
    $entries = if (Has-Property $data 'entries' -and $data.entries) { @($data.entries) } else { @($data) }
    $ids = @()
    foreach ($entry in $entries) {
        if (Has-Property $entry 'id' -and $entry.id) {
            $ids += [string]$entry.id
        }
    }

    if ($ids.Count -gt 0) {
        $forcedByQuestline[$questline] = $ids
    }
}

Get-ChildItem $dialogueRoot -Recurse -Filter *.json | ForEach-Object {
    $data = Read-Json $_.FullName
    if ($data -isnot [pscustomobject]) {
        return
    }

    $relative = $_.FullName.Substring($dialogueRoot.Length).TrimStart('\').Replace('\', '/')
    $segments = $relative.Split('/')
    $section = Get-DialogueSection $segments $data
    $tags = Unique-Strings (@('content.dialogue', 'dialogue.ambient', "section.$section") + (Get-ScopeTags $segments $section))
    $metadata = Ensure-Metadata $data
    Set-PropertyValue $metadata 'tags' $tags
    Write-Json $_.FullName $data
}

Get-ChildItem $dialogueTreeRoot -Recurse -Filter *.json | ForEach-Object {
    $data = Read-Json $_.FullName
    if ($data -isnot [pscustomobject]) {
        return
    }

    $relative = $_.FullName.Substring($dialogueTreeRoot.Length).TrimStart('\').Replace('\', '/')
    $segments = $relative.Split('/')
    $metadata = Ensure-Metadata $data
    $tags = @('content.dialogue', 'dialogue.scene')
    if ($segments[0] -eq 'quests') {
        $tags += @('scope.quest_scene', 'quest.linked')
    }
    if (Has-Property $metadata 'questline' -and $metadata.questline) {
        $tags += @("questline.$([string]$metadata.questline)")
    }
    Set-PropertyValue $metadata 'tags' (Unique-Strings $tags)
    Write-Json $_.FullName $data
}

Get-ChildItem $forcedDialogueRoot -Recurse -Filter *.json | ForEach-Object {
    $data = Read-Json $_.FullName
    if ($data -isnot [pscustomobject]) {
        return
    }

    $relative = $_.FullName.Substring($forcedDialogueRoot.Length).TrimStart('\').Replace('\', '/')
    $segments = $relative.Split('/')
    $questline = if ($segments[0] -eq 'quest') { Normalize-Token $_.BaseName } else { '' }
    $tags = @('content.dialogue', 'dialogue.forced')
    if ($questline) {
        $tags += @('quest.linked', "questline.$questline")
    }
    $metadata = Ensure-Metadata $data
    Set-PropertyValue $metadata 'tags' (Unique-Strings $tags)
    Write-Json $_.FullName $data
}

Get-ChildItem $questRoot -Recurse -Filter *.json | ForEach-Object {
    $data = Read-Json $_.FullName
    if ($data -isnot [pscustomobject]) {
        return
    }

    $questline = if (Has-Property $data 'questline' -and $data.questline) {
        Normalize-Token ([string]$data.questline)
    } else {
        Normalize-Token $_.BaseName
    }

    $metadata = Ensure-Metadata $data
    Set-PropertyValue $metadata 'tags' (Unique-Strings @('content.quest', 'dialogue.linked', "questline.$questline"))

    if (-not (Has-Property $data 'links') -or $data.links -isnot [pscustomobject]) {
        $data | Add-Member -NotePropertyName links -NotePropertyValue ([pscustomobject]@{}) -Force
    }
    if ($forcedByQuestline.ContainsKey($questline)) {
        Set-PropertyValue $data.links 'forced_dialogue' @($forcedByQuestline[$questline])
    }

    Write-Json $_.FullName $data
}

Write-Host 'Normalized dialogue and quest metadata.'