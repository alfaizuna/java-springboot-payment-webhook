package com.alfaizunawebid.baseapp.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RsaKeyConfig {

    @Value("${application.security.jwt.rsa.private-key-location}")
    private Resource privateKeyResource;

    @Value("${application.security.jwt.rsa.public-key-location}")
    private Resource publicKeyResource;

    /**
     * Membaca dan mengonversi file Private Key PEM (PKCS#8) menjadi objek PrivateKey Java.
     */
    @Bean
    public PrivateKey rsaPrivateKey() throws Exception {
        try (InputStream is = privateKeyResource.getInputStream()) {
            String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String privateKeyPEM = keyContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            log.info("RSA Private Key berhasil dimuat dari: {}", privateKeyResource.getDescription());
            return keyFactory.generatePrivate(keySpec);
        }
    }

    /**
     * Membaca dan mengonversi file Public Key PEM (X.509) menjadi objek PublicKey Java.
     */
    @Bean
    public PublicKey rsaPublicKey() throws Exception {
        try (InputStream is = publicKeyResource.getInputStream()) {
            String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String publicKeyPEM = keyContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            log.info("RSA Public Key berhasil dimuat dari: {}", publicKeyResource.getDescription());
            return keyFactory.generatePublic(keySpec);
        }
    }
}
