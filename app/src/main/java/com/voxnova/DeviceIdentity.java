package com.voxnova;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.MessageDigest;

/**
 * Manages device identity for OpenClaw protocol v3.
 * Generates and stores a stable device ID and keypair for signing challenges.
 */
public class DeviceIdentity {
    private static final String PREFS_NAME = "voxnova_device";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_PRIVATE_KEY = "private_key";
    
    private final SharedPreferences prefs;
    private String deviceId;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    
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
                
                byte[] publicBytes = Base64.decode(storedPublicKey, Base64.NO_WRAP);
                byte[] privateBytes = Base64.decode(storedPrivateKey, Base64.NO_WRAP);
                
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
                this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
                
                DebugLogger.log("DeviceIdentity loaded: " + deviceId.substring(0, 16) + "...");
                return;
            } catch (Exception e) {
                DebugLogger.error("Failed to load identity, regenerating: " + e.getMessage());
            }
        }
        
        generateNewIdentity();
    }
    
    private void generateNewIdentity() {
        try {
            // Generate EC keypair (P-256)
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256);
            KeyPair keyPair = keyGen.generateKeyPair();
            
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            
            // Device ID is SHA-256 hash of public key (hex encoded)
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            this.deviceId = hexString.toString();
            
            // Store in preferences
            String publicKeyB64 = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
            String privateKeyB64 = Base64.encodeToString(privateKey.getEncoded(), Base64.NO_WRAP);
            
            prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_PUBLIC_KEY, publicKeyB64)
                .putString(KEY_PRIVATE_KEY, privateKeyB64)
                .apply();
            
            DebugLogger.success("DeviceIdentity generated: " + deviceId.substring(0, 16) + "...");
            
        } catch (Exception e) {
            DebugLogger.error("Failed to generate identity: " + e.getMessage());
            throw new RuntimeException("Failed to generate device identity", e);
        }
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public String getPublicKeyBase64() {
        return Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }
    
    /**
     * Sign a nonce for the connect.challenge.
     * Returns base64-encoded signature.
     */
    public String signNonce(String nonce) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(nonce.getBytes("UTF-8"));
            byte[] signatureBytes = signature.sign();
            return Base64.encodeToString(signatureBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            DebugLogger.error("Failed to sign nonce: " + e.getMessage());
            throw new RuntimeException("Failed to sign nonce", e);
        }
    }
}
