# Edit the sentence and sound controls, then run from Windows PowerShell:
#   powershell.exe -ExecutionPolicy Bypass -File .\tools\generate-vocal.ps1
#
# This renders intelligible English with Windows' offline speech synthesizer,
# then reduces and reshapes it into a compact, Game Boy-like vocal sample.

$Sentence = "Beat phaser"
$OutputFileName = "vocal_layer.wav"
$VoiceName = $null       # Example: "Microsoft Zira Desktop"; null uses the default.
$SpeechRate = -5          # Windows speech rate: -10 through 10.
$SpeechVolume = 100      # Windows speech volume: 0 through 100.

$PitchSemitones = 0.0
$BitDepth = 6            # Lower values sound rougher; try 4 through 10.
$SampleHold = 3          # Higher values create more digital stepping.
$HighPassHz = 180
$LowPassHz = 4200
$OutputVolume = 0.75

$FfmpegPath = "C:\MyDocs\ffmpeg\ffmpeg6_1win64\ffmpeg.exe"
$OutputDirectory = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
$TemporaryFile = Join-Path $env:TEMP "phasegame-vocal-source.wav"

if (-not (Test-Path -LiteralPath $FfmpegPath)) {
    throw "FFmpeg was not found at '$FfmpegPath'."
}

Add-Type -AssemblyName System.Speech
$synthesizer = New-Object System.Speech.Synthesis.SpeechSynthesizer

try {
    if ($VoiceName) {
        $synthesizer.SelectVoice($VoiceName)
    }
    $synthesizer.Rate = $SpeechRate
    $synthesizer.Volume = $SpeechVolume
    $synthesizer.SetOutputToWaveFile($TemporaryFile)
    $synthesizer.Speak($Sentence)
}
finally {
    $synthesizer.Dispose()
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputPath = Join-Path $OutputDirectory $OutputFileName
$pitchRatio = [Math]::Pow(2.0, $PitchSemitones / 12.0)
$pitchRatioText =
    $pitchRatio.ToString("0.#####", [System.Globalization.CultureInfo]::InvariantCulture)
$volumeText =
    $OutputVolume.ToString("0.#####", [System.Globalization.CultureInfo]::InvariantCulture)

# Pitch without changing duration, restrict bandwidth, then quantize/hold samples.
$filters = @(
    "asetrate=48000*$pitchRatioText",
    "aresample=48000",
    "atempo=$([Math]::Round(1.0 / $pitchRatio, 5))",
    "highpass=f=$HighPassHz",
    "lowpass=f=$LowPassHz",
    "acrusher=bits=${BitDepth}:mix=1:mode=lin:aa=1:samples=$SampleHold",
    "volume=$volumeText"
)

try {
    & $FfmpegPath `
        -hide_banner `
        -loglevel error `
        -i $TemporaryFile `
        -af ($filters -join ",") `
        -ar 48000 `
        -ac 1 `
        -c:a pcm_s16le `
        -y $outputPath

    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg failed while generating $OutputFileName."
    }
}
finally {
    Remove-Item -LiteralPath $TemporaryFile -ErrorAction SilentlyContinue
}

Write-Host "Generated '$Sentence' as $outputPath"
