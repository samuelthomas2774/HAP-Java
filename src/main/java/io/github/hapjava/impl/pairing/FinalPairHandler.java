package io.github.hapjava.impl.pairing;

import io.github.hapjava.HomekitAdvertiser;
import io.github.hapjava.HomekitAuthInfo;
import io.github.hapjava.impl.crypto.*;
import io.github.hapjava.impl.http.HttpResponse;
import io.github.hapjava.impl.pairing.PairSetupRequest.Stage3Request;
import io.github.hapjava.impl.pairing.TypeLengthValueUtils.DecodeResult;
import io.github.hapjava.impl.pairing.TypeLengthValueUtils.Encoder;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

class FinalPairHandler {

  private final byte[] k;
  private final HomekitAuthInfo authInfo;
  private final HomekitAdvertiser advertiser;

  private byte[] hkdf_enc_key;

  public FinalPairHandler(byte[] k, HomekitAuthInfo authInfo, HomekitAdvertiser advertiser) {
    this.k = k;
    this.authInfo = authInfo;
    this.advertiser = advertiser;
  }

  public HttpResponse handle(PairSetupRequest req) throws Exception {
    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA512Digest());
    hkdf.init(
        new HKDFParameters(
            k,
            "Pair-Setup-Encrypt-Salt".getBytes(StandardCharsets.UTF_8),
            "Pair-Setup-Encrypt-Info".getBytes(StandardCharsets.UTF_8)));
    byte[] okm = hkdf_enc_key = new byte[32];
    hkdf.generateBytes(okm, 0, 32);

    return decrypt((Stage3Request) req, okm);
  }

  private HttpResponse decrypt(Stage3Request req, byte[] key) throws Exception {
    ChachaDecoder chacha = new ChachaDecoder(key, "PS-Msg05".getBytes(StandardCharsets.UTF_8));
    byte[] plaintext = chacha.decodeCiphertext(req.getAuthTagData(), req.getMessageData());

    DecodeResult d = TypeLengthValueUtils.decode(plaintext);
    byte[] username = d.getBytes(MessageType.USERNAME);
    byte[] ltpk = d.getBytes(MessageType.PUBLIC_KEY);
    byte[] proof = d.getBytes(MessageType.SIGNATURE);
    return createUser(username, ltpk, proof);
  }

  private HttpResponse createUser(byte[] username, byte[] ltpk, byte[] proof) throws Exception {
    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA512Digest());
    hkdf.init(
        new HKDFParameters(
            k,
            "Pair-Setup-Controller-Sign-Salt".getBytes(StandardCharsets.UTF_8),
            "Pair-Setup-Controller-Sign-Info".getBytes(StandardCharsets.UTF_8)));
    byte[] okm = new byte[32];
    hkdf.generateBytes(okm, 0, 32);

    byte[] completeData = ByteUtils.joinBytes(okm, username, ltpk);

    if (!new EdsaVerifier(ltpk).verify(completeData, proof)) {
      throw new Exception("Invalid signature");
    }
    authInfo.createUser(authInfo.getMac() + new String(username, StandardCharsets.UTF_8), ltpk);
    advertiser.setDiscoverable(false);
    return createResponse();
  }

  private HttpResponse createResponse() throws Exception {
    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA512Digest());
    hkdf.init(
        new HKDFParameters(
            k,
            "Pair-Setup-Accessory-Sign-Salt".getBytes(StandardCharsets.UTF_8),
            "Pair-Setup-Accessory-Sign-Info".getBytes(StandardCharsets.UTF_8)));
    byte[] okm = new byte[32];
    hkdf.generateBytes(okm, 0, 32);

    EdsaSigner signer = new EdsaSigner(authInfo.getPrivateKey());

    byte[] material =
        ByteUtils.joinBytes(
            okm, authInfo.getMac().getBytes(StandardCharsets.UTF_8), signer.getPublicKey());

    byte[] proof = signer.sign(material);

    Encoder encoder = TypeLengthValueUtils.getEncoder();
    encoder.add(MessageType.USERNAME, authInfo.getMac().getBytes(StandardCharsets.UTF_8));
    encoder.add(MessageType.PUBLIC_KEY, signer.getPublicKey());
    encoder.add(MessageType.SIGNATURE, proof);
    byte[] plaintext = encoder.toByteArray();

    ChachaEncoder chacha =
        new ChachaEncoder(hkdf_enc_key, "PS-Msg06".getBytes(StandardCharsets.UTF_8));
    byte[] ciphertext = chacha.encodeCiphertext(plaintext);

    encoder = TypeLengthValueUtils.getEncoder();
    encoder.add(MessageType.STATE, (short) 6);
    encoder.add(MessageType.ENCRYPTED_DATA, ciphertext);

    return new PairingResponse(encoder.toByteArray());
  }
}
