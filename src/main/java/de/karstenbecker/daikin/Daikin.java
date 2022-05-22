package de.karstenbecker.daikin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.karstenbecker.daikin.DaikinProperty.DataType;
import de.karstenbecker.daikin.DaikinProperty.PollingInterval;
import de.karstenbecker.daikin.DaikinProperty.PostProcessing;
import de.karstenbecker.daikin.ui.SetupUI;
import io.github.dschanoeh.homie_java.Configuration;
import io.github.dschanoeh.homie_java.Homie;
import io.github.dschanoeh.homie_java.Homie.State;
import io.github.dschanoeh.homie_java.Node;
import io.github.dschanoeh.homie_java.Property;
import io.github.dschanoeh.homie_java.PropertySetCallback;

public class Daikin implements PropertySetCallback {
  private static final long MINUTELY_MS = 60000L;
  private static final long HOURLY_MS = 60 * MINUTELY_MS;
  private static final long BI_HOURLY_MS = 2 * HOURLY_MS;
  private static final long DAILY_MS = 24 * HOURLY_MS;

  public class DaikinInformation {
    public final String id;
    public final String model;
    public final String type;
    public final String firmware;
    public final String software;
    public final String hardware;

    public DaikinInformation(JsonObject description) {
      id = description.get("dlb").getAsString();
      model = description.get("mod").getAsString();
      type = description.get("dty").getAsString();
      firmware = description.get("fwv").getAsString();
      software = description.get("swv").getAsString();
      hardware = description.get("hwv").getAsString();
    }

    @Override
    public String toString() {
      return String.format("DaikinInformation [id=%s, model=%s, type=%s, firmware=%s, software=%s, hardware=%s]", id, model, type, firmware, software, hardware);
    }

  }

  private final Logger logger = LoggerFactory.getLogger(Daikin.class);

  private WebsocketHelper webSocketClient;
  public static final String ITEM_SEP = "/";
  private URI url;
  private final Map<String, DaikinProperty> idToProp = new HashMap<>();

  public Daikin() throws Exception {
    webSocketClient = new WebsocketHelper();
  }

  public DaikinInformation getInformation(InetSocketAddress adr) throws IOException, InterruptedException {
    URI url = getBaseURL(adr);
    logger.debug("URL:" + url);
    if (!webSocketClient.connect(url)) {
      logger.debug("Discovery failed to connect to:" + url);
      return null;
    }
    Optional<String> reply = webSocketClient.sendDiscovery();
    if (reply.isEmpty())
      return null;
    JsonElement root = JsonParser.parseString(reply.get());
    JsonObject description = root.getAsJsonObject().get("m2m:rsp").getAsJsonObject().get("pc").getAsJsonObject().get("m2m:dvi").getAsJsonObject();
    return new DaikinInformation(description);
  }

  private URI getBaseURL(InetSocketAddress adr) {
    return URI.create("ws://" + adr.getHostString() + ":" + adr.getPort() + "/mca");
  }

  private URI getBaseURL(DaikinPollingSettings settings) {
    return URI.create("ws://" + settings.getDaikinIP() + ":" + settings.getDaikinPort() + "/mca");
  }

