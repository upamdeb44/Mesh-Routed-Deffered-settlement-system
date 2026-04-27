package com.demo.upimesh.service;


import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/*
orchestrating a full server-side pipeline for one inbound packet from a bridge node
1. hash the ciphertext
2. try to claim that hash via the idempotency cache
        - of already claimed ths is a duplicate
3. decrypt the ciphertext with the server's priavte key
        - if decryption fails then file is tampered or junk. rejected
4. check freshness - reject if signedAt val is too old.
5. handoff to settlementservice for the actual debit/credit
*/
@Service
public class BridgeIngestionService {
    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);
    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private SettlementService settlement;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public record IngestResult(String outcome,String packetHash, String reason, Long transactionId){
        public static IngestResult settled(String hash, Transaction tx){
            return new IngestResult("SETTLED", hash,null, tx.getId());
        }
        public static IngestResult duplicate(String hash){
            return new IngestResult("DUPLICATE_DROPPED", hash,null,null);
        }
        public static IngestResult invalid(String hash, String reason){
            return new IngestResult("INVALID", hash,reason,null);
        }
    }

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount){
        try{
            String packetHash = crypto.hashCiphertext(packet.getCipherText());

            //idempotency gate
            if(!idempotency.claim(packetHash)){
                log.info("Duplicate packet {} from bridge {} - dropped", packetHash.substring(0,12) + "...", bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }

            //decrypt
            PaymentInstruction instruction;
            try{
                instruction = crypto.decrypt(packet.getCipherText());
            } catch (Exception e){
                log.warn("Decryption failed for packet {}: {}", packetHash.substring(0,12) + "...",e.getMessage());
                return IngestResult.invalid(packetHash,"decryption_failed");
            }

            // check for freshness
            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt())/1000;
            if( ageSeconds>maxAgeSeconds){
                log.warn("Packet {} too old({}s), Rejected", packetHash.substring(0,12)+"...",ageSeconds);
                return IngestResult.invalid(packetHash,"old_packet");
            }
            if(ageSeconds< -300){
                return IngestResult.invalid(packetHash,"future_dated");
            }

            //settle
            Transaction tx = settlement.settle(instruction,packetHash,bridgeNodeId,hopCount);
            return IngestResult.settled(packetHash,tx);

        } catch (Exception e){
            log.error("Ingestion error: {}",e.getMessage(),e);
            return IngestResult.invalid("?","internal_error" + e.getMessage());
        }
    }
}
