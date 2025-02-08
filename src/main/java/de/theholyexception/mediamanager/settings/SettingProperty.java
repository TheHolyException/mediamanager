package de.theholyexception.mediamanager.settings;

import de.theholyexception.mediamanager.models.SettingMetadata;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

public class SettingProperty<T> {
    private T value;
    private final List<Consumer<T>> subscribers = Collections.synchronizedList(new ArrayList<>());
    private final SettingMetadata metadata;

    protected SettingProperty(SettingMetadata metadata) {
        this.metadata = metadata;
    }

    public void setValue(T value) {
        this.value = value;
        subscribers.forEach(item -> item.accept(value));
    }

    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    public void addSubscriber(Consumer<T> consumer) {
        subscribers.add(consumer);
        consumer.accept(value);
    }

    public SettingMetadata getMetadata() {
        return metadata;
    }

    public Type getArgumentType() {
        return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
