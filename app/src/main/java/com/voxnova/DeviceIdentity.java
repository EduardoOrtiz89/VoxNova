package com.voxnova;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Manages device identity for OpenClaw protocol v3.
 * Uses Ed25519 keys via BouncyCastle for signing challenges.
 */
public class DeviceIdentity {
    private static final String PREFS_NAME = "voxnova_device";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_PRIVATE_KEY = "private_key";
    
    private final SharedPreferences prefs;
    private String deviceId;
    private byte[] publicKeyRaw;  // 32 bytes raw Ed25519 public key
    private byte[] privateKeyRaw; // 32 bytes raw Ed25519 private key seed
    
    public DeviceIdentity(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadOrGenerateIdentity();
    }
    
    private void loadOrGenerateIdentity() {
        String storedDeviceId = prefs.getString(KEY_DEVICE_ID, null);
        String storedPublicKey = prefs.getString(KEY_PUBLIC_KEY, null);
        String storedPrivateKey = prefs.getString(KEY_PRIVATE_KEY, null);
        
        if (storedDeviceId != null && storedPublicKey != null && storedPrivateKey != null) {
            try {
                this.deviceId = storedDeviceId;
                this.publicKeyRaw = Base64.decode(storedPublicKey, Base64.NO_WRAP);
                this.privateKeyRaw = Base64.decode(storedPrivateKey, Base64.NO_WRAP);
                
                // Verify the stored keys are valid (32 bytes each)
                if (publicKeyRaw.length == 32 && privateKeyRaw.length == 32) {
                    DebugLogger.log("DeviceIdentity loaded (Ed25519): " + deviceId.substring(0, 16) + "...");
                    return;
                }
            } catch (Exception e) {
                DebugLogger.error("Failed to load identity, regenerating: " + e.getMessage());
            }
        }
        
        generateNewIdentity();
    }
    
    private void generateNewIdentity() {
        try {
            // Generate Ed25519 keypair using BouncyCastle
            Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
            keyGen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
            AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
            
            Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
            Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
            
            this.publicKeyRaw = publicKey.getEncoded();  // 32 bytes
            this.privateKeyRaw = privateKey.getEncoded(); // 32 bytes (seed)
            
            // Device ID is SHA-256 hash of raw public key (hex encoded)
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKeyRaw);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            this.deviceId = hexString.toString();
            
            // Store in preferences
            String publicKeyB64 = Base64.encodeToString(publicKeyRaw, Base64.NO_WRAP);
            String privateKeyB64 = Base64.encodeToString(privateKeyRaw, Base64.NO_WRAP);
            
            prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_PUBLIC_KEY, publicKeyB64)
                .putString(KEY_PRIVATE_KEY, privateKeyB64)
                .apply();
            
            DebugLogger.success("DeviceIdentity generated (Ed25519): " + deviceId.substring(0, 16) + "...");
            
        } catch (Exception e) {
            DebugLogger.error("Failed to generate identity: " + e.getMessage());
            throw new RuntimeException("Failed to generate device identity", e);
        }
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    /**
     * Returns the raw 32-byte public key encoded in base64url format.
     * This is what OpenClaw expects.
     */
    public String getPublicKeyBase64Url() {
        return base64UrlEncode(publicKeyRaw);
    }
    
    /**
     * Sign a nonce for the connect.challenge using Ed25519.
     * Returns base64url-encoded signature.
     */
    public String signNonce(String nonce) {
        try {
            // Reconstruct private key from stored seed
            Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(privateKeyRaw, 0);
            
            // Sign the nonce
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, privateKey);
            byte[] nonceBytes = nonce.getBytes("UTF-8");
            signer.update(nonceBytes, 0, nonceBytes.length);
            byte[] signature = signer.generateSignature();
            
            return base64UrlEncode(signature);
        } catch (Exception e) {
            DebugLogger.error("Failed to sign nonce: " + e.getMessage());
            throw new RuntimeException("Failed to sign nonce", e);
        }
    }
    
    /**
     * Encode bytes to base64url (no padding, URL-safe).
     */
    private static String base64UrlEncode(byte[] data) {
        String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
        return base64
            .replace('+', '-')
            .replace('/', '_')
            .replaceAll("=+$", "");
    }
}
