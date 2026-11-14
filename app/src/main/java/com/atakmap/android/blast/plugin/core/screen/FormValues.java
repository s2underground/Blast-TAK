package com.atakmap.android.blast.plugin.core.screen;

import java.util.HashMap;
import java.util.Map;

/** What the user has entered in a form, by item id. */
public final class FormValues {

    private final Map<String, String> text = new HashMap<>();
    private final Map<String, Boolean> checks = new HashMap<>();

    /** An Input's text or a Choice's code. */
    public FormValues put(String id, String value) {
        text.put(id, value == null ? "" : value);
        return this;
    }

    public FormValues put(String id, boolean checked) {
        checks.put(id, checked);
        return this;
    }

    /** Text or code for id ("" if absent). */
    public String get(String id) {
        String v = text.get(id);
        return v == null ? "" : v;
    }

    /** True if the host reported a value (text or check) for id. */
    public boolean has(String id) {
        return text.containsKey(id) || checks.containsKey(id);
    }

    public boolean checked(String id) {
        Boolean b = checks.get(id);
        return b != null && b;
    }

    /** The starting values of a form's inputs, choices and checks. */
    public static FormValues initial(Screen s) {
        FormValues v = new FormValues();
        for (Item it : s.items()) {
            if (it instanceof Input)
                v.put(((Input) it).id, ((Input) it).value);
            else if (it instanceof Choice)
                v.put(((Choice) it).id, ((Choice) it).selected);
            else if (it instanceof Check)
                v.put(((Check) it).id, ((Check) it).checked);
        }
        return v;
    }
}
