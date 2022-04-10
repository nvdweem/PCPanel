package obsremote.requests;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class GetTransitionListResponse extends ResponseBase {
    @SerializedName("current-transition")
    private String currentTransition;
    private List<Transition> transitions;

    @Data
    public static class Transition {
        private String name;
    }
}

