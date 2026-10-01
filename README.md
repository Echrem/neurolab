# NeuroLab

NeuroLab is an experimental Forge mod that lets a connectome-backed neural simulation influence Minecraft mobs. It includes a custom fly mob for repeatable experiments, an adapter for attaching the same controller to other mobs, and a live client dashboard for watching the controller's output.

The point is to make an interesting, inspectable game experiment—not to claim that a fly's consciousness has been copied into Minecraft. The bundled graph is biological data; the stimulus encoding, neural parameters, and mapping from neural activity to game movement are still exploratory.

## Project status

This is an early research prototype targeting Minecraft 1.21.1 and Forge. The Forge port is under active validation; until a client and dedicated-server play test passes, treat behavior and performance as experimental and use a backed-up test world.

## What is included

- A custom **Neuro Fly** entity with a segmented body, compound eyes, six legs, animated wings, simple autonomous movement, and a Creative-mode spawn egg.
- A connectome reader for the bundled FLYB v1 graph and a sparse, event-driven leaky integrate-and-fire (LIF) simulator.
- Commands to attach the simulator to the Neuro Fly or another mob, inspect load status, and detach it.
- A client-side engineering dashboard with a control-path diagram and six per-mob time-series charts.
- Asynchronous JSONL telemetry written to the current world's `neurolab/events.jsonl` file.

The implementation is intentionally small enough to inspect. There is no separate launcher or account service, and the mod does not require Fabric API.

## Requirements

- Minecraft **1.21.1**
- Minecraft Forge **52.1.x** for Minecraft 1.21.1
- Java **21**

Install the mod JAR on the server and on every client that needs the custom entity renderer and analysis screen. For a local single-player experiment, the client installation is enough.

## Install a published release

1. Install [Minecraft Forge for 1.21.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html) and use Java 21 to run the game.
2. Download the `neurolab-<version>.jar` asset from [GitHub Releases](https://github.com/Echrem/neurolab/releases/latest). Choose the regular mod JAR, not the `-sources.jar` or GitHub's automatically generated **Source code** archives.
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

4. Attach the connectome controller to the nearest Neuro Fly:

   ```mcfunction
   /neurolab attach @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
   ```

5. Open the dashboard with **Ctrl+N**. The `N` key can be changed in Minecraft's Controls screen; Ctrl is the required modifier.
6. When finished, detach the controller to restore the mob's previous AI state:

   ```mcfunction
   /neurolab detach @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
   ```

Useful checks:

```mcfunction
/neurolab status
/summon neurolab:neuro_fly
/neurolab attach @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
/neurolab detach @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
```

To experiment with another mob, replace `neurolab:neuro_fly` in the selector with a mob type, for example `minecraft:spider`. The prototype allows up to four simultaneous attachments. Attaching pauses the mob's vanilla AI; detaching or unloading its chunk restores the AI state that was present before attachment.

## How the controller works

The bundled graph is derived from the neuPrint `male-cns:v1.0` adult male central nervous system connectome. It contains **176,422 neurons** and **6,287,749 retained connections** after this project's documented filtering and format conversion. The data file is compressed at `src/main/resources/connectome/male-cns-v1.0.flyb.gz`.

At runtime, `ConnectomeData` reads the graph and its annotations. `FlyBrain` advances a sparse LIF network in 0.5 ms steps, grouped into 50 ms simulation updates. `BrainWorker` gives each attached brain its own lower-priority daemon thread, capped by the four-brain attachment limit. Minecraft entity and world reads and writes stay on the server thread: the worker receives numeric sensory values and publishes snapshots for the next game update. This design keeps the main tick from doing the neural integration itself; it is not a performance guarantee, and server load still needs to be measured in real worlds.

The current world-to-network and network-to-mob mappings are deliberately simple:

| Game observation | Current input proxy |
| --- | --- |
| Forward view | Local brightness sampled along a short forward ray |
| Looming | Nearby entities moving toward the mob |
| Odor | Nearby dropped item entities |
| Taste | Contact with a small set of food-related blocks |
| Touch | Collision or recent damage |

The simulator injects events into annotated sensory populations rather than directly stimulating motor neurons. Activity in selected descending-neuron labels is then summarized into forward, turn, lift, and escape-like channels. Those channels become conservative movement intents. The Neuro Fly can use lift; for other mobs, movement is constrained by the capabilities of that entity and normal Minecraft physics.

## Reading the dashboard and logs

Open the dashboard with **Ctrl+N**. It shows the sensory-encoder → connectome → motor-decoder path and six charts for the selected tracked mob: spikes per sample, active neurons, forward drive, signed turn bias, lift drive, and real-time factor. Use the left/right arrow keys to switch between tracked mobs. Each chart retains up to 180 samples; x positions represent sample order, not wall-clock time, and the server sends observations every two game ticks. A mob's plot is removed after ten seconds without new telemetry.

This panel visualizes decoded telemetry; it does not render the full connectome or per-neuron electrophysiology. Counts use a per-chart automatic range, while bounded control signals use their defined ranges; turn is plotted around a zero baseline so direction is visible. It is an inspection aid, not a calibrated measurement instrument. The server also appends observations to:

```text
<world>/neurolab/events.jsonl
```

The JSONL log is intended for offline inspection and analysis. Avoid sharing it without checking it for world or server details you do not want to publish.

## What this does not establish

The connectome supplies a biological wiring graph, but this mod is not a validated whole-animal brain emulation. The LIF time constants, thresholds, synaptic scaling, sensory rates, and motor decoding have not been fitted as a complete biological model. Minecraft brightness is not a fly's visual input; item entities and food blocks are rough stand-ins for chemical senses; and the movement adapter is a game-control layer. A successful build or an entertaining behavior should not be presented as evidence of biological fidelity.

For a video or report, describe the experiment as a connectome-informed Minecraft controller and separate observations from interpretation. The controller's current behavior should be compared against clear baselines before making claims about task performance.

## Build and tests

Build with JDK 21:

```bash
./gradlew clean test build
```

The distributable Forge mod is written to `build/libs/neurolab-<version>.jar`. Tests currently cover connectome loading and core data/simulation invariants. They do not replace a client-and-server play test.

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
