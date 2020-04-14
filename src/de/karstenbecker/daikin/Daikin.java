package de.karstenbecker.daikin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.dschanoeh.homie_java.Property.DataType;
import net.posick.mDNS.Lookup;

public class Daikin {
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
			return String.format("DaikinInformation [id=%s, model=%s, type=%s, firmware=%s, software=%s, hardware=%s]",
					id, model, type, firmware, software, hardware);
		}

	}

	private final Logger logger = LoggerFactory.getLogger(Daikin.class);

	private WebsocketHelper webSocketClient;
	public static final String ITEM_SEP = "/";

	public Daikin() throws Exception {
		webSocketClient = new WebsocketHelper();
	}

	public InetSocketAddress discoverDevice() throws Exception {
		try (Lookup lookup = new Lookup("_daikin._tcp.local.", org.xbill.DNS.Type.ANY, DClass.IN)) {
			Record[] records = lookup.lookupRecords();
			for (Record record : records) {
				if (record.getType() == org.xbill.DNS.Type.A) {
					InetSocketAddress addr = new InetSocketAddress(record.rdataToString(), 80);
					return addr;
				}
			}
		}
		return null;
	}

	public DaikinInformation getInformation(InetSocketAddress adr) throws IOException, InterruptedException {
		URI url = getBaseURL(adr);
		logger.debug("URL:" + url);
		if (!webSocketClient.connect(url)) {
			logger.debug("Discovery failed to connect to:" + url);
			return null;
		}
		Optional<String> reply = webSocketClient.sendDiscovery();
		if (!reply.isPresent())
			return null;
		JsonElement root = JsonParser.parseString(reply.get());
		JsonObject description = root.getAsJsonObject().get("m2m:rsp").getAsJsonObject().get("pc").getAsJsonObject()
				.get("m2m:dvi").getAsJsonObject();
		return new DaikinInformation(description);
	}

	private URI getBaseURL(InetSocketAddress adr) {
		return URI.create("ws://" + adr.getHostString() + ":" + adr.getPort() + "/mca");
	}

	public Set<DaikinProperty> discoverProperties(InetSocketAddress adr, List<String> endpoints) {
		URI url = getBaseURL(adr);
		try {
			if (!webSocketClient.connect(url))
				return null;
			int code = -1;
			int i = 0;
			Set<DaikinProperty> properties = new TreeSet<>();
			do {
				Optional<JsonObject> obj = webSocketClient.doQuery(Integer.toString(i));
				if (!obj.isPresent())
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
					if (!unitProfileOpt.isPresent())
						return null;
					Optional<JsonElement> sub = JsonHelper.getJsonPath(unitProfileOpt.get(), "pc", "m2m:cin", "con");
					if (!sub.isPresent())
						return null;
					String valObj = sub.get().getAsString();
					logger.debug("Profile:" + valObj);

					for (String endpoint : endpoints) {
						checkEndpoint(i + endpoint, properties);
					}
					createChannelsFromJSON(valObj, i, properties);
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

	private void checkEndpoint(String item, Set<DaikinProperty> properties) {
		Optional<JsonObject> objQueryResult = webSocketClient.doQuery(item + "/la");
		if (!objQueryResult.isPresent()) // There should be enough debug output in doQuery
			return;
		int code = objQueryResult.get().get("rsc").getAsInt();
		logger.debug("Obj:" + item + " " + code + " " + objQueryResult);
		if (code == 2000) {
			logger.debug("Found channel:" + item);
			DaikinProperty daikinProperty = new DaikinProperty(item);
			JsonElement conValue = JsonHelper.getJsonPath(objQueryResult.get(), "pc", "m2m:cin", "con").get();
			String value = conValue.getAsString();
			System.out.println("VALUE " + item + " " + value);
			daikinProperty.setValue(value);
			guessFromValue(daikinProperty, value);
			properties.add(daikinProperty);
		}
	}

	private void createChannelsFromJSON(String unitProfileString, int i, Set<DaikinProperty> properties) {
		JsonObject unitProfile = JsonParser.parseString(unitProfileString).getAsJsonObject();
		Set<String> primitives = new HashSet<>();
		buildChannels(unitProfile, Integer.toString(i), properties, primitives);
	}

	private void buildChannels(JsonElement root, String item, Set<DaikinProperty> properties, Set<String> primitives) {
		if (root.isJsonPrimitive()) {
			item += ITEM_SEP + root.getAsString();
			primitives.add(item);
		}
		try {
			Optional<JsonObject> objQueryResult = webSocketClient.doQuery(item + "/la");
			if (!objQueryResult.isPresent()) // There should be enough debug output in doQuery
				return;
			int code = objQueryResult.get().get("rsc").getAsInt();
			logger.trace("Obj:" + item + " " + code + " " + objQueryResult);
			if (code == 2000) {
				if (item.length() > 1) { // This drops channels that are at the root because they don't provide any
					// interesting data
					logger.debug("Found channel:" + item);
					DaikinProperty daikinProperty = new DaikinProperty(item);
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
				buildChannels(ele, item, properties, primitives);
			}
			return;
		}
		if (root.isJsonObject()) {
			JsonObject obj = root.getAsJsonObject();
			for (String key : obj.keySet()) {
				JsonElement ele = obj.get(key);
				buildChannels(ele, item + ITEM_SEP + key, properties, primitives);
			}
			return;
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
				property.setFormat(minValue.stripTrailingZeros().toPlainString() + ":"
						+ maxValue.stripTrailingZeros().toPlainString());
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
		//InetSocketAddress address = daikin.discoverDevice();
		//DaikinInformation information = daikin.getInformation(address);
		//System.out.println(information);
		//writeDiscoveredProperties(daikin, address, "otherPotentialEndpoints.txt", "discovered.json");
		
		daikin.webSocketClient.disconnect();
		System.exit(0);
	}

	private static void writeDiscoveredProperties(Daikin daikin, InetSocketAddress address, String otherEndpoints,
			String outputFile) throws IOException {
		List<String> endpoints = Files.readAllLines(new File(otherEndpoints).toPath());
		Set<DaikinProperty> properties = daikin.discoverProperties(address, endpoints);
		DaikinPollingSettings settings = new DaikinPollingSettings(properties);
		settings.setDaikinIP(address.getHostString());
		settings.setDaikinPort(address.getPort());
		String json = settings.toJSON(true);
		System.out.println(json);
		Files.writeString(new File(outputFile).toPath(), json);
	}
}
