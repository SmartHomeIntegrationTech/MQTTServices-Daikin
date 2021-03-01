package de.karstenbecker.daikin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.dschanoeh.homie_java.Node;
import io.github.dschanoeh.homie_java.Property;
import io.github.dschanoeh.homie_java.Property.DataType;

public class Consumption {

  private String baseName;
  private Node node;

  public Consumption(Node node, String baseName) {
    this.node = node;
    this.baseName = baseName;
  }

  public static Consumption doSetup(Node node, String baseName, Optional<String> optional) {
    Consumption consumption = new Consumption(node, baseName);
    if (optional.isPresent()) {
      JsonObject response = JsonParser.parseString(optional.get()).getAsJsonObject();
      JsonElement conValue = JsonHelper.getJsonPath(response, "m2m:rsp", "pc", "m2m:cin", "con").get();
      String jsonString = conValue.getAsString().strip();
      consumption.updateValues(jsonString, true);
    }
    return consumption;
  }

  public static void main(String[] args) {
    Consumption.doSetup(null, "energy", Optional.of(
        "{\"Electrical\":{\"Heating\":{\"D\":[0,0,0,0,0,0,0,2,0,3,10,0,0,0,0,0,null,null,null,null,null,null,null,null],\"W\":[2,3,2,4,2,2,2,1,3,1,2,3,15,0],\"M\":[90,54,56,54,45,58,51,63,47,44,55,74,81,94,null,null,null,null,null,null,null,null,null,null]}}}"));
  }

  public Map<String, String> updateValues(String json, boolean setupProperty) {
    Map<String, String> result = new LinkedHashMap<>();
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    Set<Entry<String, JsonElement>> rootSet = obj.entrySet();
    for (Entry<String, JsonElement> root : rootSet) {
      Set<Entry<String, JsonElement>> midSet = root.getValue().getAsJsonObject().entrySet();
      for (Entry<String, JsonElement> mid : midSet) {
        Set<Entry<String, JsonElement>> dataSet = mid.getValue().getAsJsonObject().entrySet();
        for (Entry<String, JsonElement> data : dataSet) {
          String energyName = root.getKey().strip();
          String purpose = mid.getKey().strip();
          String field = data.getKey().strip();
          String name = baseName + "-" + energyName.toLowerCase() + "-" + purpose.toLowerCase() + "-" + field.toLowerCase();
          JsonArray values = data.getValue().getAsJsonArray();
          Number lastNonNull = null;
          int size = values.size();
          for (int i = 0; i < size; i++) {
            JsonElement value = values.get(i);
            String nodeName = name + "-" + i;
            Property property = node.getProperty(nodeName);
            if (setupProperty) {
              property.setDataType(DataType.INTEGER);
              property.setUnit("kWh");
              property.setName(energyName + "/" + purpose + "/" + field + "[" + i + "] " + timeIndicator(field, i, size));
              property.setRetained(true);
            } else {
              if (value.isJsonNull()) {
                result.put(nodeName, null);
                property.send((Long) null);
              } else {
                lastNonNull = value.getAsNumber();
                result.put(nodeName, lastNonNull.toString());
                property.send(lastNonNull.longValue());
              }
            }
          }
          String nodeName = name + "-last";
          Property property = node.getProperty(nodeName);
          if (setupProperty) {
            property.setDataType(DataType.INTEGER);
            property.setUnit("kWh");
            property.setRetained(true);
            property.setName(energyName + "/" + purpose + "/" + field+" last non-null value");
          } else {
            result.put(nodeName, lastNonNull.toString());
            property.send(lastNonNull.longValue());
          }
        }
      }
    }
    return result;
  }

  private static final String DAYS[]= {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
  private static final String MONTH[]= {"Jan", "Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
  private String timeIndicator(String field, int i, int size) {
    switch (field) {
    case "D":
      if (size == 24) {
        if (i>=12) {
          return String.format("Today %02d:00-%02d:00", (i-12)*2, (i-12+1)*2);
        }
        return String.format("Yesterday %02d:00-%02d:00", i*2, (i+1)*2);
      }
    case "W":
      if (size==14) {
        if (i>=7) {
          return "this week "+DAYS[i-7];
        }
        return "last week "+DAYS[i];
      }
    case "M":
      if (size==24) {
        if (i>=12) {
          return "this year "+MONTH[i-12];
        }
        return "last year "+MONTH[i];
      }
    }
    return "";
  }

}
