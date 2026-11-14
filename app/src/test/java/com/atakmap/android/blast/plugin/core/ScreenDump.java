package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.screen.Button;
import com.atakmap.android.blast.plugin.core.screen.Buttons;
import com.atakmap.android.blast.plugin.core.screen.Check;
import com.atakmap.android.blast.plugin.core.screen.Choice;
import com.atakmap.android.blast.plugin.core.screen.Input;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.KeyRow;
import com.atakmap.android.blast.plugin.core.screen.Widget;
import com.atakmap.android.blast.plugin.core.screen.Legend;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.Text;

/** Writes a core screen model as text for golden files. */
final class ScreenDump {

    private ScreenDump() {
    }

    static String dump(Screen s) {
        StringBuilder sb = new StringBuilder();
        for (Item it : s.items())
            sb.append(item(it)).append("\n");
        return sb.toString();
    }

    static String item(Item it) {
        if (it instanceof Text) {
            Text t = (Text) it;
            StringBuilder sb = new StringBuilder("  text ");
            sb.append(Golden.num(t.size, 1)).append(t.bold ? " bold " : " ")
                    .append(t.tone);
            if (t.padTopDp != 0 || t.padBottomDp != 0)
                sb.append(" pad=").append(t.padTopDp).append("/")
                        .append(t.padBottomDp);
            if (t.icon != null)
                sb.append(" icon=").append(t.icon);
            if (t.mono)
                sb.append(" mono");
            if (t.selectable)
                sb.append(" selectable");
            if (t.slot != null)
                sb.append(" slot=").append(t.slot);
            sb.append(" | ").append(t.text.replace("\n", "\\n"));
            return sb.toString();
        }
        if (it instanceof Legend) {
            Legend l = (Legend) it;
            StringBuilder sb = new StringBuilder("  legend swatch=");
            sb.append(l.swatch).append(" ").append(Golden.num(l.size, 1))
                    .append(" ").append(l.tone);
            for (int i = 0; i < l.cellCount(); i++)
                sb.append(" | ").append(l.cell(i)).append(" w=")
                        .append(Golden.num(l.weight(i), 2))
                        .append(l.bold(i) ? " bold" : "");
            return sb.toString();
        }
        if (it instanceof Buttons) {
            Buttons bs = (Buttons) it;
            StringBuilder sb = new StringBuilder("  buttons");
            if (bs.marginTopDp != 0)
                sb.append(" margin=").append(bs.marginTopDp);
            for (Button b : bs.buttons)
                sb.append(" | ").append(b.action).append(" \"")
                        .append(b.label.replace("\n", "\\n")).append("\" ").append(b.kind)
                        .append(" w=").append(Golden.num(b.weight, 2))
                        .append(b.bold ? " bold" : "");
            return sb.toString();
        }
        if (it instanceof KeyRow) {
            KeyRow k = (KeyRow) it;
            return "  key " + k.kind + " bar=" + k.bar + " " + k.titleInk
                    + " | " + k.title + (k.meaning == null ? ""
                            : " | " + k.meaning);
        }
        if (it instanceof Input) {
            Input in = (Input) it;
            return "  input " + in.id + (in.numeric ? " numeric" : "")
                    + " max=" + in.maxLength + " | " + in.label + " = ["
                    + in.value + "]";
        }
        if (it instanceof Check) {
            Check c = (Check) it;
            return "  check " + c.id + " " + c.checked + " | " + c.label;
        }
        if (it instanceof Choice) {
            Choice c = (Choice) it;
            StringBuilder sb = new StringBuilder("  choice " + c.id + " per="
                    + c.perRow + " sel=[" + c.selected + "] "
                    + (c.clearable ? "clearable " : "") + c.selectedKind);
            for (int i = 0; i < c.size(); i++)
                sb.append(" | ").append(c.code(i)).append("=")
                        .append(c.label(i));
            return sb.toString();
        }
        if (it instanceof Widget)
            return "  widget " + ((Widget) it).id;
        return "  ? " + it.getClass().getSimpleName();
    }
}
