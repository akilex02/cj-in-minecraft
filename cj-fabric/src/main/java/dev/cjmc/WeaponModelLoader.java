package dev.cjmc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads a weapon's .glb. Never throws: a model that cannot be read is a `problem` text and CJ is drawn without the weapon. */
public final class WeaponModelLoader {
    public record Result(GlbModel model, String problem) {}

    private WeaponModelLoader() {}

    public static Result read(Path file) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file)) {
            return new Result(null, name + " not found; run tools/convert_assets.py --weapons");
        }
        try {
            GlbModel model = GlbModel.read(Files.readAllBytes(file));
            if (model.jointNodeIds.length != 1) {
                // the weapon's pose has one joint (the hand): a model with more would be indexed past it when drawn
                return new Result(null, name + " has " + model.jointNodeIds.length
                        + " joints but a weapon model has exactly one; run tools/convert_assets.py --weapons");
            }
            return new Result(model, null);
        } catch (IOException | RuntimeException e) {
            return new Result(null, name + " is unreadable: " + e.getMessage());
        }
    }
}
