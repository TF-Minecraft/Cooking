package net.tfminecraft.cooking.item.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class OverrideData {
    private String name;
    private String model;
    private String carveSequence;
    private final Map<String, Double> age;

    public OverrideData(String name, String model, String carveSequence) {
        this(name, model, carveSequence, Map.of());
    }

    public OverrideData(String name, String model, String carveSequence, Map<String, Double> age) {
        if (name != null) this.name = StringFormatter.formatHex(name);
        this.model = model;
        this.carveSequence = carveSequence;
        this.age = age == null || age.isEmpty() ? Map.of() : Collections.unmodifiableMap(new HashMap<>(age));
    }

    public String getName() {
        return name;
    }
    public String getModel() {
        return model;
    }
    public String getCarveSequence() {
        return carveSequence;
    }

    public Double getAge(String trackId) {
        if (trackId == null || age.isEmpty()) {
            return null;
        }
        return age.get(trackId.toLowerCase());
    }
}
