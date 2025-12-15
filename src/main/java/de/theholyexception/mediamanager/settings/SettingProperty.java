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

    public void setValueSafe(String val) {
        if (value instanceof Integer) {
            setValue((T) Integer.valueOf(val));
        } else if (value instanceof Long) {
            setValue((T) Long.valueOf(val));
        } else if (value instanceof Double) {
            setValue((T) Double.valueOf(val));
        } else if (value instanceof Float) {
            setValue((T) Float.valueOf(val));
        } else if (value instanceof Boolean) {
            setValue((T) Boolean.valueOf(val));
        } else if (value instanceof String) {
            setValue((T) val);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + value.getClass().getName());
        }
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

    public String getValueSafe() {
        if (value == null) return "null";
        return value.toString();
    }

}