  public Set<DaikinProperty> discoverProperties(InetSocketAddress adr, List<String> endpoints) {
    URI url = getBaseURL(adr);
    logger.debug("Connecting to "+url);
    try {
      if (!webSocketClient.connect(url))
        return null;
      int code = -1;
      int i = 0;
      Set<DaikinProperty> properties = new TreeSet<>();
      do {
        Optional<JsonObject> obj = webSocketClient.doQuery(Integer.toString(i));
        if (obj.isEmpty())
          return null;
        code = obj.get().get("rsc").getAsInt();
        if (code == 2000) {
          String groupName = Integer.toString(i);
          Optional<JsonElement> label = JsonHelper.getJsonPath(obj.get(), "pc", "m2m:cnt", "lbl");
          if (label.isPresent() && label.get().isJsonPrimitive()) {
            String temp = label.get().getAsString();
            groupName = temp.substring(temp.indexOf("/") + 1);
            logger.debug("Using " + groupName + " as node name.");
          }
          Optional<JsonObject> unitProfileOpt = webSocketClient.doQuery(i + "/UnitProfile/la");
          if (unitProfileOpt.isEmpty())
            return null;
          Optional<JsonElement> sub = JsonHelper.getJsonPath(unitProfileOpt.get(), "pc", "m2m:cin", "con");
          if (sub.isEmpty())
            return null;
          String valObj = sub.get().getAsString();
          logger.debug("Profile:" + valObj);

          for (String endpoint : endpoints) {
            checkEndpoint(i + endpoint, properties, groupName);
          }
          createChannelsFromJSON(valObj, i, properties, groupName);
        }
        // {"m2m:rsp":{"rsc":2000,"rqi":"12e741f64af0afd2","to":"/OpenHab","fr":"/[0]/MNAE/0/la","pc":{"m2m:cin":{"rn":"0000000b","ri":"006a_0000000b","pi":"006a","ty":4,"ct":"20000000T000000Z","lt":"20000000T000000Z","st":11,"con":"{\"version\":\"v1.2.3\"}"}}}}
        // {"m2m:rsp":{"rsc":4004,"rqi":"1c3ac0b7592824ee","to":"/OpenHab","fr":"/[0]/MNAE/3/la"}}
        i++;
      } while (code == 2000);
      return properties;
    } finally {
      webSocketClient.disconnect();
    }
  }

  private void checkEndpoint(String item, Set<DaikinProperty> properties, String groupName) {
    Optional<JsonObject> objQueryResult = webSocketClient.doQuery(item + "/la");
    if (objQueryResult.isEmpty()) // There should be enough debug output in doQuery
      return;
    int code = objQueryResult.get().get("rsc").getAsInt();
    logger.debug("Obj:" + item + " " + code + " " + objQueryResult);
    if (code == 2000) {
      logger.debug("Found channel:" + item);
      DaikinProperty daikinProperty = new DaikinProperty(item, groupName);
      JsonElement conValue = JsonHelper.getJsonPath(objQueryResult.get(), "pc", "m2m:cin", "con").get();
      String value = conValue.getAsString();
      System.out.println("VALUE " + item + " " + value);
      daikinProperty.setValue(value);
      guessFromValue(daikinProperty, value);
      properties.add(daikinProperty);
    }
  }

  private void createChannelsFromJSON(String unitProfileString, int i, Set<DaikinProperty> properties, String groupName) {
    JsonObject unitProfile = JsonParser.parseString(unitProfileString).getAsJsonObject();
    Set<String> primitives = new HashSet<>();
    buildChannels(unitProfile, Integer.toString(i), properties, primitives, groupName);
  }

  private void buildChannels(JsonElement root, String item, Set<DaikinProperty> properties, Set<String> primitives, String groupName) {
    if (root.isJsonPrimitive()) {
      item += ITEM_SEP + root.getAsString();
      primitives.add(item);
    }
    try {
      Optional<JsonObject> objQueryResult = webSocketClient.doQuery(item + "/la");
      if (objQueryResult.isEmpty()) // There should be enough debug output in doQuery
        return;
      int code = objQueryResult.get().get("rsc").getAsInt();
      logger.trace("Obj:" + item + " " + code + " " + objQueryResult);
      if (code == 2000) {
        if (item.length() > 1) { // This drops channels that are at the root because they don't provide any
          // interesting data
          logger.debug("Found channel:" + item);
          DaikinProperty daikinProperty = new DaikinProperty(item, groupName);
          guessType(item, daikinProperty, objQueryResult.get(), root);
          System.out.println("Created property:" + daikinProperty);
          if (!properties.add(daikinProperty)) {
            properties.remove(daikinProperty);
            properties.add(daikinProperty);
          }
        }
      }
    } catch (Exception e) {
      logger.debug("Error:", e);
      return;
    }
    if (root.isJsonArray()) {
      JsonArray array = root.getAsJsonArray();
      for (JsonElement ele : array) {
        buildChannels(ele, item, properties, primitives, groupName);
      }
      return;
    }
    if (root.isJsonObject()) {
      JsonObject obj = root.getAsJsonObject();
      for (String key : obj.keySet()) {
        JsonElement ele = obj.get(key);
        buildChannels(ele, item + ITEM_SEP + key, properties, primitives, groupName);
      }
    }
  }

