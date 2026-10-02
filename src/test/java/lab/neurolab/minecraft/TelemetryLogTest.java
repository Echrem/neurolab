package lab.neurolab.minecraft;

import com.google.gson.JsonParser;
import lab.neurolab.brain.TrialSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryLogTest {
    @TempDir Path temp;

    @Test void logRecordsStableIdentityAndExperimentalCondition() throws Exception {
        Path file = temp.resolve("events.jsonl");
        UUID uuid = UUID.randomUUID();
        TelemetryLog.start(file);
        try {
            TelemetryLog.record(new BrainTelemetryPayload(7, 12, 3, 0, 0, 0, 1, false),
                    uuid, "minecraft:overworld", 1234, null);
        } finally { TelemetryLog.stop(); }
        var lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        var record = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        assertEquals(uuid.toString(), record.get("entityUuid").getAsString());
        assertEquals("minecraft:overworld", record.get("dimension").getAsString());
        assertEquals("assisted", record.get("mode").getAsString());
        assertEquals(1234, record.get("gameTick").getAsLong());
    }
}
