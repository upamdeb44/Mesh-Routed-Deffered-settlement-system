package com.demo.upimesh.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

//this is the over-the-wire format. this is what hops between phones via bluetooth

public class MeshPacket {
    @NotBlank
    private String packetId; //UUID, used for gossip dedup

    @Min(0)
    private int ttl;//no. of hops remaining, decrements upon intermediary

    @NotNull
    private Long createdAt;//epoch millis

    @NotBlank
    private String cipherText;//base64(RSA encrypted AES key + AES-GCM ciphertext)

    public MeshPacket(){}

    //getters and setters
    public String getPacketId(){
        return packetId;
    }
    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public int getTtl() {
        return ttl;
    }
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public Long getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getCipherText() {
        return cipherText;
    }
    public void setCipherText(String cipherText) {
        this.cipherText = cipherText;
    }
}
