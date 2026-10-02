# NeuroLab

NeuroLab is an experimental Forge mod that lets a connectome-backed neural simulation influence Minecraft mobs. It includes a custom fly mob for repeatable experiments, an adapter for attaching the same controller to other mobs, and a live client dashboard for watching the controller's output.

The point is to make an interesting, inspectable game experiment—not to claim that a fly's consciousness has been copied into Minecraft. The bundled graph is biological data; the stimulus encoding, neural parameters, and mapping from neural activity to game movement are still exploratory.

## Project status

Version 0.4.2 targets **Minecraft 1.21.1 / Forge 52.1.16** and retains the new control modes, stimulus commands, and dashboard features. Earlier version 0.3.0 targeted Minecraft 1.21.11 and does not load on 1.21.1. Use `neurolab-1.21.1-0.4.2.jar` for the 1.21.1 profile.

This is an early research prototype targeting Minecraft 1.21.1 and Forge. The Forge port is under active validation; until a client and dedicated-server play test passes, treat behavior and performance as experimental and use a backed-up test world.

## What is included

- A custom **Neuro Fly** entity with a segmented body, compound eyes, six legs, animated wings, simple autonomous movement, and a Creative-mode spawn egg.
- A connectome reader for the bundled FLYB v1 graph and a sparse, event-driven leaky integrate-and-fire (LIF) simulator.
- Commands to attach the simulator to the Neuro Fly or another mob, inspect load status, and detach it.
- Timed sensory test pulses for controlled input/output checks.
- A client-side engineering dashboard with configurable side panels and sixteen per-mob time-series plots.
- An above-mob brain hologram for attached mobs, showing the live count of connectome edges activated by spikes in each 50 ms neural step.
- Asynchronous JSONL telemetry written to the current world's `neurolab/events.jsonl` file.

The implementation is intentionally small enough to inspect. There is no separate launcher or account service, and the mod does not require Fabric API.

## Requirements

- Minecraft **1.21.1**
- Minecraft Forge **52.1.16** for Minecraft 1.21.1
- Java **21**

Install the mod JAR on the server and on every client that needs the custom entity renderer and analysis screen. For a local single-player experiment, the client installation is enough.

## Install a published release

