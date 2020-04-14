package de.karstenbecker.daikin;

import java.util.Objects;

import io.github.dschanoeh.homie_java.Property.DataType;

public class DaikinProperty implements Comparable<DaikinProperty> {

	public static enum PollingInterval {
		NEVER, ONCE, MINUTELY, HOURLY, BI_HOURLY, DAILY
	}

	public DaikinProperty(String path) {
		this.path = path;
		this.id = path.replaceAll(Daikin.ITEM_SEP, "-").toLowerCase();
		this.name = path;
	}

	private String path;
	private String name;
	private String id;
	private String value = "";
	private Boolean settable = false;
	private Boolean retained = true;
	private String unit = "";
	private String format = "";
	private PollingInterval pollInterval = PollingInterval.HOURLY;
	private DataType dataType = DataType.STRING;

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Boolean getSettable() {
		return settable;
	}

	public void setSettable(Boolean settable) {
		this.settable = settable;
	}

	public Boolean getRetained() {
		return retained;
	}

	public void setRetained(Boolean retained) {
		this.retained = retained;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public PollingInterval getPollInterval() {
		return pollInterval;
	}

	public void setPollInterval(PollingInterval pollInterval) {
		this.pollInterval = pollInterval;
	}

	public DataType getDataType() {
		return dataType;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(id);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DaikinProperty other = (DaikinProperty) obj;
		return Objects.equals(id, other.id);
	}

	@Override
	public int compareTo(DaikinProperty o) {
		return path.compareTo(o.path);
	}

	@Override
	public String toString() {
		return String.format(
				"DaikinProperty [path=%s, name=%s, id=%s, value=%s, settable=%s, retained=%s, unit=%s, format=%s, pollInterval=%s, dataType=%s]",
				path, name, id, value, settable, retained, unit, format, pollInterval, dataType);
	}
}