package io.github.hapjava.impl;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map.Entry;
import io.github.hapjava.HomekitAccessory;
import io.github.hapjava.Service;
import io.github.hapjava.characteristics.Characteristic;

public class HomekitRegistryState implements Serializable {
  private static final long serialVersionUID = 1L;

  // Maps aids to a ConcurrentMap mapping iids to Services and Characteristics
  final ConcurrentMap<Integer, ConcurrentMap<Integer, String>> aidMap;

  public HomekitRegistryState(HomekitRegistry registry) {
    this(registry, null);
  }

  public HomekitRegistryState(HomekitRegistry registry, HomekitRegistryState oldRegistryState) {
    ConcurrentMap<Integer, ConcurrentMap<Integer, String>> aidMap = new ConcurrentHashMap<>();

    for (HomekitAccessory accessory: registry.getAccessories()) {
      int aid = registry.getAid(accessory);
      ConcurrentMap<Integer, String> iidMap = new ConcurrentHashMap<>();
      ConcurrentMap<Integer, String> oldIids = oldRegistryState != null ? oldRegistryState.aidMap.get(aid) : null;

      for (Service service: registry.getServices(accessory)) {
        iidMap.put(registry.getIid(accessory, service), "Service:" + service.getId());

        for (Characteristic characteristic: registry.getCharacteristics(service)) {
          iidMap.put(registry.getIid(accessory, characteristic),
            "Characteristic:" + characteristic.getType() + ":" + service.getId());
        }
      }

      if (oldIids != null) {
        for (Entry<Integer, String> entry: oldIids.entrySet()) {
          int iid = entry.getKey();
          String key = entry.getValue();

          if (iidMap.containsKey(iid) && iidMap.get(iid) == key) {
            // This service/characteristic still exists
          } else if (iidMap.containsKey(iid)) {
            // This iid has been assigned to another service/characteristic
          } else if (iidMap.containsValue(key)) {
            // This service/characteristic has been assigned another iid
          } else {
            iidMap.put(iid, key);
          }
        }
      }

      aidMap.put(aid, iidMap);
    }

    if (oldRegistryState != null) {
      for (Entry<Integer, ConcurrentMap<Integer, String>> entry: oldRegistryState.aidMap.entrySet()) {
        int aid = entry.getKey();
        ConcurrentMap<Integer, String> iidMap = entry.getValue();

        if (!aidMap.containsKey(aid)) aidMap.put(aid, iidMap);
      }
    }

    this.aidMap = aidMap;
  }
}
