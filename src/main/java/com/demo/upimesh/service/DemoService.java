package com.demo.upimesh.service;


import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class DemoService {
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    @Autowired private AccountRepository accounts;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;

    @PostConstruct
    public void seedAccounts(){
        if(accounts.count()==0){
            accounts.save(new Account("jake@demo","Jake", new BigDecimal(5000.00)));
            accounts.save(new Account("amy@demo","Amy", new BigDecimal(1000.00)));
            accounts.save(new Account("rosa@demo","Roza", new BigDecimal(2500.00)));
            accounts.save(new Account("charles@demo","Charles", new BigDecimal(500.00)));
            accounts.save(new Account("terrance@demo","Terrance", new BigDecimal(5000.00)));
            log.info("Seeded 5 demo accounts");
        }
    }

    private String sha256Hex(String input) throws Exception{
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b: hash) hex.append(String.format("%02x",b));
        return hex.toString();
    }

    //simulates the sender's phone
    public MeshPacket createPacket(String senderVpa, String receiverVpa, BigDecimal amount, String pin, int ttl) throws Exception{
        PaymentInstruction instruction = new PaymentInstruction(
                senderVpa,
                receiverVpa,
                amount,
                sha256Hex(pin),
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli()
        );

        String cipherText = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCipherText(cipherText);
        return packet;
    }
}
