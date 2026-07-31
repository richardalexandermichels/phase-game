[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SourceRoot,

    [string]$ManifestPath,
    [string]$OutputDirectory,
    [string]$FfmpegPath
)

$ErrorActionPreference = "Stop"
$InvariantCulture = [System.Globalization.CultureInfo]::InvariantCulture

if (-not $ManifestPath) {
    $ManifestPath = Join-Path $PSScriptRoot "pitched-sample-packs\vcsl-steinway-b.json"
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
}

function Resolve-Executable {
    param(
        [string]$ExplicitPath,
        [Parameter(Mandatory)]
        [string]$CommandName
    )

    if ($ExplicitPath) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "$CommandName was not found at '$ExplicitPath'."
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "$CommandName was not found. Add it to PATH or pass -${CommandName}Path."
    }
    return $command.Source
}

function Resolve-ChildPath {
    param(
        [Parameter(Mandatory)]
        [string]$Root,
        [Parameter(Mandatory)]
        [string]$RelativePath
    )

    if ([System.IO.Path]::IsPathRooted($RelativePath)) {
        throw "Manifest paths must be relative: '$RelativePath'."
    }

    $resolvedRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $resolvedRoot $RelativePath))
    $rootPrefix = $resolvedRoot + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest path escapes its root: '$RelativePath'."
    }
    return $candidate
}

