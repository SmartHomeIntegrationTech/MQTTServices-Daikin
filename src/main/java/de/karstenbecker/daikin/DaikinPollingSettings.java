package de.karstenbecker.daikin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.gsonfire.gson.EnumDefaultValueTypeAdapterFactory;

import de.karstenbecker.daikin.DaikinProperty.PollingInterval;
import de.karstenbecker.daikin.DaikinProperty.PostProcessing;

public class DaikinPollingSettings {
	private String homieServer = "tcp://127.0.0.1:1883";
	private String homieUser = null;
	private String homiePassword = null;
	private String homieDeviceName = "daikin-heatingunit";
	private String daikinIP;
	private int daikinPort = 80;
	private List<DaikinProperty> properties = new ArrayList<>();
  private String influxTopic;
  private String influxTable="Daikin";
  private String influxQFN="OpenHab.daikin.Heating";

	public DaikinPollingSettings(Collection<DaikinProperty> properties) {
		super();
		this.properties.addAll(properties);
	}

	public DaikinPollingSettings() {
        this(new ArrayList<>());
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
		GsonBuilder gson = new GsonBuilder().setPrettyPrinting()
		    .registerTypeAdapterFactory(new EnumDefaultValueTypeAdapterFactory<>(PollingInterval.class, PollingInterval.NEVER))
		    .registerTypeAdapterFactory(new EnumDefaultValueTypeAdapterFactory<>(PostProcessing.class, PostProcessing.NONE))
		    ;
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
	
	public static DaikinPollingSettings fromFile(File file) throws IOException {
		Gson gson = new GsonBuilder().create();
		String json = Files.readString(file.toPath());
		return gson.fromJson(json, DaikinPollingSettings.class);
	}

  public String getInfluxTopic() {
    return influxTopic;
  }

  public void setInfluxTopic(String influxTopic) {
    this.influxTopic = influxTopic;
  }

  public String getInfluxTable() {
    return influxTable;
  }

  public String getInfluxQFN() {
    return influxQFN;
  }

}