  private void guessType(String item, DaikinProperty property, JsonObject jsonValue, JsonElement profileNode) {
    JsonElement conValue = JsonHelper.getJsonPath(jsonValue, "pc", "m2m:cin", "con").get();
    String value = conValue.getAsString();
    System.out.println("VALUE " + item + " " + value);
    property.setValue(value);
    if (profileNode.isJsonPrimitive()) {
      // That is all information we have, so we can't be smart
      String asString = profileNode.getAsString();
      logger.debug(item + " " + asString);
      guessFromValue(property, value);
      return;
    }
    if (profileNode.isJsonObject()) {
      JsonObject profileNodeSub = profileNode.getAsJsonObject();
      logger.debug(item + " " + profileNodeSub);
      BigDecimal minValue = null, maxValue = null, stepValue = null;
      for (Entry<String, JsonElement> entry : profileNodeSub.entrySet()) {
        JsonElement child = entry.getValue();
        if (child.isJsonObject()) {
          JsonObject subSub = child.getAsJsonObject();
          if (subSub.has("minValue")) {
            minValue = subSub.get("minValue").getAsBigDecimal();
          }
          if (subSub.has("maxValue")) {
            maxValue = subSub.get("maxValue").getAsBigDecimal();
          }
          if (subSub.has("stepValue")) {
            stepValue = subSub.get("stepValue").getAsBigDecimal();
          }
          if (subSub.has("unit")) {
            property.setUnit(subSub.get("unit").getAsString());
          }
        }
      }
      if (minValue != null && maxValue != null) {
        property.setFormat(minValue.stripTrailingZeros().toPlainString() + ":" + maxValue.stripTrailingZeros().toPlainString());
        try {
          stepValue.longValueExact();
          property.setDataType((DataType.INTEGER));
          return;
        } catch (Exception e) {
        }
        property.setDataType((DataType.FLOAT));
        return;
      }
      guessFromValue(property, value);
      return;
    }
    if (profileNode.isJsonArray()) {
      logger.debug("Type is array");
      JsonArray jsonArray = profileNode.getAsJsonArray();
      boolean justStrings = true;
      StringJoiner sj = new StringJoiner(",");
      for (JsonElement jsonElement : jsonArray) {
        logger.debug(item + " " + jsonElement);
        if (jsonElement.isJsonPrimitive()) {
          String string = jsonElement.getAsString();
          sj.add(string);
        } else {
          justStrings = false;
        }
      }
      if (justStrings) {
        property.setDataType((DataType.ENUM));
        property.setFormat((sj.toString()));
        return;
      }

    }
  }

  private void guessFromValue(DaikinProperty daikinProperty, String value) {
    try {
      Long.parseLong(value);
      daikinProperty.setDataType(DataType.INTEGER);
      return;
    } catch (Exception e) {
    }
    try {
      new BigDecimal(value);
      daikinProperty.setDataType(DataType.FLOAT);
      return;
    } catch (Exception e) {
    }
    daikinProperty.setDataType(DataType.STRING);
  }

