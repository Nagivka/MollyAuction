package ua.nagivka.nGVKauction.models;

public enum SortMode {
    DEFAULT("По умолчанию"),
    NEWEST("Новые"),
    OLDEST("Старые"),
    EXPENSIVE("Дорогие"),
    CHEAPEST("Дешёвые"),
    EXPENSIVE_UNIT("Дорогие/шт"),
    CHEAPEST_UNIT("Дешёвые/шт");

    private final String name;

    SortMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public SortMode previous() {
        SortMode[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}