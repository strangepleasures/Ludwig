package ludwig.changes;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public abstract class Change<T extends Change> {
    public final String changeId = newId();

    public abstract <T> T accept(ChangeVisitor<T> visitor);

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
