import Foundation
import CryptoKit

// MARK: - End-to-end crypto for device-link (X25519 -> HKDF-SHA256 -> AES-GCM).
// The server only relays pubkey + the {epk, sealed} blob — never the plaintext token.
enum DeviceLinkCrypto {
    private static let salt = Data("voicedrop-device-link/v1".utf8)
    private static let info = Data("anon-token".utf8)

    // New device: ephemeral keypair; pubB64 is sent in /agent/link/start.
    static func newKeypair() -> (priv: Curve25519.KeyAgreement.PrivateKey, pubB64: String) {
        let priv = Curve25519.KeyAgreement.PrivateKey()
        return (priv, b64url(priv.publicKey.rawRepresentation))
    }

    // New device: decrypt the blob from the old device into the anon_… token.
    static func decrypt(epkB64: String, sealedB64: String, priv: Curve25519.KeyAgreement.PrivateKey) throws -> String {
        let epk = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: b64urlDecode(epkB64))
        let shared = try priv.sharedSecretFromKeyAgreement(with: epk)
        let key = shared.hkdfDerivedSymmetricKey(using: SHA256.self, salt: salt, sharedInfo: info, outputByteCount: 32)
        let box = try AES.GCM.SealedBox(combined: b64urlDecode(sealedB64))
        return String(decoding: try AES.GCM.open(box, using: key), as: UTF8.self)
    }

    // Old device: encrypt its anon_… token to the new device's public key.
    static func encrypt(token: String, toPubB64 pub: String) throws -> (epkB64: String, sealedB64: String) {
        let eph = Curve25519.KeyAgreement.PrivateKey()
        let newPub = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: b64urlDecode(pub))
        let shared = try eph.sharedSecretFromKeyAgreement(with: newPub)
        let key = shared.hkdfDerivedSymmetricKey(using: SHA256.self, salt: salt, sharedInfo: info, outputByteCount: 32)
        let sealed = try AES.GCM.seal(Data(token.utf8), using: key)
        return (b64url(eph.publicKey.rawRepresentation), b64url(sealed.combined!))
    }

    // base64url helpers (no padding)
    static func b64url(_ d: Data) -> String {
        d.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
    static func b64urlDecode(_ s: String) -> Data {
        var t = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while t.count % 4 != 0 { t += "=" }
        return Data(base64Encoded: t) ?? Data()
    }

    #if DEBUG
    // One-shot round-trip self-check; call from app launch in DEBUG, confirm console, then remove.
    static func selfTest() {
        let (priv, pub) = newKeypair()
        do {
            let (epk, sealed) = try encrypt(token: "anon_roundtrip_demo", toPubB64: pub)
            let out = try decrypt(epkB64: epk, sealedB64: sealed, priv: priv)
            print("DeviceLinkCrypto.selfTest:", out == "anon_roundtrip_demo" ? "OK" : "FAIL")
        } catch { print("DeviceLinkCrypto.selfTest ERROR:", error) }
    }
    #endif
}
