package pl.uni91.coach;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PlanCodec {
    private PlanCodec() {}

    public static JSONObject toJson(AnalysisEngine.TrainingPlan plan) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("day", plan.day); json.put("problem", plan.problem); json.put("decision", plan.decision);
        json.put("weak", plan.weak); json.put("behaviorCode", plan.behaviorCode);
        json.put("behaviorName", plan.behaviorName); json.put("targetBehavior", plan.targetBehavior);
        json.put("confidence", plan.confidence); json.put("axes", new JSONArray(plan.axes));
        JSONArray blocks = new JSONArray();
        for (AnalysisEngine.TrainingBlock b : plan.blocks) {
            JSONObject item = new JSONObject();
            item.put("role", b.role); item.put("id", b.id); item.put("name", b.name); item.put("minutes", b.minutes);
            item.put("field", b.field); item.put("goal", b.goal); item.put("organization", b.organization);
            item.put("course", b.course); item.put("questions", b.questions); item.put("progression", b.progression);
            item.put("selectionReason", b.selectionReason); blocks.put(item);
        }
        json.put("blocks", blocks); return json;
    }

    public static AnalysisEngine.TrainingPlan fromJson(JSONObject json) throws JSONException {
        List<String> axes = new ArrayList<>(); JSONArray axesJson=json.getJSONArray("axes");
        for(int i=0;i<axesJson.length();i++) axes.add(axesJson.getString(i));
        List<AnalysisEngine.TrainingBlock> blocks = new ArrayList<>(); JSONArray blockJson=json.getJSONArray("blocks");
        for(int i=0;i<blockJson.length();i++) {
            JSONObject b=blockJson.getJSONObject(i);
            blocks.add(new AnalysisEngine.TrainingBlock(b.getString("role"),b.getString("id"),b.getString("name"),b.getInt("minutes"),
                    b.optString("field"),b.optString("goal"),b.optString("organization"),b.optString("course"),
                    b.optString("questions"),b.optString("progression"),b.optString("selectionReason")));
        }
        return new AnalysisEngine.TrainingPlan(json.getString("day"),json.getString("problem"),json.getString("decision"),
                json.getString("weak"),json.optString("behaviorCode"),json.optString("behaviorName"),
                json.optString("targetBehavior"),json.optString("confidence"),axes,blocks);
    }
}
