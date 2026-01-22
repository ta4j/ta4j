/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Duration;

/**
 * Custom Gson TypeAdapter for serializing and deserializing {@link Duration}
 * objects. Serializes Duration to its ISO-8601 string representation (e.g.,
 * "PT1H30M") and deserializes it back.
 *
 * @since 0.19
 */
public class DurationTypeAdapter extends TypeAdapter<Duration> {

    @Override
    public void write(JsonWriter out, Duration value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public Duration read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String durationString = in.nextString();
        return Duration.parse(durationString);
    }
}
