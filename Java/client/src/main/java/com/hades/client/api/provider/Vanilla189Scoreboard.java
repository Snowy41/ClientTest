package com.hades.client.api.provider;

import com.hades.client.api.interfaces.scoreboard.IScore;
import com.hades.client.api.interfaces.scoreboard.IScoreObjective;
import com.hades.client.api.interfaces.scoreboard.IScoreboard;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Vanilla189Scoreboard implements IScoreboard {

    private final Object rawScoreboard;

    // Method Caches
    private static Method getObjectiveInDisplaySlotMethod;
    private static Method getSortedScoresMethod;
    private static Method getPlayersTeamMethod;

    // ScoreObjective Methods
    private static Method getDisplayNameMethod;
    private static Method getNameMethod;

    // Score Methods
    private static Method getPlayerNameMethod;
    private static Method getScorePointsMethod;

    // ScorePlayerTeam Methods
    private static Method formatPlayerNameMethod;

    public Vanilla189Scoreboard(Object rawScoreboard) {
        this.rawScoreboard = rawScoreboard;
        initReflection(rawScoreboard.getClass());
    }

    private static void initReflection(Class<?> scoreboardClass) {
        if (getObjectiveInDisplaySlotMethod != null) return;

        // Scoreboard
        getObjectiveInDisplaySlotMethod = ReflectionUtil.findMethod(scoreboardClass, new String[]{"a", "getObjectiveInDisplaySlot", "func_96539_a"}, int.class);
        
        Class<?> scoreObjectiveClass = ReflectionUtil.findClass("net.minecraft.scoreboard.ScoreObjective", "auk");
        if (scoreObjectiveClass != null) {
            getSortedScoresMethod = ReflectionUtil.findMethod(scoreboardClass, new String[]{"i", "getSortedScores", "func_96534_i"}, scoreObjectiveClass);
            
            getDisplayNameMethod = ReflectionUtil.findMethod(scoreObjectiveClass, new String[]{"d", "getDisplayName", "func_96678_d"});
            getNameMethod = ReflectionUtil.findMethod(scoreObjectiveClass, new String[]{"b", "getName", "func_96679_b"});
        }

        getPlayersTeamMethod = ReflectionUtil.findMethod(scoreboardClass, new String[]{"h", "getPlayersTeam", "func_96509_i"}, String.class);

        Class<?> scoreClass = ReflectionUtil.findClass("net.minecraft.scoreboard.Score", "aum");
        if (scoreClass != null) {
            getPlayerNameMethod = ReflectionUtil.findMethod(scoreClass, new String[]{"e", "getPlayerName", "func_96653_e"});
            getScorePointsMethod = ReflectionUtil.findMethod(scoreClass, new String[]{"c", "getScorePoints", "func_96652_c"});
        }

        Class<?> scorePlayerTeamClass = ReflectionUtil.findClass("net.minecraft.scoreboard.ScorePlayerTeam", "aul");
        Class<?> teamClass = ReflectionUtil.findClass("net.minecraft.scoreboard.Team", "auq");
        if (scorePlayerTeamClass != null && teamClass != null) {
            formatPlayerNameMethod = ReflectionUtil.findMethod(scorePlayerTeamClass, new String[]{"a", "formatPlayerName", "func_96667_a"}, teamClass, String.class);
        }
    }

    @Override
    public IScoreObjective getObjectiveInDisplaySlot(int slot) {
        if (rawScoreboard == null || getObjectiveInDisplaySlotMethod == null) return null;
        try {
            Object rawObjective = getObjectiveInDisplaySlotMethod.invoke(rawScoreboard, slot);
            if (rawObjective == null) return null;
            return new Vanilla189ScoreObjective(rawObjective);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<IScore> getScores(IScoreObjective objective) {
        if (rawScoreboard == null || getSortedScoresMethod == null || objective == null) return new ArrayList<>();
        try {
            Object rawObj = ((Vanilla189ScoreObjective) objective).getRaw();
            Collection<?> rawScores = (Collection<?>) getSortedScoresMethod.invoke(rawScoreboard, rawObj);
            List<IScore> wrappedScores = new ArrayList<>();
            for (Object rawScore : rawScores) {
                wrappedScores.add(new Vanilla189Score(rawScore));
            }
            return wrappedScores;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public String formatPlayerName(IScore score) {
        if (rawScoreboard == null || getPlayersTeamMethod == null || formatPlayerNameMethod == null || score == null) return score.getPlayerName();
        try {
            String playerName = score.getPlayerName();
            Object team = getPlayersTeamMethod.invoke(rawScoreboard, playerName);
            
            // In vanilla, formatPlayerName is static: ScorePlayerTeam.formatPlayerName(Team, String)
            String formatted = (String) formatPlayerNameMethod.invoke(null, team, playerName);
            return formatted != null ? formatted : playerName;
        } catch (Exception e) {
            return score.getPlayerName();
        }
    }

    private static class Vanilla189ScoreObjective implements IScoreObjective {
        private final Object raw;

        public Vanilla189ScoreObjective(Object raw) {
            this.raw = raw;
        }

        public Object getRaw() {
            return raw;
        }

        @Override
        public String getDisplayName() {
            if (getDisplayNameMethod == null) return "";
            try {
                return (String) getDisplayNameMethod.invoke(raw);
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public String getName() {
            if (getNameMethod == null) return "";
            try {
                return (String) getNameMethod.invoke(raw);
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static class Vanilla189Score implements IScore {
        private final Object raw;

        public Vanilla189Score(Object raw) {
            this.raw = raw;
        }

        @Override
        public String getPlayerName() {
            if (getPlayerNameMethod == null) return "";
            try {
                return (String) getPlayerNameMethod.invoke(raw);
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public int getScorePoints() {
            if (getScorePointsMethod == null) return 0;
            try {
                return (int) getScorePointsMethod.invoke(raw);
            } catch (Exception e) {
                return 0;
            }
        }
    }
}
