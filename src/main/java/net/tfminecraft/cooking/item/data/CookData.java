package net.tfminecraft.cooking.item.data;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;

public class CookData {
    private HashMap<Method, CookParameter> parameters = new HashMap<>();
    private Method currentMethod;
    private int currentTime;

    private FoodItem item;

    public CookData(String id, ConfigurationSection config) {
        for(String key : config.getKeys(false)) {
            ConfigurationSection param = config.getConfigurationSection(key);
            try {
                parameters.put(Method.valueOf(key.toUpperCase(java.util.Locale.ROOT)), new CookParameter(param.getInt("tag", 1), param.getInt("time", 15), param.getInt("burn", 30)));
            } catch (Exception e) {
                Bukkit.getLogger().info("Failed to create cooking data for "+id);
            }
        }
    }

    public CookData() {}

    public CookData(FoodItem item, CookData other) {
        this.item = item;
        parameters = new HashMap<>(other.parameters);
        this.currentMethod = other.currentMethod;
        this.currentTime = other.currentTime;
    }

    public HashMap<Method, CookParameter> getParameters() {
        return parameters;
    }
    public int getCurrentTime() {
        return currentTime;
    }
    public void setCurrentTime(int i) {
        currentTime = i;
        check();
    }
    public FoodItem getFoodItem() {
        return item;
    }
    public boolean hasMethod(Method m) {
        return parameters.containsKey(m);
    }
    public Method getCurrentMethod() {
        return currentMethod;
    }

    public void restore(Method method, int time) {
        this.currentMethod = method;
        this.currentTime = time;
    }

    public void start(Method m) {
        if(parameters.containsKey(m)) {
            currentMethod = m;
            currentTime = 0;
        }
    }
    public boolean isBeingCooked() {
        return currentMethod != null;
    }
    public void stop() {
        currentMethod = null;
        currentTime = 0;
    }
    public void reset() {
        currentMethod = null;
        currentTime = 0;
    }
    public boolean tick() {
        if(currentMethod == null) return false;
        currentTime++;
        return check();
    }

    public boolean check() {
        CookParameter p = parameters.get(currentMethod); 
        if (p == null || item == null || item.getTagTrack("cooked") == null) {
            return false;
        }
        boolean changed = false;
        if(currentTime == p.getTime()) {
            changed = true;
            item.getTagTrack("cooked").setValue(p.getTag());
        } else if(currentTime == p.getBurnTime()) {
            changed = true;
            item.getTagTrack("cooked").setValue(2);
        }
        return changed;
    }
}
