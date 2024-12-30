//
// BITMAP.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.Random;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

public class Bitmap
{
	// +-------+
	// | Setup |
	// +-------+

	public Bitmap(int dx, int dy) throws IllegalArgumentException {
		this(dx, dy, null);
	}

	protected Bitmap(int dx, int dy, long[] longs) throws IllegalArgumentException {

		if (dx < 1 || dy < 1) {
			String msg = String.format("Invalid dimensions %d,%d", dx, dy);
			throw new IllegalArgumentException(msg);
		}
			
		this.dx = dx;
		this.dy = dy;

		if (longs != null) {
			this.longs = longs;
			return;
		}
		
		this.longs = new long[getLongsNeeded(dx, dy)];
	}

	// +-------------------+
	// | Simple Properties |
	// +-------------------+

	public int getDx() { return(dx); }
	public int getDy() { return(dy); }

	public long[] getAsDNA() { return(longs); }
	
	// +------------------+
	// | get / set / fill |
	// +------------------+

	public boolean get(int x, int y) {
		
		int bitIndex = getBitIndex(x, y);
		int ilong = bitIndex / Long.SIZE;
		int ibit = bitIndex % Long.SIZE;

		return((longs[ilong] & (1L << ibit)) != 0);
	}

	public void set(int x, int y, boolean val) {

		
		int bitIndex = getBitIndex(x, y);
		int ilong = bitIndex / Long.SIZE;
		int ibit = bitIndex % Long.SIZE;

		if (val) { longs[ilong] |= (1L << ibit); }
		else { longs[ilong] &= ~(1L << ibit); }
	}

	public void fill(boolean val) {

		long setter = (val ? -1L : 0L);
		
		for (int i = 0; i < longs.length; ++i) {
			longs[i] = setter;
		}

		if (val) cleanupLastLong();
	}

	// +--------------+
	// | getTrueCount |
	// +--------------+

	public int getTrueCount() {
		int count = 0;
		for (long l : longs) count += Long.bitCount(l);
		return(count);
	}

	// +-------------+
	// | getRelative |
	// +-------------+

	public static enum EdgeStrategy
	{
		On,
		Off,
		Wrap;
	}

	public boolean getRelative(int x, int y, 
							   int dxOffset, int dyOffset,
							   EdgeStrategy edgeStrategy) {

		int xTarget = x + dxOffset;
		int yTarget = y + dyOffset;
		
		int dx = getDx();
		int dy = getDy();

		if (xTarget < 0 || xTarget >= dx ||
			yTarget < 0 || yTarget >= dy) {
			
			if (edgeStrategy == EdgeStrategy.On) return(true);
			if (edgeStrategy == EdgeStrategy.Off) return(false);
		}

		int xFinal = xTarget;
		int yFinal = yTarget;
		
		if (xTarget < 0) { while (xFinal < 0) xFinal += dx; }
		else if (xTarget > dx) { while (xFinal >= dx) xFinal -= dx; }
		
		if (yTarget < 0) { while (yFinal < 0) yFinal += dy; }
		else if (yTarget > dy) { while (yFinal >= dy) yFinal -= dy; }

		return(get(xFinal, yFinal));
	}

	// +--------------+
	// | randomize    |
	// | setOneRandom |
	// | seed         |
	// +--------------+

	public void randomize() {

		for (int i = 0; i < longs.length; ++i) {
			longs[i] = rand.nextLong();
		}

		cleanupLastLong();
	}

	public void setOneRandom(boolean val) {
		
		int x = rand.nextInt(dx);
		int y = rand.nextInt(dy);

		set(x, y, val);
	}
	
	public void seed(Long seedVal) {
		rand = (seedVal == null ? new Random() : new Random(seedVal));
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private int getBitIndex(int x, int y) throws IllegalArgumentException {
		return((dx * y) + x);
	}

	private int getBitCount() {
		return(getBitCount(dx, dy));
	}

	protected static int getBitCount(int dx, int dy) {
		return(dx * dy);
	}

	protected static int getLongsNeeded(int dx, int dy) {
		int bitCount = getBitCount(dx, dy);
		int div = bitCount / Long.SIZE;
		int mod = bitCount % Long.SIZE;
		return(div + (mod == 0 ? 0 : 1));
	}

	private void cleanupLastLong() {
		
		// make sure unused bits are empty
		
		int meaningfulBitsInLastLong = getBitCount() % Long.SIZE;
		if (meaningfulBitsInLastLong == 0) return;
		
		long mask = 0L;
		for (int i = 0; i < meaningfulBitsInLastLong; ++i) {
			mask <<= 1; mask |= 1L;
		}

		longs[longs.length-1] &= mask;
	}
	
	// +---------+
	// | Members |
	// +---------+

	private int dx;
	private int dy;
	protected long[] longs; // package-accessible

	private Random rand = new Random();

	private final static Logger log = Logger.getLogger(Bitmap.class.getName());
}
