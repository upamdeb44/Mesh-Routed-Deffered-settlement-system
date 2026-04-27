package com.demo.upimesh;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The killer test: simulates the "three bridges deliver at the same instant"
 * scenario the user explicitly cared about.
 */
@SpringBootTest
class IdempotencyConcurrencyTest {

	@Autowired private DemoService demoService;
	@Autowired private BridgeIngestionService bridge;
	@Autowired private IdempotencyService idempotency;
	@Autowired private AccountRepository accounts;
	@Autowired private HybridCryptoService crypto;
	@Autowired private ServerKeyHolder serverKey;

	@BeforeEach
	void clear() {
		idempotency.clear();
	}

	@Test
	void singlePacketDeliveredByThreeBridgesSettlesExactlyOnce() throws Exception {
		// Capture starting balances
		BigDecimal senderBefore = accounts.findById("jake@demo").orElseThrow().getBalance();
		BigDecimal receiverBefore = accounts.findById("amy@demo").orElseThrow().getBalance();

		// One packet, but we'll deliver it from 3 "bridges" simultaneously
		MeshPacket packet = demoService.createPacket(
				"jake@demo", "amy@demo", new BigDecimal("100.00"), "1234", 5);

		ExecutorService pool = Executors.newFixedThreadPool(3);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger settled = new AtomicInteger();
		AtomicInteger duplicates = new AtomicInteger();

		Future<?>[] futures = new Future[3];
		for (int i = 0; i < 3; i++) {
			final String node = "bridge-" + i;
			futures[i] = pool.submit(() -> {
				try {
					start.await();
					BridgeIngestionService.IngestResult r = bridge.ingest(packet, node, 3);
					if ("SETTLED".equals(r.outcome())) settled.incrementAndGet();
					else if ("DUPLICATE_DROPPED".equals(r.outcome())) duplicates.incrementAndGet();
				} catch (Exception e) { throw new RuntimeException(e); }
			});
		}

		start.countDown();
		for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
		pool.shutdown();

		assertEquals(1, settled.get(), "exactly one bridge should settle");
		assertEquals(2, duplicates.get(), "the other two should be duplicates");

		// Balance moved exactly once
		BigDecimal senderAfter = accounts.findById("jake@demo").orElseThrow().getBalance();
		BigDecimal receiverAfter = accounts.findById("amy@demo").orElseThrow().getBalance();
		assertEquals(senderBefore.subtract(new BigDecimal("100.00")), senderAfter);
		assertEquals(receiverBefore.add(new BigDecimal("100.00")), receiverAfter);
	}

	@Test
	void tamperedCiphertextIsRejected() throws Exception {
		MeshPacket packet = demoService.createPacket(
				"jake@demo", "amy@demo", new BigDecimal("50.00"), "1234", 5);


		char[] chars = packet.getCipherText().toCharArray();
		chars[chars.length / 2] = chars[chars.length / 2] == 'A' ? 'B' : 'A';
		packet.setCipherText(new String(chars));

		BridgeIngestionService.IngestResult r = bridge.ingest(packet, "bridge-x", 1);
		assertEquals("INVALID", r.outcome());
	}

	@Test
	void encryptDecryptRoundTrip() throws Exception {
		PaymentInstruction original = new PaymentInstruction(
				"jake@demo", "amy@demo", new BigDecimal("123.45"),
				"abcdef", "nonce-1", System.currentTimeMillis());

		String ct = crypto.encrypt(original, serverKey.getPublicKey());
		PaymentInstruction decrypted = crypto.decrypt(ct);

		assertEquals(original.getSenderVpa(), decrypted.getSenderVpa());
		assertEquals(original.getReceiverVpa(), decrypted.getReceiverVpa());
		assertEquals(0, original.getAmount().compareTo(decrypted.getAmount()));
		assertEquals(original.getNonce(), decrypted.getNonce());
	}
}