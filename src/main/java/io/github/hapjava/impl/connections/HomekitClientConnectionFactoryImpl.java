package io.github.hapjava.impl.connections;

import io.github.hapjava.HomekitAdvertiser;
import io.github.hapjava.HomekitAuthInfo;
import io.github.hapjava.impl.HomekitRegistry;
import io.github.hapjava.impl.http.HomekitClientConnection;
import io.github.hapjava.impl.http.HomekitClientConnectionFactory;
import io.github.hapjava.impl.http.HttpResponse;
import java.util.function.Consumer;

public class HomekitClientConnectionFactoryImpl implements HomekitClientConnectionFactory {

  private final HomekitAuthInfo authInfo;
  private final HomekitRegistry registry;
  private final SubscriptionManager subscriptions;
  private final HomekitAdvertiser advertiser;

  public HomekitClientConnectionFactoryImpl(
      HomekitAuthInfo authInfo,
      HomekitRegistry registry,
      SubscriptionManager subscriptions,
      HomekitAdvertiser advertiser) {
    this.registry = registry;
    this.authInfo = authInfo;
    this.subscriptions = subscriptions;
    this.advertiser = advertiser;
  }

  @Override
  public HomekitClientConnection createConnection(Consumer<HttpResponse> outOfBandMessageCallback) {
    return new ConnectionImpl(
        authInfo, registry, outOfBandMessageCallback, subscriptions, advertiser);
  }
}
