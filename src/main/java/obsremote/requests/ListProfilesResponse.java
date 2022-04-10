package obsremote.requests;

import java.util.List;

import obsremote.objects.Profile;

public class ListProfilesResponse extends ResponseBase {
    private List<Profile> profiles;

    public List<Profile> getProfiles() {
        return profiles;
    }
}