1. Install [Minecraft Forge for 1.21.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html) and use Java 21 to run the game.
2. Download the `neurolab-1.21.1-<version>.jar` asset from [GitHub Releases](https://github.com/Echrem/neurolab/releases/latest). Choose the regular mod JAR, not the `-sources.jar` or GitHub's automatically generated **Source code** archives.
3. Put the mod JAR in the Forge profile's `mods` folder. The usual folders are `%appdata%\.minecraft\mods` on Windows, `~/Library/Application Support/minecraft/mods` on macOS, and `~/.minecraft/mods` on Linux. For a third-party launcher, use that launcher's instance-specific `mods` folder.
4. Select the Forge 1.21.1 profile and launch the game. Back up your test world before trying the prototype.

For a dedicated server, install the mod on the server and on each client joining it. Release builds are published automatically after a matching `v*` version tag passes the test-and-build workflow; see [Contributing](CONTRIBUTING.md) for the versioning workflow.

## Quick start

1. Build the mod using the instructions below, or download the latest release JAR.
2. Put the JAR in the Forge installation's `mods` folder. No Fabric API is required.
3. Start a test world and spawn the fly using its Creative-mode spawn egg or this command:

   ```mcfunction
   /summon neurolab:neuro_fly
   ```

4. Neuro Fly entities automatically attach after they spawn and the bundled dataset finishes loading. Check the status:

   ```mcfunction
   /neurolab status
   ```

5. To attach another mob, target its actual entity type. For a sheep or villager, for example:

   ```mcfunction
   /neurolab attach @e[type=minecraft:sheep,limit=1,sort=nearest]
   /neurolab attach @e[type=minecraft:villager,limit=1,sort=nearest]
   ```

6. Open the dashboard with **Ctrl+N** and use **SETTINGS** to choose side panels and plots. The `N` key can be changed in Minecraft's Controls screen; Ctrl is the required modifier.
7. When finished, detach a selected mob to restore its previous AI state:

   ```mcfunction
   /neurolab detach @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
   ```

Useful checks:

```mcfunction
/neurolab status
/summon neurolab:neuro_fly
/neurolab attach @e[type=minecraft:sheep,limit=1,sort=nearest]
/neurolab stimulate @e[type=neurolab:neuro_fly,limit=1,sort=nearest] looming 1 40
/neurolab detach @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
```

Neuro Fly spawn eggs and `/summon neurolab:neuro_fly` attach automatically. Other mobs can be attached with `/neurolab attach <entity selector>`; use the exact type in the selector, such as `minecraft:sheep`, `minecraft:villager`, or `minecraft:spider`. The mod has no fixed attached-mob limit. Brain tasks share a CPU-sized worker pool, so adding many mobs can reduce the real-time factor and increase server load. Attaching starts in `assisted` mode and pauses the mob's vanilla AI; detaching or unloading its chunk restores the AI state that was present before attachment.

For controlled input/output checks, use `/neurolab stimulate <mob> <sense> <strength> [ticks]`. Strength is 0–1, duration defaults to 40 ticks, and the maximum duration is 1200 ticks. Supported senses are `eye`, `left_eye`, `right_eye`, `looming`, `touch`, `odor`, and `taste`. Stimuli enter sensory channels only; this command does not set motor output directly. NeuroLab commands require operator permission on a server.

## Controlled comparisons

Switch an attached mob between three explicit conditions without restarting its neural worker:

| Mode | Neural simulation | Minecraft movement |
| --- | --- | --- |
| `assisted` (default) | Running | Neural output, with the labeled reflex/exploration fallback when silent |
| `neural` | Running | Neural output only; silent motor channels produce no forward/turn/lift commands |
| `observe` | Running | Original AI state restored; NeuroLab records output but does not apply body commands |

```mcfunction
/neurolab mode @e[type=neurolab:neuro_fly,limit=1,sort=nearest] neural
/neurolab inspect @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
/neurolab trial @e[type=neurolab:neuro_fly,limit=1,sort=nearest] wall_baseline 1200
/neurolab endtrial @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
/neurolab stimulate @e[type=neurolab:neuro_fly,limit=1,sort=nearest] left_eye 0.8 100
/neurolab unstimulate @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
/neurolab mode @e[type=neurolab:neuro_fly,limit=1,sort=nearest] observe
```

Mode and sense arguments support tab completion. `/neurolab trial <mob> <label> <ticks>` marks a named interval of 1–12000 server ticks (up to ten minutes); labels use letters, numbers, dots, underscores, and hyphens. `/neurolab endtrial <mob>` closes it early. Starting another trial closes the previous one. Trials do not reset or pause the brain: use them to segment continuous runs, not as independent initial-state-matched experiments. Start/end markers and each sample's `trialLabel` and relative `trialTick` are recorded in JSONL. Trials end automatically at their duration. `inspect` reports the mode, neural tick, spikes, active cells, real-time factor, and active test-pulse count. `unstimulate` removes all test pulses while natural sensory input continues. Setting a sense's stimulus strength to zero cancels that sense only. Reapplying a sense replaces its strength and duration. Durations use server world ticks and expire at the exact deadline.

Changing modes preserves neural state and existing pulses; it is not a reset or a matched independent trial. Normal physics still applies in `neural` mode. In `observe` mode, body-command fields are zero because NeuroLab applies none; they do not measure the mob's actual velocity. A mob whose AI was disabled before attachment remains AI-disabled in observation mode. Attachment and mode choices are session state and are not persisted across chunk unloads.

## How the controller works

The bundled graph is derived from the neuPrint `male-cns:v1.0` adult male central nervous system connectome. It contains **176,422 neurons** and **6,287,749 retained connections** after this project's documented filtering and format conversion. The data file is compressed at `src/main/resources/connectome/male-cns-v1.0.flyb.gz`.

At runtime, `ConnectomeData` reads the graph and its annotations. `FlyBrain` advances a sparse LIF network in 0.5 ms steps, grouped into 50 ms simulation updates. Brain updates run as scheduled tasks on a shared, lower-priority daemon pool; attached mobs are not capped, but CPU time is finite and the real-time factor includes integration time and scheduler delay. Minecraft entity and world reads and writes stay on the server thread: workers receive numeric sensory values and publish snapshots for the next game update. This design keeps neural integration off the main tick; it is not a performance guarantee, and server load still needs measurement in real worlds.

The current world-to-network and network-to-mob mappings are deliberately simple:

| Game observation | Current input proxy |
| --- | --- |
| Vision | Brightness sampled along left, center, and right forward rays and sent to side-annotated retinal neurons; timed pulses can target either eye |
| Looming | Nearby entities moving toward the mob; also available as a timed test pulse |
| Odor | Nearby dropped item entities; also available as a timed test pulse |
| Taste | Contact with a small set of food-related blocks; also available as a timed test pulse |
| Touch | Collision or recent damage; also available as a timed test pulse |

The simulator injects events into annotated sensory populations rather than directly stimulating motor neurons. Activity in selected descending-neuron labels is summarized into neural forward, turn, lift, and escape channels. Since those channels can remain silent in this prototype, a separately labeled, hand-built embodiment fallback supplies slow exploration and collision/looming response while the neural channels are quiet. The dashboard and JSONL log keep neural output separate from body commands and mark when the fallback is active. This fallback is game-control scaffolding, not connectome-derived behavior. The Neuro Fly can use lift; for other mobs, movement is constrained by the capabilities of that entity and normal Minecraft physics.

## Reading the dashboard and logs

Open the dashboard with **Ctrl+N**. Its side panels show three eye samples, looming, touch, odor, taste, neural motor output, and effective body commands. Up to sixteen plots cover those channels, spikes, active neurons, and real-time factor. **SETTINGS** toggles either side panel and each plot; these local preferences persist in `config/neurolab-dashboard.properties`. Use the left/right arrow keys to switch between tracked mobs. **FREEZE** or **Space** holds a snapshot of the plots for inspection; **RESUME** returns to live data. Only the display is frozen: the server, simulation, telemetry collection, and logs continue. Use the mouse wheel over the chart area to scroll through plots that do not fit vertically. Each chart retains up to 180 samples; x positions represent sample order, not wall-clock time, and the server sends observations every two game ticks. A mob's plot is removed after ten seconds without new telemetry.

The analysis and settings screens use a solid backdrop so Minecraft's menu blur does not wash out their controls. On short displays, settings show the plot controls in two pages; use the `‹ 1/2` and `2/2 ›` buttons at the bottom.

The dashboard controls render above an opaque background and keep side panels visible at smaller GUI scales. Mob movement commands now move the entity on the server with normal collision checks, so the control remains active while vanilla mob AI is paused.

This panel visualizes decoded telemetry; it does not render the full connectome or per-neuron electrophysiology. Counts use a per-chart automatic range, while bounded control signals use their defined ranges; turn is plotted around a zero baseline so direction is visible. The above-mob hologram is a compact activity schematic; its synapse count is the actual number of graph edges traversed by spiking neurons in the latest neural step, not a display of individual anatomical synapse locations. It is an inspection aid, not a calibrated measurement instrument. The server also appends observations to:

```text
<world>/neurolab/events.jsonl
```

Each JSONL observation includes the control `mode`, stable `entityUuid`, `dimension`, and server `gameTick`, alongside the neural and body signals. When a trial is active, observations also include its `trialLabel` and tick offset; `trial_start` and `trial_end` records make boundaries explicit, including automatic expiry and entity unload. These fields help separate experimental conditions and entities across recordings. Client and server must both use the same mod version; the telemetry uses protocol 4.

The JSONL log is intended for offline inspection and analysis. Avoid sharing it without checking it for world or server details you do not want to publish.

## What this does not establish

The connectome supplies a biological wiring graph, but this mod is not a validated whole-animal brain emulation. The LIF time constants, thresholds, synaptic scaling, sensory rates, and motor decoding have not been fitted as a complete biological model. Minecraft brightness is not a fly's visual input; item entities and food blocks are rough stand-ins for chemical senses; and the movement adapter is a game-control layer. A successful build or an entertaining behavior should not be presented as evidence of biological fidelity.

For a video or report, describe the experiment as a connectome-informed Minecraft controller and separate observations from interpretation. The controller's current behavior should be compared against clear baselines before making claims about task performance.

## Build and tests

Build with JDK 21:

```bash
./gradlew clean test build
```

The distributable Forge mod is written to `build/libs/neurolab-1.21.1-<version>.jar`. Tests cover connectome invariants, control-mode isolation, pulse timing and cancellation, immutable telemetry snapshots, wire encoding, and JSONL identity fields. They do not replace a client-and-server play test.

## Source layout

```text
src/main/java/lab/neurolab/brain/       graph reader, LIF network, worker
src/main/java/lab/neurolab/entity/      Neuro Fly entity
src/main/java/lab/neurolab/minecraft/   registration, mob adapter, telemetry
src/client/java/lab/neurolab/client/    key binding, dashboard, telemetry history
src/main/resources/                     mod metadata, language, connectome, texture
src/test/java/                          automated tests
```

## Attribution and licenses

Original mod source is licensed under the MIT License. The bundled connectome is a separately licensed, transformed dataset under CC BY 4.0; the dataset attribution, paper citation, SHA-256, filtering, and conversion notes are in [`DATA_PROVENANCE.md`](DATA_PROVENANCE.md). These licenses are not interchangeable. Keep the data notice with redistributed copies of the bundled dataset.

The project is independent and is not affiliated with the dataset contributors, Mojang, or Microsoft.

## Contributing

Issues and pull requests are welcome. Include your Minecraft and Forge versions, reproduction steps, relevant log excerpt, and whether you tested in single-player or on a dedicated server. Keep comments and documentation in English, preserve dataset attribution, and distinguish measured results from assumptions. See [`CONTRIBUTING.md`](CONTRIBUTING.md).
