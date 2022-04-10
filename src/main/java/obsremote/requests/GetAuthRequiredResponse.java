package obsremote.requests;

public class GetAuthRequiredResponse extends ResponseBase {
    private boolean authRequired;

    private String challenge;

    private String salt;

    public boolean isAuthRequired() {
        return authRequired;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getSalt() {
        return salt;
    }
}
