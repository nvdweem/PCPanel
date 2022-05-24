package obsremote.events;

import com.google.gson.annotations.SerializedName;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class BaseEvent {
    private EventType eventType;

    public EventType getEventType() {
        return eventType;
    }

    @SerializedName("update-type")
    public void setUpdateType(String updateType) {
        log.debug(updateType);
        eventType = EventType.valueOf(updateType);
    }
}
