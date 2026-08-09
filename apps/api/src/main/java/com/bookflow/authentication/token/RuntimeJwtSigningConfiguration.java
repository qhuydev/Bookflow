package com.bookflow.authentication.token;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.bookflow.authentication.config.AuthenticationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.*;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

@Configuration(proxyBeanMethods=false) @Profile("!test & !testcontainers")
class RuntimeJwtSigningConfiguration {
 @Bean JwtSigningMaterial jwtSigningMaterial(Environment env, ResourceLoader loader, AuthenticationProperties properties) {
  String kid=required(env,"BOOKFLOW_AUTH_KEY_ID");
  java.security.interfaces.RSAPublicKey publicKey=(java.security.interfaces.RSAPublicKey) publicKey(loader,required(env,"BOOKFLOW_AUTH_PUBLIC_KEY_LOCATION"));
  RSAKey key=new RSAKey.Builder(publicKey).privateKey((java.security.interfaces.RSAPrivateKey) privateKey(loader,required(env,"BOOKFLOW_AUTH_PRIVATE_KEY_LOCATION"))).keyID(kid).build();
  return new JwtSigningMaterial(new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key))), BookFlowJwtDecoderFactory.create(publicKey, properties), kid);
 }
 private String required(Environment e,String n){String v=e.getProperty(n);if(v==null||v.isBlank())throw new IllegalStateException("Missing required runtime authentication key configuration: "+n);return v;}
 private PrivateKey privateKey(ResourceLoader l,String p){try{return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pem(l.getResource(p))));}catch(Exception e){throw new IllegalStateException("Unable to load runtime RSA private key.",e);}}
 private PublicKey publicKey(ResourceLoader l,String p){try{return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pem(l.getResource(p))));}catch(Exception e){throw new IllegalStateException("Unable to load runtime RSA public key.",e);}}
 private byte[] pem(Resource r)throws Exception{return Base64.getDecoder().decode(new String(r.getInputStream().readAllBytes()).replaceAll("-----BEGIN [A-Z ]+-----|-----END [A-Z ]+-----|\\s",""));}
}
