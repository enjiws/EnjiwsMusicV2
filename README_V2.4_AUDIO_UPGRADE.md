# Enjiws Music V2.4 — Ultimate Audio Upgrade

This update combines the Advanced Audio Engine and the first Hi-Res/Lossless playback groundwork.

## Included in this update
- Media3 FLAC software decoder dependency (`media3-decoder-flac:1.4.1`).
- ExoPlayer renderer configuration prefers available extension decoders.
- Existing system Equalizer retained, with persisted band settings.
- Bass Boost and Virtualizer effects attached to the active ExoPlayer audio session.
- Bass Boost and Virtualizer strengths are persisted in app preferences.
- App version updated to 2.4 / versionCode 24.

## Important audio-quality notes
- FLAC decoding is enabled through the Media3 FLAC extension.
- WAV/PCM and other formats remain subject to Android/Media3 decoder support and the actual source encoding.
- ALAC is **not** claimed as supported by this update; a compatible decoder and test media are needed before advertising ALAC support.
- 24-bit/96 kHz or 24-bit/192 kHz output depends on the source file, decoder, Android audio path, DAC/device capabilities, and any system resampling. The app must not label an ordinary or upsampled stream as Hi-Res.
- Bass Boost, Virtualizer, and Equalizer are Android AudioEffect implementations. Availability and behavior vary by device/ROM/audio session; some OEM audio stacks may ignore or conflict with effects.
- Crossfade and true gapless transitions are not added in this patch because the current Media3 player pipeline needs a dedicated transition implementation and listening tests.

## Integration / build
The supplied `app.zip` contains the `app/` module only. It does not include root `settings.gradle(.kts)`, root `build.gradle(.kts)`, Gradle wrapper, or CI workflow, so a complete Gradle build cannot be run from this ZIP alone. Merge this `app/` directory into the existing repository while preserving its root Gradle and GitHub Actions files, then run the existing Android build workflow.
