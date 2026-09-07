package ua.nagivka.mollyauction.models;

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

    private static final Category[] VALUES = values();

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Category next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public Category previous() {
        return VALUES[(ordinal() - 1 + VALUES.length) % VALUES.length];
    }
}