  public static void main(String[] args) throws Exception {
    Daikin daikin = new Daikin();
    final Options options = new Options();
    options.addOption(new Option("g", "gui", false, "Show the setup UI"));
    options.addOption(new Option("c", "config", true, "The config file to use. PollingSettings.json is the default"));
    options.addOption(new Option("e", "endpoint", true, "An additional list of endpoints to check. Built-in otherPotentialEndpoints.txt is the default"));
    options.addOption(new Option("w", "writeSettings", true, "Run endpoint checking without GUI and write config file. Specify IP as argument"));
    options.addOption(new Option("p", "polling", false, "Run polling from settings"));
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    File configFile = new File("PollingSettings.json");
    if (cmd.hasOption('c')) {
      configFile = new File(cmd.getOptionValue('c'));
    }
    URL endPointsURL = Daikin.class.getResource("otherPotentialEndpoints.txt");
    if (cmd.hasOption('e')) {
      endPointsURL = new File(cmd.getOptionValue('e')).toURI().toURL();
    }
    if (cmd.hasOption('w')) {
      List<String> endPoints = readEndPoints(endPointsURL);
      String ip = cmd.getOptionValue('w');
      InetSocketAddress address = new InetSocketAddress(ip, 80);
      System.out.println("Scanning " + ip);
      daikin.writeDiscoveredProperties(address, endPoints, configFile.getAbsolutePath());
      System.exit(0);
    }
    if (cmd.hasOption('g')) {
      DaikinPollingSettings settings = new DaikinPollingSettings();
      if (configFile.exists()) {
        settings = DaikinPollingSettings.fromFile(configFile);
      }
      SetupUI.show(settings, readEndPoints(endPointsURL));
      return;
    }
    if (cmd.hasOption('p')) {
      if (!configFile.exists()) {
        System.err.println("Could not find settings file:" + configFile);
        System.exit(1);
      }
      DaikinPollingSettings settings = DaikinPollingSettings.fromFile(configFile);
      daikin.startPolling(settings);
      daikin.webSocketClient.disconnect();
      System.exit(0);
    }
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("java -jar daikin.jar", options);
    System.exit(0);
  }

