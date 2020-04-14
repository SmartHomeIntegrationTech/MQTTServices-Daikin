package de.karstenbecker.daikin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DaikinPollingSettings {
	private String homieServer = "tcp://127.0.0.1:1883";
	private String homieUser = "";
	private String homiePassword = "";
	private String homieDeviceName = "daikin-heatingunit";
	private String daikinIP;
	private int daikinPort = 80;
	private List<DaikinProperty> properties = new ArrayList<>();

	public DaikinPollingSettings(Collection<DaikinProperty> properties) {
		super();
		this.properties.addAll(properties);
	}

	public String getHomieServer() {
		return homieServer;
	}

	public void setHomieServer(String homieServer) {
		this.homieServer = homieServer;
	}

	public String getHomieUser() {
		return homieUser;
	}

	public void setHomieUser(String homieUser) {
		this.homieUser = homieUser;
	}

	public String getHomiePassword() {
		return homiePassword;
	}

	public void setHomiePassword(String homiePassword) {
		this.homiePassword = homiePassword;
	}

	public String getHomieDeviceName() {
		return homieDeviceName;
	}

	public void setHomieDeviceName(String homieDeviceName) {
		this.homieDeviceName = homieDeviceName;
	}

	public String getDaikinIP() {
		return daikinIP;
	}

	public void setDaikinIP(String daikinIP) {
		this.daikinIP = daikinIP;
	}

	public int getDaikinPort() {
		return daikinPort;
	}

	public void setDaikinPort(int daikinPort) {
		this.daikinPort = daikinPort;
	}

	public List<DaikinProperty> getProperties() {
		return properties;
	}

	public void setProperties(List<DaikinProperty> properties) {
		this.properties = properties;
	}

	public String toJSON(boolean includeValues) {
		GsonBuilder gson = new GsonBuilder().setPrettyPrinting();
		if (!includeValues) {
			gson.setExclusionStrategies(new ExclusionStrategy() {

				@Override
				public boolean shouldSkipField(FieldAttributes f) {
					if (f.getName().equals("value"))
						return true;
					return false;
				}

				@Override
				public boolean shouldSkipClass(Class<?> clazz) {
					return false;
				}
			});
		}
		return gson.create().toJson(this);
	}

}
