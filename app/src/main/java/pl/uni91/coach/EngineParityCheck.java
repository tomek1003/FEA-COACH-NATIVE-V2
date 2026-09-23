package pl.uni91.coach;

import org.json.JSONException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Przypadki referencyjne zapisane z oryginalnego silnika Python. */
public final class EngineParityCheck {
    private EngineParityCheck() {}

    public static void run(AnalysisEngine engine) throws JSONException {
        check(engine,
                "przeniesienie ciężaru gry i zmiana strony",
                "środkowy pomocnik nie widzi wolnej strony",
                "A2", "FEA-06", "M-04", "FEA-07", "FEA-01", "FEA-20");
        check(engine,
                "brak skuteczności i wykończenia sytuacji przed bramką",
                "dużo strzałów bez gola",
                "A4", "FEA-13", "M-01", "FEA-14", "FEA-01", "FEA-20");
        check(engine,
                "brak wysokiego pressingu",
                "zespół nie doskakuje przy otwarciu rywala",
                "O1", "FEA-10", "M-01", "FEA-18", "FEA-01", "FEA-20");
        check(engine,
                "brak pewności siebie po straconej bramce",
                "zawodnicy przestają być zaangażowani",
                "MEN", "FEA-19", "M-08", "FEA-20", "FEA-01", "FEA-20");

        score(engine, 5, 5, 5, 5, "PROGRESS", "R");
        score(engine, 3, 3, 2, 3, "CONTINUE", "P");
        score(engine, 3, 2, 3, 3, "REGRESS", "W");
        score(engine, 2, 2, 2, 2, "CHANGE", "R");
        score(engine, 1, 4, 4, 4, "CHANGE", "R");
    }

    private static void check(AnalysisEngine engine, String problem, String observation,
                              String behavior, String... expectedBlocks) throws JSONException {
        AnalysisEngine.TrainingPlan plan = engine.buildPlan("Poniedziałek", problem,
                "CONTINUE", "R", observation, "MIX", Collections.emptySet(), null);
        if (!behavior.equals(plan.behaviorCode)) fail("zachowanie", behavior, plan.behaviorCode);
        List<String> expected = Arrays.asList(expectedBlocks);
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(plan.blocks.get(i).id))
                fail("blok " + i, expected.get(i), plan.blocks.get(i).id);
        }
    }

    private static void score(AnalysisEngine engine, int r, int w, int p, int g,
                              String decision, String weak) {
        AnalysisEngine.ScoreProfile result = engine.analyzeScore(r, w, p, g, "", "", "MIX");
        if (!decision.equals(result.decision)) fail("decyzja", decision, result.decision);
        if (!weak.equals(result.weak)) fail("słaby obszar", weak, result.weak);
    }

    private static void fail(String field, String expected, String actual) {
        throw new IllegalStateException("Niezgodność silnika (" + field + "): oczekiwano " + expected + ", otrzymano " + actual);
    }
}
