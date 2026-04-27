package com.demo.upimesh.service;


import com.demo.upimesh.model.MeshPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MeshSimulatorService {
    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);
    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    public MeshSimulatorService(){
        seedDefaultDevices();
    }

    private void seedDefaultDevices(){
        devices.put("phone-jake", new VirtualDevice("phone-jake", false));
        devices.put("phone-amy", new VirtualDevice("phone-amy",false));
        devices.put("phone-rosa", new VirtualDevice("phone-rosa",false));
        devices.put("phone-charles", new VirtualDevice("phone-charles",true));
        devices.put("phone-terrance", new VirtualDevice("phone-terrance",false));
        devices.put("phone-bridge", new VirtualDevice("phone-bridge", true));
    }

    public Collection<VirtualDevice> getDevices(){
        return devices.values();
    }
    public VirtualDevice getDevice(String id){
        return devices.get(id);
    }

    public void inject(String senderDeviceId, MeshPacket packet){
        VirtualDevice sender = devices.get(senderDeviceId);
        if(sender == null) throw new IllegalArgumentException("Unknown device"+ senderDeviceId);
        sender.hold(packet);
        log.info("Packet {} injected at {} (TTL={})", packet.getPacketId().substring(0,8),senderDeviceId,packet.getTtl());
    }

    //one round of gossip
    public GossipResult gossipOnce(){
        int transfers= 0;
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());

        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for(VirtualDevice d: deviceList){
            snapshot.put(d.getDeviceId(), new ArrayList<>(d.getHeldPackets()));
        }

        for (VirtualDevice src : deviceList){
            for(MeshPacket pkt : snapshot.get(src.getDeviceId())){
                if(pkt.getTtl() <= 0) continue;
                for(VirtualDevice dst : deviceList){
                    if (dst == src) continue;
                    if(dst.holds(pkt.getPacketId())) continue;
                    MeshPacket copy = new MeshPacket();
                    copy.setPacketId(pkt.getPacketId());
                    copy.setTtl(pkt.getTtl() - 1);
                    copy.setCreatedAt(pkt.getCreatedAt());
                    copy.setCipherText(pkt.getCipherText());
                    dst.hold(copy);
                    transfers++;
                }
            }
        }
        log.info("Gossip round complete: {} packet transfers", transfers);
        return new GossipResult(transfers, snapshotMap());
    }

    public  Map<String, Integer> snapshotMap(){
        Map<String, Integer> m = new LinkedHashMap<>();
        for(VirtualDevice d: devices.values()){
            m.put(d.getDeviceId(), d.packetCount());
        }
        return m;
    }

    // return all packets held by devices with internet
    public List<BridgeUpload> collectBridgeUploads(){
        List<BridgeUpload> out = new ArrayList<>();
        for(VirtualDevice d : devices.values()){
            if(!d.hasInternet()) continue;
            for(MeshPacket pkt : d.getHeldPackets()){
                out.add(new BridgeUpload(d.getDeviceId(),pkt));
            }
        }
        return out;
    }

    public void resetMesh(){
        devices.values().forEach(VirtualDevice::clear);
    }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts){}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet){}
}
