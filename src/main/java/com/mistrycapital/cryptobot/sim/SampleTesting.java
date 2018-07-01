package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.util.MCProperties;

import java.util.HashSet;
import java.util.Set;

/** Used to do in/out sample testing. In sample is defined in consecutive day segments */
public class SampleTesting {
	public enum SamplingType {
		IN_SAMPLE,
		OUT_SAMPLE,
		FULL_SAMPLE
	}

	private final SamplingType samplingType;
	private final Set<Integer> validSet;

	public SampleTesting(MCProperties properties) {
		samplingType =
			SamplingType.valueOf(properties.getProperty("sim.sampling.type", "IN_SAMPLE"));
		final int segmentLength = properties.getIntProperty("sim.sampling.segmentDays", 3);

		validSet = new HashSet<>();
		int curDate = (int) (1514764800L / 60 / 60 / 24); // start on 1/1/2018
		for(int i = 0; i < 365; i++) {
			for(int j = 0; j < segmentLength; j++) {
				final boolean inDate = i % 2 == 0;
				switch(samplingType) {
					case IN_SAMPLE:
						if(inDate) validSet.add(curDate);
						break;
					case OUT_SAMPLE:
						if(!inDate) validSet.add(curDate);
						break;
					case FULL_SAMPLE:
						validSet.add(curDate);
				}
				curDate++;
			}
		}
	}

	public boolean isSampleValid(long nanos) {
		int utcDay = (int) (nanos / (24 * 60 * 60 * 1000000000L));
		return validSet.contains(utcDay);
	}

	public SamplingType getSamplingType() {
		return samplingType;
	}
}
