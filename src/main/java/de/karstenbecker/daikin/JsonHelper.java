package de.karstenbecker.daikin;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonHelper {

	private static final Logger logger = LoggerFactory.getLogger(JsonHelper.class);

	public static Optional<JsonElement> getJsonPath(JsonObject obj, String... keys) {
		return getJsonPath(obj, false, keys);
	}

	public static Optional<JsonElement> getJsonPath(JsonObject obj, boolean allowNonExist, String... keys) {
		if (keys == null || keys.length == 0)
			return Optional.of(obj);
		for (int i = 0; i < keys.length; i++) {
			String var = keys[i];
			JsonElement sub = obj.get(var);
			if (sub == null) {
				if (!allowNonExist)
					logger.warn("Expected to find member:" + var + " in " + obj);
				return Optional.empty();
			}
			if (sub.isJsonObject())
				obj = sub.getAsJsonObject();
			else if (i != keys.length - 1) {
				if (!allowNonExist)
					logger.warn("Expected to find member:" + var + " in " + obj);
				return Optional.empty();
			}
		}
		return Optional.of(obj.get(keys[keys.length - 1]));
	}
}
