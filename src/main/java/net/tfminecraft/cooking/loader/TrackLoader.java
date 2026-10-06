package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.tag.TagTrack;



public class TrackLoader {
	public static List<TagTrack> oList = new ArrayList<>();
	public static List<TagTrack> get(){
		return oList;
	}
	public static TagTrack getByString(String id) {
		for(TagTrack r : oList) {
			if(r.getId().equalsIgnoreCase(id)) return r;
		}
		return null;
	}
	public void load(File configFile) {
		oList.clear();
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		int i = 0;
		for(String key : list) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) {
			    Bukkit.getLogger().warning("[Cooking] Ignoring non-section TrackLoader entry: " + key);
			    continue;
			}
			TagTrack r = new TagTrack(key, i, section);
			oList.add(r);
			i++;
		}
	}
}