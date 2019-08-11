package io.github.hapjava;

public interface HomekitAdvertiser {
  public void advertise(String label, String mac, int port, int configurationIndex) throws Exception;

  public void stop();

  public void setDiscoverable(boolean discoverable) throws Exception;

  public void setConfigurationIndex(int revision) throws Exception;
}
