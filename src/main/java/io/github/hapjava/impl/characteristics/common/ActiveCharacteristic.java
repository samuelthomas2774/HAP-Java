package io.github.hapjava.impl.characteristics.common;

import io.github.hapjava.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.EnumCharacteristic;
import io.github.hapjava.characteristics.EventableCharacteristic;
import io.github.hapjava.impl.ExceptionalConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ActiveCharacteristic extends EnumCharacteristic implements EventableCharacteristic {

    public static final int INACTIVE = 0;
    public static final int ACTIVE = 1;

  private final Supplier<CompletableFuture<Integer>> getter;
  private final ExceptionalConsumer<Integer> setter;
  private final Consumer<HomekitCharacteristicChangeCallback> subscriber;
  private final Runnable unsubscriber;

  public ActiveCharacteristic(
      Supplier<CompletableFuture<Integer>> getter,
      ExceptionalConsumer<Integer> setter,
      Consumer<HomekitCharacteristicChangeCallback> subscriber,
      Runnable unsubscriber) {
    super("000000B0-0000-1000-8000-0026BB765291", true, true, "Active", 1);
    this.getter = getter;
    this.setter = setter;
    this.subscriber = subscriber;
    this.unsubscriber = unsubscriber;
  }

  @Override
  protected CompletableFuture<Integer> getValue() {
    return getter.get();
  }

  @Override
  protected void setValue(Integer value) throws Exception {
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
