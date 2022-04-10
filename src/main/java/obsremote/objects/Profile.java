package obsremote.objects;

import com.google.gson.annotations.SerializedName;

public class Profile {
    @SerializedName("profile-name")
    private String name;

    public String getName() {
        return name;
    }
}
