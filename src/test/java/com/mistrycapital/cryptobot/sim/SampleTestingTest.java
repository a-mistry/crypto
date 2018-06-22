package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.util.MCProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SampleTestingTest {

	@Test
	void shouldGetValidSamples() {
		MCProperties properties = new MCProperties();
		properties.put("sim.sampling.type", "IN_SAMPLE");
		properties.put("sim.sampling.segmentDays", "5");
		SampleTesting sampleTesting = new SampleTesting(properties);
		assertTrue(sampleTesting.isSampleValid(1514764800*1000000000L)); // 1/1/2018 00:00:00
		assertTrue(sampleTesting.isSampleValid(1515142222*1000000000L)); // 1/5/2018 08:50:22
		assertFalse(sampleTesting.isSampleValid(1515196800*1000000000L)); // 1/6/2018 00:00:00
		assertFalse(sampleTesting.isSampleValid(1515268800*1000000000L)); // 1/6/2018 20:00:00

		properties.put("sim.sampling.type", "OUT_SAMPLE");
		properties.put("sim.sampling.segmentDays", "5");
		sampleTesting = new SampleTesting(properties);
		assertFalse(sampleTesting.isSampleValid(1514764800*1000000000L)); // 1/1/2018 00:00:00
		assertFalse(sampleTesting.isSampleValid(1515142222*1000000000L)); // 1/5/2018 08:50:22
		assertTrue(sampleTesting.isSampleValid(1515196800*1000000000L)); // 1/6/2018 00:00:00
		assertTrue(sampleTesting.isSampleValid(1515268800*1000000000L)); // 1/6/2018 20:00:00

		properties.put("sim.sampling.type", "FULL_SAMPLE");
		properties.put("sim.sampling.segmentDays", "5");
		sampleTesting = new SampleTesting(properties);
		assertTrue(sampleTesting.isSampleValid(1514764800*1000000000L)); // 1/1/2018 00:00:00
		assertTrue(sampleTesting.isSampleValid(1515142222*1000000000L)); // 1/5/2018 08:50:22
		assertTrue(sampleTesting.isSampleValid(1515196800*1000000000L)); // 1/6/2018 00:00:00
		assertTrue(sampleTesting.isSampleValid(1515268800*1000000000L)); // 1/6/2018 20:00:00
	}
}