package io.github.hapjava.impl.json;

import io.github.hapjava.HomekitAccessory;
import io.github.hapjava.Service;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.impl.HomekitRegistry;
import io.github.hapjava.impl.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class AccessoryController {

  private final HomekitRegistry registry;

  public AccessoryController(HomekitRegistry registry) {
    this.registry = registry;
  }

  public HttpResponse listing() throws Exception {
    JsonArrayBuilder accessories = Json.createArrayBuilder();

    Map<Integer, List<CompletableFuture<JsonObject>>> accessoryServiceFutures = new HashMap<>();
    for (HomekitAccessory accessory : registry.getAccessories()) {
      List<CompletableFuture<JsonObject>> serviceFutures = new ArrayList<>();
      for (Service service : registry.getServices(registry.getAid(accessory))) {
        serviceFutures.add(toJson(accessory, service));
      }
      accessoryServiceFutures.put(registry.getAid(accessory), serviceFutures);
    }

    Map<Integer, JsonArrayBuilder> serviceArrayBuilders = new HashMap<>();
    for (Entry<Integer, List<CompletableFuture<JsonObject>>> entry :
        accessoryServiceFutures.entrySet()) {
      JsonArrayBuilder arr = Json.createArrayBuilder();
      for (CompletableFuture<JsonObject> future : entry.getValue()) {
        arr.add(future.join());
      }
      serviceArrayBuilders.put(entry.getKey(), arr);
    }

    for (HomekitAccessory accessory : registry.getAccessories()) {
      accessories.add(
          Json.createObjectBuilder()
              .add("aid", registry.getAid(accessory))
              .add("services", serviceArrayBuilders.get(registry.getAid(accessory))));
    }

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Json.createWriter(baos)
          .write(Json.createObjectBuilder().add("accessories", accessories).build());
      return new HapJsonResponse(baos.toByteArray());
    }
  }

  private CompletableFuture<JsonObject> toJson(HomekitAccessory accessory, Service service) throws Exception {
    String shortType =
        service.getType().replaceAll("^0*([0-9a-fA-F]+)-0000-1000-8000-0026BB765291$", "$1");
    JsonObjectBuilder builder =
        Json.createObjectBuilder().add("iid", registry.getIid(accessory, service)).add("type", shortType);
    List<Characteristic> characteristics = registry.getCharacteristics(service);
    Collection<CompletableFuture<JsonObject>> characteristicFutures =
        new ArrayList<>(characteristics.size());
    for (Characteristic characteristic : characteristics) {
      characteristicFutures.add(characteristic.toJson(registry.getIid(accessory, characteristic)));
    }

    return CompletableFuture.allOf(
            characteristicFutures.toArray(new CompletableFuture<?>[characteristicFutures.size()]))
        .thenApply(
            v -> {
              JsonArrayBuilder jsonCharacteristics = Json.createArrayBuilder();
              characteristicFutures
                  .stream()
                  .map(future -> future.join())
                  .forEach(c -> jsonCharacteristics.add(c));
              builder.add("characteristics", jsonCharacteristics);

              List<Service> linkedServices = service.getLinkedServices();

              if (linkedServices.size() > 0) {
                JsonArrayBuilder jsonLinkedServices = Json.createArrayBuilder();
                for (Service linkedService: linkedServices) {
                  int iid = registry.getIid(accessory, linkedService);
                  jsonLinkedServices.add(iid);
                }
                builder.add("linked", jsonLinkedServices);
              }

              return builder.build();
            });
  }
}
