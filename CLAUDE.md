# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SynthPi is a polyphonic software synthesizer written in Java 8. It runs as a standalone JVM process that opens an audio device, listens for MIDI, and serves a browser-based control surface over an embedded Jetty + WebSocket server. The engine supports three oscillator modes — virtual analog wavetables, additive, and Karplus-Strong ("exciter").

## Build and run

This is a Maven project targeting Java 1.8.

```sh
mvn package          # produces target/SynthPi-1.0.0-SNAPSHOT-boxed.jar (fat jar via boxed-assembly.xml)
java -jar target/SynthPi-1.0.0-SNAPSHOT-boxed.jar [options]
```

There are **no tests** — `mvn test` is a no-op.

Per the README, the JAudioLibs dependencies (`jnajack`, `audioservers-*` v1.2.0) are not on Maven Central; they must be installed into the local Maven repo from https://github.com/jaudiolibs (build and `mvn install` the `audioservers` and `jnajack` projects) before `mvn package` will resolve.

Useful CLI options (parsed in [SynthPi.java](src/main/java/de/privatepublic/pi/synth/SynthPi.java) — commons-cli):

- `-headless` — no Swing window
- `-usejackaudioserver` — use JACK instead of JavaSound
- `-audiobuffersize <N>` (default 64–128, see `P.SAMPLE_BUFFER_SIZE`)
- `-httpport <port>` (default 31415)
- `-midich <1-16>`, `-pitchbendmode 0|1` (use 0 on macOS), `-midifile <path>`
- `-disablewebcache`, `-disablebrowserstart`, `-openbrowsercmd <cmd>`
- `-lowbudgetadditive` — cheaper additive synthesis (Raspberry Pi)
- `-help`

User data lives in `~/synthpi/`: `user-patches.json`, `settings.json`, `recent.json` (auto-saved on shutdown). Factory patches ship as `src/main/resources/factory-patches.json`.

## Architecture

### The audio thread is the hot path

`SynthPiAudioClient` registers as a JAudioLibs `AudioClient`; its `process(...)` callback runs in a `MAX_PRIORITY` thread driven by either JavaSound or JACK (selected via `ServiceLoader<AudioServerProvider>` matching `P.AUDIO_SYSTEM_NAME`). For every audio buffer it calls `P.interpolate()` then `synthengine.process(outputs, nframes)` on the single `ISynth` (an `AnalogSynth`).

**Anything reachable from this callback must be allocation-free and lock-free in the steady state.** That's why most parameter access goes through preallocated arrays in `P` rather than getters, why per-voice oscillator instances are created up front for all three modes, and why the `RouteFilters` strategies in `AnalogSynthVoice` are anonymous-inner-class instances captured into fields rather than constructed per buffer.

### `P.java` is the global parameter store

[P.java](src/main/java/de/privatepublic/pi/synth/P.java) is the single source of truth for synth state. Parameters are addressed by integer index constants (e.g. `P.FILTER1_FREQ`, `P.OSC1_WAVE`); `PARAM_STORE_SIZE = 84` sizes every parameter array. For each parameter index, `P` precomputes several derived representations so the audio thread doesn't have to:

- `VAL[i]` — raw 0–1
- `VALC[i]` — centered −1..1 (`(VAL-0.5)*2`)
- `VALX[i]` — `VAL^4` (cheap exponential approximation)
- `VALXC[i]` — centered quartic (signed)
- `VALMIXLOW[i]` / `VALMIXHIGH[i]` — equal-power crossfade pair derived from `VALXC`
- `IS[i]` — boolean (`VAL > 0`)
- `VAL_RAW_MIDI[i]` — 0–127

`P.set(index, val)` is the public mutator. Indices listed in `SET_INTERPOLATED` (volume, filter freq/res, mix, etc.) ramp toward `TARGET_VAL` over `TARGET_STEPS` buffers via `P.interpolate()` — this avoids zipper noise on continuous controls. All other indices update immediately. `setDirectly` also dispatches a small set of side effects (filter type/routing enum updates, oscillator-mode switch, reverb re-tuning, combined mod-wheel/LFO amount).

