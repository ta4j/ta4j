/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.ta4j.core.num.Num;

/**
 * Snapshot of an open position (net or per-lot).
 *
 * @param side              open side
 * @param amount            open amount
 * @param averageEntryPrice weighted average entry price
 * @param totalEntryCost    total entry cost (price * amount)
 * @param totalFees         total fees remaining
 * @param earliestEntryTime earliest entry time across lots
 * @param latestEntryTime   latest entry time across lots
 * @param lots              underlying lots
 * @since 0.22.2
 */
public record OpenPosition(ExecutionSide side, Num amount, Num averageEntryPrice, Num totalEntryCost, Num totalFees,
        Instant earliestEntryTime, Instant latestEntryTime, List<PositionLot> lots) implements Serializable {

    @Serial
    private static final long serialVersionUID = -6647261448915930675L;

    private static final Gson GSON = new Gson();

    public OpenPosition {
        List<PositionLot> resolvedLots = lots == null ? List.of() : lots.stream().map(PositionLot::snapshot).toList();
        lots = List.copyOf(resolvedLots);
    }

    @Override
    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty("side", side == null ? null : side.name());
        json.addProperty("amount", amount == null ? null : amount.toString());
        json.addProperty("averageEntryPrice", averageEntryPrice == null ? null : averageEntryPrice.toString());
        json.addProperty("totalEntryCost", totalEntryCost == null ? null : totalEntryCost.toString());
        json.addProperty("totalFees", totalFees == null ? null : totalFees.toString());
        json.addProperty("earliestEntryTime", earliestEntryTime == null ? null : earliestEntryTime.toString());
        json.addProperty("latestEntryTime", latestEntryTime == null ? null : latestEntryTime.toString());

        JsonArray lotsJson = new JsonArray();
        for (PositionLot lot : lots) {
            lotsJson.add(JsonParser.parseString(lot.toString()));
        }
        json.add("lots", lotsJson);
        return GSON.toJson(json);
    }
}
