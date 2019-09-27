package io.github.hapjava.impl.characteristics.lightbulb;

import io.github.hapjava.HomekitCharacteristicChangeCallback;
import io.github.hapjava.accessories.ColorfulLightbulb;
import io.github.hapjava.characteristics.EventableCharacteristic;
import io.github.hapjava.characteristics.FloatCharacteristic;
import io.github.hapjava.impl.ExceptionalConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HueCharacteristic extends FloatCharacteristic implements EventableCharacteristic {

  private final Supplier<CompletableFuture<Double>> getter;
  private final ExceptionalConsumer<Double> setter;
  private final Consumer<HomekitCharacteristicChangeCallback> subscriber;
  private final Runnable unsubscriber;

  public HueCharacteristic(
      Supplier<CompletableFuture<Double>> getter,
      ExceptionalConsumer<Double> setter,
      Consumer<HomekitCharacteristicChangeCallback> subscriber,
      Runnable unsubscriber) {
    super(
        "00000013-0000-1000-8000-0026BB765291",
        true,
        true,
        "Adjust hue of the light",
        0,
        360,
        1,
        "arcdegrees");
    this.getter = getter;
    this.setter = setter;
    this.subscriber = subscriber;
    this.unsubscriber = unsubscriber;
  }

  public HueCharacteristic(ColorfulLightbulb lightbulb) {
    this(
      () -> lightbulb.getHue(),
      v -> lightbulb.setHue(v),
      c -> lightbulb.subscribeHue(c),
      () -> lightbulb.unsubscribeHue()
    );
  }

  @Override
  protected CompletableFuture<Double> getDoubleValue() {
    return getter.get();
  }

  @Override
  protected void setValue(Double value) throws Exception {
    setter.accept(value);
  }

  @Override
  public void subscribe(HomekitCharacteristicChangeCallback callback) {
    subscriber.accept(callback);
  }

  @Override
  public void unsubscribe() {
    unsubscriber.run();
  }
}
