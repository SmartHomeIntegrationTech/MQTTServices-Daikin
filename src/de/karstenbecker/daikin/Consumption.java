package de.karstenbecker.daikin;

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

  public void updateValues(String json, boolean setupProperty) {
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    Set<Entry<String, JsonElement>> rootSet = obj.entrySet();
    for (Entry<String, JsonElement> root : rootSet) {
      Set<Entry<String, JsonElement>> midSet = root.getValue().getAsJsonObject().entrySet();
      for (Entry<String, JsonElement> mid : midSet) {
        Set<Entry<String, JsonElement>> dataSet = mid.getValue().getAsJsonObject().entrySet();
        for (Entry<String, JsonElement> data : dataSet) {
          String name = baseName + "-" + root.getKey().toLowerCase().strip() + "-" + mid.getKey().toLowerCase().strip() + "-" + data.getKey().toLowerCase().strip();
          JsonArray values = data.getValue().getAsJsonArray();
          Number lastNonNull = null;
          for (int i = 0; i < values.size(); i++) {
            JsonElement value = values.get(i);
            String nodeName = name + "-" + i;
            Property property = node.getProperty(nodeName);
            if (setupProperty) {
              property.setDataType(DataType.INTEGER);
              property.setUnit("kWh");
              property.setRetained(false);
            } else {
              if (value.isJsonNull()) {
                property.send((Long) null);
              } else {
                lastNonNull = value.getAsNumber();
                property.send(lastNonNull.longValue());
              }
            }
          }
          Property property = node.getProperty(name + "-last");
          if (setupProperty) {
            property.setDataType(DataType.INTEGER);
            property.setUnit("kWh");
            property.setRetained(false);
          } else {
            property.send(lastNonNull.longValue());
          }
        }
      }
    }
  }
}
