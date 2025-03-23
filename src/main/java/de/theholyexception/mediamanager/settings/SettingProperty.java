package de.theholyexception.mediamanager.settings;

import lombok.Getter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

public class SettingProperty<T> {
    @Getter
    private T value;
    private final List<Consumer<T>> subscribers = Collections.synchronizedList(new ArrayList<>());
    @Getter
    private final SettingMetadata metadata;

    public SettingProperty(SettingMetadata metadata) {
        this.metadata = metadata;
    }

    public void setValue(T value) {
        this.value = value;
        subscribers.forEach(item -> item.accept(value));
    }

    @Override
    public String toString() {
        return metadata.name() + " - " + value;
    }

    public void addSubscriber(Consumer<T> consumer) {
        subscribers.add(consumer);
        if (value != null) consumer.accept(value);
    }

    public void trigger() {
        subscribers.forEach(item -> item.accept(value));
    }

    public Type getArgumentType() {
        return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

}
