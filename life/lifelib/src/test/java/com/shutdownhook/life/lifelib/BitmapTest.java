//
// BITMAPTEST.JAVA
//

package com.shutdownhook.life.lifelib;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class BitmapTest
{
	@Test
	public void randomRoundTrip() throws Exception {

		Bitmap bits = new Bitmap(12,12);
		assertBits(bits, 0, new long[] { 0, 0, 0 });
		bits.randomize();

		File file = File.createTempFile("xxx", null);
		file.deleteOnExit();
		Serializers.toFile(bits, file);

		Bitmap loadedBits = Serializers.fromFile(file);
		assertBits(loadedBits, bits.getTrueCount(), bits.longs);

		Serializers.toFile(loadedBits, file, new Serializers.ExpandedSerializer());
		Bitmap expandedBits = Serializers.fromFile(file, new Serializers.ExpandedSerializer());
		assertBits(expandedBits, bits.getTrueCount(), bits.longs);
	}

	@Test
	public void oneLong() throws Exception {
		Bitmap bits = new Bitmap(8, 8);
		assertBits(bits, 0, new long[] { 0L });
		bits.set(0, 0, true);  assertBits(bits, 1, new long[] { 1L });
		bits.set(1, 0, true);  assertBits(bits, 2, new long[] { 3L });
		bits.set(1, 1, true);  assertBits(bits, 3, new long[] { 515L });
		bits.set(0, 1, true);  assertBits(bits, 4, new long[] { 771L });
		bits.set(0, 0, false); assertBits(bits, 3, new long[] { 770L });
		bits.set(7, 7, false); assertBits(bits, 3, new long[] { 770L });
	}

	@Test
	public void small() throws Exception {
		Bitmap bits = new Bitmap(2, 2);
		assertBits(bits, 0, new long[] { 0L });
		bits.set(0, 0, true);  assertBits(bits, 1, new long[] { 1L });
		bits.set(1, 0, true);  assertBits(bits, 2, new long[] { 3L });
		bits.set(1, 1, true);  assertBits(bits, 3, new long[] { 11L });
		bits.set(0, 1, true);  assertBits(bits, 4, new long[] { 15L });
		bits.set(0, 0, false);  assertBits(bits, 3, new long[] { 14L });
	}

	@Test
	public void smallest() throws Exception {
		Bitmap bits = new Bitmap(1, 1);
		assertBits(bits, 0, new long[] { 0L });
		bits.set(0, 0, true);  assertBits(bits, 1, new long[] { 1L });
	}

	@Test
	public void seven() throws Exception {
		Bitmap bits = new Bitmap(512, 1);
		bits.set(7, 0, true);
		Assert.assertTrue(bits.get(7, 0));
	}

	// +---------+
	// | Helpers |
	// +---------+

	private void printBits(Bitmap bits) {

		StringBuilder sb = new StringBuilder();
		sb.append(">>> ");
		sb.append(String.format("dx=%d, dy=%d\n",
								bits.getDx(), bits.getDy()));

		for (int i = 0; i < bits.longs.length; ++i) {
			
			String bin = Long.toBinaryString(bits.longs[i]).replace("0", ".");
			if (bin.length() < Long.SIZE) {
				bin = ".".repeat(Long.SIZE - bin.length()) + bin;
			}
			
			sb.append(String.format("%s (%d)\n", bin, bits.longs[i]));
		}

		System.out.println(sb.toString());
	}

	private void assertBits(Bitmap bits,
							int expectedTrueCount,
							long[] expectedLongs) throws Exception {

		Assert.assertEquals(expectedTrueCount, bits.getTrueCount());
		Assert.assertEquals(expectedLongs.length, bits.longs.length);

		int actualTrueCount = 0;
		for (int i = 0; i < expectedLongs.length; ++i) {
			Assert.assertEquals(expectedLongs[i], bits.longs[i]);
			actualTrueCount += Long.bitCount(bits.longs[i]);
		}

		Assert.assertEquals(actualTrueCount, bits.getTrueCount());
	}
}
