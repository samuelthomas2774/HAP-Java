package io.github.hapjava.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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

  final String hash;
  final Integer version;

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

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(this);
      oos.close();

      hash = hash(Base64.getEncoder().encodeToString(baos.toByteArray()));
      // int i = 1;
      //
      // if (homekitRegistryState != null) {
      //   Integer num = homekitRegistryState.version;
      //   if (num == null) continue;
      //   String str2 = homekitRegistryState.hash;
      //   if (str2 != null) i = str2 == this.hash ? num.intValue() : 1 + num.intValue();
      // }

      if (oldRegistryState == null || oldRegistryState.hash == null || oldRegistryState.version == null) {
        version = 1;
      } else {
        version = oldRegistryState.hash == hash ? oldRegistryState.version : oldRegistryState.version + 1;
      }

      // this.version = Integer.valueOf(i);
    } catch (IOException err) {
      throw new RuntimeException("Error getting registry state version", err);
    } catch (NoSuchAlgorithmException err) {
      throw new RuntimeException("Error getting registry state version", err);
    }
  }

  private static String hash(String originalString) throws NoSuchAlgorithmException {
      byte[] hash2 = MessageDigest.getInstance("SHA-256").digest(originalString.getBytes(StandardCharsets.UTF_8));
      StringBuffer hexString = new StringBuffer();
      for (byte b: hash2) {
          String hex = Integer.toHexString(b & 255);
          if (hex.length() == 1) {
              hexString.append('0');
          }
          hexString.append(hex);
      }
      return hexString.toString();
  }
}
