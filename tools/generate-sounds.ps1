# Edit these values, then run this script from PowerShell:
#   .\tools\generate-sounds.ps1

$FfmpegPath = "C:\MyDocs\ffmpeg\ffmpeg6_1win64\ffmpeg.exe"
$SampleRate = 48000

# Shared envelope defaults for pitched sounds.
$ToneDurationMs = 200.0
$ToneAttackMs = 70.0
$ToneReleaseMs = 70.0

# Supported waveforms: sine, square, triangle, noise.
$BasePitchHz = 220.0
$BaseWaveform = "triangle"
$BaseVolume = 0.90

$PerfectPitchHz = 294.0
$PerfectWaveform = "triangle"
$PerfectVolume = 0.50

$GoodPitchHz = 311.00
$GoodWaveform = "triangle"
$GoodVolume = 0.50

$ClosePitchHz = 330.0
$CloseWaveform = "triangle"
$CloseVolume = 0.50

# Pitch is ignored when the waveform is noise.
$MissPitchHz = 580.0
$MissWaveform = "square"
$MissDurationMs = 125.0
$MissAttackMs = 25.0
$MissReleaseMs = 75.0
$MissVolume = 0.15

# Twelve D-major-pentatonic rows used by Design Mode. Player rows are
# generated one octave above the corresponding base rows.
$DesignBasePitchHz = @(
    73.416,  # D2
    82.407,  # E2
    92.499,  # F#2
    110.000, # A2
    123.471, # B2
    146.832, # D3
    164.814, # E3
    184.997, # F#3
    220.000, # A3
    246.942, # B3
    293.665, # D4
    329.628  # E4
)

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

for ($index = 0; $index -lt $DesignBasePitchHz.Count; $index++) {
    $suffix = "{0:D2}" -f $index
    $basePitch = $DesignBasePitchHz[$index]
    $playerPitch = $basePitch * 2.0

    New-GameSound `
        -FileName "design_base_$suffix.wav" `
        -Waveform $BaseWaveform `
        -PitchHz $basePitch `
        -DurationMs $ToneDurationMs `
        -AttackMs $ToneAttackMs `
        -ReleaseMs $ToneReleaseMs `
        -Volume $BaseVolume

    New-GameSound `
        -FileName "design_player_$suffix.wav" `
        -Waveform $PerfectWaveform `
        -PitchHz $playerPitch `
        -DurationMs $ToneDurationMs `
        -AttackMs $ToneAttackMs `
        -ReleaseMs $ToneReleaseMs `
        -Volume $PerfectVolume
}

Write-Host "All game sounds regenerated in $OutputDirectory"
