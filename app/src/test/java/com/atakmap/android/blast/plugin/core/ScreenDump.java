package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.screen.Button;
import com.atakmap.android.blast.plugin.core.screen.Buttons;
import com.atakmap.android.blast.plugin.core.screen.Item;
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
                        .append(b.label).append("\" ").append(b.kind)
                        .append(" w=").append(Golden.num(b.weight, 2))
                        .append(b.bold ? " bold" : "");
            return sb.toString();
        }
        return "  ? " + it.getClass().getSimpleName();
    }
}
