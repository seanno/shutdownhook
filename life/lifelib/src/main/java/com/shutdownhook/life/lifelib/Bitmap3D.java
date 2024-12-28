//
// BITMAP3D.JAVA
//

package com.shutdownhook.life.lifelib;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

public class Bitmap3D
{
	// +-------+
	// | Setup |
	// +-------+

	public Bitmap3D(int dx, int dy) throws IllegalArgumentException {
		this(dx, dy, 1, null);
	}

	public Bitmap3D(int dx, int dy, int dz) throws IllegalArgumentException {
		this(dx, dy, dz, null);
	}

	protected Bitmap3D(int dx, int dy, int dz, long[] longs) throws IllegalArgumentException {

		if (dx < 1 || dy < 1 || dz < 1) {
			String msg = String.format("Invalid dimensions %d,%d,%d", dx, dy, dz);
			throw new IllegalArgumentException(msg);
		}
			
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;

		if (longs != null) {
			this.longs = longs;
			return;
		}
		
		this.longs = new long[getLongsNeeded(dx, dy, dz)];
	}

	// +-------------------+
	// | Simple Properties |
	// +-------------------+

	public int getDx() { return(dx); }
	public int getDy() { return(dy); }
	public int getDz() { return(dz); }

	public long[] getAsDNA() { return(longs); }
	
	// +------------------+
	// | get / set / fill |
	// +------------------+

	public boolean get(int x, int y) {
		return(get(x, y, 0));
	}

	public boolean get(int x, int y, int z) {
		
		int bitIndex = getBitIndex(x, y, z);
		int ilong = bitIndex / Long.SIZE;
		int ibit = bitIndex % Long.SIZE;

		return((longs[ilong] & (1L << ibit)) != 0);
	}

	public void set(int x, int y, boolean val) {
		set(x, y, 0, val);
	}

	public void set(int x, int y, int z, boolean val) {

		int bitIndex = getBitIndex(x, y, z);
		int ilong = bitIndex / Long.SIZE;
		int ibit = bitIndex % Long.SIZE;

		if (val) { longs[ilong] ^= (1L << ibit); }
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

	// +-----------------+
	// | getNeighborhood |
	// +-----------------+

	// returns a 2D neighborhood in the z plane
	
	public enum EdgeStrategy { On, Off, Wrap }
	
	public Bitmap3D getNeighborhood(int x, int y, int radius,
									EdgeStrategy edgeStrategy) {
		
		return(getNeighborhood(x, y, 0, radius, edgeStrategy));
	}
	
	public Bitmap3D getNeighborhood(int x, int y, int z, int radius,
									EdgeStrategy edgeStrategy) {

		int width = (radius * 2) + 1;
		Bitmap3D neighborhood = new Bitmap3D(width, width);

		for (int xDst = 0; xDst < width; ++xDst) {
			for (int yDst = 0; yDst < width; ++yDst) {

				int xSrc = x - radius + xDst;
				int ySrc = y - radius + yDst;

				boolean val = getWithEdgeStrategy(xSrc, ySrc, z, edgeStrategy);
				neighborhood.set(xDst, yDst, val);
			}
		}

		return(neighborhood);
	}

	private boolean getWithEdgeStrategy(int x, int y, int z,
										EdgeStrategy edgeStrategy) {

		int xReal = x;
		int yReal = y;

		int dx = getDx();
		int dy = getDy();

		if (x < 0 || x >= dx) {
			if (edgeStrategy == EdgeStrategy.On) return(true);
			if (edgeStrategy == EdgeStrategy.Off) return(false);
			
			if (x < 0) { while (xReal < 0) xReal += dx; }
			else { while (xReal >= dx) xReal -= dx; }
		}

		if (y < 0 || y >= dy) {
			if (edgeStrategy == EdgeStrategy.On) return(true);
			if (edgeStrategy == EdgeStrategy.Off) return(false);

			if (y < 0) { while (yReal < 0) yReal += dy; }
			else { while (yReal >= dy) yReal -= dy; }
		}

		// System.out.println(String.format("GETTING %d,%d for %d,%d,%d (%s)",
		//								 xReal, yReal, x, y, z, get(xReal, yReal, z)));
		
		return(get(xReal, yReal, z));
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
		int z = rand.nextInt(dz);

		set(x, y, z, val);
	}
	
	public void seed(Long seedVal) {
		rand = (seedVal == null ? new Random() : new Random(seedVal));
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private int getBitIndex(int x, int y, int z) throws IllegalArgumentException {
		return((dx * dy * z) + (dx * y) + x);
	}

	private int getBitCount() {
		return(getBitCount(dx, dy, dz));
	}

	protected static int getBitCount(int dx, int dy, int dz) {
		return(dx * dy * dz);
	}

	protected static int getLongsNeeded(int dx, int dy, int dz) {
		int bitCount = getBitCount(dx, dy, dz);
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
	private int dz;
	protected long[] longs; // package-accessible

	private Random rand = new Random();

	private final static Logger log = Logger.getLogger(Bitmap3D.class.getName());
}
