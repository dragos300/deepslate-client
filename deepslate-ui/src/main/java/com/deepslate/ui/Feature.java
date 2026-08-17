package com.deepslate.ui;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** A single toggleable row in the mods menu. */
public record Feature(
        String id,
        String title,
        String description,
        String credit,
        Category category,
        Function<DeepslateConfig, Boolean> getter,
        BiConsumer<DeepslateConfig, Boolean> setter
) {
    public Feature(
            String id,
            String title,
            String description,
            Category category,
            Function<DeepslateConfig, Boolean> getter,
            BiConsumer<DeepslateConfig, Boolean> setter
    ) {
        this(id, title, description, null, category, getter, setter);
    }

    public enum Category {
        UI("UI"),
        MENU("Menus"),
        HUD("HUD"),
        MISC("Misc");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public boolean get(DeepslateConfig cfg) {
        return getter.apply(cfg);
    }

    public void set(DeepslateConfig cfg, boolean value) {
        setter.accept(cfg, value);
    }

    public boolean hasCredit() {
        return credit != null && !credit.isBlank();
    }
}
