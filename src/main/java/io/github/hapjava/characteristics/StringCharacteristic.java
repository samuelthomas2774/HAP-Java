package io.github.hapjava.characteristics;

import java.util.concurrent.CompletableFuture;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

/**
 * A characteristic that provides an immutable String value.
 *
 * @author Andy Lintner
 */
public abstract class StringCharacteristic extends BaseCharacteristic<String> {

  private static final int MAX_LEN = 255;

  /**
   * Default constructor
   *
   * @param type a string containing a UUID that indicates the type of characteristic. Apple defines
   *     a set of these, however implementors can create their own as well.
   * @param description a description of the characteristic to be passed to the consuming device.
   * @param value the value of the static string.
   */
  public StringCharacteristic(String type, String description) {
    super(type, "string", true, true, description);
  }

  /** {@inheritDoc} */
  @Override
  protected CompletableFuture<JsonObjectBuilder> makeBuilder(int iid) {
    return super.makeBuilder(iid).thenApply(builder -> builder.add("maxLen", MAX_LEN));
  }

  /** {@inheritDoc} */
  @Override
  public String convert(JsonValue jsonValue) {
    return ((JsonString) jsonValue).getString();
  }

  /** {@inheritDoc} */
  @Override
  protected String getDefault() {
    return "Unknown";
  }
}
