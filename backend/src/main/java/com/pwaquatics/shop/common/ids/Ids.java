package com.pwaquatics.shop.common.ids;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/** Generates time-ordered, non-guessable UUIDv7 identifiers for all entities. */
public final class Ids {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private Ids() {}

    public static UUID newId() {
        return GENERATOR.generate();
    }
}
