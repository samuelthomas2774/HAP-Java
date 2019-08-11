package io.github.hapjava.impl.characteristics.information;

import io.github.hapjava.HomekitAccessory;
import io.github.hapjava.characteristics.StaticStringCharacteristic;

public class FirmwareRevision extends StaticStringCharacteristic {

  public FirmwareRevision(HomekitAccessory accessory) throws Exception {
    super(
        "00000052-0000-1000-8000-0026BB765291",
        "Firmware Revision",
        accessory.getFirmwareRevision());
  }
}
