package de.karstenbecker.daikin;

import java.util.Objects;

import io.github.dschanoeh.homie_java.Property;

public class DaikinProperty implements Comparable<DaikinProperty> {

  public enum PollingInterval {
    NEVER, ONCE, DAILY, BI_HOURLY, HOURLY, MINUTELY;
  }

  public enum PostProcessing {
    NONE, CONSUMPTION
  }

  public enum DataType {
    INTEGER, FLOAT, BOOLEAN, STRING, ENUM;

    io.github.dschanoeh.homie_java.Property.DataType toHomieDataType() {
      switch (this) {
      case BOOLEAN:
        return io.github.dschanoeh.homie_java.Property.DataType.BOOLEAN;
      case ENUM:
        return io.github.dschanoeh.homie_java.Property.DataType.ENUM;
      case FLOAT:
        return io.github.dschanoeh.homie_java.Property.DataType.FLOAT;
      case INTEGER:
        return io.github.dschanoeh.homie_java.Property.DataType.INTEGER;
      case STRING:
        return io.github.dschanoeh.homie_java.Property.DataType.STRING;
      }
      return null;
    }
  }

  private final String path;
  private final String groupName;
  private String name;
  private String value = "";
  private Boolean settable = false;
  private Boolean retained = true;
  private String unit = "";
  private String format = "";
  private PollingInterval pollInterval = PollingInterval.HOURLY;
  private DataType dataType = DataType.STRING;
  private PostProcessing postProcessing = PostProcessing.NONE;
  public transient Property homieProperty;
  public transient Object postProcessor;

  public DaikinProperty(String path, String groupName) {
    this.path = path;
    this.name = groupName + path.substring(path.indexOf('/'));
    this.groupName = groupName;
  }

  public String getPath() {
    return path;
  }

  public String getGroupName() {
    return groupName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getId() {
    return path.replaceAll(Daikin.ITEM_SEP, "-").toLowerCase();
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
    result = prime * result + Objects.hash(path);
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
    return Objects.equals(path, other.path);
  }

  @Override
  public int compareTo(DaikinProperty o) {
    return path.compareTo(o.path);
  }

  @Override
  public String toString() {
    return String.format("DaikinProperty [path=%s, name=%s, id=%s, value=%s, settable=%s, retained=%s, unit=%s, format=%s, pollInterval=%s, dataType=%s]", path, name, getId(), value, settable,
        retained, unit, format, pollInterval, dataType);
  }

  public PostProcessing getPostProcessing() {
    return postProcessing;
  }

  public void setPostProcessing(PostProcessing postProcessing) {
    this.postProcessing = postProcessing;
  }

}