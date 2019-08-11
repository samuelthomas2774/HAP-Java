package io.github.hapjava.impl.jmdns;

import io.github.hapjava.HomekitAdvertiser;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmdnsHomekitAdvertiser implements HomekitAdvertiser {

  private static final String SERVICE_TYPE = "_hap._tcp.local.";

  private final JmDNS jmdns;
  private boolean discoverable = true;
  private static final Logger logger = LoggerFactory.getLogger(JmdnsHomekitAdvertiser.class);
  private boolean isAdvertising = false;

  private String label;
  private String mac;
  private int port;
  private int configurationIndex;
  public int category = 1;

  public JmdnsHomekitAdvertiser(InetAddress localAddress, int _category) throws UnknownHostException, IOException {
    jmdns = JmDNS.create(localAddress);
    category = _category;
  }

  public JmdnsHomekitAdvertiser(InetAddress localAddress) throws UnknownHostException, IOException {
    this(localAddress, 1);
  }

  public synchronized void advertise(String label, String mac, int port, int configurationIndex)
      throws Exception {
    if (isAdvertising) {
      throw new IllegalStateException("Homekit advertiser is already running");
    }
    this.label = label;
    this.mac = mac;
    this.port = port;
    this.configurationIndex = configurationIndex;

    logger.info("Advertising accessory " + label);

    registerService();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Stopping advertising in response to shutdown.");
                  jmdns.unregisterAllServices();
                }));
    isAdvertising = true;
  }

  public synchronized void stop() {
    jmdns.unregisterAllServices();
    isAdvertising = false;
  }

  public synchronized void setDiscoverable(boolean discoverable) throws IOException {
    if (this.discoverable != discoverable) {
      this.discoverable = discoverable;
      if (isAdvertising) {
        logger.info("Re-creating service due to change in discoverability to " + discoverable);
        jmdns.unregisterAllServices();
        registerService();
      }
    }
  }

  public synchronized void setConfigurationIndex(int revision) throws IOException {
    if (this.configurationIndex != revision) {
      this.configurationIndex = revision;
      if (isAdvertising) {
        logger.info("Re-creating service due to change in configuration index to " + revision);
        jmdns.unregisterAllServices();
        registerService();
      }
    }
  }

  private void registerService() throws IOException {
    logger.info("Registering " + SERVICE_TYPE + " on port " + port);
    Map<String, String> props = new HashMap<>();
    props.put("sf", discoverable ? "1" : "0");
    props.put("id", mac);
    props.put("md", label);
    props.put("c#", Integer.toString(configurationIndex));
    props.put("s#", "1");
    props.put("ff", "0");
    props.put("ci", Integer.toString(category));
    jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, label, port, 1, 1, props));
  }
}