function Get-WavMetadata {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $stream = [System.IO.File]::OpenRead($Path)
    $reader = New-Object System.IO.BinaryReader($stream)
    try {
        $fourCc = {
            param([System.IO.BinaryReader]$BinaryReader)
            return [System.Text.Encoding]::ASCII.GetString($BinaryReader.ReadBytes(4))
        }
        if ((& $fourCc $reader) -ne "RIFF") {
            throw "'$Path' is not a RIFF file."
        }
        [void]$reader.ReadUInt32()
        if ((& $fourCc $reader) -ne "WAVE") {
            throw "'$Path' is not a WAVE file."
        }

        $format = $null
        $channels = $null
        $sampleRate = $null
        $blockAlign = $null
        $bitsPerSample = $null
        $dataBytes = $null

        while ($reader.BaseStream.Position + 8 -le $reader.BaseStream.Length) {
            $chunkId = & $fourCc $reader
            $chunkSize = [uint64]$reader.ReadUInt32()
            $chunkStart = $reader.BaseStream.Position
            $chunkEnd = $chunkStart + $chunkSize
            if ($chunkEnd -gt $reader.BaseStream.Length) {
                throw "'$Path' contains a truncated '$chunkId' chunk."
            }

            if ($chunkId -eq "fmt ") {
                if ($chunkSize -lt 16) {
                    throw "'$Path' contains an invalid fmt chunk."
                }
                $format = $reader.ReadUInt16()
                $channels = $reader.ReadUInt16()
                $sampleRate = $reader.ReadUInt32()
                [void]$reader.ReadUInt32()
                $blockAlign = $reader.ReadUInt16()
                $bitsPerSample = $reader.ReadUInt16()
            } elseif ($chunkId -eq "data") {
                $dataBytes = $chunkSize
            }

            $reader.BaseStream.Position = $chunkEnd + ($chunkSize % 2)
        }

        if ($null -eq $format -or $null -eq $dataBytes -or $blockAlign -le 0) {
            throw "'$Path' is missing required WAV chunks."
        }
        return [pscustomobject]@{
            Format = [int]$format
            Channels = [int]$channels
            SampleRate = [int]$sampleRate
            BitsPerSample = [int]$bitsPerSample
            DurationSeconds = [double]$dataBytes / $blockAlign / $sampleRate
        }
    } finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

$manifestFullPath = (Resolve-Path -LiteralPath $ManifestPath).Path
$sourceFullPath = (Resolve-Path -LiteralPath $SourceRoot).Path
$outputFullPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$manifest = Get-Content -Raw -LiteralPath $manifestFullPath | ConvertFrom-Json

if ($manifest.schemaVersion -ne 1) {
    throw "Unsupported pitched-sample manifest schema '$($manifest.schemaVersion)'."
}
if (-not $manifest.entries -or $manifest.entries.Count -eq 0) {
    throw "The manifest contains no output entries."
}
if ($manifest.sampleRate -le 0 -or $manifest.durationMs -le 0 -or
    $manifest.fadeOutMs -le 0 -or $manifest.fadeOutMs -ge $manifest.durationMs) {
    throw "The manifest has invalid sample-rate or envelope settings."
}

$FfmpegExecutable = Resolve-Executable $FfmpegPath "ffmpeg"

$duplicateOutputs = $manifest.entries |
    Group-Object outputFile |
    Where-Object Count -gt 1
if ($duplicateOutputs) {
    throw "The manifest repeats output filename '$($duplicateOutputs[0].Name)'."
}

New-Item -ItemType Directory -Force -Path $outputFullPath | Out-Null
$stagingDirectory = Join-Path $outputFullPath (".pitched-samples-" + [guid]::NewGuid().ToString("N"))
$backupDirectory = Join-Path $stagingDirectory "backup"
New-Item -ItemType Directory -Force -Path $stagingDirectory, $backupDirectory | Out-Null

$durationSeconds = $manifest.durationMs / 1000.0
$fadeSeconds = $manifest.fadeOutMs / 1000.0
$fadeStartSeconds = $durationSeconds - $fadeSeconds
$durationText = $durationSeconds.ToString("0.#####", $InvariantCulture)
$fadeText = $fadeSeconds.ToString("0.#####", $InvariantCulture)
$fadeStartText = $fadeStartSeconds.ToString("0.#####", $InvariantCulture)
$sampleRate = [int]$manifest.sampleRate
$globalGainDb = if ($null -ne $manifest.globalGainDb) {
    [double]$manifest.globalGainDb
} else {
    0.0
}
$limiterAutoLevel = if ($null -ne $manifest.limiterAutoLevel) {
    [bool]$manifest.limiterAutoLevel
} else {
    $true
}
$limiterLevelText = if ($limiterAutoLevel) { "true" } else { "false" }
$created = @()

try {
    foreach ($entry in $manifest.entries) {
        if ($entry.outputFile -notmatch '^[a-z][a-z0-9_]*\.wav$') {
            throw "Invalid Android raw-resource filename '$($entry.outputFile)'."
        }
        if ($entry.targetMidi -lt 0 -or $entry.targetMidi -gt 127 -or
            $entry.sourceMidi -lt 0 -or $entry.sourceMidi -gt 127) {
            throw "Invalid MIDI note in entry '$($entry.outputFile)'."
        }

        $source = Resolve-ChildPath $sourceFullPath $entry.sourceFile
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Missing source audio for $($entry.outputFile): '$source'."
        }
        $destination = Join-Path $stagingDirectory $entry.outputFile
        $semitones = [int]$entry.targetMidi - [int]$entry.sourceMidi
        $pitchRatio = [math]::Pow(2.0, $semitones / 12.0)
        $pitchRate = ($sampleRate * $pitchRatio).ToString("0.########", $InvariantCulture)
        $gainText = (
            [double]$entry.gainDb + $globalGainDb
        ).ToString("0.#####", $InvariantCulture)

        $filters = @("aresample=$sampleRate")
        if ($semitones -ne 0) {
            $filters += "asetrate=$pitchRate"
            $filters += "aresample=$sampleRate"
        }
        $filters += "volume=${gainText}dB"
        $filters += "atrim=start=0:end=$durationText"
        $filters += "afade=t=out:st=${fadeStartText}:d=$fadeText"
        # Keep the limiter's look-ahead from delaying the finger-down transient.
        $filters += (
            "alimiter=limit=0.891251:level=${limiterLevelText}:latency=1"
        )

        & $FfmpegExecutable `
            -hide_banner `
            -loglevel error `
            -y `
            -i $source `
            -af ($filters -join ',') `
            -ac 1 `
            -ar $sampleRate `
            -c:a pcm_s16le `
            $destination
        if ($LASTEXITCODE -ne 0) {
            throw "FFmpeg failed while rendering '$($entry.outputFile)'."
        }

        $metadata = Get-WavMetadata $destination
        if ($metadata.Format -ne 1 -or
            $metadata.SampleRate -ne $sampleRate -or
            $metadata.Channels -ne 1 -or
            $metadata.BitsPerSample -ne 16 -or
            [math]::Abs($metadata.DurationSeconds - $durationSeconds) -gt 0.002) {
            throw "Rendered WAV '$($entry.outputFile)' does not match the engine contract."
        }

        $created += [pscustomobject]@{
            Output = $entry.outputFile
            Note = $entry.targetNote
            Source = [System.IO.Path]::GetFileName($source)
            Shift = $semitones
        }
    }

    foreach ($entry in $manifest.entries) {
        $existing = Join-Path $outputFullPath $entry.outputFile
        if (Test-Path -LiteralPath $existing -PathType Leaf) {
            Copy-Item -LiteralPath $existing -Destination $backupDirectory
        }
    }

    try {
        foreach ($entry in $manifest.entries) {
            Copy-Item `
                -LiteralPath (Join-Path $stagingDirectory $entry.outputFile) `
                -Destination (Join-Path $outputFullPath $entry.outputFile) `
                -Force
        }
    } catch {
        foreach ($entry in $manifest.entries) {
            $backup = Join-Path $backupDirectory $entry.outputFile
            if (Test-Path -LiteralPath $backup -PathType Leaf) {
                Copy-Item `
                    -LiteralPath $backup `
                    -Destination (Join-Path $outputFullPath $entry.outputFile) `
                    -Force
            }
        }
        throw
    }

    $created | Format-Table -AutoSize
    Write-Host "Installed $($created.Count) '$($manifest.name)' sounds in $outputFullPath"
} finally {
    if (Test-Path -LiteralPath $stagingDirectory) {
        Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
    }
}
