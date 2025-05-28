package de.theholyexception.mediamanager.util;

public enum TargetSystem {

    DEFAULT("default"),
    ANIWORLD("aniworld"),
    AUTOLOADER("autoloader");

    private final String name;

    TargetSystem(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
