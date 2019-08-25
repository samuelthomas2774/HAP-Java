package io.github.hapjava.impl;

import io.github.hapjava.HomekitAccessory;
import io.github.hapjava.Service;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.impl.services.AccessoryInformationService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomekitRegistry {

  // TODO: add some way of saving/restoring this

  private static final Logger logger = LoggerFactory.getLogger(HomekitRegistry.class);

  private final String label;
  private final Map<Integer, HomekitAccessory> accessories;
  private final Map<HomekitAccessory, Integer> nextIids = new HashMap<>();
  private final Map<HomekitAccessory, List<Service>> services = new HashMap<>();
  private final Map<Service, List<Characteristic>> serviceCharacteristics = new HashMap<>();
  private final Map<HomekitAccessory, Map<Integer, Service>> serviceIds = new HashMap<>();
  private final Map<HomekitAccessory, Map<Integer, Characteristic>> characteristicIds = new HashMap<>();
  private boolean isAllowUnauthenticatedRequests = false;
  private HomekitRegistryState state;
  private Consumer<HomekitRegistryState> callback;

  public HomekitRegistry(String label) {
    this.label = label;
    this.accessories = new ConcurrentHashMap<>();
    // reset();
  }

  public void reset() {
    reset(null);
  }

  public synchronized void reset(HomekitRegistryState registryState) {
    nextIids.clear();
    serviceCharacteristics.clear();
    serviceIds.clear();
    characteristicIds.clear();
    services.clear();

    for (HomekitAccessory accessory : accessories.values()) {
      assignIids(accessory, registryState);
    }

    recalculateState(registryState);
  }

  public HomekitRegistryState saveIds() {
    return saveIds(null);
  }

  public HomekitRegistryState saveIds(HomekitRegistryState oldRegistryState) {
    return new HomekitRegistryState(this, oldRegistryState);
  }

  private void assignIids(HomekitAccessory accessory, HomekitRegistryState registryState) {
    List<Service> newServices;

    try {
      newServices = new ArrayList<>(2);
      Collection<Service> accessoryServices = accessory.getServices();
      boolean hasAccessoryInformationService = false;
      for (Service service: accessoryServices) {
        if (service.getType() == "0000003E-0000-1000-8000-0026BB765291") hasAccessoryInformationService = true;
      }
      if (!hasAccessoryInformationService) newServices.add(new AccessoryInformationService(accessory));
      newServices.addAll(accessoryServices);
    } catch (Exception e) {
      logger.error("Could not instantiate services for accessory " + accessory.getLabel(), e);
      services.put(accessory, Collections.emptyList());
      return;
    }

    int aid = getAid(accessory);
    int iid = 1;
    Map<String, Integer> existingServiceIids = new HashMap<>();
    Map<String, Integer> existingCharacteristicIids = new HashMap<>();

    if (registryState != null) {
      Map<Integer, String> iidMap = registryState.aidMap.get(aid);
      if (iidMap != null) {
        for (Entry<Integer, String> entry: iidMap.entrySet()) {
          if (entry.getKey() >= iid) iid = entry.getKey() + 1;
          if (entry.getValue().startsWith("Service:")) {
            existingServiceIids.put(entry.getValue().substring(8), entry.getKey());
          } else if (entry.getValue().startsWith("Characteristic:")) {
            existingCharacteristicIids.put(entry.getValue().substring(15), entry.getKey());
          } else {
            // Invalid key
          }
        }
      }
    }

    Map<Integer, Service> newServiceIds = new HashMap<>();
    Map<Integer, Characteristic> newCharacteristics = new HashMap<>();
    for (Service service : newServices) {
      String serviceId = service.getId();
      if (existingServiceIids.containsKey(serviceId)) {
        newServiceIds.put(existingServiceIids.get(serviceId), service);
      } else {
        newServiceIds.put(iid++, service);
      }

      List<Characteristic> newServiceCharacteristics = new ArrayList<>();
      for (Characteristic characteristic : service.getCharacteristics()) {
        newServiceCharacteristics.add(characteristic);
        String characteristicId = characteristic.getType() + ":" + serviceId;
        if (existingCharacteristicIids.containsKey(characteristicId)) {
          newCharacteristics.put(existingCharacteristicIids.get(characteristicId), characteristic);
        } else {
          newCharacteristics.put(iid++, characteristic);
        }
      }
      serviceCharacteristics.put(service, newServiceCharacteristics);
    }
    nextIids.put(accessory, iid++);
    services.put(accessory, newServices);
    serviceIds.put(accessory, newServiceIds);
    characteristicIds.put(accessory, newCharacteristics);
  }

  public String getLabel() {
    return label;
  }

  public void add(HomekitAccessory accessory) {
    add(accessory.getId(), accessory, null);
  }

  public void add(HomekitAccessory accessory, HomekitRegistryState registryState) {
    add(accessory.getId(), accessory, registryState);
  }

  public void add(int aid, HomekitAccessory accessory, HomekitRegistryState registryState) {
    if (accessories.get(aid) == accessory) return;
    if (accessories.containsKey(aid)) {
      // throw new Exception("Already have an accessory with AID " + aid);
      return;
    }
    accessories.put(aid, accessory);
    assignIids(accessory, registryState);
  }

  public void remove(HomekitAccessory accessory) {
    accessories.remove(getAid(accessory));
    nextIids.remove(accessory);
    for (Service service: services.get(accessory)) {
      serviceCharacteristics.remove(service);
    }
    services.remove(accessory);
    serviceIds.remove(accessory);
    characteristicIds.remove(accessory);
  }

  public boolean isAllowUnauthenticatedRequests() {
    return isAllowUnauthenticatedRequests;
  }

  public void setAllowUnauthenticatedRequests(boolean allow) {
    this.isAllowUnauthenticatedRequests = allow;
  }

  public int getAid(HomekitAccessory accessory) {
    return accessory.getId();
  }

  public int getIid(HomekitAccessory accessory, Service service) {
    // int aid = getAid(accessory);
    Map<Integer, Service> iids = serviceIds.get(accessory);
    for (Entry<Integer, Service> entry: iids.entrySet()) {
      if (entry.getValue() == service) return entry.getKey();
    }
    Integer nextIid = nextIids.get(accessory);
    if (nextIid == null) nextIid = 1;
    iids.put(nextIid, service);
    nextIids.put(accessory, nextIid + 1);
    return nextIid;
  }

  public int getIid(HomekitAccessory accessory, Service service, Characteristic characteristic) {
    return getIid(accessory, characteristic);
  }

  public int getIid(HomekitAccessory accessory, Characteristic characteristic) {
    // int aid = getAid(accessory);
    Map<Integer, Characteristic> iids = characteristicIds.get(accessory);
    for (Entry<Integer, Characteristic> entry: iids.entrySet()) {
      if (entry.getValue() == characteristic) return entry.getKey();
    }
    Integer nextIid = nextIids.get(accessory);
    if (nextIid == null) nextIid = 1;
    iids.put(nextIid, characteristic);
    nextIids.put(accessory, nextIid + 1);
    return nextIid;
  }

  public Collection<HomekitAccessory> getAccessories() {
    return accessories.values();
  }

  public HomekitAccessory getAccessory(int aid) {
    return accessories.get(aid);
  }

  public HomekitAccessory getAccessory(Service service) {
    for (HomekitAccessory accessory: accessories.values()) {
      if (services.get(accessory).contains(service)) return accessory;
    }

    return null;
  }

  public HomekitAccessory getAccessory(Characteristic characteristic) {
    for (HomekitAccessory accessory: accessories.values()) {
      Service service = getService(accessory, characteristic);
      if (service != null) return accessory;
    }

    return null;
  }

  public List<Service> getServices(Integer aid) {
    return getServices(getAccessory(aid));
  }

  public List<Service> getServices(HomekitAccessory accessory) {
    return services.get(accessory);
  }

  public Service getService(int aid, int iid) {
    return getService(getAccessory(aid), iid);
  }

  public Service getService(HomekitAccessory accessory, int iid) {
    Map<Integer, Service> iids = serviceIds.get(accessory);
    return iids.get(iid);
  }

  public Service getService(HomekitAccessory accessory, Characteristic characteristic) {
    for (Service service: services.get(accessory)) {
      if (serviceCharacteristics.get(service).contains(characteristic)) return service;
    }

    return null;
  }

  public Service getService(Characteristic characteristic) {
    for (HomekitAccessory accessory: accessories.values()) {
      Service service = getService(accessory, characteristic);
      if (service != null) return service;
    }

    return null;
  }

  public List<Characteristic> getCharacteristics(Service service) {
    return serviceCharacteristics.get(service);
  }

  public Characteristic getCharacteristic(int aid, int iid) {
    return getCharacteristic(getAccessory(aid), iid);
  }

  public Characteristic getCharacteristic(HomekitAccessory accessory, int iid) {
    Map<Integer, Characteristic> iids = characteristicIds.get(accessory);
    return iids.get(iid);
  }

  public void recalculateState(HomekitRegistryState oldRegistryState) {
    recalculateState(oldRegistryState, true);
  }

  private void recalculateState(HomekitRegistryState oldRegistryState, boolean notifyChange) {
    HomekitRegistryState currentState = this.state;
    this.state = saveIds(oldRegistryState);
    if (notifyChange && currentState != null && currentState.hash != this.state.hash) {
      Consumer<HomekitRegistryState> consumer = this.callback;
      if (consumer != null) consumer.accept(this.state);
    }
  }

  public void onChange(Consumer<HomekitRegistryState> callback2) {
    onChange(callback2, false, null);
  }

  public void onChange(Consumer<HomekitRegistryState> _callback, boolean initial, HomekitRegistryState oldRegistryState) {
    this.callback = _callback;
    if (initial) {
      if (this.state == null) recalculateState(oldRegistryState, false);
      this.callback.accept(this.state);
    }
  }
}