  private static List<String> readEndPoints(URL endPointsURL) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(endPointsURL.openStream()));
    return reader.lines().collect(Collectors.toList());
  }

  public void startPolling(DaikinPollingSettings settings) {
    Configuration config = new Configuration();
    config.setBrokerUrl(settings.getHomieServer());
    config.setDeviceID(settings.getHomieDeviceName());
    config.setDeviceName(settings.getHomieDeviceName());
    config.setBrokerUsername(settings.getHomieUser());
    config.setBrokerPassword(settings.getHomiePassword());
    if (settings.getInfluxTopic() != null) {
      logger.warn("Logging influx to Topic:" + settings.getInfluxTopic());
    }

    Homie homie = new Homie(config, "de.karstenbecker.daikin", "0.0.1");
    Map<String, Node> nodes = new HashMap<>();
    url = getBaseURL(settings);
    for (DaikinProperty prop : settings.getProperties()) {
      if (prop.getPollInterval() == PollingInterval.NEVER)
        continue;
      String groupName = prop.getGroupName();
      Node node = nodes.get(groupName);
      if (node == null) {
        node = homie.createNode(groupName.toLowerCase(), groupName);
        node.setName(groupName);
        nodes.put(groupName, node);
      }
      Property property = node.getProperty(prop.getId());
      property.setDataType(prop.getDataType().toHomieDataType());
      property.setFormat(prop.getFormat());
      property.setName(prop.getName());
      property.setRetained(prop.getRetained());
      property.setUnit(property.getUnit());
      idToProp.put(property.getID(), prop);
      if (prop.getSettable()) {
        property.makeSettable(this);
      }
      prop.homieProperty = property;
      if (prop.getPostProcessing() == PostProcessing.CONSUMPTION) {
        synchronized (webSocketClient) {
          try {
            if (!webSocketClient.connect(url))
              logger.error("Could not connect to daikin adpater:" + url);
            prop.postProcessor = Consumption.doSetup(node, property.getID(), webSocketClient.sendQuery(prop.getPath() + "/la"));
            webSocketClient.disconnect();
          } catch (Exception e) {
            logger.error("Failed to collect data for setup", e);
          }
        }

      }
    }
    homie.setup();
    waitForHomie(homie);
    synchronized (webSocketClient) {
      try {
        if (!webSocketClient.connect(url))
          logger.error("Could not connect to daikin adpater:" + url);
        for (DaikinProperty prop : settings.getProperties()) {
          if (prop.getPollInterval() != PollingInterval.NEVER) {
            pollItem(prop);
          }
        }
        webSocketClient.disconnect();
      } catch (Exception e) {
        logger.error("Failed to collect data", e);
      }
    }
    long currentRun = System.currentTimeMillis();
    long nextMinutely = currentRun + MINUTELY_MS;
    long nextHourly = currentRun + HOURLY_MS;
    long nextBiHourly = currentRun + BI_HOURLY_MS;
    long nextDaily = currentRun + DAILY_MS;
    while (true) {
      currentRun = System.currentTimeMillis();
      waitForHomie(homie);
      try {
        synchronized (webSocketClient) {
          if (!webSocketClient.connect(url)) {
            logger.error("Could not connect to daikin adpater:" + url);
            Thread.sleep(30000);
            continue;
          }
          String influxString = pollItems(settings, nextMinutely, nextHourly, nextBiHourly, nextDaily, currentRun);
          if (influxString != null && !influxString.isBlank() && settings.getInfluxTopic() != null) {
            logger.trace("Posting influx message:"+influxString);
            boolean publish = homie.publish(settings.getInfluxTopic(), new MqttMessage(influxString.getBytes(StandardCharsets.UTF_8)));
            if (!publish) {
              logger.warn("Failed to post influx message:"+influxString);
            }
          }
          webSocketClient.disconnect();
        }
        if (currentRun > nextMinutely) {
          nextMinutely = nextMinutely + MINUTELY_MS;
        }
        if (currentRun > nextHourly) {
          nextHourly = nextHourly + HOURLY_MS;
        }
        if (currentRun > nextBiHourly) {
          nextBiHourly = nextBiHourly + BI_HOURLY_MS;
        }
        if (currentRun > nextDaily) {
          nextDaily = nextDaily + DAILY_MS;
        }
      } catch (Exception e) {
        logger.error("Failed to collect data", e);
      }
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
      }
    }
  }

  private void waitForHomie(Homie homie) {
    while (homie.getState() != State.READY) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
    }
  }

  private String pollItems(DaikinPollingSettings settings, long nextMinutely, long nextHourly, long nextBiHourly, long nextDaily, long currentRun) {
    StringJoiner items = new StringJoiner(",");
    for (DaikinProperty prop : settings.getProperties()) {
      long compareAgainst = Long.MAX_VALUE;
      switch (prop.getPollInterval()) {
      case DAILY:
        compareAgainst = nextDaily;
        break;
      case BI_HOURLY:
        compareAgainst = nextBiHourly;
        break;
      case HOURLY:
        compareAgainst = nextHourly;
        break;
      case MINUTELY:
        compareAgainst = nextMinutely;
        break;
      case NEVER:
      case ONCE:
      default:
        break;
      }
      if (currentRun > compareAgainst) {
        if (prop.getPollInterval() != PollingInterval.MINUTELY)
          logger.warn("Checking " + prop);
        Map<String, String> values = pollItem(prop);
        if (values != null) {
          for (Entry<String, String> e : values.entrySet()) {
            if (e.getValue()!=null)
              items.add(e.getKey() + "=" + e.getValue());
          }
        }
      }
    }
    if (items.length() == 0)
      return null;
    return settings.getInfluxTable() + ",qfn=" + settings.getInfluxQFN() + " " + items.toString() + " " + (System.currentTimeMillis() * 1000000L);
  }

  private Map<String, String> pollItem(DaikinProperty property) {
    Map<String, String> result = new LinkedHashMap<>();
    Optional<JsonObject> objQueryResult = webSocketClient.doQuery(property.getPath() + "/la");
    if (!objQueryResult.isPresent()) {
      logger.warn("failed to read " + property.getPath());
      return null;
    }
    int code = objQueryResult.get().get("rsc").getAsInt();
    logger.debug("Obj:" + property.getName() + " " + code + " " + objQueryResult);
    if (code == 2000) {
      JsonElement conValue = JsonHelper.getJsonPath(objQueryResult.get(), "pc", "m2m:cin", "con").get();
      String value = conValue.getAsString().strip();
      if (property.getPostProcessing() != null) {
        switch (property.getPostProcessing()) {
        case CONSUMPTION:
          result.putAll(((Consumption) property.postProcessor).updateValues(value, false));
          break;
        case NONE:
          break;
        }
      }
      switch (property.getDataType()) {
      case FLOAT:
        BigDecimal bd = new BigDecimal(value);
        property.homieProperty.send(bd.doubleValue());
        result.put(property.getId(), bd.toPlainString());
        return result;
      case BOOLEAN:
        boolean boolValue = "0".contentEquals(value);
        if (boolValue)
          property.homieProperty.send(Boolean.FALSE);
        else
          property.homieProperty.send(Boolean.TRUE);
        result.put(property.getId(), boolValue ? "1" : "0");
        return result;
      case INTEGER:
        long parseLong = new BigDecimal(value).longValue();
        property.homieProperty.send(parseLong);
        result.put(property.getId(), Long.toString(parseLong));
        return result;
      case ENUM:
      case STRING:
        if (!value.equals(""))
          property.homieProperty.send(value);
        result.put(property.getId(), escapeAndQuote(value));
        return result;
      default:
        break;

      }
    } else {
      logger.warn("Response code was not 2000 for item:" + property.getName() + " code was:" + code);
    }
    return null;
  }

  private static String escapeAndQuote(String value) {
    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
    return '"' + escaped + '"';
  }

  public void writeDiscoveredProperties(InetSocketAddress address, List<String> endPoints, String outputFile) throws IOException {
    Set<DaikinProperty> properties = discoverProperties(address, endPoints);
    DaikinPollingSettings settings = new DaikinPollingSettings(properties);
    settings.setDaikinIP(address.getHostString());
    settings.setDaikinPort(address.getPort());
    String json = settings.toJSON(true);
    System.out.println(json);
    File outFile = new File(outputFile);
    Files.writeString(outFile.toPath(), json);
    System.out.println("Wrote settings to file:" + outFile);
  }

  @Override
  public void performSet(Property property, String value) {
    DaikinProperty daikinProperty = idToProp.get(property.getID());
    String item = daikinProperty.getPath();
    try {
      synchronized (webSocketClient) {
        if (!webSocketClient.connect(url)) {
          logger.error("Could not connect to daikin adpater:" + url);
          return;
        }
        Optional<String> response = webSocketClient.setValue(item, value);
        pollItem(daikinProperty);
        webSocketClient.disconnect();
        if (!response.isPresent())
          return;
        JsonElement ele = JsonParser.parseString(response.get());
        if (!ele.isJsonObject()) {
          logger.warn("Json response is not an obj:" + response);
          return;
        }
        JsonObject obj = ele.getAsJsonObject();
        JsonElement rsp = obj.get("m2m:rsp");
        if (rsp == null || !rsp.isJsonObject()) {
          logger.warn("Expected a m2m:rsp Json object, but got:" + response);
          return;
        }
        if (!BigDecimal.valueOf(2001).equals(rsp.getAsJsonObject().get("rsc").getAsBigDecimal())) {
          logger.warn("Expected code 2001, but got:" + response);
          return;
        }
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }

  }

}
