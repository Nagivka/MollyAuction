package ua.nagivka.nGVKauction.models;

public enum Category {
    ALL("Все"),
    BLOCKS("Блоки"),
    TOOLS("Инструменты"),
    WEAPONS("Оружие"),
    ARMOR("Броня"),
    FOOD("Еда"),
    FUEL("Топливо"),
    POTIONS("Зелья"),
    MECHANISMS("Механизмы"),
    ALCHEMY("Алхимия"),
    ENCHANTS("Зачарования"),
    JEWELRY("Ювелирные"),
    UNIQUE("Уникальные");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Category next() {
        Category[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Category previous() {
        Category[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}