`OSC_PATH[i]` maps each parameter index to an OSC-style path string (e.g. `/filter/1/freq`). This is the bridge to the web UI — see below.

### Synth engine layout

`AnalogSynth` (the only `ISynth` impl) owns:
- a fixed pool of 8 `AnalogSynthVoice` instances (`P.POLYPHONY_MAX`)
- a single chain of FX `IProcessor`s applied to the summed voice output: `DistortionExp → Chorus → Delay → Freeverb` (and `Limiter` if `P.LIMITER_ENABLED`)
- voice allocation in `onMidiNoteMessage`: prefers a voice already playing the same note, else picks the lowest `captureWeight` (RESTing voices win, then released voices by env value, then by hold time)
- mono/legato handling via the static `Key` list

Each `AnalogSynthVoice` carries **all three oscillator implementations** (`WaveTableOscillator`, `AdditiveOscillator`, `ExciterOscillator`) for primary and secondary slots. The active pair is selected per-buffer from `P.VAL_OSCILLATOR_MODE`. Two `MultiModeFilter`s are routed via one of nine prebuilt `RouteFilters` strategies (3 routings × 4 on/off combinations) chosen each buffer based on `P.VAL_FILTER_ROUTING` and `FILTERn_ON`.

### Communication layers

Three input sources feed the same parameter store:

1. **MIDI** ([MidiHandler](src/main/java/de/privatepublic/pi/synth/comm/MidiHandler.java)) — auto-discovers transmitters, routes notes/CC/pitchbend to receivers via `IMidiNoteReceiver` / `IPitchBendReceiver`. Supports MIDI learn mode for binding CCs to parameter indices; mappings persist in `settings.json`.
2. **WebSocket OSC-style messages** ([SynthSocket](src/main/java/de/privatepublic/pi/synth/comm/web/SynthSocket.java) + [ControlMessageDispatcher](src/main/java/de/privatepublic/pi/synth/comm/ControlMessageDispatcher.java)) — text frames of the form `/path/to/param=floatvalue` on `ws://host:31415/oscsocket`. The dispatcher parses commands (`/command/...` for randomize/load/save/sequence/learn) vs. parameter updates (matched against `P.OSC_PATH[]`).
3. **MIDI file playback** ([MidiPlayback](src/main/java/de/privatepublic/pi/synth/comm/MidiPlayback.java)) — for testing without a controller; bundled sequences in `src/main/resources/midiseq/`.

All three converge on `P.set()` (or the voice-level note callbacks). Parameter changes propagate back to all connected websocket clients via `ControlMessageDispatcher.update()` so multiple browser tabs stay in sync.

### Web UI

Served from `src/main/resources/webresources/` via [JettyWebServerInterface](src/main/java/de/privatepublic/pi/synth/comm/web/JettyWebServerInterface.java). Resources are eagerly cached at startup into a `Hashtable` (see `CachedResource.CACHE_FILENAMES` — **add new static assets to that list or they won't be served**, except in `-disablewebcache` mode). The live UI is `index.html` + `synthcontrol.js` (jQuery + jquery.knob).

A second UI mockup lives under `webresources/controls/` (referenced in the latest commit "mockup new interface") — it's a work-in-progress redesign and is not currently wired into the cache list or routing.

### Persistence

[PresetHandler](src/main/java/de/privatepublic/pi/synth/PresetHandler.java) reads/writes JSON via `org.json` (note: an old version, 20140107). Patch IDs use prefix `f<n>` for factory entries and `u<n>` for user entries. Patch list sorting is by category then name. The class also has a `main(...)` method used as a one-off migration tool to strip legacy `description`/`oscmsg` fields from `factory-patches.json`.
