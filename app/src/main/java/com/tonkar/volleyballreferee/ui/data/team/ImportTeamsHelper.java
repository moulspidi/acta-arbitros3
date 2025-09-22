package com.tonkar.volleyballreferee.ui.data.team;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.tonkar.volleyballreferee.engine.api.model.PlayerDto;
import com.tonkar.volleyballreferee.engine.api.model.TeamDto;
import com.tonkar.volleyballreferee.engine.game.GameType;
import com.tonkar.volleyballreferee.engine.team.GenderType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ImportTeamsHelper {
    private static final Gson gson = new Gson();
    private static final Type TEAM_LIST = new TypeToken<List<TeamDto>>() {}.getType();

    public static List<TeamDto> readTeamsFromUri(Context ctx, Uri uri) throws Exception {
        String json = readAll(ctx, uri);
        try {
            return normalize(gson.fromJson(json, TEAM_LIST));
        } catch (Exception ignored) {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject() && root.getAsJsonObject().has("teams")) {
                return normalize(gson.fromJson(root.getAsJsonObject().get("teams"), TEAM_LIST));
            }
            throw new IllegalArgumentException("Invalid JSON: expected array or {\"teams\": [...]}");
        }
    }

    private static String readAll(Context ctx, Uri uri) throws Exception {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        if (is == null) throw new IllegalStateException("Cannot open file");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static List<TeamDto> normalize(List<TeamDto> in) {
        List<TeamDto> out = new ArrayList<>();
        if (in == null) return out;
        for (TeamDto t : in) {
            if (t == null || t.getName() == null || t.getName().trim().isEmpty()) continue;
            if (t.getKind() == null) t.setKind(GameType.INDOOR);
            if (t.getGender() == null) t.setGender(GenderType.MIXED);
            if (t.getPlayers() == null) t.setPlayers(new ArrayList<>());
            if (t.getLiberos() == null) t.setLiberos(new ArrayList<>());
            List<PlayerDto> players = new ArrayList<>();
            for (PlayerDto p : t.getPlayers()) {
                if (p == null) continue;
                if (p.getName() == null) p.setName("");
                players.add(p);
            }
            t.setPlayers(players);
            out.add(t);
        }
        return out;
    }
}
