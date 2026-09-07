package ua.nagivka.mollyauction.models;

public enum SortMode {
    DEFAULT("По умолчанию"),
    NEWEST("Новые"),
    OLDEST("Старые"),
    EXPENSIVE("Дорогие"),
    CHEAPEST("Дешёвые"),
    EXPENSIVE_UNIT("Дорогие/шт"),
    CHEAPEST_UNIT("Дешёвые/шт");

    private static final SortMode[] VALUES = values();

    private final String name;

    SortMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public SortMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public SortMode previous() {
        return VALUES[(ordinal() - 1 + VALUES.length) % VALUES.length];
    }
}
