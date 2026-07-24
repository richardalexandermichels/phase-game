# Edit these values, then run this script from PowerShell:
#   .\tools\generate-sounds.ps1

$FfmpegPath = "C:\MyDocs\ffmpeg\ffmpeg6_1win64\ffmpeg.exe"
$SampleRate = 48000

# Shared envelope defaults for pitched sounds.
$ToneDurationMs = 80.0
$ToneAttackMs = 3.0
$ToneReleaseMs = 70.0

# Supported waveforms: sine, square, triangle, noise.
$BasePitchHz = 220.0
$BaseWaveform = "triangle"
$BaseVolume = 0.90

$PerfectPitchHz = 660.0
$PerfectWaveform = "triangle"
$PerfectVolume = 0.50

$GoodPitchHz = 680.00
$GoodWaveform = "triangle"
$GoodVolume = 0.50

$ClosePitchHz = 690.0
$CloseWaveform = "triangle"
$CloseVolume = 0.50

# Pitch is ignored when the waveform is noise.
$MissPitchHz = 580.0
$MissWaveform = "square"
$MissDurationMs = 125.0
$MissAttackMs = 25.0
$MissReleaseMs = 75.0
$MissVolume = 0.15

$OutputDirectory = Join-Path $PSScriptRoot "..\app\src\main\res\raw"

function Format-Number {
    param([double]$Value)

    return $Value.ToString("0.#####", [System.Globalization.CultureInfo]::InvariantCulture)
}

function New-GameSound {
    param(
        [Parameter(Mandatory)]
        [string]$FileName,

        [Parameter(Mandatory)]
        [ValidateSet("sine", "square", "triangle", "noise")]
        [string]$Waveform,

        [double]$PitchHz,
        [double]$DurationMs,
        [double]$AttackMs,
        [double]$ReleaseMs,
        [double]$Volume
    )

    if ($AttackMs + $ReleaseMs -gt $DurationMs) {
        throw "$FileName has an attack plus release longer than its duration."
    }

    $durationSeconds = Format-Number ($DurationMs / 1000.0)
    $attackSeconds = Format-Number ($AttackMs / 1000.0)
    $releaseSeconds = Format-Number ($ReleaseMs / 1000.0)
    $releaseStartSeconds = Format-Number (($DurationMs - $ReleaseMs) / 1000.0)
    $frequency = Format-Number $PitchHz
    $volumeValue = Format-Number $Volume

    $source = switch ($Waveform) {
        "sine" {
            "sine=frequency=${frequency}:duration=${durationSeconds}:sample_rate=$SampleRate"
        }
        "square" {
            "aevalsrc=sgn(sin(2*PI*${frequency}*t)):s=${SampleRate}:d=${durationSeconds}"
        }
        "triangle" {
            "aevalsrc=(2/PI)*asin(sin(2*PI*${frequency}*t)):s=${SampleRate}:d=${durationSeconds}"
        }
        "noise" {
            "anoisesrc=color=white:duration=${durationSeconds}:sample_rate=$SampleRate"
        }
    }

    $filters = @()
    if ($AttackMs -gt 0.0) {
        $filters += "afade=t=in:st=0:d=$attackSeconds"
    }
    if ($ReleaseMs -gt 0.0) {
        $filters += "afade=t=out:st=${releaseStartSeconds}:d=$releaseSeconds"
    }
    $filters += "volume=$volumeValue"

    $outputPath = Join-Path $OutputDirectory $FileName

    & $FfmpegPath `
        -hide_banner `
        -loglevel error `
        -f lavfi `
        -i $source `
        -af ($filters -join ",") `
        -ac 1 `
        -c:a pcm_s16le `
        -y $outputPath

    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg failed while generating $FileName."
    }

    Write-Host "Generated $FileName ($Waveform, $PitchHz Hz)"
}

if (-not (Test-Path -LiteralPath $FfmpegPath)) {
    throw "FFmpeg was not found at '$FfmpegPath'. Update `$FfmpegPath at the top of this script."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

New-GameSound `
    -FileName "player_click.wav" `
    -Waveform $BaseWaveform `
    -PitchHz $BasePitchHz `
    -DurationMs $ToneDurationMs `
    -AttackMs $ToneAttackMs `
    -ReleaseMs $ToneReleaseMs `
    -Volume $BaseVolume

New-GameSound `
    -FileName "player_perfect.wav" `
    -Waveform $PerfectWaveform `
    -PitchHz $PerfectPitchHz `
    -DurationMs $ToneDurationMs `
    -AttackMs $ToneAttackMs `
    -ReleaseMs $ToneReleaseMs `
    -Volume $PerfectVolume

New-GameSound `
    -FileName "player_good.wav" `
    -Waveform $GoodWaveform `
    -PitchHz $GoodPitchHz `
    -DurationMs $ToneDurationMs `
    -AttackMs $ToneAttackMs `
    -ReleaseMs $ToneReleaseMs `
    -Volume $GoodVolume

New-GameSound `
    -FileName "player_close.wav" `
    -Waveform $CloseWaveform `
    -PitchHz $ClosePitchHz `
    -DurationMs $ToneDurationMs `
    -AttackMs $ToneAttackMs `
    -ReleaseMs $ToneReleaseMs `
    -Volume $CloseVolume

New-GameSound `
    -FileName "player_miss.wav" `
    -Waveform $MissWaveform `
    -PitchHz $MissPitchHz `
    -DurationMs $MissDurationMs `
    -AttackMs $MissAttackMs `
    -ReleaseMs $MissReleaseMs `
    -Volume $MissVolume

Write-Host "All game sounds regenerated in $OutputDirectory"
