package com.hades.client.api.interfaces.scoreboard;

import java.util.List;

public interface IScoreboard {
    /** Returns the objective currently displayed in the specified slot (e.g., 1 for sidebar) */
    IScoreObjective getObjectiveInDisplaySlot(int slot);

    /** Returns all valid, formatted scores for the given objective */
    List<IScore> getScores(IScoreObjective objective);

    /** Resolves the player's team and formats their name with prefixes and suffixes */
    String formatPlayerName(IScore score);
}
